"""Safety boundaries for the phone runner; all adb responses and APKs are synthetic."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1] / "test_collection_probe.py"
SPEC = importlib.util.spec_from_file_location("collection_probe", SOURCE)
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class PhoneRunnerSafety(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.adb = self.root / "adb"
        self.adb.write_bytes(b"synthetic executable placeholder")
        for relative in ["debug/app-debug.apk", "androidTest/debug/app-debug-androidTest.apk"]:
            apk = self.root / "android/app/build/outputs/apk" / relative
            apk.parent.mkdir(parents=True, exist_ok=True)
            apk.write_bytes(b"synthetic artifact")
        self.targets = "List of devices attached\nfixture-phone\tdevice\n"
        self.api = "36"
        self.virtual = "0"
        self.existing_test = False
        self.instrumentation = probe.SUCCESS + "\nINSTRUMENTATION_CODE: -1\n"
        self.cleanup = "Success\n"
        self.calls = []

    def respond(self, command, **kwargs):
        if command[1:] == ["devices"]:
            return subprocess.CompletedProcess(command, 0, self.targets, "")
        self.assertEqual(command[1:3], ["-s", "fixture-phone"])
        operation = command[3:]
        self.calls.append(operation)
        if operation[:2] == ["shell", "getprop"]:
            value = self.virtual if operation[2] == "ro.kernel.qemu" else self.api
        elif operation[:4] == ["shell", "pm", "list", "packages"]:
            value = "package:" + probe.TEST if self.existing_test else ""
        elif operation[0] == "install":
            value = "Success\n"
        elif operation[:3] == ["shell", "am", "instrument"]:
            value = self.instrumentation
        elif operation == ["uninstall", probe.TEST]:
            value = self.cleanup
        else:
            self.fail("Unexpected device command")
        return subprocess.CompletedProcess(command, 0, value, "")

    def run_probe(self):
        with patch.object(probe, "ROOT", self.root), \
             patch.object(probe.subprocess, "run", side_effect=self.respond), \
             patch("sys.argv", ["probe", "--adb", str(self.adb)]), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            result = probe.main()
        self.assertNotIn("fixture-phone", output.getvalue())
        return result

    def assert_no_install(self):
        self.assertFalse(any(call[0] in {"install", "uninstall"} for call in self.calls))

    def evidence(self):
        return json.loads((self.root / ".cache/as-003/self-query-result.json").read_text())

    def test_missing_unauthorized_and_emulator_targets_do_not_install(self):
        for row in ["", "fixture-phone\tunauthorized\n", "emulator-5554\tdevice\n"]:
            with self.subTest(row=row):
                self.targets = "List of devices attached\n" + row
                with self.assertRaisesRegex(RuntimeError, "exactly one authorized physical phone"):
                    self.run_probe()
                self.assert_no_install()

    def test_ambiguous_phones_do_not_install(self):
        self.targets += "second-fixture\tdevice\n"
        with self.assertRaises(RuntimeError):
            self.run_probe()
        self.assert_no_install()

    def test_virtual_target_cannot_impersonate_physical_phone(self):
        self.virtual = "1"
        with self.assertRaisesRegex(RuntimeError, "physical phone"):
            self.run_probe()
        self.assert_no_install()

    def test_unsupported_api_does_not_install(self):
        self.api = "27"
        with self.assertRaisesRegex(RuntimeError, "API 28"):
            self.run_probe()
        self.assert_no_install()

    def test_existing_test_package_is_preserved(self):
        self.existing_test = True
        with self.assertRaisesRegex(RuntimeError, "preserve"):
            self.run_probe()
        self.assert_no_install()

    def test_success_requires_marker_and_result_minus_one(self):
        self.assertEqual(self.run_probe(), 0)
        evidence = self.evidence()
        self.assertTrue(evidence["passed"])
        self.assertTrue(evidence["testPackageRemoved"])
        self.assertNotIn("fixture-phone", json.dumps(evidence))
        self.assertEqual([call for call in self.calls if call[0] == "uninstall"],
                         [["uninstall", probe.TEST]])
        instrument = next(call for call in self.calls if "instrument" in call)
        self.assertIn("-r", instrument)

    def test_pretty_stream_without_result_code_is_rejected_and_cleaned(self):
        self.instrumentation = probe.SUCCESS + "\n"
        with self.assertRaises(RuntimeError):
            self.run_probe()
        self.assertFalse(self.evidence()["passed"])
        self.assertTrue(self.evidence()["testPackageRemoved"])

    def test_exit_zero_with_failure_result_is_rejected_and_cleaned(self):
        self.instrumentation = probe.SUCCESS + "\nINSTRUMENTATION_CODE: 0\n"
        with self.assertRaisesRegex(RuntimeError, "failed"):
            self.run_probe()
        self.assertFalse(self.evidence()["passed"])
        self.assertTrue(self.evidence()["testPackageRemoved"])

    def test_success_result_without_marker_is_rejected_and_cleaned(self):
        self.instrumentation = "INSTRUMENTATION_CODE: -1\n"
        with self.assertRaises(RuntimeError):
            self.run_probe()
        self.assertTrue(self.evidence()["testPackageRemoved"])

    def test_failure_diagnostics_keep_only_fixed_stage_type_and_counts(self):
        self.instrumentation = (
            "INSTRUMENTATION_RESULT: probe_stage=missing_transitions\n"
            "INSTRUMENTATION_RESULT: fixture_resumed=2\n"
            "INSTRUMENTATION_RESULT: fixture_paused=1\n"
            "INSTRUMENTATION_RESULT: stream=FAIL: IllegalStateException\n"
            "untrusted payload fixture-phone com.example.synthetic\nINSTRUMENTATION_CODE: 0\n"
        )
        with self.assertRaisesRegex(RuntimeError, "missing_transitions"):
            self.run_probe()
        evidence = self.evidence()
        self.assertEqual(evidence["failureStage"], "missing_transitions")
        self.assertEqual(evidence["failureType"], "IllegalStateException")
        self.assertEqual(evidence["fixture_resumed"], 2)
        self.assertEqual(evidence["fixture_paused"], 1)
        self.assertNotIn("fixture-phone", json.dumps(evidence))
        self.assertNotIn("com.example.synthetic", json.dumps(evidence))

    def test_timeout_is_cleaned_and_evidence_preserved(self):
        original = self.respond

        def timeout(command, **kwargs):
            if "instrument" in command:
                raise subprocess.TimeoutExpired(command, 90)
            return original(command, **kwargs)

        with patch.object(self, "respond", side_effect=timeout):
            with self.assertRaises(subprocess.TimeoutExpired):
                self.run_probe()
        self.assertFalse(self.evidence()["passed"])
        self.assertTrue(self.evidence()["testPackageRemoved"])

    def test_cleanup_failure_is_not_reported_as_clean(self):
        self.cleanup = "Failure\n"
        with self.assertRaisesRegex(RuntimeError, "cleanup failed"):
            self.run_probe()
        self.assertFalse(self.evidence()["testPackageRemoved"])


if __name__ == "__main__":
    unittest.main()
