#!/usr/bin/env python3
"""Run the bounded synthetic self-package probe on one authorized physical phone."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = "com.attentionstats.debug"
TEST = APP + ".test"
RUNNER = TEST + "/com.attentionstats.qualification.AnalyticsInstrumentation"
SUCCESS = "PASS: self-package usage probe; 2 synthetic activities resumed/paused;"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    args = parser.parse_args()
    if not args.adb or not Path(args.adb).is_file():
        parser.error("Provide an existing adb with --adb; no SDK installation is attempted")
    apks = {
        "debug": ROOT / "android/app/build/outputs/apk/debug/app-debug.apk",
        "test": ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
    }
    if any(not path.is_file() for path in apks.values()):
        parser.error("Build :app:assembleDebug and :app:assembleDebugAndroidTest first")
    listing = subprocess.run([args.adb, "devices"], capture_output=True, text=True, timeout=30)
    if listing.returncode:
        raise RuntimeError("adb target listing failed")
    targets = [line.split()[0] for line in listing.stdout.splitlines()[1:]
               if line.strip().endswith("\tdevice") and not line.startswith("emulator-")]
    if len(targets) != 1:
        raise RuntimeError(f"Need exactly one authorized physical phone; found {len(targets)}. "
                           "Connect USB debugging; no emulator fallback.")
    serial = targets[0]  # Never print or persist the target identifier.

    def device(*command: str, timeout: int = 90) -> str:
        result = subprocess.run([args.adb, "-s", serial, *command], capture_output=True,
                                text=True, timeout=timeout)
        if result.returncode:
            # adb errors can echo identifiers/commands; only name the failed operation.
            raise RuntimeError(f"Phone operation failed: {command[0]}")
        return result.stdout

    if device("shell", "getprop", "ro.kernel.qemu").strip() == "1":
        raise RuntimeError("A physical phone is required")
    api = int(device("shell", "getprop", "ro.build.version.sdk").strip())
    if api < 28:
        raise RuntimeError("Self-package public query requires API 28; no result accepted")
    if "package:" + TEST in device("shell", "pm", "list", "packages", TEST).splitlines():
        raise RuntimeError("Test package already exists; preserve it and resolve ownership first")
    evidence = {
        "scope": "synthetic self-package only; no production collector qualification",
        "api": api,
        "artifacts": {name: {"sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                             "size": path.stat().st_size} for name, path in apks.items()},
        "passed": False,
        "testPackageRemoved": False,
    }
    installed_test = False
    try:
        if "Success" not in device("install", "-r", str(apks["debug"]), timeout=180):
            raise RuntimeError("Debug installation did not report success")
        if "Success" not in device("install", str(apks["test"]), timeout=180):
            raise RuntimeError("Test installation did not report success")
        installed_test = True
        # Raw result formatting retains the final code; default pretty mode prints only stream.
        output = device("shell", "am", "instrument", "-w", "-r", "-e", "qualification", "usage-self", RUNNER)
        # adb exit zero can still describe a failing instrumentation run.
        lines = output.splitlines()
        evidence["passed"] = SUCCESS in output and "INSTRUMENTATION_CODE: -1" in lines
        if not evidence["passed"]:
            # Only fixed diagnostic keys/values, never raw output, names or event timestamps.
            stage = re.search(r"^INSTRUMENTATION_RESULT: probe_stage=(preflight|fixtures|query|missing_transitions)$",
                              output, re.M)
            counts = {key: int(match[1]) for key in ["fixture_resumed", "fixture_paused"]
                      if (match := re.search(rf"^INSTRUMENTATION_RESULT: {key}=([0-2])$", output, re.M))}
            evidence["failureStage"] = stage[1] if stage else "unreported"
            failure = re.search(r"^INSTRUMENTATION_RESULT: stream=FAIL: ([A-Za-z]+)\s*$", output, re.M)
            evidence["failureType"] = failure[1] if failure else "unreported"
            evidence["processCrashed"] = "Process crashed" in output
            evidence["runnerMissing"] = "Unable to find instrumentation info" in output
            evidence.update(counts)
            raise RuntimeError("Self-package probe failed; no source result accepted; stage=" +
                               evidence["failureStage"] + "; type=" + evidence["failureType"] +
                               "; fixture counts=" + json.dumps(counts) +
                               "; processCrashed=" + str(evidence["processCrashed"]) +
                               "; runnerMissing=" + str(evidence["runnerMissing"]))
        print("PASS: physical-phone self-package probe; two synthetic activity lifecycle pairs")
    finally:
        try:
            if installed_test:
                evidence["testPackageRemoved"] = "Success" in device("uninstall", TEST)
                if not evidence["testPackageRemoved"]:
                    raise RuntimeError("Temporary test package cleanup failed")
        finally:
            evidence_path = ROOT / ".cache/as-003/self-query-result.json"
            evidence_path.parent.mkdir(parents=True, exist_ok=True)
            evidence_path.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print("Temporary test package removed; debug app retained; no grants/settings changed")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, subprocess.TimeoutExpired) as error:
        # Do not expose subprocess exceptions containing adb serials or raw device output.
        print(str(error) if isinstance(error, RuntimeError) else
              f"Probe stopped: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
