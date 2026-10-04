import ctypes, json, os, subprocess, webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT=8765
user32=ctypes.windll.user32
KEYS={'A':0x41,'B':0x42,'X':0x58,'Y':0x59,'VOLUME_UP':0xAF,'VOLUME_DOWN':0xAE,'MEDIA_PLAY':0xB3,'MEDIA_NEXT':0xB0,'MEDIA_PREV':0xB1}

def key(name):
    vk=KEYS.get(name)
    if vk: user32.keybd_event(vk,0,0,0); user32.keybd_event(vk,0,2,0)

def launch(name):
    if name=='Steam': subprocess.Popen(['cmd','/c','start','steam://open/main'],shell=True)
    elif name=='Chrome': subprocess.Popen(['cmd','/c','start','chrome'],shell=True)
    elif name=='Explorador': subprocess.Popen(['explorer.exe'])
    elif name=='Área de Trabalho': user32.keybd_event(0x5B,0,0,0);user32.keybd_event(0x44,0,0,0);user32.keybd_event(0x44,0,2,0);user32.keybd_event(0x5B,0,2,0)

class H(BaseHTTPRequestHandler):
    def reply(self,code=200): self.send_response(code);self.send_header('Access-Control-Allow-Origin','*');self.end_headers();self.wfile.write(b'OK')
    def do_GET(self): self.reply(200 if self.path=='/ping' else 404)
    def do_POST(self):
        n=int(self.headers.get('Content-Length','0')); body=self.rfile.read(n).decode('utf-8')
        try:
            if self.path=='/key': key(body.strip())
            elif self.path=='/launch': launch(body.strip())
            elif self.path=='/mouse':
                dx,dy=map(int,body.split(','));user32.mouse_event(0x0001,dx,dy,0,0)
            else: return self.reply(404)
            self.reply()
        except Exception as e: print(e);self.reply(500)
    def log_message(self,*a): pass

if __name__=='__main__':
    print('PC Remote Windows Server')
    print('Porta:',PORT)
    print('Deixe esta janela aberta. No celular, use o IPv4 deste PC.')
    ThreadingHTTPServer(('0.0.0.0',PORT),H).serve_forever()
