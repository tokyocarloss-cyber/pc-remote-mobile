"""Real HTTP integration tests. Native input/capture are explicitly mocked."""
import json, socket, tempfile, threading, time, unittest, urllib.error, urllib.request
from concurrent.futures import ThreadPoolExecutor
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch, MagicMock
import security
import pc_remote_server as nexus
import companion_v2 as companion

class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.patches = [patch.object(nexus,'ROOT',Path(cls.tmp.name)),patch.object(companion,'RECEIVED_DIR',Path(cls.tmp.name)),patch.object(nexus,'DEVICES',security.Devices()),patch.object(companion,'DEVICES',security.Devices())]
        for p in cls.patches:p.start()
        companion.DEVICES = nexus.DEVICES
        cls.http = ThreadingHTTPServer(('127.0.0.1',0),companion.CompanionHandler)
        cls.base = 'http://127.0.0.1:'+str(cls.http.server_port)
        threading.Thread(target=cls.http.serve_forever,daemon=True).start()
    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown();cls.http.server_close()
        for p in reversed(cls.patches):p.stop()
        cls.tmp.cleanup()
    def setUp(self):
        nexus.DEVICES.revoke_all()
        self.token=nexus.DEVICES.pair(nexus.DEVICES.open_pairing(),'Test Android')
    def call(self,path,data=None,headers=None,auth=True):
        h={'Authorization':'Bearer '+self.token} if auth else {}
        h.update(headers or {})
        try:
            with urllib.request.urlopen(urllib.request.Request(self.base+path,data=data,headers=h),timeout=4) as r:return r.status,r.read()
        except urllib.error.HTTPError as e:return e.code,e.read()
    def test_ping(self):self.assertEqual(self.call('/ping',auth=False)[0],200)
    def test_every_advanced_route_requires_auth(self):
        for path in ['/status','/screen.jpg','/apps','/fs/list','/fs/recent','/fs/download?path=x','/capture.jpg','/stream/status','/stream.raw','/outbox','/download/a','/app-icon/a']:
            with self.subTest(path=path):self.assertEqual(self.call(path,auth=False)[0],401)
        for path in ['/upload/a','/power','/key','/mouse-abs','/hotkey','/zoom','/record-toggle','/gamepad','/clipboard','/outbox-ack/a']:
            with self.subTest(path=path):self.assertEqual(self.call(path,b'',auth=False)[0],401)
    def test_cross_origin_denied_in_subclass(self):
        for path in ['/ping','/fs/list','/stream.raw']:
            self.assertEqual(self.call(path,headers={'Origin':'https://evil.invalid'})[0],403)
    def test_revocation_immediate(self):
        nexus.DEVICES.revoke_all();self.assertEqual(self.call('/status')[0],401)
    def test_pair_window_pin_and_reuse(self):
        self.assertEqual(self.call('/pair',b'{}',auth=False)[0],403)
        pin=nexus.DEVICES.open_pairing()
        code,raw=self.call('/pair',json.dumps({'pin':pin,'name':'Phone'}).encode(),auth=False)
        self.assertEqual(code,200);self.assertTrue(nexus.DEVICES.valid(json.loads(raw)['token']))
        self.assertEqual(self.call('/pair',json.dumps({'pin':pin}).encode(),auth=False)[0],403)
    def test_pair_attempt_limit_and_expiry(self):
        pin=nexus.DEVICES.open_pairing()
        for _ in range(5):self.assertIsNone(nexus.DEVICES.pair('bad','x'))
        self.assertIsNone(nexus.DEVICES.pair(pin,'x'))
        pin=nexus.DEVICES.open_pairing();nexus.DEVICES.expires=0
        self.assertIsNone(nexus.DEVICES.pair(pin,'x'))
    def test_bad_inputs_denied(self):
        self.assertEqual(self.call('/key',b'UNKNOWN')[0],400)
        self.assertEqual(self.call('/click',b'unknown')[0],400)
        self.assertEqual(self.call('/mouse',b'NaN,1')[0],400)
        self.assertEqual(self.call('/key',b'x'*65537)[0],413)
    def test_streamed_download(self):
        data=b'abc'*8192;(nexus.ROOT/'example.bin').write_bytes(data)
        self.assertEqual(self.call('/download/example.bin'),(200,data))
        self.assertEqual(self.call('/download/not-here.bin')[0],404)
    def test_upload_and_duplicate_concurrent(self):
        payload=b'nexus'*20000
        with ThreadPoolExecutor(max_workers=3) as pool:
            results=list(pool.map(lambda _:self.call('/upload/same.bin',payload)[0],range(3)))
        self.assertEqual(results,[200]*3)
        files=list(companion.RECEIVED_DIR.glob('same*.bin'));self.assertEqual(len(files),3)
        self.assertTrue(all(p.read_bytes()==payload for p in files))
    def test_empty_upload_and_filename(self):
        self.assertEqual(self.call('/upload/empty.bin',b'')[0],200)
        for path in ['/upload/..','/upload/%2e%2e%5csecret','/upload/C%3Asecret','/upload/%2fsecret']:
            self.assertEqual(self.call(path,b'x')[0],400)
    def test_interrupted_upload_cleans_partial(self):
        with socket.create_connection(self.http.server_address) as s:
            s.sendall(('POST /upload/broken.bin HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer '+self.token+'\r\nContent-Length: 1000\r\n\r\nshort').encode())
            s.shutdown(socket.SHUT_WR)
            while s.recv(4096):pass
        self.assertFalse((companion.RECEIVED_DIR/'broken.bin').exists())
        self.assertFalse(list(companion.RECEIVED_DIR.glob('*.nexus-part')))
    def test_private_credentials_outside_shared_roots(self):
        for path in [str(Path.home()/'.ssh/id_rsa'),str(Path.home()/'AppData/Local/NEXUS/security'),str(Path.home()/'Documents/../../secret')]:
            with self.assertRaises(PermissionError):companion.allowed_pc_path(path)
    def test_gamepad_validation_and_reset(self):
        pad=MagicMock()
        with patch.object(nexus,'GAMEPAD',pad),patch.object(nexus,'PAD_BUTTONS',{'A':1}):
            for data in [{'kind':'stick','stick':'left','x':float('nan')},{'kind':'button','button':'A','down':'false'},{'kind':'wrong'}]:
                self.assertEqual(self.call('/gamepad',json.dumps(data).encode())[0],400)
            self.assertEqual(self.call('/gamepad',b'{"kind":"reset"}')[0],200)
            pad.reset.assert_called_once()

if __name__=='__main__':unittest.main()
