"""NEXUS PC Remote 2.0 — authenticated local Windows companion."""
from __future__ import annotations

import ctypes
import hmac
import io
import ipaddress
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import threading
import time
import urllib.parse
from collections import defaultdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT = 8765
VERSION = "2.0.0"
DATA_DIR = Path(os.environ.get("NEXUS_DATA_DIR", str(Path.home() / ".nexus_remote")))
DATA_DIR.mkdir(parents=True, exist_ok=True)
ROOT = Path.home() / "Downloads" / "NEXUS PC Remote"
ROOT.mkdir(parents=True, exist_ok=True)
AUTH_FILE = DATA_DIR / "auth.json"
MAX_UPLOAD = 512 * 1024 * 1024
MAX_TEXT = 16 * 1024
PIN = f"{secrets.randbelow(1_000_000):06d}"
ATTEMPTS = defaultdict(list)
ATTEMPTS_LOCK = threading.Lock()
INPUT_LOCK = threading.Lock()

def _read_token():
    try:
        t = json.loads(AUTH_FILE.read_text(encoding="utf-8")).get("token", "")
        if isinstance(t, str) and len(t) >= 32:
            return t
    except (OSError, ValueError, TypeError):
        pass
    t = secrets.token_urlsafe(32)
    AUTH_FILE.write_text(json.dumps({"token": t}), encoding="utf-8")
    return t

TOKEN = _read_token()
USER32 = ctypes.windll.user32 if os.name == "nt" else None
KEYS = {
    "ENTER": 0x0D, "ESC": 0x1B, "SPACE": 0x20, "TAB": 0x09,
    "BACKSPACE": 0x08, "UP": 0x26, "DOWN": 0x28, "LEFT": 0x25, "RIGHT": 0x27,
    "VOLUME_UP": 0xAF, "VOLUME_DOWN": 0xAE, "VOLUME_MUTE": 0xAD,
    "MEDIA_PLAY": 0xB3, "MEDIA_NEXT": 0xB0, "MEDIA_PREV": 0xB1,
    "WIN": 0x5B, "F5": 0x74,
}
GAMEPAD = None
VG = None
if os.name == "nt":
    try:
        import vgamepad as VG
        GAMEPAD = VG.VX360Gamepad()
    except Exception:
        GAMEPAD = None
GAME_BUTTONS = {
    "A": "XUSB_GAMEPAD_A", "B": "XUSB_GAMEPAD_B",
    "X": "XUSB_GAMEPAD_X", "Y": "XUSB_GAMEPAD_Y",
    "LB": "XUSB_GAMEPAD_LEFT_SHOULDER",
    "RB": "XUSB_GAMEPAD_RIGHT_SHOULDER",
    "START": "XUSB_GAMEPAD_START", "BACK": "XUSB_GAMEPAD_BACK",
    "UP": "XUSB_GAMEPAD_DPAD_UP", "DOWN": "XUSB_GAMEPAD_DPAD_DOWN",
    "LEFT": "XUSB_GAMEPAD_DPAD_LEFT", "RIGHT": "XUSB_GAMEPAD_DPAD_RIGHT",
}

def press(vk):
    if USER32 is None:
        raise RuntimeError("Comandos do teclado exigem Windows.")
    with INPUT_LOCK:
        USER32.keybd_event(vk, 0, 0, 0)
        USER32.keybd_event(vk, 0, 2, 0)

def send_text(s):
    if USER32 is None:
        raise RuntimeError("Teclado exige Windows.")
    from ctypes import wintypes
    class KEYBDINPUT(ctypes.Structure):
        _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD),
                    ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD),
                    ("dwExtraInfo", ctypes.c_size_t)]
    class UNION(ctypes.Union):
        _fields_ = [("ki", KEYBDINPUT)]
    class INPUT(ctypes.Structure):
        _fields_ = [("type", wintypes.DWORD), ("u", UNION)]
    with INPUT_LOCK:
        for unit in s.encode("utf-16-le"):
            pass
        data = s.encode("utf-16-le")
        for i in range(0, len(data), 2):
            code = int.from_bytes(data[i:i+2], "little")
            for flag in (0x0004, 0x0004 | 0x0002):
                event = INPUT(1, UNION(ki=KEYBDINPUT(0, code, flag, 0, 0)))
                if USER32.SendInput(1, ctypes.byref(event), ctypes.sizeof(INPUT)) != 1:
                    raise OSError("Falha ao digitar no Windows.")

def move_mouse(dx, dy):
    if USER32 is None:
        raise RuntimeError("Mouse exige Windows.")
    dx = max(-500, min(500, int(dx)))
    dy = max(-500, min(500, int(dy)))
    USER32.mouse_event(0x0001, dx, dy, 0, 0)

def click_mouse(button):
    if USER32 is None:
        raise RuntimeError("Mouse exige Windows.")
    flags = {"left": (0x0002, 0x0004), "right": (0x0008, 0x0010),
             "middle": (0x0020, 0x0040)}
    if button not in flags:
        raise ValueError("Botão de mouse inválido")
    down, up = flags[button]
    USER32.mouse_event(down, 0, 0, 0, 0)
    USER32.mouse_event(up, 0, 0, 0, 0)

