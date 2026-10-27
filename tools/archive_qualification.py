#!/usr/bin/env python3
"""Build/run the isolated AS-006 synthetic archive-envelope APK; never touch product data."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "native/archive-qualification"
PACKAGE = "org.attentionstats.qualification.archive"
RUNNER = PACKAGE + "/.ArchiveInstrumentation"
APK = PROJECT / "build/outputs/apk/debug/archive-qualification-debug.apk"
LIMITS = {
    "checks": (800, 4000), "rejections": (300, 1800), "round_trips": (9, 9),
    "hkdf_vectors": (3, 3), "cancellations": (6, 6), "largest_plaintext": (3145735, 3145735),
    "segments": (4, 4), "golden_cases": (3, 3), "vector_cases": (3, 3),
}


def measurements(output: str) -> dict[str, int]:
    lines = output.splitlines()
    codes = [line for line in lines if line.startswith("INSTRUMENTATION_CODE:")]
    streams = [line for line in lines if line.startswith("INSTRUMENTATION_RESULT: stream=")]
    if codes != ["INSTRUMENTATION_CODE: -1"] or streams != [
        "INSTRUMENTATION_RESULT: stream=PASS: synthetic archive envelope qualification"
    ]:
        raise RuntimeError("Synthetic archive qualification failed; raw phone output withheld")
    values = {}
    for key, (low, high) in LIMITS.items():
        prefix = f"INSTRUMENTATION_RESULT: {key}="
        found = [line[len(prefix):] for line in output.splitlines() if line.startswith(prefix)]
        if len(found) != 1 or not re.fullmatch(r"[0-9]{1,9}", found[0]):
            raise RuntimeError("Missing/duplicate/malformed aggregate; no result accepted")
        value = int(found[0])
        if not low <= value <= high:
            raise RuntimeError("Aggregate outside fixture bounds; no result accepted")
        values[key] = value
    return values


def physical_target(output: str) -> str:
    targets = [line.split()[0] for line in output.splitlines()[1:]
               if line.strip().endswith("\tdevice") and not line.startswith("emulator-")]
    if len(targets) != 1:
        raise RuntimeError("Connect exactly one authorized physical debug phone; no emulator fallback")
    return targets[0]


def build(sdk: Path, write_locks: bool) -> int:
    if not (sdk / "platforms").is_dir() or not (ROOT / ".cache/android-debug.keystore").is_file():
        raise RuntimeError("Use the documented task-local SDK and existing debug signing key")
    env = os.environ.copy()
    env.update(ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
               GRADLE_USER_HOME=str(ROOT / ".cache/gradle-user-home"))
    wrapper = ROOT / "android" / ("gradlew.bat" if os.name == "nt" else "gradlew")
    args = [str(wrapper), "-p", str(PROJECT), "assembleDebug", "lintDebug", "dependencies",
            "--no-daemon", "--console=plain", "--max-workers=4", "-Pandroid.builder.sdkDownload=false"]
    if write_locks:
        args.append("--write-locks")
    return subprocess.run(args, env=env, cwd=ROOT, check=False).returncode


def run(adb: str) -> None:
    if not APK.is_file():
        raise RuntimeError("Build the standalone fixture APK first")
    listing = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=30, check=True)
    serial = physical_target(listing.stdout)  # Never print/persist device identifiers.

    def device(*command: str, timeout: int = 90) -> str:
        proc = subprocess.run([adb, "-s", serial, *command], capture_output=True,
                              text=True, timeout=timeout)
        if proc.returncode:
            raise RuntimeError(f"Phone operation failed: {command[0]}; raw output withheld")
        return proc.stdout

    if device("shell", "getprop", "ro.kernel.qemu").strip() == "1":
        raise RuntimeError("Physical phone required")
    if device("shell", "getprop", "ro.product.cpu.abi").strip() != "arm64-v8a":
        raise RuntimeError("This fixture qualifies ARM64 only")
    api = int(device("shell", "getprop", "ro.build.version.sdk").strip())
    if api < 24:
        raise RuntimeError("Fixture requires API 24 or newer")
    if "package:" + PACKAGE in device("shell", "pm", "list", "packages", PACKAGE).splitlines():
        raise RuntimeError("Fixture package already exists; preserve it and resolve ownership first")
    evidence = {
        "scope": "standalone synthetic archive primitive/envelope fixture only; no history parser, provider or Keystore",
        "api": api, "abi": "arm64-v8a", "passed": False, "packageRemoved": False,
        "apk": {"sha256": hashlib.sha256(APK.read_bytes()).hexdigest(), "size": APK.stat().st_size},
    }
    installed = False
    try:
        if "Success" not in device("install", str(APK), timeout=180):
            raise RuntimeError("Fixture installation did not report success")
        installed = True
        output = device("shell", "am", "instrument", "-w", "-r", RUNNER, timeout=180)
        try:
            evidence["measurements"] = measurements(output)
        except RuntimeError:
            stages = "preflight|hkdf|credentials|round_trips|envelope_mutations|keyset_allowlist|stream_mutations|limits_cancellation|published_vectors"
            stage = re.search(rf"^INSTRUMENTATION_RESULT: stage=({stages})$", output, re.M)
            failure = re.search(r"^INSTRUMENTATION_RESULT: stream=FAIL: ([A-Za-z]{1,80})\s*$", output, re.M)
            evidence["failureStage"] = stage[1] if stage else "unreported"
            evidence["failureType"] = failure[1] if failure else "unreported"
            raise RuntimeError("Archive fixture failed; stage=" + evidence["failureStage"] +
                               "; type=" + evidence["failureType"] + "; raw output withheld") from None
        # Read only these two fixed synthetic fixture outputs, never arbitrary app paths.
        directory = ROOT / ".cache/as-006/archive"
        directory.mkdir(parents=True, exist_ok=True)
        evidence["independentReaderCases"] = 0
        for remote, local, maximum in [
            ("synthetic-vectors.json", "phone-vectors.json", 16_384),
            ("synthetic-large.json", "phone-large.json", 6 * 1024 * 1024),
        ]:
            # File-backed capture bounds host RAM; validate size before reading/parsing.
            with tempfile.TemporaryFile() as captured:
                proc = subprocess.run([adb, "-s", serial, "exec-out", "run-as", PACKAGE,
                                       "cat", "no_backup/" + remote], stdout=captured,
                                      stderr=subprocess.DEVNULL, timeout=30, check=False)
                if proc.returncode or not 1 <= captured.tell() <= maximum:
                    raise RuntimeError("Missing/oversized synthetic vector output")
                captured.seek(0)
                payload = captured.read(maximum + 1)
            path = directory / local
            path.write_bytes(payload)
            verified = subprocess.run(["node", str(ROOT / "tools/archive_reader.mjs"), str(path)],
                                      capture_output=True, text=True, timeout=30, check=False)
            expected = 3 if local == "phone-vectors.json" else 1
            if verified.returncode or verified.stdout.strip() != (
                f"PASS: independent reader; {expected} synthetic vectors"
            ):
                raise RuntimeError("Independent synthetic envelope reader rejected phone output")
            evidence["independentReaderCases"] += expected
        evidence["passed"] = True
    finally:
        try:
            if installed:
                evidence["packageRemoved"] = "Success" in device("uninstall", PACKAGE)
                if not evidence["packageRemoved"]:
                    raise RuntimeError("Owned fixture package cleanup failed")
        finally:
            path = ROOT / ".cache/as-006/archive-phone-result.json"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print("PASS: synthetic archive envelope qualification on physical phone")
    print(json.dumps(evidence, indent=2))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["build", "run"])
    parser.add_argument("--sdk", type=Path, default=ROOT / ".cache/as-004/sdk")
    parser.add_argument("--adb", default=shutil.which("adb") or str(ROOT / ".cache/as-004/sdk/platform-tools/adb.exe"))
    parser.add_argument("--write-locks", action="store_true", help="only for reviewed dependency introduction/refresh")
    args = parser.parse_args()
    if args.action == "build":
        return build(args.sdk.resolve(), args.write_locks)
    if not Path(args.adb).is_file():
        parser.error("Provide an existing adb; no shared SDK/server changes are attempted")
    run(args.adb)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as failure:
        print(str(failure) if isinstance(failure, RuntimeError) else
              f"Archive qualification stopped: {type(failure).__name__}", file=sys.stderr)
        sys.exit(1)
