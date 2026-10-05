import ctypes, os, subprocess, sys, threading, time
from pathlib import Path
from http.server import ThreadingHTTPServer
try:
 import pystray
except Exception:
 pystray=None
from PIL import Image, ImageDraw
from pc_remote_server import H,PORT,DISCOVERY_PORT,local_ip,ROOT,GAMEPAD,run_discovery_responder,phone_connected
from drop_edge import DropEdge

server=None
edge=None
CREATE_NO_WINDOW=0x08000000

def icon_image():
 im=Image.new("RGB",(64,64),(4,7,12));d=ImageDraw.Draw(im)
 d.rounded_rectangle((7,7,57,57),14,fill=(12,23,34));d.ellipse((19,19,45,45),fill=(21,155,255));d.ellipse((27,27,37,37),fill=(93,231,255))
 return im

def message(title,text):
 ctypes.windll.user32.MessageBoxW(0,text,title,0x40)

def stop(icon=None,item=None):
 global server
 if server:server.shutdown()
 try:
  if edge:edge.root.after(0,edge.root.destroy)
 except:pass
 try:
  if icon:icon.stop()
 except:pass

def open_folder(icon=None,item=None):os.startfile(str(ROOT))

def firewall_ok():
 try:
  a=subprocess.run(['netsh','advfirewall','firewall','show','rule','name=NEXUS PC Remote TCP'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=CREATE_NO_WINDOW)
  b=subprocess.run(['netsh','advfirewall','firewall','show','rule','name=NEXUS PC Remote UDP'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=CREATE_NO_WINDOW)
  return a.returncode==0 and b.returncode==0
 except Exception:return False

def repair_firewall(icon=None,item=None,quiet=False):
 try:
  cmd=(f'/c netsh advfirewall firewall add rule name="NEXUS PC Remote TCP" dir=in action=allow protocol=TCP localport={PORT} profile=private '
       f'& netsh advfirewall firewall add rule name="NEXUS PC Remote UDP" dir=in action=allow protocol=UDP localport={DISCOVERY_PORT} profile=private')
  r=ctypes.windll.shell32.ShellExecuteW(None,'runas','cmd.exe',cmd,None,0)
  if r<=32 and not quiet:message('NEXUS PC Remote','Não foi possível pedir permissão para liberar a conexão no Firewall do Windows.')
  elif not quiet:message('NEXUS PC Remote','Autorize a janela do Windows. Depois disso, celular e PC devem se encontrar automaticamente.')
 except Exception as e:
  if not quiet:message('NEXUS PC Remote','Falha ao configurar o Firewall:\n'+str(e))

def install_gamepad_driver(icon=None,item=None):
 try:
  import vgamepad
  base=Path(vgamepad.__file__).resolve().parent
  arch='x64' if sys.maxsize>2**32 else 'x86'
  msi=base/'win'/'vigem'/'install'/arch/('ViGEmBusSetup_'+arch+'.msi')
  if not msi.exists():raise FileNotFoundError(str(msi))
  subprocess.Popen(['msiexec','/i',str(msi)])
  message('NEXUS PC Remote','Instalador do controle virtual aberto.\n\nConclua a instalação e reinicie o NEXUS PC Remote.')
 except Exception:
  message('NEXUS PC Remote','O controle virtual opcional ainda não está instalado. O restante do NEXUS PC Remote continua funcionando normalmente.')

def run_server():
 global server
 server=ThreadingHTTPServer(("0.0.0.0",PORT),H);server.serve_forever()

def run_tray():
 if pystray is None:return
 pad='Controle virtual: pronto' if GAMEPAD else 'Controle virtual: indisponível'
 menu=pystray.Menu(
  pystray.MenuItem(lambda _:"NEXUS • "+local_ip()+":"+str(PORT),None,enabled=False),
  pystray.MenuItem(lambda _:"Celular: conectado" if phone_connected() else "Celular: offline",None,enabled=False),
  pystray.MenuItem("Corrigir conexão / Firewall",repair_firewall),
  pystray.MenuItem(pad,install_gamepad_driver,enabled=GAMEPAD is None),
  pystray.MenuItem("Abrir transferências",open_folder),
  pystray.MenuItem("Sair",stop))
 icon=pystray.Icon("nexus_pc_remote",icon_image(),"NEXUS PC Remote",menu);icon.run()

if __name__=="__main__":
 if not firewall_ok():threading.Timer(.8,lambda:repair_firewall(quiet=True)).start()
 threading.Thread(target=run_discovery_responder,daemon=True,name="DiscoveryResponder").start()
 threading.Thread(target=run_server,daemon=True,name="RemoteServer").start()
 threading.Thread(target=run_tray,daemon=True,name="Tray").start()
 edge=DropEdge()
 try:edge.run()
 finally:
  if server:server.shutdown()
