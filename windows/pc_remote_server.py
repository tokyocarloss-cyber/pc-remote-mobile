import ctypes, io, json, os, socket, subprocess, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT=8765
ROOT=Path.home()/"Downloads"/"PC Remote"; ROOT.mkdir(parents=True,exist_ok=True)
OUTBOX=ROOT/"To Phone"; OUTBOX.mkdir(parents=True,exist_ok=True)
u=ctypes.windll.user32
KEYS={'A':0x41,'B':0x42,'X':0x58,'Y':0x59,'ENTER':0x0D,'ESC':0x1B,'SPACE':0x20,'UP':0x26,'DOWN':0x28,'LEFT':0x25,'RIGHT':0x27,'VOLUME_UP':0xAF,'VOLUME_DOWN':0xAE,'VOLUME_MUTE':0xAD,'MEDIA_PLAY':0xB3,'MEDIA_NEXT':0xB0,'MEDIA_PREV':0xB1,'LB':0x51,'RB':0x45,'LT':0x31,'RT':0x33,'L3':0x10,'R3':0x11,'START':0x0D,'SELECT':0x1B}

def press(vk):
 if vk:u.keybd_event(vk,0,0,0);u.keybd_event(vk,0,2,0)

def set_clipboard(s):
 p=subprocess.Popen(['powershell','-NoProfile','-Command','$input | Set-Clipboard'],stdin=subprocess.PIPE,text=True,encoding='utf-8');p.communicate(s)

def paste_text(s):
 set_clipboard(s);u.keybd_event(0x11,0,0,0);press(0x56);u.keybd_event(0x11,0,2,0)

def launch(n):
 cmds={'Steam':'start steam://open/main','Chrome':'start chrome','Explorador':'explorer','Configurações':'start ms-settings:','Gerenciador':'taskmgr'}
 if n=='Área de Trabalho':u.keybd_event(0x5B,0,0,0);press(0x44);u.keybd_event(0x5B,0,2,0)
 elif n in cmds:subprocess.Popen(cmds[n],shell=True)
 else:
  for base in [Path(os.environ.get('PROGRAMDATA',''))/'Microsoft/Windows/Start Menu/Programs',Path(os.environ.get('APPDATA',''))/'Microsoft/Windows/Start Menu/Programs']:
   p=next(iter(base.rglob(n+'.lnk')),None) if base.exists() else None
   if p:os.startfile(str(p));return

def apps():
 out=['Steam','Chrome','Explorador','Configurações','Gerenciador','Área de Trabalho'];seen=set(out)
 for base in [Path(os.environ.get('PROGRAMDATA',''))/'Microsoft/Windows/Start Menu/Programs',Path(os.environ.get('APPDATA',''))/'Microsoft/Windows/Start Menu/Programs']:
  if base.exists():
   for p in base.rglob('*.lnk'):
    if p.stem not in seen and len(out)<160:out.append(p.stem);seen.add(p.stem)
 return out

def screenshot():
 try:
  from PIL import ImageGrab
  im=ImageGrab.grab();im.thumbnail((1280,720));b=io.BytesIO();im.save(b,'JPEG',quality=60,optimize=True);return b.getvalue()
 except:return b''

def local_ip():
 s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
 try:s.connect(('8.8.8.8',80));return s.getsockname()[0]
 except:return '127.0.0.1'
 finally:s.close()

class H(BaseHTTPRequestHandler):
 def sendb(self,b=b'OK',typ='text/plain',code=200):
  self.send_response(code);self.send_header('Access-Control-Allow-Origin','*');self.send_header('Content-Type',typ);self.send_header('Content-Length',str(len(b)));self.end_headers();self.wfile.write(b)
 def body(self):return self.rfile.read(min(int(self.headers.get('Content-Length','0')),256*1024*1024))
 def do_GET(self):
  if self.path=='/ping':return self.sendb(b'PC Remote')
  if self.path=='/screen.jpg':
   b=screenshot();return self.sendb(b,'image/jpeg',200 if b else 503)
  if self.path=='/apps':return self.sendb(json.dumps(apps(),ensure_ascii=False).encode(),'application/json')
  if self.path=='/outbox':
   items=[{'name':p.name,'size':p.stat().st_size} for p in OUTBOX.iterdir() if p.is_file()]
   return self.sendb(json.dumps(items,ensure_ascii=False).encode(),'application/json')
  if self.path.startswith('/outbox/'):
   p=OUTBOX/Path(urllib.parse.unquote(self.path[8:])).name
   if p.exists():return self.sendb(p.read_bytes(),'application/octet-stream')
  if self.path.startswith('/download/'):
   p=ROOT/Path(urllib.parse.unquote(self.path[10:])).name
   if p.exists():return self.sendb(p.read_bytes(),'application/octet-stream')
  return self.sendb(b'',code=404)
 def do_POST(self):
  raw=self.body()
  try:
   if self.path=='/key':press(KEYS.get(raw.decode().strip(),0))
   elif self.path=='/text':paste_text(raw.decode())
   elif self.path=='/clipboard':set_clipboard(raw.decode())
   elif self.path=='/launch':launch(raw.decode().strip())
   elif self.path=='/mouse':
    dx,dy=map(int,raw.decode().split(','));u.mouse_event(1,dx,dy,0,0)
   elif self.path=='/click':
    f=(2,4) if raw.decode().strip()=='left' else (8,16);u.mouse_event(f[0],0,0,0,0);u.mouse_event(f[1],0,0,0,0)
   elif self.path=='/scroll':u.mouse_event(0x0800,0,0,int(raw.decode()),0)
   elif self.path.startswith('/upload/'):(ROOT/Path(urllib.parse.unquote(self.path[8:])).name).write_bytes(raw)
   elif self.path.startswith('/outbox-ack/'):
    p=OUTBOX/Path(urllib.parse.unquote(self.path[12:])).name
    if p.exists():p.unlink()
   else:return self.sendb(b'',code=404)
   return self.sendb()
  except Exception as e:return self.sendb(str(e).encode(),code=500)
 def log_message(self,*a):pass

def run_server():ThreadingHTTPServer(('0.0.0.0',PORT),H).serve_forever()
if __name__=='__main__':
 print('=== PC REMOTE ===\nIP:',local_ip(),'\nPorta:',PORT,'\nArquivos:',ROOT);run_server()
