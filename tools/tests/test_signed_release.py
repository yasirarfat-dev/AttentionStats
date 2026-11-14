import base64
import hashlib
import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
spec = importlib.util.spec_from_file_location("verify_signed_release", Path(__file__).resolve().parents[1] / "verify_signed_release.py")
signed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signed)


class SignedReleaseTests(unittest.TestCase):
    def test_current_profile_requires_both_explicit_version_fields(self):
        for fields in [[], ['--version-name', '1.1.0'], ['--version-code', '11']]:
            with self.subTest(fields=fields), patch.object(sys, 'argv', ['verify_signed_release.py', '--certificate', 'synthetic.pem', '--profile', 'current', *fields]):
                with self.assertRaisesRegex(ValueError, 'explicit expected version'):
                    signed.main()

    def test_certificate_is_exactly_one_pem(self):
        data = b"artificial-certificate-for-parser-only"
        pem = "-----BEGIN CERTIFICATE-----\n" + base64.b64encode(data).decode() + "\n-----END CERTIFICATE-----\n"
        self.assertEqual(signed.certificate_digest(pem), hashlib.sha256(data).hexdigest())
        for invalid in ("", pem + pem, "-----BEGIN CERTIFICATE-----\n!!!!\n-----END CERTIFICATE-----"):
            with self.assertRaises(ValueError):
                signed.certificate_digest(invalid)

    def test_signature_failure_and_unsigned_entries_are_rejected(self):
        from subprocess import CompletedProcess
        for code, output in [(1, "jar verified"), (16, "jar verified"), (20, "jar verified"), (0, "jar is unsigned"), (4, "jar is unsigned")]:
            with self.subTest(code=code, output=output), patch.object(signed.subprocess, "run", return_value=CompletedProcess([], code, output, "")):
                with self.assertRaises(ValueError):
                    signed.verify_jar(Path("synthetic.aab"))

    def test_normal_self_signed_upload_certificate_warning_is_accepted(self):
        from subprocess import CompletedProcess
        with patch.object(signed.subprocess, "run", return_value=CompletedProcess([], 4, "jar verified, with signer errors.", "")):
            signed.verify_jar(Path("synthetic.aab"))


if __name__ == "__main__":
    unittest.main()
