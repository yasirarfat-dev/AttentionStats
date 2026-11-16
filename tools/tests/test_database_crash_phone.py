"""Host-only safety checks: no adb/device access."""

import importlib.util
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "database_crash_phone", Path(__file__).resolve().parents[1] / "database_crash_phone.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class CrashPhoneSafety(unittest.TestCase):
    def test_marker_poll_waits_for_complete_object(self):
        for code, raw in [(1, b'{}'), (0, b''), (0, b'{'), (0, b'null'),
                          (0, b'[]'), (0, b'\xff')]:
            with self.subTest(code=code, raw=raw):
                self.assertIsNone(runner.read_marker(
                    runner.subprocess.CompletedProcess([], code, stdout=raw)))
        self.assertEqual(runner.read_marker(runner.subprocess.CompletedProcess(
            [], 0, stdout=b'{"pid":123}')), {"pid": 123})

    def test_only_exact_single_owned_pid_authorized(self):
        marker = {"token": "synthetic", "before": True, "writerPolicyChecks": 2, "pid": 123}
        self.assertEqual(runner.kill_pid(marker, "synthetic", True, "123"), "123")
        invalid = [
            ({**marker, "token": "other"}, "123"),
            ({**marker, "before": False}, "123"),
            ({**marker, "before": 1}, "123"),
            ({**marker, "writerPolicyChecks": True}, "123"),
            ({**marker, "writerPolicyChecks": 1}, "123"),
            ({**marker, "pid": 1}, "1"),
            ({**marker, "pid": -123}, "-123"),
            ({**marker, "pid": "123"}, "123"),
            (marker, "124"), (marker, "123 124"), (marker, "123\n124"),
            (marker, "123;kill -9 456"), (marker, ""),
        ]
        for value, pid in invalid:
            with self.subTest(marker=value, pid=pid), self.assertRaises(RuntimeError):
                runner.kill_pid(value, "synthetic", True, pid)

    def test_intentional_crash_is_not_verification(self):
        scope = "isolated precommit rollback process-kill recovery checks"
        self.assertFalse(runner.passed(b"Process crashed.\nINSTRUMENTATION_CODE: 0\n", scope))
        self.assertFalse(runner.passed(b"PASS: unrelated\nINSTRUMENTATION_CODE: -1\n", scope))
        result = ("PASS: 13 " + scope + "\nINSTRUMENTATION_CODE: -1\n").encode()
        self.assertTrue(runner.passed(result, scope))
        self.assertFalse(runner.passed(result.replace(b"-1", b"0"), scope))
        self.assertFalse(runner.passed(result + b"INSTRUMENTATION_FAILED: bad\n", scope))
        self.assertFalse(runner.passed(result + b"INSTRUMENTATION_CODE: 0\n", scope))

    def test_target_required_and_emulator_rejected_before_subprocess(self):
        with patch.object(runner.subprocess, "run") as execute:
            for serial in ("", "emulator-5554"):
                with self.assertRaises(RuntimeError):
                    runner.Phone("adb", serial)
            execute.assert_not_called()

    def test_every_adb_command_has_explicit_target(self):
        phone = runner.Phone("adb", "synthetic-target")
        with patch.object(runner.subprocess, "run") as execute:
            execute.return_value.returncode = 0
            execute.return_value.stdout = b"device\n"
            self.assertEqual(phone.run("get-state"), b"device\n")
            self.assertEqual(execute.call_args.args[0], ["adb", "-s", "synthetic-target", "get-state"])

    def test_mismatched_artifact_stops_before_preparation(self):
        phone = unittest.mock.Mock()
        phone.digest.return_value = "unexpected"
        with patch.object(runner.subprocess, "Popen") as prepare:
            with self.assertRaises(RuntimeError):
                runner.run_pair(phone, "before")
            prepare.assert_not_called()
            phone.state.assert_not_called()

    def test_timeout_retains_partial_verifier_output(self):
        phone = runner.Phone("adb", "synthetic-target")
        with tempfile.TemporaryDirectory() as directory:
            capture = Path(directory) / "verify.txt"
            with patch.object(runner.subprocess, "run", side_effect=runner.subprocess.TimeoutExpired(
                    ["adb"], 15, output=b"partial verifier", stderr=b" interrupted")):
                with self.assertRaises(runner.subprocess.TimeoutExpired):
                    phone.run("shell", "am", "instrument", capture_path=capture)
            self.assertEqual(capture.read_bytes(), b"partial verifier interrupted")


if __name__ == "__main__":
    unittest.main()
