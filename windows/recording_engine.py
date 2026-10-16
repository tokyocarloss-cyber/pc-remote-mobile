"""Local MJPEG/AVI recorder for both PC and explicitly shared Android screens.

The recorder controls quality, capture rate and output size independently of the
interactive JPEG streaming path. Audio is not recorded. No third-party encoder
binary or Xbox Game Bar shortcut is required.
"""
import io
import struct
import threading
import time
from datetime import datetime
from pathlib import Path
from PIL import Image, ImageOps

OUTPUT_DIR = Path.home() / "Videos" / "NEXUS PC Remote"


def profile(width=1280, height=720, fps=30, quality=75):
    width, height = int(width), int(height)
    fps, quality = int(fps), int(quality)
    if width < 160 or height < 160 or width > 2560 or height > 2560:
        raise ValueError("Invalid recording dimensions")
    if fps not in (5, 10, 15, 24, 30, 60):
        raise ValueError("Unsupported recording FPS")
    if quality not in (55, 75, 90):
        raise ValueError("Unsupported JPEG quality")
    return width, height, fps, quality


def _chunk(tag, data):
    return tag + struct.pack("<I", len(data)) + data + (b"\0" if len(data) & 1 else b"")


class MjpegAviWriter:
    """Minimal indexed AVI writer with one MJPG video stream."""
    def __init__(self, target, width, height, fps):
        self.path = Path(target)
        self.width, self.height, self.fps = int(width), int(height), int(fps)
        self.handle = self.path.open("w+b")
        self.index = []
        self.frames = 0
        self.closed = False
        try:
            avih = struct.pack(
                "<IIIIIIIIII4I",
                1000000 // fps, width * height * 3 * fps, 0, 0x10,
                0, 0, 1, width * height * 3, width, height, 0, 0, 0, 0
            )
            strh = struct.pack(
                "<4s4sIHHIIIIIIIIhhhh",
                b"vids", b"MJPG", 0, 0, 0,
                0, 1, fps, 0, 0, width * height * 3, 0xffffffff, 0,
                0, 0, width, height
            )
            strf = struct.pack(
                "<IiiHHIIiiII",
                40, width, height, 1, 24, int.from_bytes(b"MJPG", "little"),
                width * height * 3, 0, 0, 0, 0
            )
            header = _chunk(b"avih", avih)
            stream = _chunk(b"strh", strh) + _chunk(b"strf", strf)
            header += b"LIST" + struct.pack("<I", len(stream) + 4) + b"strl" + stream
            hdrl = b"LIST" + struct.pack("<I", len(header) + 4) + b"hdrl" + header
            self.handle.write(b"RIFF\0\0\0\0AVI " + hdrl)
            self.movie_start = self.handle.tell()
            self.handle.write(b"LIST\0\0\0\0movi")
            self.first_chunk = self.handle.tell()
        except Exception:
            self.handle.close()
            self.path.unlink(missing_ok=True)
            raise

    def write(self, jpeg):
        if self.closed:
            raise ValueError("AVI writer closed")
        offset = self.handle.tell() - (self.movie_start + 8)
        self.handle.write(_chunk(b"00dc", jpeg))
        self.index.append((offset, len(jpeg)))
        self.frames += 1

    def close(self):
        if self.closed:
            return
        self.closed = True
        try:
            end_of_movie = self.handle.tell()
            if (end_of_movie - self.movie_start - 8) & 1:
                self.handle.write(b"\0")
                end_of_movie += 1
            self.handle.write(b"idx1")
            self.handle.write(struct.pack("<I", len(self.index) * 16))
            for offset, size in self.index:
                self.handle.write(struct.pack("<4sIII", b"00dc", 0x10, offset, size))
            eof = self.handle.tell()
            self.handle.seek(4)
            self.handle.write(struct.pack("<I", eof - 8))
            self.handle.seek(self.movie_start + 4)
            self.handle.write(struct.pack("<I", end_of_movie - self.movie_start - 8))
            # avih frame count + strh length: locate the headers in the small prefix.
            self.handle.seek(0)
            head = self.handle.read(256) if self.handle.readable() else None
            if head is None:
                raise OSError("AVI output must be readable for finalization")
            for tag, relative in ((b"avih", 16), (b"strh", 32)):
                pos = head.find(tag)
                if pos < 0:
                    raise OSError("AVI header missing")
                self.handle.seek(pos + 8 + relative)
                self.handle.write(struct.pack("<I", self.frames))
            self.handle.flush()
        finally:
            self.handle.close()


