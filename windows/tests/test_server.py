"""Regression tests for the NEXUS Windows LAN server."""
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ["NEXUS_DATA_DIR"] = tempfile.mkdtemp(prefix="nexus-test-auth-")
import pc_remote_server as server


class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.download_dir = tempfile.TemporaryDirectory(prefix="nexus-test-files-")
        server.ROOT = Path(cls.download_dir.name)
        cls.http = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        cls.base = "http://127.0.0.1:" + str(cls.http.server_port)
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        cls.download_dir.cleanup()

    def setUp(self):
        with server.ATTEMPTS_LOCK:
            server.ATTEMPTS.clear()

    def request(self, method, path, body=None, token=None):
        headers = {}
        if token:
            headers["X-Nexus-Token"] = token
        req = urllib.request.Request(self.base + path, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=3) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as ex:
            return ex.code, ex.read()

    def test_ping_without_pair(self):
        code, raw = self.request("GET", "/ping")
        self.assertEqual(code, 200)
        self.assertEqual(json.loads(raw)["version"], "2.0.0")

    def test_command_requires_auth(self):
        self.assertEqual(self.request("POST", "/key", b"ENTER")[0], 401)
        self.assertEqual(self.request("GET", "/files")[0], 401)
        self.assertEqual(self.request("GET", "/screen.jpg")[0], 401)

    def test_pair_wrong_pin_then_success(self):
        self.assertEqual(self.request("POST", "/pair", b"not-a-pin")[0], 401)
        code, data = self.request("POST", "/pair", server.PIN.encode())
        self.assertEqual(code, 200)
        token = json.loads(data)["token"]
        self.assertEqual(token, server.TOKEN)
        code, data = self.request("GET", "/status", token=token)
        self.assertEqual(code, 200)
        self.assertTrue(json.loads(data)["connected"])

    def test_pair_rate_limit(self):
        for _ in range(5):
            self.assertEqual(self.request("POST", "/pair", b"bad")[0], 401)
        self.assertEqual(self.request("POST", "/pair", b"bad")[0], 429)

    def test_reject_invalid_command(self):
        self.assertEqual(self.request("POST", "/key", b"NOT_ALLOWED", server.TOKEN)[0], 400)
        self.assertEqual(self.request("POST", "/launch", b"arbitrary.exe", server.TOKEN)[0], 400)
        self.assertEqual(self.request("POST", "/mouse", b"bad", server.TOKEN)[0], 400)

    def test_file_roundtrip(self):
        code, data = self.request("POST", "/upload/example.txt", b"hello nexus", server.TOKEN)
        self.assertEqual(code, 200)
        name = json.loads(data)["name"]
        code, data = self.request("GET", "/files", token=server.TOKEN)
        self.assertEqual(code, 200)
        self.assertTrue(any(f["name"] == name for f in json.loads(data)["files"]))
        code, data = self.request("GET", "/download/" + name, token=server.TOKEN)
        self.assertEqual(code, 200)
        self.assertEqual(data, b"hello nexus")

    def test_duplicate_names_are_not_overwritten(self):
        self.request("POST", "/upload/duplicate.txt", b"first", server.TOKEN)
        code, data = self.request("POST", "/upload/duplicate.txt", b"second", server.TOKEN)
        self.assertEqual(code, 200)
        self.assertNotEqual(json.loads(data)["name"], "duplicate.txt")
        self.assertEqual((server.ROOT / "duplicate.txt").read_bytes(), b"first")

    def test_block_bad_filename(self):
        self.assertEqual(self.request("POST", "/upload/%3Fbad.txt", b"a", server.TOKEN)[0], 400)


if __name__ == "__main__":
    unittest.main()
