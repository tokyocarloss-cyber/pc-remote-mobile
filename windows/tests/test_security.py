"""Regressions for LAN access and HTTP input validation on Windows."""
import tempfile, threading, unittest, urllib.error, urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
import pc_remote_server as nexus

class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.root = nexus.ROOT
        nexus.ROOT = Path(cls.tmp.name)
        cls.http = ThreadingHTTPServer(("127.0.0.1", 0), nexus.H)
        cls.base = "http://127.0.0.1:" + str(cls.http.server_port)
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        nexus.ROOT = cls.root
        cls.tmp.cleanup()

    def call(self, path, data=None, headers=None):
        request = urllib.request.Request(self.base + path, data=data, headers=headers or {})
        try:
            with urllib.request.urlopen(request, timeout=4) as resp:
                return resp.status, resp.read()
        except urllib.error.HTTPError as err:
            return err.code, err.read()

    def test_ping(self):
        self.assertEqual(self.call("/ping")[0], 200)

    def test_cross_origin_denied(self):
        self.assertEqual(self.call("/ping", headers={"Origin": "https://example.org"})[0], 403)
        self.assertEqual(self.call("/key", b"ENTER", {"Origin": "https://example.org"})[0], 403)

    def test_bad_inputs_denied(self):
        self.assertEqual(self.call("/key", b"UNKNOWN")[0], 400)
        self.assertEqual(self.call("/click", b"unknown")[0], 400)

    def test_streamed_download(self):
        (nexus.ROOT / "example.bin").write_bytes(b"abc" * 8192)
        self.assertEqual(self.call("/download/example.bin"), (200, b"abc" * 8192))
        self.assertEqual(self.call("/download/not-here.bin")[0], 404)
