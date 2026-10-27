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
MEASUREMENT_LIMITS = {
    "query_calls": (3, 64), "query_total_ns": (0, 90_000_000_000),
    "query_max_ns": (0, 90_000_000_000), "iteration_total_ns": (0, 90_000_000_000),
    "iteration_max_ns": (0, 90_000_000_000), "completed_queries": (1, 62),
    "events_scanned_max": (4, 1_000), "cancel_before_calls": (0, 0),
    "cancel_after_scanned": (0, 0), "cancel_during_scanned": (1, 1_000),
    "cancelled_batches": (3, 3), "discarded_snapshots": (1, 1),
}


def parse_measurements(output: str) -> dict[str, int]:
    """Accept only complete, unique aggregate counters; never persist raw phone output."""
    values = {}
    for key, (minimum, maximum) in MEASUREMENT_LIMITS.items():
        prefix = f"INSTRUMENTATION_RESULT: {key}="
        matches = [line[len(prefix):] for line in output.splitlines() if line.startswith(prefix)]
        if len(matches) != 1 or not re.fullmatch(r"[0-9]{1,12}", matches[0]):
            raise RuntimeError("Missing or malformed probe measurements; no result accepted")
        value = int(matches[0])
        if not minimum <= value <= maximum:
            raise RuntimeError("Probe measurements outside test bounds; no result accepted")
        values[key] = value
    if values["query_calls"] != values["completed_queries"] + 2:
        raise RuntimeError("Inconsistent probe query counts; no result accepted")
    for phase, count in [("query", values["query_calls"]),
                         ("iteration", values["completed_queries"])]:
        if not values[f"{phase}_max_ns"] <= values[f"{phase}_total_ns"] <= values[f"{phase}_max_ns"] * count:
            raise RuntimeError("Inconsistent probe durations; no result accepted")
    return values


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
        if SUCCESS not in output or "INSTRUMENTATION_CODE: -1" not in lines:
            # Only fixed diagnostic keys/values, never raw output, names or event timestamps.
            stage = re.search(r"^INSTRUMENTATION_RESULT: probe_stage=(preflight|fixtures|query|cancellation|missing_transitions)$",
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
        evidence["measurements"] = parse_measurements(output)
        evidence["passed"] = True
        print("PASS: physical-phone self-package probe; two synthetic activity lifecycle pairs")
        print("Aggregate query/iteration measurements and three cancellation boundaries: " +
              json.dumps(evidence["measurements"], sort_keys=True))
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
