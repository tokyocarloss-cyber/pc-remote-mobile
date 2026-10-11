"""Local pairing, hashed device credentials and a DPAPI-protected TLS identity."""
import ctypes
import datetime
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import ssl
import tempfile
import threading
import time

DATA = Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'NEXUS' / 'security'
DATA.mkdir(parents=True, exist_ok=True)


def protect(data, decrypt=False):
    if os.name != 'nt':
        raise RuntimeError('Persistent credentials require Windows DPAPI')
    class Blob(ctypes.Structure):
        _fields_ = [('size', ctypes.c_ulong), ('data', ctypes.POINTER(ctypes.c_ubyte))]
    buf = ctypes.create_string_buffer(data)
    src = Blob(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_ubyte)))
    dst = Blob()
    fn = ctypes.windll.crypt32.CryptUnprotectData if decrypt else ctypes.windll.crypt32.CryptProtectData
    if not fn(ctypes.byref(src), None, None, None, None, 1, ctypes.byref(dst)):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(dst.data, dst.size)
    finally:
        ctypes.windll.kernel32.LocalFree(ctypes.cast(dst.data, ctypes.c_void_p))


class Devices:
    def __init__(self, path=None):
        self.path = path
        self.lock = threading.RLock()
        self.devices = {}
        self.expires = 0
        self.pin = ''
        self.attempts = 0
        if path and path.exists():
            self.devices = json.loads(protect(path.read_bytes(), True))

    def save(self):
        if self.path:
            temp = self.path.with_suffix('.tmp')
            temp.write_bytes(protect(json.dumps(self.devices).encode()))
            temp.replace(self.path)

    def open_pairing(self):
        with self.lock:
            self.pin = f'{secrets.randbelow(1000000):06d}'
            self.expires = time.monotonic() + 120
            self.attempts = 0
            return self.pin

    def pair(self, pin, name):
        with self.lock:
            if time.monotonic() >= self.expires or self.attempts >= 5:
                return None
            self.attempts += 1
            if not hmac.compare_digest(str(pin), self.pin):
                return None
            token = secrets.token_urlsafe(32)
            self.devices[hashlib.sha256(token.encode()).hexdigest()] = {'name': str(name)[:80], 'created': int(time.time())}
            self.save()
            self.expires = 0  # Each local approval authorizes exactly one phone.
            return token

    def valid(self, token):
        if not token or len(token) > 128:
            return False
        with self.lock:
            return hashlib.sha256(token.encode()).hexdigest() in self.devices

    def revoke_all(self):
        with self.lock:
            self.devices.clear()
            self.expires = 0
            self.save()


DEVICES = Devices(DATA / 'devices.dpapi')


def tls_context():
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import NameOID
    cert_path = DATA / 'server.pem'
    key_path = DATA / 'server-key.dpapi'
    if not cert_path.exists() or not key_path.exists():
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        now = datetime.datetime.now(datetime.timezone.utc)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'NEXUS Local Companion')])
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
                .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(days=1))
                .not_valid_after(now + datetime.timedelta(days=3650)).sign(key, hashes.SHA256()))
        pem = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
        key_path.write_bytes(protect(pem))
        cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    # SSLContext needs a filename; the private key is removed immediately after loading.
    with tempfile.TemporaryDirectory(dir=DATA) as directory:
        temp = Path(directory) / 'key.pem'
        temp.write_bytes(protect(key_path.read_bytes(), True))
        ctx.load_cert_chain(cert_path, temp)
    return ctx


def fingerprint():
    der = ssl.PEM_cert_to_DER_cert((DATA / 'server.pem').read_text())
    return hashlib.sha256(der).hexdigest().upper()


class SecureServerMixin:
    """TLS handshake in the worker, with a timeout: a slow peer cannot block accept."""
    def get_request(self):
        sock, address = super().get_request()
        sock.settimeout(15)
        return self.tls.wrap_socket(sock, server_side=True, do_handshake_on_connect=False), address
