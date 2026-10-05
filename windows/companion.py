import ctypes, io, os, shutil, subprocess, sys, threading, time, urllib.request
from pathlib import Path
from http.server import ThreadingHTTPServer
try:
 import pystray
except Exception:
 pystray=None
try:
 import mss
except Exception:
 mss=None
from PIL import Image, ImageDraw
import pc_remote_server as servermod
from pc_remote_server import H,PORT,DISCOVERY_PORT,local_ip,ROOT,GAMEPAD,run_discovery_responder,phone_connected
from drop_edge import DropEdge

server=None
edge=None
tray_icon=None
server_error=''
CREATE_NO_WINDOW=0x08000000
TCP_RULE='NEXUS PC Remote TCP v2'
UDP_RULE='NEXUS PC Remote UDP v2'
RECEIVED_DIR=Path.home()/'Downloads';RECEIVED_DIR.mkdir(parents=True,exist_ok=True)
LIBRARY_DIR=Path.home()/'Desktop'/'Alll'
_ORIGINAL_APPS_CATALOG=servermod.apps_catalog
FRAME_BYTES=b''
FRAME_LOCK=threading.Lock()

def apps_catalog_with_custom_folder():
 items=_ORIGINAL_APPS_CATALOG();seen={str(x.get('name','')).casefold() for x in items}
 try:
  if LIBRARY_DIR.exists():
   for p in sorted(LIBRARY_DIR.rglob('*'),key=lambda x:x.name.casefold()):
    if len(items)>=420:break
    if not p.is_file() or p.suffix.lower() not in ('.lnk','.url','.exe','.bat','.cmd'):continue
    name=p.stem.strip()
    if not name or name.casefold() in seen:continue
    items.append({'name':name,'store':'Alll'})
    servermod.APP_INDEX[name]={'shortcut':str(p),'launch_cmd':None,'steam_id':None,'icon_hint':str(p) if p.suffix.lower()=='.exe' else None,'store':'Alll'}
    seen.add(name.casefold())
 except Exception:pass
 return items

servermod.apps_catalog=apps_catalog_with_custom_folder

def icon_image():
 im=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(im)
 d.rounded_rectangle((5,5,59,59),14,fill=(7,13,20,255),outline=(55,207,255,255),width=3)
 d.line((19,45,19,19,45,45,45,19),fill=(76,222,255,255),width=7,joint='curve')
 return im

def message(title,text):ctypes.windll.user32.MessageBoxW(0,text,title,0x40)

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

def open_folder(icon=None,item=None):os.startfile(str(RECEIVED_DIR))

def local_server_ok():
 try:
  with urllib.request.urlopen(f'http://127.0.0.1:{PORT}/ping',timeout=.6) as r:return r.status==200 and b'PC Remote' in r.read(64)
 except Exception:return False

