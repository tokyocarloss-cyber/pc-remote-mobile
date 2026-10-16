import ctypes, io, os, shutil, socket, struct, subprocess, sys, threading, time, urllib.parse, urllib.request, winreg
from pathlib import Path
from http.server import ThreadingHTTPServer

try:
    import pystray
except Exception:
    pystray = None
try:
    import mss
except Exception:
    mss = None
from PIL import Image, ImageDraw
from recording_engine import PC_RECORDER, PHONE_RECORDER, unique_output
from recording_dashboard import RecorderDashboard

import json
import entertainment_hub as entertainment
import steamrip_catalog
import pc_remote_server as servermod
from pc_remote_server import PORT, DISCOVERY_PORT, ROOT, GAMEPAD, local_ip, phone_connected, run_discovery_responder
from drop_edge import DropEdge

CREATE_NO_WINDOW = 0x08000000
TCP_RULE = 'NEXUS PC Remote TCP v4'
UDP_RULE = 'NEXUS PC Remote UDP v4'
RECEIVED_DIR = Path.home() / 'Downloads'
LIBRARY_DIR = Path.home() / 'Desktop' / 'Alll'
RECEIVED_DIR.mkdir(parents=True, exist_ok=True)
server = None
edge = None
tray_icon = None
server_error = ''
AUTOSTART_VALUE = 'NEXUS PC Remote'
PREFS_KEY = r'Software\\NEXUS PC Remote'

def startup_command():
    exe = Path(sys.executable if getattr(sys, 'frozen', False) else __file__).resolve()
    return f'"{exe}"'

def autostart_enabled():
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r'Software\\Microsoft\\Windows\\CurrentVersion\\Run') as key:
            value, _ = winreg.QueryValueEx(key, AUTOSTART_VALUE)
            return bool(value)
    except Exception:
        return False

def set_autostart(enabled):
    try:
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, r'Software\\Microsoft\\Windows\\CurrentVersion\\Run') as key:
            if enabled:
                winreg.SetValueEx(key, AUTOSTART_VALUE, 0, winreg.REG_SZ, startup_command())
            else:
                try:
                    winreg.DeleteValue(key, AUTOSTART_VALUE)
                except FileNotFoundError:
                    pass
        return True
    except Exception:
        return False

def first_run_autostart_prompt():
    try:
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, PREFS_KEY) as key:
            try:
                asked, _ = winreg.QueryValueEx(key, 'AskedAutostart')
                if int(asked) == 1:
                    return
            except Exception:
                pass
            answer = ctypes.windll.user32.MessageBoxW(
                0,
                'Deseja iniciar o NEXUS PC Remote automaticamente sempre que este usuário entrar no Windows?\\n\\nVocê poderá mudar isso depois pelo ícone do NEXUS na bandeja.',
                'NEXUS PC Remote',
                0x24
            )
            if answer == 6:
                set_autostart(True)
            winreg.SetValueEx(key, 'AskedAutostart', 0, winreg.REG_DWORD, 1)
    except Exception:
        pass

FRAME_BYTES = b''
FRAME_SEQ = 0
FRAME_LOCK = threading.Lock()
# The network sender waits for a new capture instead of busy-polling.
FRAME_READY = threading.Condition(FRAME_LOCK)
CAPTURE_ERROR = ''
CAPTURE_SOURCE = 'iniciando'
CAPTURE_FPS = 0.0
CAPTURE_ENCODE_MS = 0.0
CAPTURE_WIDTH = 0
CAPTURE_HEIGHT = 0
FRAME_LAST_AT = 0.0
RECORDING = False
RECORD_STARTED_AT = 0.0
RECORDER_DASHBOARD = None
PHONE_FRAME_LOCK = threading.Lock()
PHONE_FRAME_BYTES = b''
PHONE_FRAME_AT = 0.0
PHONE_PROFILE_LOCK = threading.Lock()
PHONE_PROFILE = dict(width=1280, height=720, fps=10, quality=75)


def phone_frame_snapshot():
    with PHONE_FRAME_LOCK:
        if time.monotonic() - PHONE_FRAME_AT > 3.0:
            return None
        return PHONE_FRAME_BYTES or None


def get_phone_profile():
    with PHONE_PROFILE_LOCK:
        return PHONE_PROFILE.copy()


def set_phone_profile(width, height, fps, quality):
    from recording_engine import profile
    width, height, fps, quality = profile(width, height, fps, quality)
    with PHONE_PROFILE_LOCK:
        PHONE_PROFILE.update(width=width, height=height, fps=fps, quality=quality)


def capture_pc_image():
    if mss is None:
        from PIL import ImageGrab
        return ImageGrab.grab().convert('RGB')
    with mss.mss() as capture:
        shot = capture.grab(capture.monitors[1])
        # Clone the buffer while the MSS context is valid.
        return Image.frombytes('RGB', shot.size, shot.rgb)


def screenshot_to_file(device):
    if device == 'phone':
        data = phone_frame_snapshot()
        if not data:
            raise RuntimeError('Inicie o compartilhamento no Android primeiro')
        with Image.open(io.BytesIO(data)) as frame:
            picture = frame.convert('RGB')
    elif device == 'pc':
        picture = capture_pc_image()
    else:
        raise ValueError('Dispositivo inválido')
    target = unique_output(device + '-print', 'png')
    picture.save(target, 'PNG')
    return str(target)