def wheel(steps):
    if USER32 is None:
        raise RuntimeError("Mouse exige Windows.")
    USER32.mouse_event(0x0800, 0, 0, max(-1200, min(1200, int(steps))), 0)

def gamepad_button(payload):
    if GAMEPAD is None or VG is None:
        raise RuntimeError("XInput indisponível. Instale vgamepad e ViGEmBus no PC.")
    name = str(payload.get("button", "")).upper()
    state = payload.get("pressed")
    if name not in GAME_BUTTONS or not isinstance(state, bool):
        raise ValueError("Comando de controle inválido")
    flag = getattr(VG.XUSB_BUTTON, GAME_BUTTONS[name])
    with INPUT_LOCK:
        if state:
            GAMEPAD.press_button(button=flag)
        else:
            GAMEPAD.release_button(button=flag)
        GAMEPAD.update()

def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 80))
        return s.getsockname()[0]
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"
    finally:
        s.close()

def launch_app(name):
    allowed = {"steam", "chrome", "explorer", "settings", "taskmgr", "desktop"}
    if name not in allowed:
        raise ValueError("Aplicativo não permitido")
    if os.name != "nt":
        raise RuntimeError("Abrir programas exige Windows.")
    if name == "desktop":
        USER32.keybd_event(0x5B, 0, 0, 0)
        press(0x44)
        USER32.keybd_event(0x5B, 0, 2, 0)
    elif name == "steam":
        os.startfile("steam://open/main")
    elif name == "chrome":
        subprocess.Popen(["cmd", "/c", "start", "", "chrome"], shell=False)
    elif name == "explorer":
        subprocess.Popen(["explorer.exe"])
    elif name == "settings":
        os.startfile("ms-settings:")
    elif name == "taskmgr":
        subprocess.Popen(["taskmgr.exe"])

def screenshot(quality=65):
    try:
        from PIL import ImageGrab
        image = ImageGrab.grab()
        if image.width > 1600:
            image.thumbnail((1600, 900))
        buffer = io.BytesIO()
        image.convert("RGB").save(buffer, "JPEG", quality=quality, optimize=True)
        return buffer.getvalue()
    except Exception:
        return b""

def safe_name(raw):
    name = urllib.parse.unquote(raw).replace("\\", "/").split("/")[-1].strip()
    if not name or name in {".", ".."} or len(name) > 160:
        raise ValueError("Nome de arquivo inválido")
    if re.search(r'[<>:"|?*\x00-\x1f]', name):
        raise ValueError("Nome de arquivo inválido")
    if name.endswith((".", " ")):
        raise ValueError("Nome de arquivo inválido")
    return name

def unique_destination(name):
    p = ROOT / name
    k = 1
    while p.exists():
        p = ROOT / f"{Path(name).stem} ({k}){Path(name).suffix}"
        k += 1
    return p

