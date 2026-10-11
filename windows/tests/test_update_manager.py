"""Update protocol guardrails: explicit PC staging, integrity and no remote execution."""
import tempfile
import unittest
import zipfile
from pathlib import Path
from update_manager import UpdateManager, sha256_file, _validated_source


class UpdateTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
        self.apk=self.root/'nexus.apk'
        with zipfile.ZipFile(self.apk,'w') as archive:
            archive.writestr('AndroidManifest.xml',b'ANDROID TEST PACKAGE')
            archive.writestr('classes.dex',b'X'*2048)
        self.exe=self.root/'nexus.exe'
        self.exe.write_bytes(b'MZ'+b'X'*2500)
        self.manager=UpdateManager(self.root/'stage')

    def tearDown(self):
        self.temp.cleanup()

    def test_explicit_stage_then_manifest(self):
        self.assertFalse(self.manager.info()['ready'])
        result=self.manager.prepare(self.apk,self.exe)
        self.assertTrue(result['ready'])
        self.assertEqual(result['android']['sha256'],sha256_file(self.apk))
        self.assertEqual(result['windows']['sha256'],sha256_file(self.exe))
        self.assertEqual(self.manager.android_file().read_bytes(),self.apk.read_bytes())

    def test_reject_unsigned_fake_and_wrong_extensions(self):
        invalid=self.root/'bad.apk'
        invalid.write_bytes(b'A'*3000)
        with self.assertRaises(ValueError):self.manager.prepare(invalid,self.exe)
        with self.assertRaises(ValueError):self.manager.prepare(self.apk,self.apk)

    def test_reject_remote_apply_until_frozen_exe(self):
        manifest=self.manager.prepare(self.apk,self.exe)
        with self.assertRaises(ValueError):
            self.manager.begin_windows_install('wrong',
                manifest['android']['sha256'],manifest['windows']['sha256'])
        with self.assertRaises(RuntimeError):
            self.manager.begin_windows_install(manifest['nonce'],
                manifest['android']['sha256'],manifest['windows']['sha256'])

    def test_expired_and_tamper(self):
        manifest=self.manager.prepare(self.apk,self.exe)
        self.manager.android_file().write_bytes(b'CHANGED')
        self.assertNotEqual(sha256_file(self.manager.android_file()),
                            manifest['android']['sha256'])
        self.manager.bundle['expires_at']=0
        self.assertFalse(self.manager.info()['ready'])
        self.assertIsNone(self.manager.android_file())


if __name__=='__main__': unittest.main()
