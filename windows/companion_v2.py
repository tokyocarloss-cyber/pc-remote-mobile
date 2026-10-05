import ctypes, io, os, shutil, struct, subprocess, sys, threading, time, urllib.parse, urllib.request
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

FRAME_BYTES = b''
FRAME_SEQ = 0
FRAME_LOCK = threading.Lock()
CAPTURE_ERROR = ''
CAPTURE_SOURCE = 'iniciando'


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
    global FRAME_BYTES, FRAME_SEQ, CAPTURE_ERROR, CAPTURE_SOURCE
    while True:
        started = time.perf_counter()
        data = b''
        try:
            if mss is not None:
                with mss.mss() as sct:
                    monitor = sct.monitors[1]
                    while True:
                        started = time.perf_counter()
                        shot = sct.grab(monitor)
                        im = Image.frombytes('RGB', shot.size, shot.rgb)
                        data = encode_frame(im)
                        with FRAME_LOCK:
                            FRAME_BYTES = data
                            FRAME_SEQ += 1
                        CAPTURE_SOURCE = 'MSS'
                        CAPTURE_ERROR = ''
                        target = max(15, min(120, int(servermod.SCREEN_TARGET_FPS)))
                        wait = (1.0 / target) - (time.perf_counter() - started)
                        if wait > 0:
                            time.sleep(wait)
            else:
                raise RuntimeError('MSS indisponível')
        except Exception as e:
            CAPTURE_ERROR = str(e)
            CAPTURE_SOURCE = 'fallback'
            try:
                data = servermod.screenshot_original()
                if data:
                    with FRAME_LOCK:
                        FRAME_BYTES = data
                        FRAME_SEQ += 1
            except Exception as fallback_error:
                CAPTURE_ERROR = f'{CAPTURE_ERROR}; fallback: {fallback_error}'
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
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        if path == '/stream/status':
            with FRAME_LOCK:
                seq = FRAME_SEQ
                has_frame = bool(FRAME_BYTES)
            payload = servermod.json.dumps({
                'ok': has_frame,
                'seq': seq,
                'source': CAPTURE_SOURCE,
                'error': CAPTURE_ERROR,
                'width': servermod.SCREEN_WIDTH,
                'height': servermod.SCREEN_HEIGHT,
                'fps': servermod.SCREEN_TARGET_FPS
            }).encode()
            return self.sendb(payload, 'application/json', 200 if has_frame else 503)
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

        last_seq = -1
        last_send = time.monotonic()
        try:
            while True:
                with FRAME_LOCK:
                    data = FRAME_BYTES
                    seq = FRAME_SEQ
                if data and seq != last_seq:
                    self.wfile.write(struct.pack('>I', len(data)))
                    self.wfile.write(data)
                    self.wfile.flush()
                    last_seq = seq
                    last_send = time.monotonic()
                elif time.monotonic() - last_send > 2.0:
                    fallback = fast_screenshot()
                    if fallback:
                        self.wfile.write(struct.pack('>I', len(fallback)))
                        self.wfile.write(fallback)
                        self.wfile.flush()
                        last_send = time.monotonic()
                time.sleep(.002)
        except Exception:
            return

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
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


def open_downloads(icon=None, item=None):
    os.startfile(str(RECEIVED_DIR))


def stop(icon=None, item=None):
    global server
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
        pystray.MenuItem('Abrir Downloads', open_downloads),
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
    start_services(); start_tray()
    edge = DropEdge()
    try:
        edge.run()
    finally:
        stop()
