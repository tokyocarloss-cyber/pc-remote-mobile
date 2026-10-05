import ctypes, io, json, os, re, shutil, socket, subprocess, urllib.parse, hashlib, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT=8765
DISCOVERY_PORT=8766
CREATE_NO_WINDOW=0x08000000
ROOT=Path.home()/"Downloads"/"PC Remote"; ROOT.mkdir(parents=True,exist_ok=True)
OUTBOX=ROOT/"To Phone"; OUTBOX.mkdir(parents=True,exist_ok=True)
ICON_DIR=ROOT/".icons"; ICON_DIR.mkdir(parents=True,exist_ok=True)
RECEIVED_DIR=Path.home()/"Downloads"; RECEIVED_DIR.mkdir(parents=True,exist_ok=True)
UPLOAD_CALLBACK=None
SCREEN_WIDTH=1280
SCREEN_HEIGHT=720
SCREEN_QUALITY=60
SCREEN_TARGET_FPS=60
u=ctypes.windll.user32
k32=ctypes.windll.kernel32
LAST_SEEN=0.0
LAST_CLIENT=''
try:
 import vgamepad as vg
 GAMEPAD=vg.VX360Gamepad()
except Exception:
 GAMEPAD=None
PAD_BUTTONS={}
if GAMEPAD:
 PAD_BUTTONS={'A':vg.XUSB_BUTTON.XUSB_GAMEPAD_A,'B':vg.XUSB_BUTTON.XUSB_GAMEPAD_B,'X':vg.XUSB_BUTTON.XUSB_GAMEPAD_X,'Y':vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,'LB':vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,'RB':vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,'UP':vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,'DOWN':vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,'LEFT':vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,'RIGHT':vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,'START':vg.XUSB_BUTTON.XUSB_GAMEPAD_START,'SELECT':vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,'L3':vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,'R3':vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB}

KEYS={'A':0x41,'B':0x42,'X':0x58,'Y':0x59,'ENTER':0x0D,'ESC':0x1B,'SPACE':0x20,'UP':0x26,'DOWN':0x28,'LEFT':0x25,'RIGHT':0x27,'VOLUME_UP':0xAF,'VOLUME_DOWN':0xAE,'VOLUME_MUTE':0xAD,'MEDIA_PLAY':0xB3,'MEDIA_NEXT':0xB0,'MEDIA_PREV':0xB1,'LB':0x51,'RB':0x45,'LT':0x31,'RT':0x33,'L3':0x10,'R3':0x11,'START':0x0D,'SELECT':0x1B}
APP_INDEX={}

def mark_seen(client=''):
 global LAST_SEEN,LAST_CLIENT
 LAST_SEEN=time.monotonic()
 if client:LAST_CLIENT=client

def phone_connected():return time.monotonic()-LAST_SEEN<12

def gamepad_event(raw):
 if not GAMEPAD:return False
 d=json.loads(raw.decode());kind=d.get('kind')
 if kind=='button':
  b=PAD_BUTTONS.get(d.get('button'))
  if b:(GAMEPAD.press_button if d.get('down') else GAMEPAD.release_button)(button=b)
 elif kind=='stick':
  x=max(-1.0,min(1.0,float(d.get('x',0))));y=max(-1.0,min(1.0,float(d.get('y',0))))
  if d.get('stick')=='left':GAMEPAD.left_joystick_float(x_value_float=x,y_value_float=y)
  else:GAMEPAD.right_joystick_float(x_value_float=x,y_value_float=y)
 elif kind=='trigger':
  v=max(0.0,min(1.0,float(d.get('value',0))))
  if d.get('trigger')=='LT':GAMEPAD.left_trigger_float(value_float=v)
  else:GAMEPAD.right_trigger_float(value_float=v)
 GAMEPAD.update();return True

def press(vk):
 if vk:u.keybd_event(vk,0,0,0);u.keybd_event(vk,0,2,0)

def set_clipboard(text):
 """Unicode clipboard without spawning PowerShell or CMD windows."""
 CF_UNICODETEXT=13;GMEM_MOVEABLE=0x0002
 data=(str(text)+'\0').encode('utf-16-le')
 if not u.OpenClipboard(None):raise OSError('OpenClipboard falhou')
 h=None
 try:
  u.EmptyClipboard()
  h=k32.GlobalAlloc(GMEM_MOVEABLE,len(data))
  if not h:raise MemoryError('GlobalAlloc falhou')
  p=k32.GlobalLock(h)
  if not p:raise MemoryError('GlobalLock falhou')
  ctypes.memmove(p,data,len(data));k32.GlobalUnlock(h)
  if not u.SetClipboardData(CF_UNICODETEXT,h):raise OSError('SetClipboardData falhou')
  h=None
 finally:
  u.CloseClipboard()
  if h:k32.GlobalFree(h)

