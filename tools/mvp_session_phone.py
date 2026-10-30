#!/usr/bin/env python3
"""Run explicitly authorized live entry/session checks; retain history and other services."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import queue
import re
import shutil
import subprocess
import threading
import time

import reels_phone


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["session", "entry", "block"])
    parser.add_argument("--source", choices=["instagram", "facebook", "youtube", "tiktok"], default="tiktok")
    parser.add_argument("--operator-authorized", action="store_true", required=True)
    parser.add_argument("--adb", default=shutil.which("adb"))
    args = parser.parse_args()
    reels_phone.audit_apks()
    listing = subprocess.run([args.adb, "devices"], check=True, capture_output=True, text=True).stdout
    devices = [line.split()[0] for line in listing.splitlines()[1:]
               if line.endswith("\tdevice") and not line.startswith("emulator-")]
    if len(devices) != 1:
        raise RuntimeError("Exactly one authorized physical phone is required")
    prefix = [args.adb, "-s", devices[0]]

    def phone(*arguments, timeout=90):
        response = subprocess.run(prefix + list(arguments), capture_output=True, text=True, timeout=timeout)
        if response.returncode:
            raise RuntimeError("Scoped phone command failed; private output withheld")
        return response.stdout

    def services():
        return set(phone("shell", "settings", "get", "secure", "enabled_accessibility_services").strip().split(":")) - {"", "null"}

    packages = {"instagram": "com.instagram.android", "facebook": "com.facebook.katana",
                "youtube": "com.google.android.youtube", "tiktok": "com.zhiliaoapp.musically"}

    def launch(source):
        package = packages[source]
        if source in ("instagram", "youtube", "facebook"):
            link = {"instagram": "https://www.instagram.com/reels/", "youtube": "https://www.youtube.com/shorts", "facebook": "fb://reels"}[source]
            phone("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", link, "-p", package)
        else:
            component = phone("shell", "cmd", "package", "resolve-activity", "--user", "0", "--brief",
                              "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", package).strip().splitlines()[-1]
            if not re.fullmatch(re.escape(package) + r"/[A-Za-z0-9_.$]+", component):
                raise RuntimeError("Unexpected launcher component; no input sent")
            phone("shell", "am", "start", "-n", component)

    prior_services = services()
    if phone("shell", "getprop", "ro.kernel.qemu").strip() == "1":
        raise RuntimeError("A physical phone is required")
    if "package:" + reels_phone.TEST in phone("shell", "pm", "list", "packages", reels_phone.TEST).splitlines():
        raise RuntimeError("Existing test package preserved")
    if "ActiveInstrumentation" in phone("shell", "dumpsys", "activity", "processes"):
        raise RuntimeError("Active instrumentation preserved")
    result = {"recordedAtUtc": datetime.now(timezone.utc).isoformat(), "mode": args.mode, "source": args.source,
              "passed": False, "controlledSwipes": 0, "navigationTaps": 0,
              "artifacts": {key: hashlib.sha256(path.read_bytes()).hexdigest() for key, path in
                            {"preview": reels_phone.PREVIEW, "test": reels_phone.TEST_APK}.items()}}
    installed = False
    process = None
    lines = []
    current_source = args.source
    navigated = set()
    try:
        if "Success" not in phone("install", "-r", str(reels_phone.PREVIEW)):
            raise RuntimeError("Preview installation failed")
        phone("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        phone("shell", "wm", "dismiss-keyguard")
        phone("shell", "am", "start", "-n", reels_phone.APP + "/com.attentionstats.MainActivity")
        time.sleep(3)
        if "Success" not in phone("install", str(reels_phone.TEST_APK)):
            raise RuntimeError("Test installation failed")
        installed = True
        qualification = {"session": "reels-session-operator", "entry": "reels-first-entry-operator", "block": "reels-block-operator"}[args.mode]
        process = subprocess.Popen(prefix + ["shell", "am", "instrument", "--no-restart", "-w", "-r", "-e", "qualification", qualification,
            "-e", "operator_authorized", "adb-diary-timers", "-e", "progress_app", args.source, reels_phone.RUNNER],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        output = queue.Queue()

        def reader():
            for line in process.stdout:
                output.put(line.strip())
            output.put(None)

        threading.Thread(target=reader, daemon=True).start()
        deadline = time.monotonic() + 190
        while time.monotonic() < deadline:
            try:
                line = output.get(timeout=1)
            except queue.Empty:
                continue
            if line is None:
                break
            lines.append(line)
            if line.startswith("INSTRUMENTATION_STATUS: session_phase="):
                phase = line.split("=", 1)[1]
                print("Physical session phase: " + phase, flush=True)
                if phase in ("first", "return", "second"):
                    current_source = "instagram" if phase == "second" else "tiktok"
                    launch(current_source)
                elif phase == "outside":
                    phone("shell", "input", "keyevent", "KEYCODE_HOME")
                elif phase == "screen_off":
                    phone("shell", "input", "keyevent", "KEYCODE_SLEEP")
                elif phase == "wake":
                    phone("shell", "input", "keyevent", "KEYCODE_WAKEUP")
                    phone("shell", "wm", "dismiss-keyguard")
            if line in ("INSTRUMENTATION_STATUS: first_entry_ready=1", "INSTRUMENTATION_STATUS: block_ready=1"):
                launch(current_source)
            if line.startswith(("INSTRUMENTATION_STATUS: session_navigation=", "INSTRUMENTATION_STATUS: viewer_navigation=")):
                navigation = json.loads(line.split("=", 1)[1])
                if navigation.get("app") == current_source and navigation.get("bounded") and current_source not in navigated:
                    ids = {"instagram": {"clips_tab"}, "facebook": {"reels_tab"},
                           "youtube": {"shorts_tab", "shorts_tab_button"}}.get(current_source, set())
                    controls = [row for row in navigation.get("controls", []) if row.get("id") in ids]
                    if len(controls) == 1:
                        control = controls[0]
                        if not all(type(control.get(key)) is int for key in ("left", "top", "right", "bottom")) or not (
                                0 <= control["left"] < control["right"] <= 4096 and 0 <= control["top"] < control["bottom"] <= 4096):
                            raise RuntimeError("Invalid fixed-control geometry; no input sent")
                        foreground = phone("shell", "dumpsys", "activity", "activities")
                        if not any("topResumedActivity" in value and packages[current_source] + "/" in value for value in foreground.splitlines()):
                            raise RuntimeError("Selected source left foreground; no input sent")
                        phone("shell", "input", "tap", str((control["left"] + control["right"]) // 2), str((control["top"] + control["bottom"]) // 2))
                        navigated.add(current_source)
                        result["navigationTaps"] += 1
            if line == "INSTRUMENTATION_STATUS: first_scroll_ready=1":
                foreground = phone("shell", "dumpsys", "activity", "activities")
                if not any("topResumedActivity" in value and packages[current_source] + "/" in value for value in foreground.splitlines()):
                    raise RuntimeError("Selected source left foreground; no swipe sent")
                phone("shell", "input", "swipe", "350", "900", "350", "400", "500")
                result["controlledSwipes"] += 1
        process.wait(timeout=15)
        success = {"session": "PASS: physical cross-app unlock-session continuity",
                   "entry": "PASS: first viewer encounter and HUD without scrolling",
                   "block": "PASS: authorized Reels blocking overlay and bypass"}[args.mode]
        result["passed"] = "INSTRUMENTATION_RESULT: stream=" + success in lines and "INSTRUMENTATION_CODE: -1" in lines
        result["checks"] = {match[1]: int(match[2]) for line in lines
                            if (match := re.fullmatch(r"INSTRUMENTATION_RESULT: ([a-z_]+)=([01])", line))}
    finally:
        if process is not None and process.poll() is None:
            # Only this owned instrumentation target is stopped on a bounded failure.
            phone("shell", "am", "force-stop", reels_phone.APP)
            process.wait(timeout=15)
        phone("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        phone("shell", "wm", "dismiss-keyguard")
        if installed:
            result["testPackageRemoved"] = "Success" in phone("uninstall", reels_phone.TEST)
            if not result["testPackageRemoved"] or "Success" not in phone("install", "-r", str(reels_phone.PREVIEW)):
                raise RuntimeError("Owned test cleanup failed")
        phone("shell", "am", "start", "-n", reels_phone.APP + "/com.attentionstats.MainActivity")
        result["accessibilitySelectionsPreserved"] = services() == prior_services
        cache = reels_phone.CACHE / ("session-live" if args.mode == "session" else args.mode + "-live-" + args.source)
        cache.with_suffix(".log").write_text("\n".join(lines) + "\n", encoding="utf-8")
        cache.with_suffix(".json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result), flush=True)
    if not result["passed"] or not result["accessibilitySelectionsPreserved"]:
        raise RuntimeError("Live qualification did not pass; bounded private evidence retained")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError):
        raise SystemExit("Live qualification stopped; private phone output withheld")
