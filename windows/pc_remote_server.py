import ctypes, io, json, os, socket, subprocess, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT=8765
ROOT=Path.home()/"Downloads"/"PC Remote"
ROOT.mkdir(parents=True,exist_ok=True)
u=ctypes.windll.user32
KEYS={'A':0x41,'B':0x42,'X':0x58,'Y':0x59,'ENTER':0x0D,'ESC':0x1B,'SPACE':0x20,'UP':0x26,'DOWN':0x28,'LEFT':0x25,'RIGHT':0x27,'VOLUME_UP':0xAF,'VOLUME_DOWN':0xAE,'VOLUME_MUTE':0xAD,'MEDIA_PLAY':0xB3,'MEDIA_NEXT':0xB0,'MEDIA_PREV':0xB1}

def press(vk): u.keybd_event(vk,0,0,0);u.keybd_event(vk,0,2,0)
def text(s):
    for ch in s:
        u.keybd_event(0,ord(ch),4,0);u.keybd_event(0,ord(ch),6,0)
def launch(n):
    cmds={'Steam':'start steam://open/main','Chrome':'start chrome','Explorador':'explorer','Configurações':'start ms-settings:','Gerenciador':'taskmgr'}
    if n=='Área de Trabalho': u.keybd_event(0x5B,0,0,0);press(0x44);u.keybd_event(0x5B,0,2,0)
    elif n in cmds: subprocess.Popen(cmds[n],shell=True)
def screenshot():
    try:
        from PIL import ImageGrab
        b=io.BytesIO();ImageGrab.grab().save(b,'JPEG',quality=60);return b.getvalue()
    except Exception:return b''

def local_ip():
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    try:s.connect(('8.8.8.8',80));return s.getsockname()[0]
    except:return '127.0.0.1'
    finally:s.close()

class H(BaseHTTPRequestHandler):
    def headers(self,code=200,typ='text/plain',length=None):
        self.send_response(code);self.send_header('Access-Control-Allow-Origin','*');self.send_header('Content-Type',typ)
        if length is not None:self.send_header('Content-Length',str(length))
        self.end_headers()
    def body(self):
        n=int(self.headers.get('Content-Length','0'));return self.rfile.read(n)
    def ok(self,s=b'OK'):
        self.headers(200,'text/plain',len(s));self.wfile.write(s)
    def do_GET(self):
        if self.path=='/ping':return self.ok(b'PC Remote')
        if self.path=='/screen.jpg':
            b=screenshot();self.headers(200 if b else 503,'image/jpeg',len(b));return self.wfile.write(b)
        if self.path=='/apps':
            b=json.dumps(['Steam','Chrome','Explorador','Configurações','Gerenciador','Área de Trabalho']).encode();self.headers(200,'application/json',len(b));return self.wfile.write(b)
        if self.path.startswith('/download/'):
            p=ROOT/Path(urllib.parse.unquote(self.path[10:])).name
            if p.exists():b=p.read_bytes();self.headers(200,'application/octet-stream',len(b));return self.wfile.write(b)
        self.headers(404)
    def do_POST(self):
        raw=self.body()
        try:
            if self.path=='/key': press(KEYS.get(raw.decode().strip(),0))
            elif self.path=='/text': text(raw.decode())
            elif self.path=='/launch': launch(raw.decode().strip())
            elif self.path=='/mouse':
                dx,dy=map(int,raw.decode().split(','));u.mouse_event(1,dx,dy,0,0)
            elif self.path=='/click':
                kind=raw.decode().strip();flag=(2,4) if kind=='left' else (8,16)
                u.mouse_event(flag[0],0,0,0,0);u.mouse_event(flag[1],0,0,0,0)
            elif self.path=='/scroll': u.mouse_event(0x0800,0,0,int(raw.decode()),0)
            elif self.path.startswith('/upload/'):
                name=Path(urllib.parse.unquote(self.path[8:])).name;(ROOT/name).write_bytes(raw)
            else:self.headers(404);return
            self.ok()
        except Exception as e:self.headers(500);self.wfile.write(str(e).encode())
    def log_message(self,*a):pass

if __name__=='__main__':
    print('=== PC REMOTE ===');print('IP:',local_ip());print('Porta:',PORT);print('Arquivos:',ROOT)
    try:import PIL
    except:print('AVISO: instale Pillow para transmitir a tela: py -m pip install pillow')
    ThreadingHTTPServer(('0.0.0.0',PORT),H).serve_forever()