def paste_text(s):
 set_clipboard(s);u.keybd_event(0x11,0,0,0);press(0x56);u.keybd_event(0x11,0,2,0)

def power_command(action):
 action=action.strip().lower()
 if action=='shutdown':subprocess.Popen(['shutdown.exe','/s','/t','0'],creationflags=CREATE_NO_WINDOW);return True
 if action=='restart':subprocess.Popen(['shutdown.exe','/r','/t','0'],creationflags=CREATE_NO_WINDOW);return True
 if action=='sleep':
  try:return bool(ctypes.windll.powrprof.SetSuspendState(False,True,False) or True)
  except Exception:return False
 return False

def store_for(name):
 n=name.lower()
 if 'steam' in n:return 'Steam'
 if 'epic' in n:return 'Epic'
 if 'xbox' in n or 'game pass' in n:return 'Xbox'
 if n.startswith('ea ') or 'ea app' in n:return 'EA'
 if 'ubisoft' in n or 'uplay' in n:return 'Ubisoft'
 if 'battle.net' in n or 'blizzard' in n:return 'Battle.net'
 if 'gog' in n:return 'GOG'
 return 'Outros'

def steam_roots():
 roots=[]
 candidates=[Path(os.environ.get('PROGRAMFILES(X86)',''))/'Steam',Path(os.environ.get('PROGRAMFILES',''))/'Steam']
 for r in candidates:
  if r.exists() and r not in roots:roots.append(r)
 for r in list(roots):
  vdf=r/'steamapps'/'libraryfolders.vdf'
  if vdf.exists():
   try:
    txt=vdf.read_text(encoding='utf-8',errors='ignore')
    for p in re.findall(r'"path"\s+"([^"]+)"',txt):
     q=Path(p.replace('\\\\','\\'))
     if q.exists() and q not in roots:roots.append(q)
   except:pass
 return roots

def apps_catalog():
 global APP_INDEX
 out=[];seen=set();idx={}
 def add(name,store='Outros',shortcut=None,launch_cmd=None,steam_id=None,icon_hint=None):
  if not name or name.casefold() in seen or len(out)>=320:return
  item={'name':name,'store':store};out.append(item);seen.add(name.casefold());idx[name]={'shortcut':shortcut,'launch_cmd':launch_cmd,'steam_id':steam_id,'icon_hint':icon_hint,'store':store}
 add('Steam','Steam',launch_cmd='steam://open/main')
 add('Chrome','Outros',launch_cmd='chrome')
 add('Explorador','Outros',launch_cmd='explorer.exe')
 add('Configurações','Outros',launch_cmd='ms-settings:')
 add('Gerenciador','Outros',launch_cmd='taskmgr.exe')
 add('Área de Trabalho','Outros',launch_cmd='__desktop__')
 for base in [Path(os.environ.get('PROGRAMDATA',''))/'Microsoft/Windows/Start Menu/Programs',Path(os.environ.get('APPDATA',''))/'Microsoft/Windows/Start Menu/Programs']:
  if base.exists():
   for p in base.rglob('*.lnk'):add(p.stem,store_for(p.stem),shortcut=str(p))
 for root in steam_roots():
  steamapps=root/'steamapps'
  if not steamapps.exists():continue
  for mf in steamapps.glob('appmanifest_*.acf'):
   try:
    txt=mf.read_text(encoding='utf-8',errors='ignore');m_name=re.search(r'"name"\s+"([^"]+)"',txt);m_id=re.search(r'"appid"\s+"(\d+)"',txt)
    if m_name and m_id:add(m_name.group(1),'Steam',steam_id=m_id.group(1),icon_hint=str(root/'appcache'/'librarycache'/(m_id.group(1)+'_icon.jpg')))
   except:pass
 APP_INDEX=idx
 return out