class Handler(BaseHTTPRequestHandler):
    server_version = "NEXUS/2.0"
    protocol_version = "HTTP/1.1"

    def send_bytes(self, payload=b"", code=200, kind="application/octet-stream"):
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    def response(self, value, code=200):
        self.send_bytes(json.dumps(value, ensure_ascii=False).encode("utf-8"), code, "application/json; charset=utf-8")

    def local_client(self):
        try:
            addr = ipaddress.ip_address(self.client_address[0])
            return addr.is_private or addr.is_loopback
        except ValueError:
            return False

    def authorized(self):
        if not self.local_client():
            self.response({"error": "Acesso restrito à rede local"}, 403)
            return False
        token = self.headers.get("X-Nexus-Token", "")
        if not hmac.compare_digest(token, TOKEN):
            self.response({"error": "Dispositivo não pareado"}, 401)
            return False
        return True

    def read_body(self, limit=MAX_TEXT):
        try:
            n = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            raise ValueError("Content-Length inválido")
        if n < 0 or n > limit:
            raise ValueError("Tamanho de requisição inválido")
        return self.rfile.read(n)

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path)
        if path.path == "/ping":
            return self.response({"name": "NEXUS PC Remote", "version": VERSION})
        if not self.authorized():
            return
        if path.path == "/status":
            return self.response({"connected": True, "version": VERSION,
                                  "computer": socket.gethostname(),
                                  "gamepad": GAMEPAD is not None})
        if path.path == "/apps":
            apps = [{"id": a, "name": n} for a, n in [
                ("steam", "Steam"), ("chrome", "Chrome"),
                ("explorer", "Explorador"), ("settings", "Configurações"),
                ("taskmgr", "Gerenciador"), ("desktop", "Área de trabalho")]]
            return self.response({"apps": apps})
        if path.path == "/screen.jpg":
            try:
                quality = max(30, min(85, int(urllib.parse.parse_qs(path.query).get("q", [65])[0])))
            except ValueError:
                return self.response({"error": "Qualidade inválida"}, 400)
            data = screenshot(quality)
            return self.send_bytes(data, 200 if data else 503, "image/jpeg" if data else "text/plain")
        if path.path == "/files":
            files = [{"name": p.name, "size": p.stat().st_size} for p in ROOT.iterdir() if p.is_file() and not p.name.startswith(".")]
            return self.response({"files": sorted(files, key=lambda f: f["name"].lower())[:300]})
        if path.path.startswith("/download/"):
            try:
                name = safe_name(path.path[len("/download/"):])
                file = ROOT / name
                if not file.is_file():
                    return self.response({"error": "Arquivo não encontrado"}, 404)
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(file.stat().st_size))
                self.send_header("Content-Disposition", "attachment")
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                with file.open("rb") as src:
                    shutil.copyfileobj(src, self.wfile, 64 * 1024)
                return
            except (ValueError, OSError) as exc:
                return self.response({"error": str(exc)}, 400)
        self.response({"error": "Rota não encontrada"}, 404)

    def do_POST(self):
        path = urllib.parse.urlsplit(self.path).path
        if not self.local_client():
            return self.response({"error": "Acesso restrito à rede local"}, 403)
        if path == "/pair":
            with ATTEMPTS_LOCK:
                now = time.monotonic()
                ip = self.client_address[0]
                ATTEMPTS[ip] = [t for t in ATTEMPTS[ip] if now - t < 60]
                if len(ATTEMPTS[ip]) >= 5:
                    return self.response({"error": "Aguarde 1 minuto antes de tentar novamente"}, 429)
            try:
                candidate = self.read_body(64).decode("utf-8").strip()
            except (ValueError, UnicodeError) as exc:
                return self.response({"error": str(exc)}, 400)
            if hmac.compare_digest(candidate, PIN):
                with ATTEMPTS_LOCK:
                    ATTEMPTS.pop(ip, None)
                return self.response({"token": TOKEN, "version": VERSION})
            with ATTEMPTS_LOCK:
                ATTEMPTS[ip].append(time.monotonic())
            return self.response({"error": "Código de pareamento incorreto"}, 401)
        if not self.authorized():
            return
        try:
            if path.startswith("/upload/"):
                name = safe_name(path[len("/upload/"):])
                try:
                    length = int(self.headers.get("Content-Length", "-1"))
                except ValueError:
                    raise ValueError("Tamanho inválido")
                if length < 0 or length > MAX_UPLOAD:
                    return self.response({"error": "Limite de 512 MB por arquivo"}, 413)
                temp = ROOT / (".upload-" + secrets.token_hex(12))
                try:
                    remaining = length
                    with temp.open("wb") as dst:
                        while remaining:
                            data = self.rfile.read(min(64 * 1024, remaining))
                            if not data:
                                raise ConnectionError("Envio interrompido")
                            dst.write(data)
                            remaining -= len(data)
                    dest = unique_destination(name)
                    temp.replace(dest)
                finally:
                    temp.unlink(missing_ok=True)
                return self.response({"ok": True, "name": dest.name})
            raw = self.read_body()
            value = raw.decode("utf-8")
            if path == "/key":
                if value not in KEYS:
                    raise ValueError("Tecla não permitida")
                press(KEYS[value])
            elif path == "/text":
                send_text(value[:2000])
            elif path == "/mouse":
                x, y = value.split(",", 1)
                move_mouse(int(x), int(y))
            elif path == "/click":
                click_mouse(value)
            elif path == "/scroll":
                wheel(int(value))
            elif path == "/launch":
                launch_app(value)
            elif path == "/gamepad":
                gamepad_button(json.loads(value))
            elif path == "/power":
                if os.name != "nt":
                    raise RuntimeError("Comando disponível apenas no Windows")
                if value == "shutdown":
                    subprocess.Popen(["shutdown", "/s", "/t", "10"])
                elif value == "restart":
                    subprocess.Popen(["shutdown", "/r", "/t", "10"])
                else:
                    raise ValueError("Comando de energia inválido")
            else:
                return self.response({"error": "Rota não encontrada"}, 404)
            self.response({"ok": True})
        except (ValueError, UnicodeError, json.JSONDecodeError) as exc:
            self.response({"error": str(exc)}, 400)
        except Exception as exc:
            self.response({"error": str(exc)}, 500)

    def log_message(self, fmt, *args):
        if os.environ.get("NEXUS_VERBOSE") == "1":
            super().log_message(fmt, *args)

def run():
    print("=== NEXUS PC Remote 2.0 ===", flush=True)
    print("IP:   ", local_ip(), flush=True)
    print("Porta:", PORT, flush=True)
    print("PIN:  ", PIN, flush=True)
    print("Pasta:", ROOT, flush=True)
    print("XInput:", "pronto" if GAMEPAD is not None else "opcional (vgamepad + ViGEmBus)", flush=True)
    print("Não encaminhe a porta 8765 para a Internet.", flush=True)
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    server.serve_forever()

if __name__ == "__main__":
    run()
