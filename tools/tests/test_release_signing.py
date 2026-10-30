import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("release_signing", Path(__file__).resolve().parents[1] / "release_signing.py")
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningTests(unittest.TestCase):
    def test_private_material_rejected_inside_repository(self):
        for path in (signing.ROOT, signing.ROOT / ".cache/upload.json", signing.ROOT / "keys/../upload.p12"):
            with self.assertRaises(ValueError):
                signing.outside_repository(path)

    def test_unsupported_configuration_rejected_before_decrypt(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "signing.json"
            path.write_text(json.dumps({"version": 2}), encoding="utf-8")
            with patch.object(signing, "dpapi") as decrypt:
                with self.assertRaises(ValueError):
                    signing.signing_environment(path)
                decrypt.assert_not_called()

    @unittest.skipUnless(os.name == "nt", "Windows current-user credential protection")
    def test_protection_roundtrip_and_corruption_rejection(self):
        secret = b"artificial-upload-password-not-an-actual-credential"
        encrypted = signing.dpapi(secret)
        self.assertNotIn(secret, encrypted)
        self.assertEqual(signing.dpapi(encrypted, decrypt=True), secret)
        with self.assertRaises(RuntimeError):
            signing.dpapi(encrypted[:-20], decrypt=True)

    def test_existing_key_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as folder:
            key = Path(folder) / "attention-stats-upload.p12"
            key.write_bytes(b"artificial-existing-keystore")
            with patch.object(signing.os, "name", "nt"), patch.object(signing.shutil, "which", return_value="keytool"), \
                 patch.object(signing, "run", return_value='"owner","S-1-5-21-1-2-3-1001"'), \
                 patch.object(signing, "dpapi") as protect:
                with self.assertRaises(ValueError):
                    signing.provision(folder)
                protect.assert_not_called()
            self.assertEqual(key.read_bytes(), b"artificial-existing-keystore")


if __name__ == "__main__":
    unittest.main()