def launch(n):
 if n not in APP_INDEX:apps_catalog()
 item=APP_INDEX.get(n)
 if not item:return
 if item.get('steam_id'):os.startfile('steam://rungameid/'+item['steam_id']);return
 if item.get('shortcut') and Path(item['shortcut']).exists():os.startfile(item['shortcut']);return
 cmd=item.get('launch_cmd')
 if cmd=='__desktop__':u.keybd_event(0x5B,0,0,0);press(0x44);u.keybd_event(0x5B,0,2,0);return
 if not cmd:return
 if ':' in cmd and not cmd.lower().endswith('.exe'):
  try:os.startfile(cmd);return
  except:pass
 exe=shutil.which(cmd) or cmd
 subprocess.Popen([exe],creationflags=CREATE_NO_WINDOW)

def resolve_shortcut(path):
 if not path:return ''
 escaped=str(path).replace("'","''")
 cmd="$s=(New-Object -ComObject WScript.Shell).CreateShortcut('"+escaped+"');$s.TargetPath"
 try:return subprocess.check_output(['powershell','-NoProfile','-NonInteractive','-WindowStyle','Hidden','-Command',cmd],text=True,encoding='utf-8',errors='ignore',timeout=4,creationflags=CREATE_NO_WINDOW).strip()
 except:return ''

