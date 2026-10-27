#!/usr/bin/env python3
"""Install the bundled preview and run scoped tests on one authorized physical phone."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP = "com.attentionstats.debug"
TEST = APP + ".test"
RUNNER = TEST + "/com.attentionstats.qualification.AnalyticsInstrumentation"
PREVIEW = ROOT / "android/app/build/outputs/apk/preview/app-preview.apk"
TEST_APK = ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
CACHE = ROOT / ".cache/reels-mvp"


def validate_structure(raw: bytes) -> dict:
    """Accept only the documented bounded, flat, content-free diagnostic shape."""
    if len(raw) > 32_768: raise RuntimeError("Structure diagnostic exceeds bound")
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result: raise ValueError("Duplicate key")
            result[key] = value
        return result
    def expect(condition):
        if not condition: raise ValueError("Invalid diagnostic")
    def ident(value, limit=80):
        return isinstance(value, str) and len(value) <= limit and re.fullmatch(r"[A-Za-z0-9_.$]*", value) is not None
    def number(value): return type(value) is int and -2_147_483_648 <= value <= 2_147_483_647
    try:
        value = json.loads(raw, object_pairs_hook=unique)
        expect(type(value) is dict and set(value) == {"scope", "targets"})
        expect(value["scope"] == "consented structural detector qualification only; no content")
        targets = value["targets"]
        expect(type(targets) is dict and 1 <= len(targets) <= 2 and set(targets) <= {"instagram", "facebook"})
        for target in targets.values():
            base_keys = {"version", "nodes", "bounded", "editable", "ids", "positions", "controls", "classes", "scrolls"}
            geometry_keys = {"geometryRevision", "viewport", "anchors"}
            expect(type(target) is dict and set(target) in [base_keys, base_keys | geometry_keys])
            expect(isinstance(target["version"], str) and re.fullmatch(r"[A-Za-z0-9._-]{1,100}", target["version"]) is not None)
            expect(type(target["nodes"]) is int and 0 <= target["nodes"] <= 192)
            expect(type(target["bounded"]) is bool and type(target["editable"]) is bool)
            for key, limit, length in [("ids", 96, 80), ("classes", 24, 120)]:
                expect(type(target[key]) is list and len(target[key]) <= limit and all(ident(x, length) for x in target[key]))
            expect(type(target["positions"]) is list and len(target["positions"]) <= 12)
            for row in target["positions"]:
                expect(type(row) is dict and set(row) == {"id", "owner", "row", "column", "centered"})
                expect(ident(row["id"]) and ident(row["owner"]) and number(row["row"]) and number(row["column"]) and type(row["centered"]) is bool)
            expect(type(target["controls"]) is list and len(target["controls"]) <= 32)
            for row in target["controls"]:
                expect(type(row) is dict and set(row) == {"id", "class", "left", "top", "right", "bottom"})
                expect(ident(row["id"]) and ident(row["class"], 120))
                expect(all(number(row[key]) for key in ["left", "top", "right", "bottom"]))
            expect(type(target["scrolls"]) is list and len(target["scrolls"]) <= 16)
            for row in target["scrolls"]:
                expect(type(row) is dict and set(row) == {"source", "sourceClass", "from", "to", "items", "scrollY", "maxScrollY", "deltaY"})
                expect(ident(row["source"]) and ident(row["sourceClass"], 120))
                expect(all(number(row[key]) for key in ["from", "to", "items", "scrollY", "maxScrollY", "deltaY"]))
            if "geometryRevision" in target:
                expect(type(target["geometryRevision"]) is int and target["geometryRevision"] == 1)
                bounds_keys = {"left", "top", "right", "bottom"}
                def bounds(row):
                    expect(all(number(row[key]) for key in bounds_keys))
                    expect(row["left"] <= row["right"] and row["top"] <= row["bottom"])
                expect(type(target["viewport"]) is dict and set(target["viewport"]) == bounds_keys)
                bounds(target["viewport"])
                expect(type(target["anchors"]) is list and len(target["anchors"]) <= 16)
                layout_keys = bounds_keys | {"slot", "id", "row", "column"}
                def layout(row, extra):
                    expect(type(row) is dict and set(row) == layout_keys | extra)
                    expect(type(row["slot"]) is int and 0 <= row["slot"] < target["nodes"])
                    expect(ident(row["id"]) and number(row["row"]) and number(row["column"]))
                    bounds(row)
                slots = set()
                for anchor in target["anchors"]:
                    layout(anchor, {"ancestors"})
                    expect(anchor["slot"] not in slots)
                    slots.add(anchor["slot"])
                    expect(type(anchor["ancestors"]) is list and len(anchor["ancestors"]) <= 3)
                    previous = anchor["slot"]
                    for ancestor in anchor["ancestors"]:
                        layout(ancestor, set())
                        expect(ancestor["slot"] < previous)
                        previous = ancestor["slot"]
        return value
    except (ValueError, TypeError, KeyError, UnicodeError, RecursionError):
        raise RuntimeError("Diagnostic does not match the bounded metadata-only schema; raw content withheld") from None


def audit_apks() -> None:
    build_tools = ROOT / ".cache/as-004/sdk/build-tools/37.0.0"
    aapt = build_tools / ("aapt2.exe" if os.name == "nt" else "aapt2")
    signer = build_tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    certificates = []
    for apk, expected in [(PREVIEW, APP), (TEST_APK, TEST)]:
        if not apk.exists(): continue
        def local(*args: str) -> str:
            result = subprocess.run(list(args), capture_output=True, text=True, timeout=90)
            if result.returncode: raise RuntimeError("Local APK identity/signature audit failed")
            return result.stdout
        badging = local(str(aapt), "dump", "badging", str(apk))
        if re.findall(r"^package: name='([^']+)'", badging, re.M) != [expected]:
            raise RuntimeError("APK package identity mismatch")
        signatures = local(str(signer), "verify", "--print-certs", str(apk))
        found = re.findall(r"^(?:Signer #[0-9]+|V[0-9]+ Signer):? certificate SHA-256 digest: ([0-9a-f]{64})$", signatures, re.M)
        if len(set(found)) != 1: raise RuntimeError("Expected one verified APK signer")
        certificates.append(found[0])
        if apk == PREVIEW:
            permissions = local(str(aapt), "dump", "permissions", str(apk))
            used = re.findall(r"^uses-permission: name='([^']+)'", permissions, re.M)
            if set(used) - {APP + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}:
                raise RuntimeError("Unexpected preview permission; installation stopped")
    if len(set(certificates)) != 1: raise RuntimeError("Preview/test signer mismatch")


def parse_result(output: str, mode: str) -> dict[str, int]:
    expected = {"storage": "PASS: synthetic main-app Reels storage checks",
                "ui": "PASS: own-window React Native preview screens"}[mode]
    lines = output.splitlines()
    if [x for x in lines if x.startswith("INSTRUMENTATION_CODE:")] != ["INSTRUMENTATION_CODE: -1"] or \
       [x for x in lines if x.startswith("INSTRUMENTATION_RESULT: stream=")] != ["INSTRUMENTATION_RESULT: stream=" + expected]:
        stage = re.search(r"^INSTRUMENTATION_RESULT: probe_stage=([a-z_]{1,40})$", output, re.M)
        kind = re.search(r"^INSTRUMENTATION_RESULT: stream=FAIL: ([A-Za-z]{1,80})\s*$", output, re.M)
        raise RuntimeError("Reels " + mode + " probe failed; stage=" + (stage[1] if stage else "unreported") +
                           "; type=" + (kind[1] if kind else "unreported") + "; raw phone output withheld")
    limits = {"storage_checks": (30, 200), "detector_checks": (30, 100), "key_rejections": (3, 3), "sentinel_files": (1, 3),
              "fixture_removed": (1, 1)} if mode == "storage" else {"ui_checks": (20, 160), "shell_routes": (20, 20)}
    values = {}
    for key, (low, high) in limits.items():
        found = re.findall(rf"^INSTRUMENTATION_RESULT: {key}=([0-9]{{1,6}})$", output, re.M)
        if len(found) != 1 or not low <= int(found[0]) <= high:
            raise RuntimeError("Missing, duplicate or out-of-range Reels aggregate")
        values[key] = int(found[0])
    return values


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["qualify", "ui", "health", "show-counter", "install", "structure", "own-failure-image"])
    parser.add_argument("--label", choices=["reels-entry", "reels-next", "reels-return", "feed", "comments", "ad", "profile", "inbox", "outside"], default="reels-entry")
    parser.add_argument("--user-authorized-display", action="store_true",
                        help="Required for show-counter; use only after the person explicitly requests display")
    sdk_adb = Path(os.environ.get("ANDROID_HOME", str(ROOT / ".cache/as-004/sdk"))) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    parser.add_argument("--adb", type=Path, default=sdk_adb)
    args = parser.parse_args()
    if args.action == "show-counter" and not args.user_authorized_display:
        parser.error("show-counter requires explicit user display authorization; sources/grants stay separate")
    if not args.adb.is_file() or not PREVIEW.is_file() or (args.action in ["qualify", "ui"] and not TEST_APK.is_file()):
        raise RuntimeError("Existing adb and built preview/test APKs required")
    with zipfile.ZipFile(PREVIEW) as archive:
        if not archive.read("assets/index.android.bundle") or any(
                name.startswith("lib/") and not name.startswith("lib/arm64-v8a/") for name in archive.namelist()):
            raise RuntimeError("Expected self-contained ARM64 preview")
    audit_apks()
    listing = subprocess.run([str(args.adb), "devices"], capture_output=True, text=True, timeout=30, check=True)
    targets = [line.split()[0] for line in listing.stdout.splitlines()[1:]
               if line.strip().endswith("\tdevice") and not line.startswith("emulator-")]
    if len(targets) != 1:
        raise RuntimeError("Connect exactly one authorized physical debug phone; no emulator fallback")
    serial = targets[0]  # Never printed or persisted.

    def device(*command: str, timeout: int = 90, binary: bool = False):
        result = subprocess.run([str(args.adb), "-s", serial, *command], capture_output=True,
                                text=not binary, timeout=timeout)
        if result.returncode:
            raise RuntimeError("Phone operation failed: " + command[0] + "; raw output withheld")
        return result.stdout

    if device("shell", "getprop", "ro.kernel.qemu").strip() == "1" or \
       device("shell", "getprop", "ro.product.cpu.abi").strip() != "arm64-v8a":
        raise RuntimeError("Physical ARM64 phone required")
    api = int(device("shell", "getprop", "ro.build.version.sdk").strip())
    if api < 26:
        raise RuntimeError("Own-window UI qualification requires API 26 or newer")
    if "package:" + TEST in device("shell", "pm", "list", "packages", TEST).splitlines():
        raise RuntimeError("Existing test package preserved; resolve ownership before qualification")
    if args.action in ["health", "show-counter"]:
        if api < 31: raise RuntimeError("Non-restarting health probe requires API 31 or newer")
        installed_health = False
        try:
            if "Success" not in device("install", str(TEST_APK), timeout=180):
                raise RuntimeError("Health probe installation did not report success")
            installed_health = True
            output = device("shell", "am", "instrument", *(["--no-restart"] if args.action == "health" else []),
                            "-w", "-r", "-e", "qualification",
                            "reels-show-counter" if args.action == "show-counter" else "reels-health", RUNNER)
            lines = output.splitlines()
            if [x for x in lines if x.startswith("INSTRUMENTATION_CODE:")] != ["INSTRUMENTATION_CODE: -1"] or \
               "INSTRUMENTATION_RESULT: stream=PASS: bounded preview control health" not in lines:
                kind = re.search(r"^INSTRUMENTATION_RESULT: stream=FAIL: ([A-Za-z]{1,80})", output, re.M)
                raise RuntimeError("Counter control probe failed; type=" + (kind[1] if kind else "unreported") +
                                   "; no health restart fallback attempted; raw output withheld")
            flags = {}
            for key in ["accessibilityEnabled", "serviceConnected", "calibrationEnabled", "overlayEnabled", "storageReady", "enabledSources", "pausedSources"]:
                found = re.findall(rf"^INSTRUMENTATION_RESULT: {key}=([0-3])$", output, re.M)
                if len(found) != 1: raise RuntimeError("Malformed health flag; raw output withheld")
                flags[key] = int(found[0])
            print(json.dumps(flags, indent=2))
        finally:
            if installed_health and "Success" not in device("uninstall", TEST):
                raise RuntimeError("Owned health probe cleanup failed")
            if installed_health and args.action == "show-counter":
                if "Success" not in device("install", "-r", str(PREVIEW), timeout=180):
                    raise RuntimeError("Same-preview replacement failed; no settings or data reset attempted")
        return 0
    if args.action == "own-failure-image":
        raw = device("exec-out", "run-as", APP, "cat", "no_backup/reels-ui-failure.png", binary=True)
        if not raw.startswith(b"\x89PNG\r\n\x1a\n") or len(raw) > 16 * 1024 * 1024:
            raise RuntimeError("Own-window image outside bound")
        CACHE.mkdir(parents=True, exist_ok=True)
        (CACHE / "ui-failure.png").write_bytes(raw)
        device("shell", "run-as", APP, "rm", "no_backup/reels-ui-failure.png")
        print("Own-window failure image saved to ignored local cache")
        return 0
    if args.action == "structure":
        raw = device("exec-out", "run-as", APP, "cat", "no_backup/reels-structure.json", binary=True)
        value = validate_structure(raw)
        CACHE.mkdir(parents=True, exist_ok=True)
        (CACHE / ("structure-" + args.label + ".json")).write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"savedPrivateStructure": True, "targets": {
            key: {"bounded": target["bounded"], "editableExcluded": target["editable"],
                  "layoutIdCount": len(target["ids"]), "positionCount": len(target["positions"])}
            for key, target in value["targets"].items()}}))
        return 0
    evidence = {"scope": "main-app bundled preview; synthetic encrypted storage and own-window UI only",
                "api": api, "abi": "arm64-v8a", "installed": False, "passed": False,
                "testPackageRemoved": False,
                "artifacts": {name: {"sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                                     "size": path.stat().st_size}
                              for name, path in {"preview": PREVIEW, "test": TEST_APK}.items() if path.is_file()}}
    installed_test = False
    CACHE.mkdir(parents=True, exist_ok=True)
    try:
        if "Success" not in device("install", "-r", str(PREVIEW), timeout=180):
            raise RuntimeError("Preview installation did not report success; no app data reset attempted")
        evidence["installed"] = True
        if args.action in ["qualify", "ui"]:
            if "Success" not in device("install", str(TEST_APK), timeout=180):
                raise RuntimeError("Test installation did not report success")
            installed_test = True
            for mode in (["storage", "ui"] if args.action == "qualify" else ["ui"]):
                output = device("shell", "am", "instrument", "-w", "-r", "-e", "qualification",
                                "reels-" + mode, RUNNER, timeout=180)
                evidence[mode] = parse_result(output, mode)
                print("PASS: Reels " + mode + " phone checks", flush=True)
            for name in ["controls", "privacy", "reels", "facebook", "hud-collapsed", "hud-expanded",
                         *["shell-" + item for item in ["today", "history", "patterns", "protect", "settings", "diary",
                             "explanation", "categories", "focus", "rules", "sources", "storage", "backup",
                             "appearance", "counter", "convenience", "about", "welcome"]]]:
                relative = "no_backup/reels-ui-" + name + ".png"
                data = device("exec-out", "run-as", APP, "cat", relative, binary=True)
                if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) > 16 * 1024 * 1024:
                    raise RuntimeError("Own-window screenshot outside expected bounds")
                (CACHE / ("ui-" + name + ".png")).write_bytes(data)
                device("shell", "run-as", APP, "rm", relative)
            evidence["passed"] = True
    finally:
        try:
            if installed_test:
                evidence["testPackageRemoved"] = "Success" in device("uninstall", TEST)
                if not evidence["testPackageRemoved"]:
                    raise RuntimeError("Owned test package cleanup failed")
                # Instrumentation/package removal can disconnect an enabled accessibility service.
                # Replacing our same preview restores normal package lifecycle without editing grants.
                if "Success" not in device("install", "-r", str(PREVIEW), timeout=180):
                    raise RuntimeError("Final standalone preview replacement failed; no data reset attempted")
            if evidence["installed"]:
                device("shell", "am", "start", "-n", APP + "/com.attentionstats.MainActivity")
        finally:
            (CACHE / "phone-result.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as failure:
        print(str(failure) if isinstance(failure, RuntimeError) else
              "Reels phone operation stopped: " + type(failure).__name__, file=sys.stderr)
        sys.exit(1)