def firewall_ok():
 try:
  a=subprocess.run(['netsh','advfirewall','firewall','show','rule',f'name={TCP_RULE}'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=CREATE_NO_WINDOW)
  b=subprocess.run(['netsh','advfirewall','firewall','show','rule',f'name={UDP_RULE}'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=CREATE_NO_WINDOW)
  return a.returncode==0 and b.returncode==0
 except Exception:return False

def repair_firewall(quiet=False):
 try:
  cmd=(f'/c netsh advfirewall firewall delete rule name="{TCP_RULE}" >nul 2>&1 '
       f'& netsh advfirewall firewall delete rule name="{UDP_RULE}" >nul 2>&1 '
       f'& netsh advfirewall firewall add rule name="{TCP_RULE}" dir=in action=allow protocol=TCP localport={PORT} profile=any enable=yes '
       f'& netsh advfirewall firewall add rule name="{UDP_RULE}" dir=in action=allow protocol=UDP localport={DISCOVERY_PORT} profile=any enable=yes')
  r=ctypes.windll.shell32.ShellExecuteW(None,'runas','cmd.exe',cmd,None,0)
  if r<=32 and not quiet:message('NEXUS PC Remote','Não foi possível pedir permissão para liberar a conexão no Firewall do Windows.')
  elif not quiet:message('NEXUS PC Remote','Autorize a janela do Windows uma vez. Depois o NEXUS pode ficar só na bandeja.')
 except Exception as e:
  if not quiet:message('NEXUS PC Remote','Falha ao configurar o Firewall:\n'+str(e))

def repair_firewall_action(icon,item):repair_firewall(False)

def install_gamepad_driver(icon=None,item=None):
 try:
  import vgamepad
  base=Path(vgamepad.__file__).resolve().parent;arch='x64' if sys.maxsize>2**32 else 'x86'
  msi=base/'win'/'vigem'/'install'/arch/('ViGEmBusSetup_'+arch+'.msi')
  if not msi.exists():raise FileNotFoundError(str(msi))
  subprocess.Popen(['msiexec','/i',str(msi)],creationflags=CREATE_NO_WINDOW)
  message('NEXUS PC Remote','Instalador do controle virtual aberto.\n\nConclua a instalação e reinicie o NEXUS PC Remote.')
 except Exception:message('NEXUS PC Remote','O controle virtual é opcional e ainda não está instalado. O restante do NEXUS continua funcionando normalmente.')

def capture_loop():
 global FRAME_BYTES
 if mss is None:return
 try:
  with mss.mss() as sct:
   monitor=sct.monitors[1]
   while True:
    started=time.perf_counter()
    try:
     shot=sct.grab(monitor);im=Image.frombytes('RGB',shot.size,shot.rgb)
     w=max(640,min(2560,int(servermod.SCREEN_WIDTH)));h=max(360,min(1440,int(servermod.SCREEN_HEIGHT)))
     if im.width>w or im.height>h:im.thumbnail((w,h),Image.Resampling.BILINEAR)
     b=io.BytesIO();im.save(b,'JPEG',quality=max(35,min(90,int(servermod.SCREEN_QUALITY))),optimize=False)
     data=b.getvalue()
     with FRAME_LOCK:FRAME_BYTES=data
    except Exception:pass
    target=max(15,min(120,int(servermod.SCREEN_TARGET_FPS)))
    wait=(1.0/target)-(time.perf_counter()-started)
    if wait>0:time.sleep(wait)
 except Exception:pass

def fast_screenshot():
 with FRAME_LOCK:data=FRAME_BYTES
 if data:return data
 try:return servermod.screenshot_original()
 except Exception:return b''

def notify_received(path):
 try:
  size=path.stat().st_size;txt=f'{path.name} • {max(1,size//1024)} KB'
  if tray_icon:tray_icon.notify(txt,'Arquivo recebido do celular')
 except Exception:pass

def cleanup_old_transfer_stubs():
 try:
  for p in ROOT.iterdir():
   if p.is_file() and p.stat().st_size==0:p.unlink()
 except Exception:pass

def run_server():
 global server,server_error
 try:server=ThreadingHTTPServer(('0.0.0.0',PORT),H);server.serve_forever()
 except OSError as e:
  if local_server_ok():server_error='Servidor NEXUS já estava ativo'
  else:server_error='Porta 8765 indisponível: '+str(e)
 except Exception as e:server_error=str(e)

def make_menu():
 pad='Controle virtual: pronto' if GAMEPAD else 'Controle virtual: indisponível'
 firewall='Firewall: liberado' if firewall_ok() else 'Liberar conexão no Firewall'
 return pystray.Menu(
  pystray.MenuItem(lambda _:f'NEXUS • {local_ip()}:{PORT}',None,enabled=False),
  pystray.MenuItem(lambda _:'Servidor: ativo' if local_server_ok() else ('Servidor: '+server_error if server_error else 'Servidor: iniciando'),None,enabled=False),
  pystray.MenuItem(lambda _:'Celular: conectado' if phone_connected() else 'Celular: aguardando',None,enabled=False),
  pystray.MenuItem(lambda _:f'Biblioteca extra: {LIBRARY_DIR}',None,enabled=False),
  pystray.MenuItem(firewall,repair_firewall_action,enabled=not firewall_ok()),
  pystray.MenuItem(pad,install_gamepad_driver,enabled=GAMEPAD is None),
  pystray.MenuItem('Abrir Downloads',open_folder),
  pystray.MenuItem('Sair',stop))

def start_tray():
 global tray_icon
 if pystray is None:return
 tray_icon=pystray.Icon('nexus_pc_remote',icon_image(),'NEXUS PC Remote',make_menu())
 try:tray_icon.run_detached()
 except Exception:threading.Thread(target=tray_icon.run,daemon=False,name='NexusTray').start()

def start_services():
 cleanup_old_transfer_stubs()
 servermod.screenshot_original=servermod.screenshot;servermod.screenshot=fast_screenshot;servermod.UPLOAD_CALLBACK=notify_received
 threading.Thread(target=capture_loop,daemon=True,name='NexusCapture').start()
 threading.Thread(target=run_discovery_responder,daemon=True,name='DiscoveryResponder').start()
 threading.Thread(target=run_server,daemon=True,name='RemoteServer').start()

if __name__=='__main__':
 start_services();start_tray();edge=DropEdge()
 try:edge.run()
 finally:
  if server:
   try:server.shutdown()
   except:pass
  try:
   if tray_icon:tray_icon.stop()
  except:pass
