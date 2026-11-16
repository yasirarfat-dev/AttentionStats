"""Run one isolated crash pair on an explicitly selected, already prepared phone.

Install/remove the hash-reviewed diagnostic separately. Private output stays in
.cache; this runner never resets adb, changes settings, or opens live history.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.attentionstats.debug"
RUNNER = PACKAGE + ".test/com.attentionstats.qualification.AnalyticsInstrumentation"


def require(value, message):
    if not value:
        raise RuntimeError(message)


def kill_pid(marker, token, before, current_pid):
    """Authorize only the single app PID bound to this exact synthetic marker."""
    require(marker.get("token") == token, "Marker token mismatch; no kill")
    require(marker.get("before") is before, "Marker phase mismatch; no kill")
    require(type(marker.get("writerPolicyChecks")) is int
            and marker["writerPolicyChecks"] == 2, "Writer policy mismatch; no kill")
    pid = marker.get("pid")
    require(type(pid) is int and pid > 1, "Invalid marker PID; no kill")
    require(re.fullmatch(r"[0-9]+", current_pid) is not None
            and int(current_pid) == pid, "Own app PID mismatch; no kill")
    return str(pid)


def passed(raw, scope):
    lines = raw.decode(errors="replace").splitlines()
    return (any("PASS:" in line and scope in line for line in lines)
            and [line for line in lines if line.startswith("INSTRUMENTATION_CODE:")]
            == ["INSTRUMENTATION_CODE: -1"]
            and not any("FAIL:" in line or "INSTRUMENTATION_FAILED" in line for line in lines))


def read_marker(result):
    """An empty/not-yet-published marker is not authority to kill anything."""
    if result.returncode != 0:
        return None
    try:
        value = json.loads(result.stdout)
    except (ValueError, UnicodeError):
        return None
    return value if isinstance(value, dict) else None


class Phone:
    def __init__(self, adb, serial):
        require(bool(serial) and not serial.startswith("emulator-"), "Explicit physical target required")
        self.prefix = [adb, "-s", serial]

    def run(self, *args, timeout=15, absent=False, capture_path=None):
        try:
            result = subprocess.run(self.prefix + list(args), capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired as failure:
            if capture_path:
                capture_path.write_bytes((failure.stdout or b"") + (failure.stderr or b""))
            raise
        if capture_path:
            capture_path.write_bytes(result.stdout + result.stderr)
        require(result.returncode == 0 or (absent and result.returncode == 1 and not result.stdout),
                "Scoped adb command failed; private details withheld")
        return result.stdout

    def digest(self, package):
        location = self.run("shell", "pm", "path", package).decode().strip()
        require(location.startswith("package:/data/app/") and "\n" not in location,
                "Expected one installed APK")
        digest = self.run("shell", "sha256sum", location[8:]).decode().split()[0]
        require(re.fullmatch(r"[0-9a-f]{64}", digest), "Invalid APK digest")
        return digest

    def state(self):
        values = [self.run("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/" + name + ".xml")
                  for name in ("appearance", "system-backup")]
        values += [self.run("shell", "settings", "get", scope, key) for scope, key in (
            ("system", "font_scale"), ("secure", "enabled_accessibility_services"),
            ("secure", "accessibility_enabled"))]
        values.append(self.run("reverse", "--list"))
        return [hashlib.sha256(value).hexdigest() for value in values]

    def idle(self):
        require(b"mFinished=false" not in self.run("shell", "dumpsys", "activity"),
                "Existing unfinished instrumentation preserved")

    def bound(self):
        return any(PACKAGE in line and "Bound services:" in line for line in
                   self.run("shell", "dumpsys", "accessibility").decode().splitlines())

    def instrument(self, mode, token=None):
        args = ["shell", "am", "instrument", "-w", "-r", "-e", "databaseCrash", mode]
        if token:
            args += ["-e", "databaseCrashToken", token]
        return args + [RUNNER]


def run_pair(phone, phase):
    evidence = json.loads((ROOT / "docs/evidence/database-crash-build.json").read_text())
    expected_product = evidence["productSha256Expected"]
    expected_test = evidence["build"]["testSha256"]
    require(phone.digest(PACKAGE) == expected_product, "Product differs from reviewed pairing")
    require(phone.digest(PACKAGE + ".test") == expected_test, "Diagnostic differs from reviewed pairing")
    phone.idle()
    require(phone.bound(), "Own Accessibility service must be bound before trial")
    original = phone.state()
    token = str(uuid.uuid4())
    output = ROOT / ".cache/database-crash-runs" / token
    output.mkdir(parents=True, exist_ok=False)
    (output / "baseline.json").write_text(json.dumps({
        "phase": phase, "token": token, "stateHashes": original,
        "productSha256": expected_product, "testSha256": expected_test,
    }, indent=2))
    marker_path = "no_backup/database-crash-" + token + "/ready.json"
    # Retain the token before starting: an interrupted host can resume the verifier.
    print("Private recovery record: " + str(output.relative_to(ROOT)), flush=True)
    process = None
    try:
        with (output / "prepare.txt").open("wb") as raw:
            args = phone.instrument("prepare-" + phase, token)
            args.insert(3, "--no-restart")
            process = subprocess.Popen(phone.prefix + args, stdout=raw, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 65
            marker = None
            while time.monotonic() < deadline:
                result = subprocess.run(phone.prefix + ["exec-out", "run-as", PACKAGE, "cat", marker_path],
                                        capture_output=True, timeout=10)
                marker = read_marker(result)
                if marker is not None:
                    break
                require(process.poll() is None, "Preparation ended before marker; inspect private output")
                time.sleep(0.5)
            require(marker is not None, "No marker; no process killed")
            (output / "ready.json").write_text(json.dumps(marker))
            pid = kill_pid(marker, token, phase == "before",
                           phone.run("shell", "pidof", PACKAGE).decode().strip())
            # Intentionally kill only the validated app process, never a host adb process.
            phone.run("shell", "run-as", PACKAGE, "kill", "-9", pid)
            process.wait(timeout=30)
        require(phone.run("shell", "pidof", PACKAGE, absent=True).decode().strip() != pid,
                "Prepared PID still alive")
        result = phone.run(*phone.instrument("verify-" + phase, token), timeout=180,
                           capture_path=output / "verify.txt")
        scope = "isolated " + ("precommit rollback" if phase == "before" else "postcommit durability")
        require(passed(result, scope + " process-kill recovery checks"),
                "Recovery verifier failed; inspect private output")
        cleanup = phone.run(*phone.instrument("cleanup"), timeout=90, capture_path=output / "cleanup.txt")
        require(passed(cleanup, "4 independent crash/migration directory and Keystore prefix absence checks"),
                "Independent cleanup failed; inspect private output")
        phone.run("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW",
                  "-d", "attentionstats://today", PACKAGE, timeout=30)
        for _ in range(15):
            if phone.bound():
                break
            time.sleep(1)
        require(phone.bound(), "Own service has not rebound; preserve state for scoped recovery")
        require(phone.state() == original, "Original settings/reverse mappings differ")
        require(phone.digest(PACKAGE) == expected_product
                and phone.digest(PACKAGE + ".test") == expected_test, "Installed artifact changed")
        phone.idle()
        print("PASS: " + phase + " recovery and independent cleanup; original state/artifacts preserved")
    finally:
        # A timeout is not permission to kill blindly. Let the bounded Android
        # preparation clean itself up; retain all private records for recovery.
        if process is not None and process.poll() is None:
            print("Preparation may still be active; allow its 90-second timeout before recovery", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("before", "after"))
    parser.add_argument("--serial", required=True, help="Authorized physical phone; never commit this value")
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    try:
        run_pair(Phone(args.adb, args.serial), args.phase)
    except RuntimeError as failure:
        print("STOP: " + str(failure), file=sys.stderr)
        return 1
    except (OSError, ValueError, subprocess.SubprocessError):
        # Exception strings can include the device serial or private adb output.
        print("STOP: crash qualification incomplete; retain private records. No adb reset or broad kill.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
