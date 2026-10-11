"""CI-only TLS protocol fixture. Does not inject native input or capture a PC screen."""
import datetime, json, ssl, sys, tempfile, threading, time
from pathlib import Path
from http.server import ThreadingHTTPServer
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'windows'))
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
import pc_remote_server as nexus
import security
from unittest.mock import MagicMock
nexus.u=MagicMock()  # Protocol test only, never native input.
class Handler(nexus.H):
    def do_POST(self):
        if self.path=="/mouse":
            with open("fixture-mouse.jsonl","a") as log:
                log.write(json.dumps({"port":self.client_address[1],"time":time.monotonic()})+"\n")
        super().do_POST()
nexus.DEVICES=security.Devices()
nexus.DEVICES.open_pairing();nexus.DEVICES.pin='123456';nexus.DEVICES.expires=time.monotonic()+600
key=rsa.generate_private_key(public_exponent=65537,key_size=2048)
now=datetime.datetime.now(datetime.timezone.utc)
name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'NEXUS CI Fixture')])
cert=x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(days=1)).not_valid_after(now+datetime.timedelta(days=1)).sign(key,hashes.SHA256())
with tempfile.TemporaryDirectory() as tmp:
    cert_path=Path(tmp)/'cert.pem';key_path=Path(tmp)/'key.pem'
    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(cert_path,key_path)
    class Server(security.SecureServerMixin,ThreadingHTTPServer):pass
    http=Server(('0.0.0.0',8765),Handler);http.tls=ctx
    Path('fixture-ready').write_text('ready')
    http.serve_forever()