def hidden_startup():
    try:
        si = subprocess.STARTUPINFO()
        si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        si.wShowWindow = 0
        return si
    except Exception:
        return None


def hidden_kwargs():
    return dict(
        creationflags=CREATE_NO_WINDOW,
        startupinfo=hidden_startup(),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )


def silent_resolve_shortcut(path):
    escaped = str(path).replace("'", "''")
    cmd = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" + escaped + "');$s.TargetPath"
    try:
        return subprocess.check_output(
            ['powershell.exe', '-NoLogo', '-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', cmd],
            text=True, encoding='utf-8', errors='ignore', timeout=4,
            creationflags=CREATE_NO_WINDOW, startupinfo=hidden_startup(), stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return ''


def hidden_popen(args, cwd=None):
    return subprocess.Popen(args, cwd=cwd, **hidden_kwargs())


def safe_launch(name):
    if name not in servermod.APP_INDEX:
        servermod.apps_catalog()
    item = servermod.APP_INDEX.get(name)
    if not item:
        return
    steam_id = item.get('steam_id')
    if steam_id:
        os.startfile('steam://rungameid/' + str(steam_id)); return
    shortcut = item.get('shortcut')
    if shortcut and Path(shortcut).exists():
        p = Path(shortcut)
        if p.suffix.lower() in ('.bat', '.cmd'):
            hidden_popen(['cmd.exe', '/d', '/s', '/c', str(p)], cwd=str(p.parent)); return
        os.startfile(str(p)); return
    cmd = item.get('launch_cmd')
    if cmd == '__desktop__':
        u = ctypes.windll.user32
        u.keybd_event(0x5B, 0, 0, 0); servermod.press(0x44); u.keybd_event(0x5B, 0, 2, 0); return
    if not cmd:
        return
    value = str(cmd).strip()
    if '://' in value or value.startswith('ms-settings:'):
        os.startfile(value); return
    if value.lower() == 'explorer':
        hidden_popen(['explorer.exe']); return
    if value.lower() == 'taskmgr':
        hidden_popen(['taskmgr.exe']); return
    exe = shutil.which(value) or value
    try:
        hidden_popen([exe])
    except Exception:
        hidden_popen(['cmd.exe', '/d', '/s', '/c', value])


# Keep the native Win32 clipboard implementation from pc_remote_server.py.
# It avoids PowerShell/CMD flashes when sending text from the phone.
servermod.resolve_shortcut = silent_resolve_shortcut
servermod.launch = safe_launch

_ORIGINAL_APPS_CATALOG = servermod.apps_catalog
_ORIGINAL_APP_ICON = servermod.app_icon


def apps_catalog_with_alll():
    items = _ORIGINAL_APPS_CATALOG()
    seen = {str(x.get('name', '')).casefold() for x in items}
    try:
        if LIBRARY_DIR.exists():
            for p in sorted(LIBRARY_DIR.rglob('*'), key=lambda x: x.name.casefold()):
                if len(items) >= 420:
                    break
                if not p.is_file() or p.suffix.lower() not in ('.lnk', '.url', '.exe', '.bat', '.cmd'):
                    continue
                name = p.stem.strip()
                if not name or name.casefold() in seen:
                    continue
                items.append({'name': name, 'store': 'Alll'})
                servermod.APP_INDEX[name] = {
                    'shortcut': str(p), 'launch_cmd': None, 'steam_id': None,
                    'icon_hint': str(p), 'store': 'Alll'
                }
                seen.add(name.casefold())
    except Exception:
        pass
    return items


def better_app_icon(name):
    if name not in servermod.APP_INDEX:
        apps_catalog_with_alll()
    item = servermod.APP_INDEX.get(name)
    if not item:
        return None, None
    cache = servermod.ICON_DIR / (servermod.hashlib.sha1(name.encode('utf-8')).hexdigest() + '.png')
    if cache.exists() and cache.stat().st_size > 100:
        return cache.read_bytes(), 'image/png'

    candidates = []
    hint = item.get('icon_hint')
    shortcut = item.get('shortcut')
    if hint:
        candidates.append(str(hint))
    if shortcut:
        try:
            target = silent_resolve_shortcut(shortcut)
            if target:
                candidates.append(target)
        except Exception:
            pass
        candidates.append(str(shortcut))

    for value in candidates:
        try:
            p = Path(value)
            if p.is_file() and p.suffix.lower() in ('.png', '.jpg', '.jpeg', '.webp'):
                typ = 'image/png' if p.suffix.lower() == '.png' else 'image/jpeg'
                return p.read_bytes(), typ
        except Exception:
            pass

    for value in candidates:
        try:
            p = Path(value)
            if not p.exists():
                continue
            src = str(p).replace("'", "''")
            dst = str(cache).replace("'", "''")
            ps = (
                "Add-Type -AssemblyName System.Drawing;"
                "$i=[System.Drawing.Icon]::ExtractAssociatedIcon('" + src + "');"
                "if($i){$b=$i.ToBitmap();$b.Save('" + dst + "',[System.Drawing.Imaging.ImageFormat]::Png);$b.Dispose();$i.Dispose()}"
            )
            subprocess.run(
                ['powershell.exe', '-NoLogo', '-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', ps],
                timeout=5, creationflags=CREATE_NO_WINDOW, startupinfo=hidden_startup(),
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
            )
            if cache.exists() and cache.stat().st_size > 100:
                return cache.read_bytes(), 'image/png'
        except Exception:
            pass
    try:
        return _ORIGINAL_APP_ICON(name)
    except Exception:
        return None, None


servermod.apps_catalog = apps_catalog_with_alll
servermod.app_icon = better_app_icon


def encode_frame(im):
    w = max(640, min(2560, int(servermod.SCREEN_WIDTH)))
    h = max(360, min(1440, int(servermod.SCREEN_HEIGHT)))
    if im.width > w or im.height > h:
        im.thumbnail((w, h), Image.Resampling.BILINEAR)
    b = io.BytesIO()
    im.save(b, 'JPEG', quality=max(35, min(90, int(servermod.SCREEN_QUALITY))), optimize=False)
    return b.getvalue()


def capture_loop():
    """Encode at the requested profile, keeping only the freshest compressed frame.

    Capturing and encoding are serial; the stream threads cannot queue old captures.
    Actual FPS is measured rather than inferred from the requested FPS.
    """
    global FRAME_BYTES, FRAME_SEQ, CAPTURE_ERROR, CAPTURE_SOURCE
    global CAPTURE_FPS, CAPTURE_ENCODE_MS, CAPTURE_WIDTH, CAPTURE_HEIGHT, FRAME_LAST_AT
    count = 0
    sample_started = time.perf_counter()

    def publish(data, width, height, encoded_ms):
        nonlocal count, sample_started
        global FRAME_BYTES, FRAME_SEQ, CAPTURE_FPS, CAPTURE_ENCODE_MS
        global CAPTURE_WIDTH, CAPTURE_HEIGHT, FRAME_LAST_AT
        now = time.perf_counter()
        count += 1
        measured_fps = None
        elapsed = now - sample_started
        if elapsed >= 1.0:
            measured_fps = count / elapsed
            count = 0
            sample_started = now
        with FRAME_READY:
            FRAME_BYTES = data
            FRAME_SEQ += 1
            CAPTURE_WIDTH, CAPTURE_HEIGHT = width, height
            CAPTURE_ENCODE_MS = round(encoded_ms, 1)
            FRAME_LAST_AT = time.monotonic()
            if measured_fps is not None:
                CAPTURE_FPS = round(measured_fps, 1)
            FRAME_READY.notify_all()

    while True:
        started = time.perf_counter()
        try:
            if mss is None:
                raise RuntimeError('MSS indisponível')
            with mss.mss() as sct:
                monitor = sct.monitors[1]
                while True:
                    started = time.perf_counter()
                    shot = sct.grab(monitor)
                    # BGRX avoids the additional conversion/copy made by shot.rgb.
                    try:
                        im = Image.frombuffer('RGB', shot.size, shot.bgra, 'raw', 'BGRX', 0, 1)
                    except (ValueError, TypeError):
                        im = Image.frombytes('RGB', shot.size, shot.rgb)
                    data = encode_frame(im)
                    if data:
                        publish(data, im.width, im.height, (time.perf_counter() - started) * 1000)
                    CAPTURE_SOURCE = 'MSS'
                    CAPTURE_ERROR = ''
                    target = max(15, min(120, int(servermod.SCREEN_TARGET_FPS)))
                    wait = (1.0 / target) - (time.perf_counter() - started)
                    if wait > 0:
                        time.sleep(wait)
        except Exception as e:
            CAPTURE_ERROR = str(e)[:200]
            CAPTURE_SOURCE = 'fallback'
            started = time.perf_counter()
            try:
                data = servermod.screenshot_original()
                if data:
                    publish(data, 0, 0, (time.perf_counter() - started) * 1000)
            except Exception as fallback_error:
                CAPTURE_ERROR = f'{CAPTURE_ERROR}; fallback: {fallback_error}'[:200]
            target = max(10, min(30, int(servermod.SCREEN_TARGET_FPS)))
            wait = (1.0 / target) - (time.perf_counter() - started)
            if wait > 0:
                time.sleep(wait)


def fast_screenshot():
    with FRAME_LOCK:
        data = FRAME_BYTES
    if data:
        return data
    try:
        return servermod.screenshot_original()
    except Exception:
        return b''


def unique_target(name):
    target = RECEIVED_DIR / Path(name).name
    stem, suffix, n = target.stem, target.suffix, 1
    while target.exists() or target.with_name(target.name + '.nexus-part').exists():
        target = RECEIVED_DIR / f'{stem}_{n}{suffix}'; n += 1
    return target


def notify_received(path):
    try:
        size = path.stat().st_size
        txt = f'{path.name} • {max(1, size // 1024)} KB'
        if tray_icon:
            tray_icon.notify(txt, 'Arquivo recebido do celular')
    except Exception:
        pass


def cleanup_legacy_staging():
    try:
        for p in ROOT.iterdir():
            if not p.is_file():
                continue
            try:
                if p.stat().st_size <= 0:
                    p.unlink(missing_ok=True)
                else:
                    target = unique_target(p.name)
                    shutil.move(str(p), str(target))
            except Exception:
                pass
    except Exception:
        pass


def power_action(value):
    if value == 'shutdown':
        hidden_popen(['shutdown.exe', '/s', '/t', '0'])
    elif value == 'restart':
        hidden_popen(['shutdown.exe', '/r', '/t', '0'])
    elif value == 'sleep':
        try:
            ctypes.windll.powrprof.SetSuspendState(False, True, False)
        except Exception:
            hidden_popen(['rundll32.exe', 'powrprof.dll,SetSuspendState', '0,1,0'])


def allowed_pc_path(raw):
    home = Path.home().resolve()
    value = urllib.parse.unquote_plus(str(raw or '')).strip()
    p = Path(value).expanduser() if value else home
    if not p.is_absolute():
        p = home / p
    p = p.resolve()
    if p == home or home in p.parents:
        return p
    raise PermissionError('Fora da pasta do usuário')


def file_roots():
    home = Path.home()
    roots = []
    for name, p in [
        ('Início', home),
        ('Área de Trabalho', home / 'Desktop'),
        ('Downloads', home / 'Downloads'),
        ('Documentos', home / 'Documents'),
        ('Imagens', home / 'Pictures'),
        ('Vídeos', home / 'Videos')
    ]:
        if p.exists():
            roots.append({'name': name, 'path': str(p), 'dir': True, 'size': 0})
    return roots


def list_pc_files(raw_path):
    if not raw_path:
        return {'path': '', 'parent': '', 'items': file_roots()}
    p = allowed_pc_path(raw_path)
    if not p.exists() or not p.is_dir():
        raise FileNotFoundError(str(p))
    home = Path.home().resolve()
    parent = '' if p == home else str(p.parent)
    items = []
    for item in sorted(p.iterdir(), key=lambda x: (not x.is_dir(), x.name.casefold())):
        try:
            st = item.stat()
            items.append({
                'name': item.name,
                'path': str(item),
                'dir': item.is_dir(),
                'size': 0 if item.is_dir() else st.st_size,
                'mtime': int(st.st_mtime)
            })
            if len(items) >= 250:
                break
        except Exception:
            pass
    return {'path': str(p), 'parent': parent, 'items': items}


def recent_pc_files():
    found = []
    home = Path.home()
    for root in [home / 'Downloads', home / 'Desktop', home / 'Pictures', home / 'Videos']:
        if not root.exists():
            continue
        try:
            for p in root.rglob('*'):
                if p.is_file():
                    try:
                        st = p.stat()
                        found.append((st.st_mtime, p, st.st_size))
                    except Exception:
                        pass
                if len(found) > 1200:
                    break
        except Exception:
            pass
    found.sort(key=lambda x: x[0], reverse=True)
    return [
        {'name': p.name, 'path': str(p), 'dir': False, 'size': size, 'mtime': int(mtime)}
        for mtime, p, size in found[:60]
    ]


def queue_latest_recording():
    time.sleep(1.5)
    captures = Path.home() / 'Videos' / 'Captures'
    if not captures.exists():
        return
    try:
        candidates = sorted(captures.glob('*.mp4'), key=lambda p: p.stat().st_mtime, reverse=True)
        if not candidates:
            return
        src = candidates[0]
        if src.stat().st_mtime < RECORD_STARTED_AT - 5:
            return
        target = servermod.OUTBOX / src.name
        n = 1
        while target.exists():
            target = servermod.OUTBOX / f'{src.stem}_{n}{src.suffix}'
            n += 1
        shutil.copy2(src, target)
        if tray_icon:
            tray_icon.notify('Vídeo enviado para o celular', 'Gravação NEXUS concluída')
    except Exception:
        pass


def toggle_recording():
    """Backwards-compatible control, now using actual AVI recording (not Game Bar)."""
    if PC_RECORDER.status()['active']:
        PC_RECORDER.stop()
        return 'stopped'
    PC_RECORDER.start(capture_pc_image, 'pc', 1280, 720, 30, 75)
    return 'started'


def send_hotkey(name):
    u = ctypes.windll.user32
    combos = {
        'ALT_F4': ([0x12], 0x73),
        'ALT_TAB': ([0x12], 0x09),
        'WIN_D': ([0x5B], 0x44),
        'SNIP': ([0x5B, 0x10], 0x53),
        'CTRL_SHIFT_ESC': ([0x11, 0x10], 0x1B),
        'ESC': ([], 0x1B),
        'ENTER': ([], 0x0D)
    }
    mods, key = combos.get(name, ([], 0))
    if not key:
        return False
    for m in mods:
        u.keybd_event(m, 0, 0, 0)
    u.keybd_event(key, 0, 0, 0); u.keybd_event(key, 0, 2, 0)
    for m in reversed(mods):
        u.keybd_event(m, 0, 2, 0)
    return True


def absolute_mouse(raw):
    try:
        x, y = [float(v) for v in raw.decode().split(',', 1)]
        sw = ctypes.windll.user32.GetSystemMetrics(0)
        sh = ctypes.windll.user32.GetSystemMetrics(1)
        ctypes.windll.user32.SetCursorPos(
            int(max(0, min(1, x)) * (sw - 1)),
            int(max(0, min(1, y)) * (sh - 1))
        )
        return True
    except Exception:
        return False


def zoom_wheel(raw):
    try:
        delta = int(float(raw.decode()))
        delta = max(-1200, min(1200, delta))
    except Exception:
        return False
    u = ctypes.windll.user32
    u.keybd_event(0x11, 0, 0, 0)
    u.mouse_event(0x0800, 0, 0, delta, 0)
    u.keybd_event(0x11, 0, 2, 0)
    return True


class CompanionHandler(servermod.H):
    def entertainment_response(self, value, code=200):
        return self.sendb(json.dumps(value, ensure_ascii=False).encode('utf-8'),
                          'application/json; charset=utf-8', code)

    def entertainment_auth(self):
        if not self.allow_local():
            return False
        if not entertainment.authenticated(self.headers.get('X-Nexus-Central-Key', '')):
            self.entertainment_response({'error': 'Código da Central inválido'}, 401)
            return False
        return True

    def do_GET(self):
        if not self.allow_local():
            return
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        if path.startswith('/entertainment/'):
            if not self.entertainment_auth():
                return
            try:
                q = urllib.parse.parse_qs(parsed.query)
                if path == '/entertainment/status':
                    return self.entertainment_response({'ok': True, 'connected': True})
                if path == '/entertainment/search':
                    kind = q.get('type', [''])[0]
                    term = q.get('q', [''])[0]
                    return self.entertainment_response({'items': entertainment.search(kind, term)})
                if path == '/entertainment/library':
                    kind = q.get('type', [''])[0]
                    return self.entertainment_response({'items': entertainment.library(kind)})
                if path == '/entertainment/downloads':
                    return self.entertainment_response({'items': entertainment.list_downloads()})
                return self.entertainment_response({'error': 'Rota desconhecida'}, 404)
            except Exception as exc:
                return self.entertainment_response({'error': str(exc)[:170]}, 400)
        if path == '/fs/list':
            q = urllib.parse.parse_qs(parsed.query)
            raw_path = q.get('path', [''])[0]
            try:
                payload = servermod.json.dumps(list_pc_files(raw_path), ensure_ascii=False).encode('utf-8')
                return self.sendb(payload, 'application/json')
            except Exception as e:
                return self.sendb(str(e).encode('utf-8'), code=400)
        if path == '/fs/recent':
            payload = servermod.json.dumps(recent_pc_files(), ensure_ascii=False).encode('utf-8')
            return self.sendb(payload, 'application/json')
        if path == '/fs/download':
            q = urllib.parse.parse_qs(parsed.query)
            try:
                p = allowed_pc_path(q.get('path', [''])[0])
                if not p.is_file():
                    raise FileNotFoundError(str(p))
                return self.sendb(p.read_bytes(), 'application/octet-stream')
            except Exception as e:
                return self.sendb(str(e).encode('utf-8'), code=404)
        if path in ('/phone-screen/profile', '/phone-screen/status', '/record/status'):
            if not entertainment.authenticated(self.headers.get('X-Nexus-Central-Key','')):
                return self.sendb(b'Authorization required', code=401)
        if path == '/phone-screen/profile':
            return self.sendb(json.dumps(get_phone_profile()).encode('utf-8'), 'application/json')
        if path == '/phone-screen/status':
            with PHONE_FRAME_LOCK:
                age_ms = round((time.monotonic()-PHONE_FRAME_AT)*1000) if PHONE_FRAME_AT else None
            return self.sendb(json.dumps({'active': age_ms is not None and age_ms < 3000,
                'age_ms': age_ms, 'recording': PHONE_RECORDER.status()}).encode('utf-8'), 'application/json')
        if path == '/record/status':
            return self.sendb(json.dumps({'pc': PC_RECORDER.status(),
                'phone': PHONE_RECORDER.status(), 'phone_profile': get_phone_profile()}).encode('utf-8'), 'application/json')
        if path == '/capture.jpg':
            data = fast_screenshot()
            return self.sendb(data, 'image/jpeg', 200 if data else 503)
        if path == '/stream/status':
            with FRAME_READY:
                seq = FRAME_SEQ
                has_frame = bool(FRAME_BYTES)
                actual_width, actual_height = CAPTURE_WIDTH, CAPTURE_HEIGHT
                capture_fps, encode_ms = CAPTURE_FPS, CAPTURE_ENCODE_MS
                age_ms = round((time.monotonic() - FRAME_LAST_AT) * 1000) if FRAME_LAST_AT else None
            payload = servermod.json.dumps({
                'ok': has_frame and age_ms is not None and age_ms < 3000,
                'seq': seq,
                'source': CAPTURE_SOURCE,
                'error': CAPTURE_ERROR,
                'width': actual_width,
                'height': actual_height,
                'requested_width': servermod.SCREEN_WIDTH,
                'requested_height': servermod.SCREEN_HEIGHT,
                'target_fps': servermod.SCREEN_TARGET_FPS,
                'actual_capture_fps': capture_fps,
                'encode_ms': encode_ms,
                'frame_age_ms': age_ms,
                'jpeg_quality': servermod.SCREEN_QUALITY
            }).encode()
            return self.sendb(payload, 'application/json', 200 if has_frame and age_ms is not None and age_ms < 3000 else 503)
        if path != '/stream.raw':
            return super().do_GET()

        q = urllib.parse.parse_qs(parsed.query)
        try:
            servermod.SCREEN_WIDTH = max(640, min(2560, int(q.get('w', [servermod.SCREEN_WIDTH])[0])))
            servermod.SCREEN_HEIGHT = max(360, min(1440, int(q.get('h', [servermod.SCREEN_HEIGHT])[0])))
            servermod.SCREEN_QUALITY = max(35, min(90, int(q.get('q', [servermod.SCREEN_QUALITY])[0])))
            servermod.SCREEN_TARGET_FPS = max(15, min(120, int(q.get('fps', [servermod.SCREEN_TARGET_FPS])[0])))
        except Exception:
            pass
        servermod.mark_seen(self.client_address[0])
        self.send_response(200)
        self.send_header('Content-Type', 'application/x-nexus-jpeg-stream')
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.close_connection = True

        try:
            self.connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        except OSError:
            pass

        last_seq = -1
        try:
            while True:
                with FRAME_READY:
                    if FRAME_SEQ == last_seq:
                        FRAME_READY.wait(timeout=0.5)
                    # Obtain the newest frame *after* the wait. Slow clients
                    # skip old frames instead of growing a queue of stale video.
                    data, seq = FRAME_BYTES, FRAME_SEQ
                if not data or seq == last_seq:
                    continue
                self.wfile.write(struct.pack('>I', len(data)))
                self.wfile.write(data)
                self.wfile.flush()
                last_seq = seq
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            return


    def do_POST(self):
        if not self.allow_local():
            return
        path = urllib.parse.urlparse(self.path).path
        if path.startswith('/entertainment/'):
            if not self.entertainment_auth():
                return
            try:
                if int(self.headers.get('Content-Length', '0')) > 8192:
                    return self.entertainment_response({'error': 'Solicitação grande demais'}, 413)
                raw = self.body()
                data = json.loads(raw.decode('utf-8'))
                if not isinstance(data, dict):
                    raise ValueError('Objeto JSON esperado')
                if path == '/entertainment/dispatch':
                    result = entertainment.dispatch_task(
                        data.get('id', ''), data.get('action', ''),
                        data.get('payload', {}))
                elif path == '/entertainment/download':
                    result = entertainment.start_download(data.get('url', ''))
                elif path == '/entertainment/cancel':
                    result = entertainment.cancel_download(data.get('id', ''))
                elif path == '/entertainment/play':
                    result = entertainment.play_video(str(data.get('id', '')))
                elif path == '/entertainment/launch-game':
                    result = entertainment.launch_game(str(data.get('id', '')))
                elif path == '/entertainment/steam':
                    result = entertainment.steam_action(data.get('id', ''), data.get('action', ''))
                elif path == '/entertainment/steamrip':
                    result = steamrip_catalog.open_in_browser(
                        url=str(data.get('url', '')),
                        query=str(data.get('query', ''))
                    )
                elif path == '/entertainment/open':
                    result = entertainment.open_catalog_link(data.get('url', ''))
                else:
                    return self.entertainment_response({'error': 'Rota desconhecida'}, 404)
                servermod.mark_seen(self.client_address[0])
                return self.entertainment_response(result)
            except Exception as exc:
                return self.entertainment_response({'error': str(exc)[:170]}, 400)
        if path.startswith('/upload/'):
            try:
                raw = self.body()
            except ValueError as e:
                return self.sendb(str(e).encode(), code=413)
            if not raw:
                return self.sendb(b'Empty upload rejected', code=400)
            name = Path(urllib.parse.unquote_plus(path[8:])).name or 'arquivo'
            try:
                servermod.mark_seen(self.client_address[0])
                target = unique_target(name)
                part = target.with_name(target.name + '.nexus-part')
                with open(part, 'wb') as f:
                    f.write(raw); f.flush(); os.fsync(f.fileno())
                os.replace(part, target)
                notify_received(target)
                return self.sendb(b'OK')
            except Exception as e:
                try:
                    part.unlink(missing_ok=True)
                except Exception:
                    pass
                return self.sendb(str(e).encode(), code=500)
        if path in ('/phone-screen/frame', '/record/start', '/record/stop',
                    '/record/screenshot', '/record-toggle'):
            if not entertainment.authenticated(self.headers.get('X-Nexus-Central-Key','')):
                return self.sendb(b'Authorization required', code=401)
        if path == '/phone-screen/frame':
            global PHONE_FRAME_BYTES, PHONE_FRAME_AT
            try:
                if self.headers.get('Transfer-Encoding'):
                    return self.sendb(b'Explicit frame size required', code=400)
                length = int(self.headers.get('Content-Length', '-1'))
                if not 128 <= length <= 2500000:
                    return self.sendb(b'Invalid JPEG length', code=413)
                frame = self.rfile.read(length)
                if len(frame) != length or not frame.startswith(b'\xff\xd8') or not frame.endswith(b'\xff\xd9'):
                    return self.sendb(b'Invalid JPEG', code=400)
                with PHONE_FRAME_LOCK:
                    PHONE_FRAME_BYTES = frame
                    PHONE_FRAME_AT = time.monotonic()
                servermod.mark_seen(self.client_address[0])
                return self.sendb(b'OK')
            except Exception:
                return self.sendb(b'Invalid phone frame', code=400)
        if path in ('/record/start', '/record/stop', '/record/screenshot'):
            try:
                raw = self.body()
                data = json.loads(raw or b'{}')
                device = str(data.get('device', 'pc'))
                if device not in ('pc', 'phone'):
                    return self.sendb(b'Unknown device', code=400)
                manager = PC_RECORDER if device == 'pc' else PHONE_RECORDER
                if path == '/record/screenshot':
                    payload = {'path': screenshot_to_file(device)}
                elif path == '/record/stop':
                    payload = manager.stop()
                else:
                    if device == 'phone':
                        cfg = get_phone_profile()
                        width, height = int(data.get('width', cfg['width'])), int(data.get('height', cfg['height']))
                        fps, quality = int(data.get('fps', cfg['fps'])), int(data.get('quality', cfg['quality']))
                        set_phone_profile(width, height, fps, quality)
                    else:
                        width, height = int(data.get('width', 1280)), int(data.get('height', 720))
                        fps, quality = int(data.get('fps', 30)), int(data.get('quality', 75))
                    path_on_disk = manager.start(capture_pc_image if device == 'pc' else phone_frame_snapshot,
                                                 device, width, height, fps, quality)
                    payload = {'path': path_on_disk, 'active': True}
                return self.sendb(json.dumps(payload).encode('utf-8'), 'application/json')
            except (RuntimeError, ValueError, TypeError) as error:
                return self.sendb(json.dumps({'error': str(error)}).encode('utf-8'), 'application/json', 400)
            except Exception as error:
                return self.sendb(json.dumps({'error': str(error)}).encode('utf-8'), 'application/json', 500)
        if path == '/record-toggle':
            servermod.mark_seen(self.client_address[0])
            try:
                state = toggle_recording()
                return self.sendb(state.encode('utf-8'))
            except Exception as e:
                return self.sendb(str(e).encode('utf-8'), code=500)
        if path == '/power':
            try:
                raw = self.body().decode('utf-8', errors='ignore').strip().lower()
                if raw not in ('shutdown', 'restart', 'sleep'):
                    return self.sendb(b'Invalid power action', code=400)
                servermod.mark_seen(self.client_address[0]); power_action(raw); return self.sendb()
            except Exception as e:
                return self.sendb(str(e).encode(), code=500)
        if path in ('/mouse-abs', '/hotkey', '/zoom'):
            try:
                raw = self.body()
            except Exception:
                return self.sendb(b'bad', code=400)
            servermod.mark_seen(self.client_address[0])
            if path == '/mouse-abs':
                ok = absolute_mouse(raw)
            elif path == '/hotkey':
                ok = send_hotkey(raw.decode(errors='ignore').strip().upper())
            else:
                ok = zoom_wheel(raw)
            return self.sendb(b'OK' if ok else b'FAIL', code=200 if ok else 400)
        return super().do_POST()


def icon_image():
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle((5, 5, 59, 59), 14, fill=(5, 9, 14, 255), outline=(35, 190, 255, 255), width=3)
    d.line((19, 45, 19, 19, 45, 45, 45, 19), fill=(67, 218, 255, 255), width=7, joint='curve')
    return im


def message(title, text):
    ctypes.windll.user32.MessageBoxW(0, text, title, 0x40)


def local_server_ok():
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{PORT}/ping', timeout=.6) as r:
            return r.status == 200 and b'PC Remote' in r.read(64)
    except Exception:
        return False


def firewall_ok():
    try:
        for rule in (TCP_RULE, UDP_RULE):
            r = subprocess.run(['netsh', 'advfirewall', 'firewall', 'show', 'rule', f'name={rule}'], **hidden_kwargs())
            if r.returncode != 0:
                return False
        return True
    except Exception:
        return False


def repair_firewall(icon=None, item=None):
    cmd = (f'/c netsh advfirewall firewall delete rule name="{TCP_RULE}" >nul 2>&1 '
           f'& netsh advfirewall firewall delete rule name="{UDP_RULE}" >nul 2>&1 '
           f'& netsh advfirewall firewall add rule name="{TCP_RULE}" dir=in action=allow protocol=TCP localport={PORT} profile=any enable=yes '
           f'& netsh advfirewall firewall add rule name="{UDP_RULE}" dir=in action=allow protocol=UDP localport={DISCOVERY_PORT} profile=any enable=yes')
    result = ctypes.windll.shell32.ShellExecuteW(None, 'runas', 'cmd.exe', cmd, None, 0)
    if result <= 32:
        message('NEXUS PC Remote', 'Não foi possível configurar o Firewall.')


def show_entertainment_key(icon=None, item=None):
    message('NEXUS Central — código de pareamento',
            'Digite no aplicativo Android o código abaixo:\n\n'
            + entertainment.pairing_key()
            + '\n\nUse somente na sua rede local confiável.')


def copy_entertainment_key(icon=None, item=None):
    try:
        servermod.set_clipboard(entertainment.pairing_key())
        if tray_icon:
            tray_icon.notify('Cole o código na aba Cinema ou Jogos do Android.',
                             'Código NEXUS Central copiado')
    except Exception:
        message('NEXUS Central', 'Não foi possível copiar o código.')


def open_downloads(icon=None, item=None):
    os.startfile(str(RECEIVED_DIR))


def open_recorder_dashboard(icon=None, item=None):
    if edge is not None and RECORDER_DASHBOARD is not None:
        edge.root.after(0, RECORDER_DASHBOARD.show)
    else:
        message('NEXUS', 'Painel ainda não está pronto.')


def open_phone_files(icon=None, item=None):
    ip = getattr(servermod, 'LAST_CLIENT', '') or ''
    if not ip:
        return message('NEXUS PC Remote', 'Abra o NEXUS no celular e conecte ao PC primeiro.')
    try:
        os.startfile(f'http://{ip}:8767/')
    except Exception:
        message('NEXUS PC Remote', 'Não foi possível abrir a memória do celular.')




def stop(icon=None, item=None):
    global server
    PC_RECORDER.stop()
    PHONE_RECORDER.stop()
    try:
        if server:
            server.shutdown()
    except Exception:
        pass
    try:
        if edge:
            edge.root.after(0, edge.root.destroy)
    except Exception:
        pass
    try:
        if tray_icon:
            tray_icon.stop()
    except Exception:
        pass


def toggle_autostart(icon=None, item=None):
    enabled = autostart_enabled()
    ok = set_autostart(not enabled)
    if tray_icon:
        try:
            tray_icon.notify(
                ('Ativado' if not enabled else 'Desativado') if ok else 'Não foi possível alterar',
                'Iniciar com o Windows'
            )
            tray_icon.update_menu()
        except Exception:
            pass

def make_menu():
    pad = 'Controle virtual: pronto' if GAMEPAD else 'Controle virtual: indisponível'
    firewall = 'Firewall: liberado' if firewall_ok() else 'Liberar conexão no Firewall'
    return pystray.Menu(
        pystray.MenuItem(lambda _: f'NEXUS • {local_ip()}:{PORT}', None, enabled=False),
        pystray.MenuItem(lambda _: 'Servidor: ativo' if local_server_ok() else ('Servidor: ' + server_error if server_error else 'Servidor: iniciando'), None, enabled=False),
        pystray.MenuItem(lambda _: 'Celular: conectado' if phone_connected() else 'Celular: aguardando', None, enabled=False),
        pystray.MenuItem(lambda _: f'Tela: {CAPTURE_SOURCE}' + (f' • {CAPTURE_ERROR}' if CAPTURE_ERROR else ''), None, enabled=False),
        pystray.MenuItem(lambda _: f'Biblioteca extra: {LIBRARY_DIR}', None, enabled=False),
        pystray.MenuItem(firewall, repair_firewall, enabled=not firewall_ok()),
        pystray.MenuItem(pad, None, enabled=False),
        pystray.MenuItem(
            lambda _: 'Iniciar com o Windows: ativado' if autostart_enabled() else 'Iniciar com o Windows: desativado',
            toggle_autostart
        ),
        pystray.MenuItem('Gravar PC / Celular e tirar prints', open_recorder_dashboard),
        pystray.MenuItem('Abrir arquivos do celular', open_phone_files),
        pystray.MenuItem('Abrir Downloads', open_downloads),
        pystray.MenuItem('Mostrar código NEXUS Central', show_entertainment_key),
        pystray.MenuItem('Copiar código NEXUS Central', copy_entertainment_key),
        pystray.MenuItem('Sair', stop)
    )


def start_tray():
    global tray_icon
    if pystray is None:
        return
    tray_icon = pystray.Icon('nexus_pc_remote', icon_image(), 'NEXUS PC Remote', make_menu())
    try:
        tray_icon.run_detached()
    except Exception:
        threading.Thread(target=tray_icon.run, daemon=False, name='NexusTray').start()


def run_server():
    global server, server_error
    try:
        server = ThreadingHTTPServer(('0.0.0.0', PORT), CompanionHandler)
        server.serve_forever()
    except OSError as e:
        server_error = 'Servidor NEXUS já estava ativo' if local_server_ok() else 'Porta 8765 indisponível: ' + str(e)
    except Exception as e:
        server_error = str(e)


def start_services():
    servermod.screenshot_original = servermod.screenshot
    servermod.screenshot = fast_screenshot
    cleanup_legacy_staging()
    threading.Thread(target=capture_loop, daemon=True, name='NexusCapture').start()
    threading.Thread(target=run_discovery_responder, daemon=True, name='DiscoveryResponder').start()
    threading.Thread(target=run_server, daemon=True, name='RemoteServer').start()


if __name__ == '__main__':
    first_run_autostart_prompt()
    start_services(); start_tray()
    edge = DropEdge()
    RECORDER_DASHBOARD = RecorderDashboard(edge.root, capture_pc_image,
                                           phone_frame_snapshot, lambda: phone_frame_snapshot() is not None,
                                           set_phone_profile)
    try:
        edge.run()
    finally:
        stop()