def _as_image(source):
    if source is None:
        return None
    if isinstance(source, Image.Image):
        return source
    if isinstance(source, (bytes, bytearray)):
        with Image.open(io.BytesIO(source)) as image:
            return image.convert("RGB")
    raise TypeError("Frame source must return a PIL image or JPEG bytes")


def encode_sample(source, width, height, quality):
    im = _as_image(source)
    if im is None:
        return None
    im = im.convert("RGB")
    im.thumbnail((width, height), Image.Resampling.BILINEAR)
    background = Image.new("RGB", (width, height), "#090909")
    background.paste(im, ((width - im.width) // 2, (height - im.height) // 2))
    output = io.BytesIO()
    background.save(output, format="JPEG", quality=quality, optimize=False)
    return output.getvalue()


def unique_output(kind, ext):
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    filename = OUTPUT_DIR / f"NEXUS-{kind}-{stamp}.{ext}"
    i = 2
    while filename.exists():
        filename = OUTPUT_DIR / f"NEXUS-{kind}-{stamp}-{i}.{ext}"
        i += 1
    return filename


class RecordingManager:
    def __init__(self):
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._worker = None
        self._path = None
        self._frames = 0
        self._error = ""

    def status(self):
        with self._lock:
            return dict(active=self._worker is not None and self._worker.is_alive(),
                        path=str(self._path) if self._path else "",
                        frames=self._frames, error=self._error)

    def start(self, source, kind, width=1280, height=720, fps=30, quality=75):
        width, height, fps, quality = profile(width, height, fps, quality)
        with self._lock:
            if self._worker and self._worker.is_alive():
                raise RuntimeError("There is already a recording in progress")
            # Fail before creating a misleading empty file.
            first = _as_image(source())
            if first is None:
                raise RuntimeError("No live frames from the selected device")
            if kind == "phone" and first.height > first.width and width > height:
                width, height = height, width
            self._stop = threading.Event()
            self._frames, self._error = 0, ""
            self._path = unique_output(kind, "avi")
            def run():
                writer = None
                try:
                    writer = MjpegAviWriter(self._path, width, height, fps)
                    next_at = time.monotonic()
                    while not self._stop.is_set():
                        jpeg = encode_sample(source(), width, height, quality)
                        if jpeg:
                            writer.write(jpeg)
                            written = 1
                            next_at += 1.0 / fps
                            # If capture is slower than the requested FPS, repeat the
                            # freshest sample to keep real-time video duration correct.
                            # These are repeated frames, not additional captured FPS.
                            while next_at < time.monotonic() and written < fps * 2:
                                writer.write(jpeg)
                                written += 1
                                next_at += 1.0 / fps
                            with self._lock:
                                self._frames += written
                        else:
                            next_at += 1.0 / fps
                        if time.monotonic() - next_at > 2:
                            next_at = time.monotonic()
                        self._stop.wait(max(0.0, next_at - time.monotonic()))
                except Exception as error:
                    with self._lock:
                        self._error = str(error)[:200]
                finally:
                    if writer:
                        writer.close()
            self._worker = threading.Thread(target=run, name=f"NEXUS-{kind}-Recorder", daemon=True)
            self._worker.start()
            return str(self._path)

    def stop(self):
        with self._lock:
            worker = self._worker
        if worker:
            self._stop.set()
            worker.join(timeout=10)
        return self.status()


PC_RECORDER = RecordingManager()
PHONE_RECORDER = RecordingManager()
