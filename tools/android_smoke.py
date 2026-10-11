"""Launch, theme and navigation smoke checks on an actual Android emulator."""
from pathlib import Path
import atexit, re, subprocess, time, xml.etree.ElementTree as ET
from xml.sax.saxutils import escape
OUT=Path('android-smoke');OUT.mkdir(exist_ok=True)
PACKAGE='com.pcremote.mobile'
def adb(*args,**kw):return subprocess.check_output(['adb',*args],timeout=35,**kw)
def shell(*args):return adb('shell',*args).decode(errors='replace')
def screenshot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def collect_diagnostics():
    try:
        (OUT/'crash-log.txt').write_text(shell('logcat','-d','-b','crash'))
        (OUT/'system-log.txt').write_text(shell('logcat','-d','-t','1500'))
        screenshot('final-state')
    except Exception as error: print('Diagnostic collection:',error)
atexit.register(collect_diagnostics)
def launch():
    shell('am','start','-W','-n',PACKAGE+'/.NexusV2Activity');time.sleep(3)
    state=shell('dumpsys','activity','activities')
    assert PACKAGE in state and shell('pidof',PACKAGE).strip(),'NEXUS process did not start'
def nodes():
    shell('uiautomator','dump','/sdcard/nexus-ui.xml')
    xml=shell('cat','/sdcard/nexus-ui.xml')
    (OUT/'last-ui.xml').write_text(xml)
    return ET.fromstring(xml)
def tap(label):
    for node in nodes().iter('node'):
        if label in (node.get('text'),node.get('content-desc')):
            box=list(map(int,re.findall(r'\d+',node.get('bounds'))))
            shell('input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2));time.sleep(1.2);return
    raise AssertionError('UI action not found: '+label)
adb('install','-r','app.apk')
shell('pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')
shell('logcat','-c')
# Pixel Launcher can ANR during cold emulator boot; stop that unrelated process.
shell('am','force-stop','com.google.android.apps.nexuslauncher')
for i,theme in enumerate(['NEXUS Neon','Attack on Titan','Frutiger Aero','Dark Souls','Anime Prism','Naruto','Cyber Samurai','Sakura Night','Retro CRT','Arctic Glass']):
    shell('am','force-stop',PACKAGE)
    shell('run-as',PACKAGE,'mkdir','-p','shared_prefs')
    pref='<map><string name="theme">'+escape(theme)+'</string><boolean name="theme_sounds" value="false"/></map>'
    adb('shell',f"run-as {PACKAGE} sh -c 'cat > shared_prefs/nexus_settings.xml'",input=pref.encode())
    launch();screenshot(f'theme-{i:02}')
for label in ['Apps','Mouse','Controle','Início']:
    tap(label);screenshot('page-'+label)
for label in ['Arquivos','Tela','Teclado','Mídia','Configurações','Flow']:
    tap('Mais');tap(label);screenshot('page-'+label)
shell('settings','put','system','accelerometer_rotation','0')
shell('settings','put','system','user_rotation','1');time.sleep(2);screenshot('landscape')
shell('settings','put','system','user_rotation','0')
# Exercise the real Android pinned-TLS transport, PIN failure, Keystore save and reconnect.
fixture=subprocess.Popen(['python','tools/emulator_pc_fixture.py'])
try:
    for _ in range(30):
        if Path('fixture-ready').exists():break
        time.sleep(.2)
    assert Path('fixture-ready').exists(),'TLS fixture did not start'
    shell('am','force-stop',PACKAGE)
    secure='<map><string name="last_host">10.0.2.2</string></map>'
    adb('shell',f"run-as {PACKAGE} sh -c 'cat > shared_prefs/nexus_secure.xml'",input=secure.encode())
    launch()
    def tap_class(name):
        for node in nodes().iter('node'):
            if node.get('class')==name:
                box=list(map(int,re.findall(r'\d+',node.get('bounds'))))
                shell('input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2));return
        raise AssertionError('UI class not found: '+name)
    tap_class('android.widget.CheckBox')
    tap_class('android.widget.EditText');shell('input','text','000000');shell('input','keyevent','4')
    tap('AUTORIZAR');time.sleep(1)
    assert 'PIN inválido' in ET.tostring(nodes(),encoding='unicode'),'Wrong PIN was not rejected'
    tap_class('android.widget.EditText');shell('input','keyevent','123')
    for _ in range(6):shell('input','keyevent','67')
    shell('input','text','123456');shell('input','keyevent','4');tap('AUTORIZAR');time.sleep(2)
    assert 'Conectado' in ET.tostring(nodes(),encoding='unicode'),'TLS pairing failed'
    screenshot('paired-tls')
    shell('am','force-stop',PACKAGE);launch();time.sleep(2)
    assert 'Conectado' in ET.tostring(nodes(),encoding='unicode'),'Persisted Keystore reconnect failed'
    screenshot('reconnected-tls')
finally:
    fixture.terminate();fixture.wait(timeout=10)
crashes=shell('logcat','-d','-b','crash');(OUT/'crash-log.txt').write_text(crashes)
assert 'FATAL EXCEPTION' not in crashes,crashes
(OUT/'result.txt').write_text('PASS: 10 themes, 10 destinations, rotation, wrong PIN rejected, TLS pairing and persisted reconnect, no Java crash. No physical-PC input assertion.\n')
