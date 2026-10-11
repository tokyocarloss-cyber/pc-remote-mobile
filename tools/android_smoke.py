"""Launch, theme and navigation smoke checks on an actual Android emulator."""
from pathlib import Path
import re, subprocess, time, xml.etree.ElementTree as ET
from xml.sax.saxutils import escape
OUT=Path('android-smoke');OUT.mkdir(exist_ok=True)
PACKAGE='com.pcremote.mobile'
def adb(*args,**kw):return subprocess.check_output(['adb',*args],timeout=35,**kw)
def shell(*args):return adb('shell',*args).decode(errors='replace')
def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def launch():
    shell('am','start','-W','-n',PACKAGE+'/.NexusV2Activity');time.sleep(3)
    state=shell('dumpsys','activity','activities')
    assert PACKAGE in state and shell('pidof',PACKAGE).strip(),'NEXUS process did not start'
def nodes():
    shell('uiautomator','dump','/sdcard/nexus-ui.xml')
    xml=shell('cat','/sdcard/nexus-ui.xml');return ET.fromstring(xml)
def tap(label):
    for node in nodes().iter('node'):
        if label in (node.get('text'),node.get('content-desc')):
            box=list(map(int,re.findall(r'\d+',node.get('bounds'))))
            shell('input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2));time.sleep(1.2);return
    raise AssertionError('UI action not found: '+label)
adb('install','-r','app.apk')
shell('pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
shell('logcat','-c')
for i,theme in enumerate(['NEXUS Neon','Attack on Titan','Frutiger Aero','Dark Souls','Anime Prism','Naruto','Cyber Samurai','Sakura Night','Retro CRT','Arctic Glass']):
    shell('am','force-stop',PACKAGE)
    shell('run-as',PACKAGE,'mkdir','-p','shared_prefs')
    pref='<map><string name="theme">'+escape(theme)+'</string><boolean name="theme_sounds" value="false"/></map>'
    adb('shell','run-as',PACKAGE,'sh','-c','cat > shared_prefs/nexus_settings.xml',input=pref.encode())
    launch();screenshot(f'theme-{i:02}')
for label in ['Apps','Mouse','Controle','Início']:
    tap(label);screenshot('page-'+label)
for label in ['Arquivos','Tela','Teclado','Mídia','Configurações','Flow']:
    tap('Mais');tap(label);screenshot('page-'+label)
shell('settings','put','system','accelerometer_rotation','0')
shell('settings','put','system','user_rotation','1');time.sleep(2);screenshot('landscape')
shell('settings','put','system','user_rotation','0')
crashes=shell('logcat','-d','-b','crash');(OUT/'crash-log.txt').write_text(crashes)
assert 'FATAL EXCEPTION' not in crashes,crashes
(OUT/'result.txt').write_text('PASS: 10 themes, 10 destinations, rotation, no Java crash. No physical-PC input assertion.\n')
