"""Safety checks for bounded public aggregates and phone selection; no adb calls."""

import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch
from tempfile import TemporaryDirectory
from types import SimpleNamespace

SPEC = importlib.util.spec_from_file_location(
    "archive_qualification", Path(__file__).resolve().parents[1] / "archive_qualification.py")
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class ArchiveQualificationSafety(unittest.TestCase):
    def output(self):
        return "\n".join([
            "INSTRUMENTATION_RESULT: stream=PASS: synthetic archive envelope qualification",
            "INSTRUMENTATION_CODE: -1",
            *[f"INSTRUMENTATION_RESULT: {key}={low}" for key, (low, _) in probe.LIMITS.items()],
        ])

    def test_complete_bounded_result(self):
        self.assertEqual(probe.measurements(self.output())["round_trips"], 9)

    def test_adb_zero_does_not_mean_pass(self):
        with self.assertRaises(RuntimeError):
            probe.measurements(self.output().replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"))

    def test_success_stream_required(self):
        with self.assertRaises(RuntimeError):
            probe.measurements(self.output().replace("stream=PASS:", "stream=FAIL:"))

    def test_duplicate_or_contradictory_completion_rejected(self):
        for extra in ["INSTRUMENTATION_CODE: 0", "INSTRUMENTATION_CODE: -1",
                      "INSTRUMENTATION_RESULT: stream=FAIL: IllegalStateException"]:
            with self.subTest(extra=extra), self.assertRaises(RuntimeError):
                probe.measurements(self.output() + "\n" + extra)

    def test_missing_duplicate_malformed_and_outside_limits(self):
        for key, (low, high) in probe.LIMITS.items():
            line = f"INSTRUMENTATION_RESULT: {key}={low}"
            for replacement in ["", line + "\n" + line, line + "x", line.replace(f"={low}", f"={high + 1}")]:
                with self.subTest(key=key, replacement=replacement), self.assertRaises(RuntimeError):
                    probe.measurements(self.output().replace(line, replacement))

    def test_no_physical_target_and_unauthorized_rejected(self):
        for listing in ["List of devices attached\n", "List of devices attached\nfixture\tunauthorized\n",
                        "List of devices attached\nemulator-5554\tdevice\n"]:
            with self.assertRaises(RuntimeError):
                probe.physical_target(listing)

    def test_ambiguous_phones_rejected(self):
        with self.assertRaises(RuntimeError):
            probe.physical_target("List of devices attached\nfixture-a\tdevice\nfixture-b\tdevice\n")

    def test_single_phone_selected_without_emulator(self):
        self.assertEqual(probe.physical_target(
            "List of devices attached\nemulator-5554\tdevice\nfixture\tdevice\n"), "fixture")

    def fake_phone(self, calls, preinstalled=False):
        def invoke(command, **kwargs):
            calls.append(command)
            suffix = command[3:]
            if command[1:] == ["devices"]:
                output = "List of devices attached\nfixture\tdevice\n"
            elif suffix == ["shell", "getprop", "ro.kernel.qemu"]:
                output = "0\n"
            elif suffix == ["shell", "getprop", "ro.product.cpu.abi"]:
                output = "arm64-v8a\n"
            elif suffix == ["shell", "getprop", "ro.build.version.sdk"]:
                output = "36\n"
            elif suffix[:4] == ["shell", "pm", "list", "packages"]:
                output = "package:" + probe.PACKAGE + "\n" if preinstalled else ""
            elif suffix[0] in ["install", "uninstall"]:
                output = "Success\n"
            elif suffix[:3] == ["shell", "am", "instrument"]:
                output = "INSTRUMENTATION_RESULT: stream=FAIL: GeneralSecurityException\nINSTRUMENTATION_CODE: 0"
            else:
                raise AssertionError("Unexpected phone operation")
            return SimpleNamespace(returncode=0, stdout=output)
        return invoke

    def test_existing_package_is_preserved(self):
        calls = []
        with TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            apk.write_bytes(b"fixture")
            with patch.object(probe, "APK", apk), patch.object(
                probe.subprocess, "run", side_effect=self.fake_phone(calls, preinstalled=True)
            ), self.assertRaisesRegex(RuntimeError, "already exists"):
                probe.run("adb")
        self.assertFalse(any(c[3:4] in [["install"], ["uninstall"]] for c in calls))

    def test_owned_package_removed_after_instrumentation_failure(self):
        calls = []
        with TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "fixture.apk"
            apk.write_bytes(b"fixture")
            with patch.object(probe, "APK", apk), patch.object(probe, "ROOT", root), patch.object(
                probe.subprocess, "run", side_effect=self.fake_phone(calls)
            ), self.assertRaisesRegex(RuntimeError, "Archive fixture failed"):
                probe.run("adb")
            self.assertTrue((root / ".cache/as-006/archive-phone-result.json").is_file())
        self.assertEqual([c[3:] for c in calls if c[3:4] == ["uninstall"]], [["uninstall", probe.PACKAGE]])

    def completed_phone(self, calls, bad_reader=False, oversized=False):
        base = self.fake_phone(calls)

        def invoke(command, **kwargs):
            if command[0] == "node":
                calls.append(command)
                count = 3 if command[-1].endswith("phone-vectors.json") else 1
                return SimpleNamespace(returncode=1 if bad_reader else 0,
                                       stdout=f"PASS: independent reader; {count} synthetic vectors\n")
            if command[3:6] == ["shell", "am", "instrument"]:
                calls.append(command)
                return SimpleNamespace(returncode=0, stdout=self.output())
            if command[3:5] == ["exec-out", "run-as"]:
                calls.append(command)
                kwargs["stdout"].write(b"x" * 16385 if oversized else b"{}")
                return SimpleNamespace(returncode=0)
            return base(command, **kwargs)
        return invoke

    def run_completed(self, bad_reader=False, oversized=False):
        calls = []
        with TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "fixture.apk"
            apk.write_bytes(b"fixture")
            with patch.object(probe, "APK", apk), patch.object(probe, "ROOT", root), patch.object(
                probe.subprocess, "run", side_effect=self.completed_phone(calls, bad_reader, oversized)
            ), patch("builtins.print"):
                if bad_reader or oversized:
                    with self.assertRaises(RuntimeError):
                        probe.run("adb")
                else:
                    probe.run("adb")
            evidence = json.loads((root / ".cache/as-006/archive-phone-result.json").read_text())
        self.assertTrue(evidence["packageRemoved"])
        self.assertEqual(evidence["passed"], not (bad_reader or oversized))
        return calls, evidence

    def test_owned_package_removed_after_independent_reader_failure(self):
        self.run_completed(bad_reader=True)

    def test_oversized_vector_rejected_and_owned_package_removed(self):
        self.run_completed(oversized=True)

    def test_success_reads_only_fixed_synthetic_outputs_then_removes_package(self):
        calls, evidence = self.run_completed()
        self.assertEqual(evidence["independentReaderCases"], 4)
        self.assertEqual([c[3:] for c in calls if c[3:5] == ["exec-out", "run-as"]], [
            ["exec-out", "run-as", probe.PACKAGE, "cat", "no_backup/synthetic-vectors.json"],
            ["exec-out", "run-as", probe.PACKAGE, "cat", "no_backup/synthetic-large.json"],
        ])
        self.assertEqual(calls[-1][3:], ["uninstall", probe.PACKAGE])


if __name__ == "__main__":
    unittest.main()
