"""Preserve live data and foreign test-package ownership when phone operations fail."""

import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("reels_phone", Path(__file__).resolve().parents[1] / "reels_phone.py")
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class ReelsPhoneSafety(unittest.TestCase):
    def run_failure(self, existing=False, install_failure=False):
        calls = []
        with TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "preview.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("assets/index.android.bundle", b"synthetic")
            test = root / "test.apk"
            test.write_bytes(b"synthetic")
            adb = root / "adb"
            adb.touch()

            def phone(command, **_kwargs):
                calls.append(command)
                if command[1:] == ["devices"]:
                    output = "List of devices attached\nprivate-fixture-id\tdevice\n"
                else:
                    args = command[3:]
                    if args == ["shell", "getprop", "ro.kernel.qemu"]: output = "0\n"
                    elif args == ["shell", "getprop", "ro.product.cpu.abi"]: output = "arm64-v8a\n"
                    elif args == ["shell", "getprop", "ro.build.version.sdk"]: output = "36\n"
                    elif args[:4] == ["shell", "pm", "list", "packages"]:
                        output = "package:" + probe.TEST + "\n" if existing else ""
                    elif args[:2] == ["install", str(test)]:
                        output = "Failure" if install_failure else "Success\n"
                    elif args[0] in ["install", "uninstall"]: output = "Success\n"
                    elif args[:3] == ["shell", "am", "instrument"]:
                        output = "INSTRUMENTATION_RESULT: stream=FAIL: IllegalStateException\nINSTRUMENTATION_CODE: 0\n"
                    elif args[:3] == ["shell", "am", "start"]: output = "Starting owned app\n"
                    else: raise AssertionError("Unexpected phone operation")
                return SimpleNamespace(returncode=0, stdout=output)

            with patch.object(probe, "PREVIEW", apk), patch.object(probe, "TEST_APK", test), \
                 patch.object(probe, "audit_apks"), \
                 patch.object(probe, "CACHE", root / "cache"), \
                 patch.object(probe.sys, "argv", ["reels_phone.py", "qualify", "--adb", str(adb)]), \
                 patch.object(probe.subprocess, "run", side_effect=phone), self.assertRaises(RuntimeError):
                probe.main()
            evidence = root / "cache/phone-result.json"
            result = json.loads(evidence.read_text()) if evidence.exists() else None
            if evidence.exists(): self.assertNotIn("private-fixture-id", evidence.read_text())
        return [call[3:] for call in calls if "-s" in call], result

    def test_existing_test_package_preserved_before_any_install(self):
        calls, evidence = self.run_failure(existing=True)
        self.assertFalse(any(call[0] in ["install", "uninstall"] for call in calls))
        self.assertIsNone(evidence)

    def test_failed_probe_removes_only_test_package_and_leaves_preview(self):
        calls, evidence = self.run_failure()
        self.assertEqual([call for call in calls if call[0] == "uninstall"], [["uninstall", probe.TEST]])
        self.assertTrue(evidence["installed"] and evidence["testPackageRemoved"])
        self.assertFalse(evidence["passed"])
        self.assertTrue(any(call[:3] == ["shell", "am", "start"] for call in calls))
        self.assertFalse(any("clear" in call or "grant" in call for call in calls))

    def test_failed_test_install_never_uninstalls_unowned_package(self):
        calls, evidence = self.run_failure(install_failure=True)
        self.assertFalse(any(call[0] == "uninstall" for call in calls))
        self.assertTrue(evidence["installed"])

    def test_contradictory_completion_rejected(self):
        output = "\n".join(["INSTRUMENTATION_RESULT: stream=PASS: own-window React Native preview screens",
                            "INSTRUMENTATION_RESULT: ui_checks=12", "INSTRUMENTATION_CODE: -1"])
        self.assertEqual(probe.parse_result(output, "ui"), {"ui_checks": 12})
        for extra in ["INSTRUMENTATION_CODE: 0", "INSTRUMENTATION_RESULT: ui_checks=12"]:
            with self.assertRaises(RuntimeError): probe.parse_result(output + "\n" + extra, "ui")

    def structure(self):
        return {"scope": "consented structural detector qualification only; no content", "targets": {
            "instagram": {"version": "fixture.1", "nodes": 2, "bounded": True, "editable": False,
                          "ids": ["synthetic_pager"], "classes": ["android.view.View"], "positions": [], "controls": [], "scrolls": []}}}

    def test_only_bounded_structural_diagnostic_accepted(self):
        value = self.structure()
        self.assertEqual(probe.validate_structure(json.dumps(value).encode()), value)

    def test_content_fields_and_arbitrary_strings_rejected(self):
        extra = self.structure()
        extra["targets"]["instagram"]["text"] = "PRIVATE_SYNTHETIC_MARKER"
        classes = self.structure()
        classes["targets"]["instagram"]["classes"] = ["private synthetic words"]
        target = self.structure()
        target["targets"]["unknown"] = target["targets"].pop("instagram")
        for value in [extra, classes, target]:
            with self.assertRaisesRegex(RuntimeError, "metadata-only schema"):
                probe.validate_structure(json.dumps(value).encode())

    def test_malformed_duplicate_and_oversized_diagnostics_rejected(self):
        for raw in [b"{", b"\xff", b" " * 32769, b'{"scope":1,"scope":2,"targets":{}}']:
            with self.assertRaises(RuntimeError): probe.validate_structure(raw)

    def test_node_and_position_bounds_rejected(self):
        for key, value in [("nodes", 193), ("nodes", True), ("positions", [{}] * 13), ("scrolls", [{}] * 17)]:
            data = self.structure()
            data["targets"]["instagram"][key] = value
            with self.assertRaises(RuntimeError): probe.validate_structure(json.dumps(data).encode())


if __name__ == "__main__":
    unittest.main()
