import threading, sys
from http.server import ThreadingHTTPServer
import pystray
from PIL import Image, ImageDraw
from pc_remote_server import H,PORT,local_ip,ROOT
from drop_edge import DropEdge

server=None
edge=None

def icon_image():
 im=Image.new("RGB",(64,64),(10,14,28));d=ImageDraw.Draw(im)
 d.rounded_rectangle((8,8,56,56),12,fill=(25,34,62));d.ellipse((20,20,44,44),fill=(66,217,255))
 return im

def stop(icon,item=None):
 global server
 if server:server.shutdown()
 try:
  if edge:edge.root.after(0,edge.root.destroy)
 except:pass
 icon.stop()

def open_folder(icon,item=None):
 import os;os.startfile(str(ROOT))

def run_server():
 global server
 server=ThreadingHTTPServer(("0.0.0.0",PORT),H);server.serve_forever()

def run_tray():
 menu=pystray.Menu(
  pystray.MenuItem(lambda _:"PC Remote • "+local_ip()+":"+str(PORT),None,enabled=False),
  pystray.MenuItem("Abrir pasta de transferências",open_folder),
  pystray.MenuItem("Sair",stop))
 icon=pystray.Icon("pc_remote",icon_image(),"PC Remote • conectado",menu);icon.run()

if __name__=="__main__":
 threading.Thread(target=run_server,daemon=True,name="RemoteServer").start()
 threading.Thread(target=run_tray,daemon=True,name="Tray").start()
 edge=DropEdge()
 try:edge.run()
 finally:
  if server:server.shutdown()
