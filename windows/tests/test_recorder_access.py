"""New capture endpoints must not be remotely usable without an authorization key."""
import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

import companion_v2 as nexus
import entertainment_hub as hub


class RecordAccessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), nexus.CompanionHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = "http://127.0.0.1:" + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def call(self, path, payload=None, headers=None):
        req = urllib.request.Request(self.base + path, data=payload, headers=headers or {})
        try:
            with urllib.request.urlopen(req, timeout=5) as res:
                return res.status, res.read()
        except urllib.error.HTTPError as err:
            return err.code, err.read()

    def test_new_capture_paths_require_authentication(self):
        for path in ("/record/status", "/phone-screen/status", "/phone-screen/profile"):
            self.assertEqual(self.call(path)[0], 401, path)
        for path in ("/record/start", "/record/stop", "/record/screenshot",
                     "/record-toggle", "/phone-screen/frame"):
            self.assertEqual(self.call(path, b"{}")[0], 401, path)

    def test_valid_key_can_read_record_status_without_starting_capture(self):
        token = hub.pairing_key()
        code, raw = self.call("/record/status", headers={"X-Nexus-Central-Key": token})
        self.assertEqual(code, 200)
        self.assertIn("pc", json.loads(raw))
        self.assertIn("phone", json.loads(raw))

    def test_bad_key_is_rejected(self):
        code, _ = self.call("/record/status", headers={"X-Nexus-Central-Key": "wrong"})
        self.assertEqual(code, 401)


if __name__ == "__main__":
    unittest.main()
