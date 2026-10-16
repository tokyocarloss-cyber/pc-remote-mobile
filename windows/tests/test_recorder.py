import io
import os
import struct
import tempfile
import unittest
from pathlib import Path
from PIL import Image
import recording_engine as rec


class RecorderTests(unittest.TestCase):
    def test_validation(self):
        self.assertEqual(rec.profile(1280, 720, 30, 75), (1280, 720, 30, 75))
        with self.assertRaises(ValueError):
            rec.profile(1280, 720, 120, 75)
        with self.assertRaises(ValueError):
            rec.profile(1280, 720, 30, 101)

    def test_quality_and_frame_size(self):
        pic = Image.new("RGB", (800, 600))
        px = pic.load()
        for y in range(600):
            for x in range(800):
                px[x, y] = ((x * y) % 256, (x + y) % 256, (x * 3 + y * 7) % 256)
        low = rec.encode_sample(pic, 640, 360, 55)
        high = rec.encode_sample(pic, 640, 360, 90)
        with Image.open(io.BytesIO(low)) as img:
            self.assertEqual(img.size, (640, 360))
        self.assertGreater(len(high), len(low))

    def test_avi_index_and_duration(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "test.avi"
            jpeg = rec.encode_sample(Image.new("RGB", (320, 240)), 320, 240, 75)
            writer = rec.MjpegAviWriter(target, 320, 240, 15)
            writer.write(jpeg)
            writer.write(jpeg)
            writer.close()
            blob = target.read_bytes()
            self.assertEqual(blob[:4], b"RIFF")
            self.assertEqual(blob[8:12], b"AVI ")
            self.assertEqual(blob[blob.find(b"idx1") + 8:blob.find(b"idx1") + 12], b"00dc")
            self.assertEqual(struct.unpack_from("<I", blob, 4)[0], len(blob) - 8)
            avih = blob.find(b"avih")
            strh = blob.find(b"strh")
            self.assertEqual(struct.unpack_from("<I", blob, avih + 8 + 16)[0], 2)
            self.assertEqual(struct.unpack_from("<I", blob, strh + 8 + 32)[0], 2)
            self.assertGreater(blob.count(b"00dc"), 2)


if __name__ == "__main__":
    unittest.main()
