"""Streaming regression tests; run on Windows CI with Pillow and the companion imports."""
import io
import json
import time
import threading
import unittest
import urllib.request
from http.server import ThreadingHTTPServer

from PIL import Image
import companion_v2 as nexus
import pc_remote_server as servermod


class StreamingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), nexus.CompanionHandler)
        cls.base = "http://127.0.0.1:" + str(cls.server.server_port)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def test_quality_is_real_and_resolution_is_limited(self):
        # The JPEG data, not only the UI title, must change with quality.
        img = Image.new("RGB", (1400, 880))
        px = img.load()
        for y in range(0, 880, 8):
            for x in range(0, 1400, 8):
                for oy in range(8):
                    for ox in range(8):
                        px[x + ox, y + oy] = (
                            (x * 7 + y * 11) % 256,
                            (x * 3 + y * 17) % 256,
                            (x * 13 + y * 5) % 256
                        )
        defaults = (servermod.SCREEN_WIDTH, servermod.SCREEN_HEIGHT, servermod.SCREEN_QUALITY)
        try:
            servermod.SCREEN_WIDTH, servermod.SCREEN_HEIGHT = 640, 360
            servermod.SCREEN_QUALITY = 40
            low = nexus.encode_frame(img.copy())
            servermod.SCREEN_QUALITY = 85
            high = nexus.encode_frame(img.copy())
            self.assertNotEqual(low, high)
            self.assertGreater(len(high), len(low))
            with Image.open(io.BytesIO(low)) as decoded:
                self.assertLessEqual(decoded.width, 640)
                self.assertLessEqual(decoded.height, 360)
        finally:
            servermod.SCREEN_WIDTH, servermod.SCREEN_HEIGHT, servermod.SCREEN_QUALITY = defaults

    def test_status_reports_actual_not_requested_fps(self):
        with nexus.FRAME_READY:
            nexus.FRAME_BYTES = b"jpeg-placeholder"
            nexus.FRAME_SEQ += 1
            nexus.FRAME_LAST_AT = time.monotonic()
            nexus.CAPTURE_WIDTH = 1152
            nexus.CAPTURE_HEIGHT = 720
            nexus.CAPTURE_FPS = 31.4
            nexus.CAPTURE_ENCODE_MS = 24.2
            nexus.CAPTURE_SOURCE = "TEST"
        with urllib.request.urlopen(self.base + "/stream/status", timeout=3) as response:
            self.assertEqual(response.status, 200)
            state = json.loads(response.read())
        self.assertEqual(state["width"], 1152)
        self.assertEqual(state["height"], 720)
        self.assertEqual(state["actual_capture_fps"], 31.4)
        self.assertEqual(state["encode_ms"], 24.2)
        self.assertIn("target_fps", state)

    def test_conflation_notification_changes_sequence(self):
        with nexus.FRAME_READY:
            first = nexus.FRAME_SEQ
            observed = []
            def waiter():
                with nexus.FRAME_READY:
                    nexus.FRAME_READY.wait_for(lambda: nexus.FRAME_SEQ != first, timeout=2)
                    observed.append(nexus.FRAME_SEQ)
            worker = threading.Thread(target=waiter)
            worker.start()
            # Release the lock before signaling with a newly published frame.
        with nexus.FRAME_READY:
            nexus.FRAME_SEQ += 1
            nexus.FRAME_READY.notify_all()
        worker.join(timeout=3)
        self.assertEqual(observed, [first + 1])


if __name__ == "__main__":
    unittest.main()