def app_icon(name):
 if name not in APP_INDEX:apps_catalog()
 item=APP_INDEX.get(name)
 if not item:return None,None
 hint=item.get('icon_hint')
 if hint and Path(hint).is_file() and Path(hint).suffix.lower() in ('.png','.jpg','.jpeg'):
  typ='image/png' if Path(hint).suffix.lower()=='.png' else 'image/jpeg';return Path(hint).read_bytes(),typ
 target=hint or (resolve_shortcut(item.get('shortcut')) if item.get('shortcut') else '')
 if not target or not Path(target).exists():return None,None
 cache=ICON_DIR/(hashlib.sha1(name.encode('utf-8')).hexdigest()+'.png')
 if not cache.exists():
  src=str(target).replace("'","''");dst=str(cache).replace("'","''")
  ps="Add-Type -AssemblyName System.Drawing;$i=[System.Drawing.Icon]::ExtractAssociatedIcon('"+src+"');if($i){$i.ToBitmap().Save('"+dst+"',[System.Drawing.Imaging.ImageFormat]::Png)}"
  try:subprocess.run(['powershell','-NoProfile','-NonInteractive','-WindowStyle','Hidden','-Command',ps],timeout=5,creationflags=CREATE_NO_WINDOW,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  except:pass
 return (cache.read_bytes(),'image/png') if cache.exists() else (None,None)

def screenshot():
 try:
  from PIL import ImageGrab
  im=ImageGrab.grab();im.thumbnail((SCREEN_WIDTH,SCREEN_HEIGHT));b=io.BytesIO();im.save(b,'JPEG',quality=SCREEN_QUALITY,optimize=False);return b.getvalue()
 except:return b''

def local_ip():
 s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
 try:s.connect(('8.8.8.8',80));return s.getsockname()[0]
 except:return '127.0.0.1'
 finally:s.close()

def run_discovery_responder():
 s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
 try:s.bind(('',DISCOVERY_PORT))
 except OSError:return
 while True:
  try:
   data,addr=s.recvfrom(1024)
   if data.strip().startswith(b'NEXUS_DISCOVER'):
    s.sendto(f'NEXUS|{local_ip()}|{PORT}'.encode(),addr)
  except Exception:pass

def unique_download(name):
 target=RECEIVED_DIR/Path(name).name;stem=target.stem;suffix=target.suffix;n=1
 while target.exists():target=RECEIVED_DIR/f'{stem}_{n}{suffix}';n+=1
 return target

class H(BaseHTTPRequestHandler):
 protocol_version='HTTP/1.1'
 def sendb(self,b=b'OK',typ='text/plain',code=200):
  self.send_response(code);self.send_header('Access-Control-Allow-Origin','*');self.send_header('Content-Type',typ);self.send_header('Content-Length',str(len(b)));self.send_header('Connection','keep-alive');self.end_headers();self.wfile.write(b)
 def body(self):
  n=int(self.headers.get('Content-Length','0'))
  if n>256*1024*1024:raise ValueError('Arquivo maior que o limite de 256 MB')
  return self.rfile.read(n)
 def do_GET(self):
  global SCREEN_WIDTH,SCREEN_HEIGHT,SCREEN_QUALITY,SCREEN_TARGET_FPS
  parsed=urllib.parse.urlparse(self.path);p=parsed.path
  if p=='/ping':return self.sendb(b'PC Remote')
  if p=='/status':
   mark_seen(self.client_address[0]);return self.sendb(json.dumps({'gamepad':GAMEPAD is not None,'ip':local_ip(),'paired':phone_connected(),'client':LAST_CLIENT,'screen':{'width':SCREEN_WIDTH,'height':SCREEN_HEIGHT,'quality':SCREEN_QUALITY,'fps':SCREEN_TARGET_FPS}}).encode(),'application/json')
  if p=='/screen.jpg':
   mark_seen(self.client_address[0]);q=urllib.parse.parse_qs(parsed.query)
   try:
    SCREEN_WIDTH=max(640,min(2560,int(q.get('w',[SCREEN_WIDTH])[0])));SCREEN_HEIGHT=max(360,min(1440,int(q.get('h',[SCREEN_HEIGHT])[0])));SCREEN_QUALITY=max(35,min(90,int(q.get('q',[SCREEN_QUALITY])[0])));SCREEN_TARGET_FPS=max(15,min(120,int(q.get('fps',[SCREEN_TARGET_FPS])[0])))
   except:pass
   b=screenshot();return self.sendb(b,'image/jpeg',200 if b else 503)
  if p=='/apps':
   mark_seen(self.client_address[0]);return self.sendb(json.dumps(apps_catalog(),ensure_ascii=False).encode(),'application/json')
  if p.startswith('/app-icon/'):
   mark_seen(self.client_address[0]);name=urllib.parse.unquote(p[10:]);b,typ=app_icon(name);return self.sendb(b,typ) if b else self.sendb(b'',code=404)
  if p=='/outbox':
   mark_seen(self.client_address[0]);items=[{'name':x.name,'size':x.stat().st_size} for x in OUTBOX.iterdir() if x.is_file()];return self.sendb(json.dumps(items,ensure_ascii=False).encode(),'application/json')
  if p.startswith('/outbox/'):
   mark_seen(self.client_address[0]);f=OUTBOX/Path(urllib.parse.unquote(p[8:])).name
   if f.exists():return self.sendb(f.read_bytes(),'application/octet-stream')
  if p.startswith('/download/'):
   mark_seen(self.client_address[0]);f=ROOT/Path(urllib.parse.unquote(p[10:])).name
   if f.exists():return self.sendb(f.read_bytes(),'application/octet-stream')
  return self.sendb(b'',code=404)
 def do_POST(self):
  try:raw=self.body()
  except ValueError as e:return self.sendb(str(e).encode(),code=413)
  try:
   p=urllib.parse.urlparse(self.path).path;mark_seen(self.client_address[0])
   if p=='/pair':return self.sendb(json.dumps({'paired':True,'ip':local_ip(),'client':LAST_CLIENT}).encode(),'application/json')
   if p=='/power':
    if not power_command(raw.decode(errors='ignore')):return self.sendb(b'Invalid power action',code=400)
   elif p=='/gamepad':
    if not gamepad_event(raw):return self.sendb(b'Virtual gamepad unavailable',code=503)
   elif p=='/key':press(KEYS.get(raw.decode().strip(),0))
   elif p=='/text':paste_text(raw.decode())
   elif p=='/clipboard':set_clipboard(raw.decode())
   elif p=='/launch':launch(raw.decode().strip())
   elif p=='/mouse':
    dx,dy=map(int,raw.decode().split(','));u.mouse_event(1,dx,dy,0,0)
   elif p=='/click':
    f=(2,4) if raw.decode().strip()=='left' else (8,16);u.mouse_event(f[0],0,0,0,0);u.mouse_event(f[1],0,0,0,0)
   elif p=='/scroll':u.mouse_event(0x0800,0,0,int(raw.decode()),0)
   elif p.startswith('/upload/'):
    target=unique_download(urllib.parse.unquote(p[8:]));target.write_bytes(raw)
    if UPLOAD_CALLBACK:
     try:UPLOAD_CALLBACK(target)
     except:pass
   elif p.startswith('/outbox-ack/'):
    f=OUTBOX/Path(urllib.parse.unquote(p[12:])).name
    if f.exists():f.unlink()
   else:return self.sendb(b'',code=404)
   return self.sendb()
  except Exception as e:return self.sendb(str(e).encode(),code=500)
 def log_message(self,*a):pass

def run_server():
 threading.Thread(target=run_discovery_responder,daemon=True,name='NexusDiscovery').start();ThreadingHTTPServer(('0.0.0.0',PORT),H).serve_forever()
if __name__=='__main__':run_server()
