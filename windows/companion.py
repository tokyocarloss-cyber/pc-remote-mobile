import ctypes, os, subprocess, sys, threading
from pathlib import Path
from http.server import ThreadingHTTPServer
import pystray
from PIL import Image, ImageDraw
from pc_remote_server import H,PORT,local_ip,ROOT,GAMEPAD
from drop_edge import DropEdge

server=None
edge=None

def icon_image():
 im=Image.new("RGB",(64,64),(4,7,12));d=ImageDraw.Draw(im)
 d.rounded_rectangle((7,7,57,57),14,fill=(12,23,34));d.ellipse((19,19,45,45),fill=(21,155,255));d.ellipse((27,27,37,37),fill=(93,231,255))
 return im

def message(title,text):
 ctypes.windll.user32.MessageBoxW(0,text,title,0x40)

def stop(icon,item=None):
 global server
 if server:server.shutdown()
 try:
  if edge:edge.root.after(0,edge.root.destroy)
 except:pass
 icon.stop()

def open_folder(icon,item=None):os.startfile(str(ROOT))

def install_gamepad_driver(icon,item=None):
 try:
  import vgamepad
  base=Path(vgamepad.__file__).resolve().parent
  arch='x64' if sys.maxsize>2**32 else 'x86'
  msi=base/'win'/'vigem'/'install'/arch/('ViGEmBusSetup_'+arch+'.msi')
  if not msi.exists():raise FileNotFoundError(str(msi))
  subprocess.Popen(['msiexec','/i',str(msi)])
  message('NEXUS PC Remote','Instalador do controle virtual aberto.\n\nConclua a instalação e reinicie o NEXUS PC Remote.')
 except Exception as e:message('NEXUS PC Remote','Não foi possível abrir o driver do controle virtual.\n'+str(e))

def run_server():
 global server
 server=ThreadingHTTPServer(("0.0.0.0",PORT),H);server.serve_forever()

def run_tray():
 pad='Controle virtual: pronto' if GAMEPAD else 'Controle virtual: instalar driver'
 menu=pystray.Menu(
  pystray.MenuItem(lambda _:"NEXUS • "+local_ip()+":"+str(PORT),None,enabled=False),
  pystray.MenuItem(pad,install_gamepad_driver,enabled=GAMEPAD is None),
  pystray.MenuItem("Abrir transferências",open_folder),
  pystray.MenuItem("Sair",stop))
 icon=pystray.Icon("nexus_pc_remote",icon_image(),"NEXUS PC Remote",menu);icon.run()

if __name__=="__main__":
 threading.Thread(target=run_server,daemon=True,name="RemoteServer").start()
 threading.Thread(target=run_tray,daemon=True,name="Tray").start()
 edge=DropEdge()
 try:edge.run()
 finally:
  if server:server.shutdown()
