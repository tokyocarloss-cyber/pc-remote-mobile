import ctypes, os, subprocess, sys, threading, time, urllib.request
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
tray_icon=None
server_error=''
CREATE_NO_WINDOW=0x08000000
TCP_RULE='NEXUS PC Remote TCP v2'
UDP_RULE='NEXUS PC Remote UDP v2'

def icon_image():
 im=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(im)
 d.rounded_rectangle((5,5,59,59),14,fill=(7,13,20,255),outline=(55,207,255,255),width=3)
 d.line((19,45,19,19,45,45,45,19),fill=(76,222,255,255),width=7,joint='curve')
 return im

def message(title,text):
 ctypes.windll.user32.MessageBoxW(0,text,title,0x40)

def stop(icon=None,item=None):
 global server
 if server:
  try:server.shutdown()
  except:pass
 try:
  if edge:edge.root.after(0,edge.root.destroy)
 except:pass
 try:
  if tray_icon:tray_icon.stop()
 except:pass

def open_folder(icon=None,item=None):os.startfile(str(ROOT))

def local_server_ok():
 try:
  with urllib.request.urlopen(f'http://127.0.0.1:{PORT}/ping',timeout=.6) as r:
   return r.status==200 and b'PC Remote' in r.read(64)
 except Exception:return False

def firewall_ok():
 try:
  a=subprocess.run(['netsh','advfirewall','firewall','show','rule',f'name={TCP_RULE}'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=CREATE_NO_WINDOW)
  b=subprocess.run(['netsh','advfirewall','firewall','show','rule',f'name={UDP_RULE}'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=CREATE_NO_WINDOW)
  return a.returncode==0 and b.returncode==0
 except Exception:return False

def repair_firewall(icon=None,item=None,quiet=False):
 try:
  # profile=any is intentional: Ethernet is often classified as Public while the phone is on Wi-Fi.
  cmd=(f'/c netsh advfirewall firewall delete rule name="{TCP_RULE}" >nul 2>&1 '
       f'& netsh advfirewall firewall delete rule name="{UDP_RULE}" >nul 2>&1 '
       f'& netsh advfirewall firewall add rule name="{TCP_RULE}" dir=in action=allow protocol=TCP localport={PORT} profile=any enable=yes '
       f'& netsh advfirewall firewall add rule name="{UDP_RULE}" dir=in action=allow protocol=UDP localport={DISCOVERY_PORT} profile=any enable=yes')
  r=ctypes.windll.shell32.ShellExecuteW(None,'runas','cmd.exe',cmd,None,0)
  if r<=32 and not quiet:message('NEXUS PC Remote','Não foi possível pedir permissão para liberar a conexão no Firewall do Windows.')
  elif not quiet:message('NEXUS PC Remote','Autorize a janela do Windows. A regra será liberada para redes Privadas e Públicas, inclusive PC no cabo + celular no Wi‑Fi.')
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
  message('NEXUS PC Remote','O controle virtual é opcional e ainda não está instalado. O restante do NEXUS continua funcionando normalmente.')

def run_server():
 global server,server_error
 try:
  server=ThreadingHTTPServer(('0.0.0.0',PORT),H)
  server.serve_forever()
 except OSError as e:
  # If an older NEXUS instance already owns the port, reuse it instead of dying silently.
  if local_server_ok():server_error='Servidor NEXUS já estava ativo'
  else:server_error='Porta 8765 indisponível: '+str(e)
 except Exception as e:server_error=str(e)

def make_menu():
 pad='Controle virtual: pronto' if GAMEPAD else 'Controle virtual: indisponível'
 return pystray.Menu(
  pystray.MenuItem(lambda _:f'NEXUS • {local_ip()}:{PORT}',None,enabled=False),
  pystray.MenuItem(lambda _:'Servidor: ativo' if local_server_ok() else ('Servidor: '+server_error if server_error else 'Servidor: iniciando'),None,enabled=False),
  pystray.MenuItem(lambda _:'Celular: conectado' if phone_connected() else 'Celular: aguardando',None,enabled=False),
  pystray.MenuItem('Liberar conexão no Firewall',repair_firewall),
  pystray.MenuItem(pad,install_gamepad_driver,enabled=GAMEPAD is None),
  pystray.MenuItem('Abrir transferências',open_folder),
  pystray.MenuItem('Sair',stop))

def start_tray():
 global tray_icon
 if pystray is None:return
 tray_icon=pystray.Icon('nexus_pc_remote',icon_image(),'NEXUS PC Remote',make_menu())
 try:
  tray_icon.run_detached()
 except Exception:
  threading.Thread(target=tray_icon.run,daemon=False,name='NexusTray').start()

def start_services():
 threading.Thread(target=run_discovery_responder,daemon=True,name='DiscoveryResponder').start()
 threading.Thread(target=run_server,daemon=True,name='RemoteServer').start()

if __name__=='__main__':
 start_services()
 start_tray()
 if not firewall_ok():threading.Timer(1.0,lambda:repair_firewall(quiet=True)).start()
 edge=DropEdge()
 try:edge.run()
 finally:
  if server:
   try:server.shutdown()
   except:pass
  try:
   if tray_icon:tray_icon.stop()
  except:pass
