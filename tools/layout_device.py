#!/usr/bin/env python3
"""Manage an isolated layout emulator; never target a physical phone or shared AVD."""

import argparse
import json
import os
from pathlib import Path
import socket
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".cache/layout-device"
NAME = "attentionstats_layout_api37"
PROFILES = {
    "narrow": (320, 568, 1.0),
    "short": (360, 480, 1.0),
    "standard": (360, 800, 1.0),
    "landscape": (640, 360, 1.0),
    "large-text": (320, 568, 2.0),
    "tablet": (600, 960, 1.0),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "start", "status", "profile", "capture", "stop"])
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", ROOT / ".cache/as-004/sdk")))
    parser.add_argument("--profile", choices=PROFILES, default="standard")
    parser.add_argument("--label", default="screen")
    args = parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True)
    registry = CACHE / "owned.json"
    if args.action == "prepare":
        if registry.exists():
            raise RuntimeError("Existing isolated configuration preserved; use start/status")
        sdk = args.sdk.resolve()
        image = sdk / "system-images/android-37.0/google_apis_playstore/x86_64"
        if not image.is_dir() or not (sdk / "emulator/emulator.exe").is_file():
            raise RuntimeError("Existing API37 x86_64 image and Windows emulator required; no SDK download or shared change")
        home = CACHE / "avds"
        avd = home / (NAME + ".avd")
        avd.mkdir(parents=True)
        (home / (NAME + ".ini")).write_text(f"avd.ini.encoding=UTF-8\npath={avd.as_posix()}\ntarget=android-37\n")
        values = {"AvdId": NAME, "abi.type": "x86_64", "hw.cpu.arch": "x86_64", "hw.cpu.ncore": "2",
                  "hw.ramSize": "2560", "hw.lcd.width": "720", "hw.lcd.height": "1280", "hw.lcd.density": "320",
                  "hw.gpu.enabled": "yes", "hw.gpu.mode": "host", "hw.keyboard": "yes", "hw.mainKeys": "no",
                  "disk.dataPartition.size": "2048M", "image.sysdir.1": image.as_posix() + "/",
                  "tag.id": "google_apis_playstore", "target": "android-37", "showDeviceFrame": "no"}
        (avd / "config.ini").write_text("".join(f"{key}={value}\n" for key, value in values.items()))
        def available(port):
            for number in (port, port + 1):
                with socket.socket() as connection:
                    try:
                        connection.bind(("127.0.0.1", number))
                    except OSError:
                        return False
            return True
        port = next(port for port in range(5554, 5584, 2) if available(port))
        registry.write_text(json.dumps({"avd": NAME, "port": port, "avdHome": str(home), "sdk": str(sdk)}))
        print("PASS: isolated API37 layout configuration prepared; no shared AVD changed")
        return
    data = json.loads(registry.read_text(encoding="utf-8-sig"))
    if data["avd"] != NAME or Path(data["avdHome"]).resolve() != (CACHE / "avds").resolve():
        raise RuntimeError("Unrecognized emulator ownership")
    port = data["port"]
    if type(port) is not int or port not in range(5554, 5584, 2):
        raise RuntimeError("Unexpected emulator port")
    sdk = Path(data["sdk"])
    adb = sdk / "platform-tools/adb.exe"
    target = "emulator-" + str(port)
    def call(*command, binary=False):
        result = subprocess.run([str(adb), "-s", target, *command], capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError("Owned emulator not ready; private platform output withheld")
        return result.stdout if binary else result.stdout.decode(errors="replace").strip()
    if args.action == "start":
        if data.get("pid") and os.name == "nt":
            result = subprocess.run(["tasklist", "/FI", f"PID eq {int(data['pid'])}", "/FO", "CSV", "/NH"], capture_output=True, text=True)
            if "emulator.exe" in result.stdout.lower():
                raise RuntimeError("Owned emulator process already running")
        env = os.environ.copy()
        env.update(ANDROID_AVD_HOME=data["avdHome"], ANDROID_EMULATOR_HOME=str(CACHE))
        with (CACHE / "emulator-out.log").open("wb") as output, (CACHE / "emulator-err.log").open("wb") as error:
            process = subprocess.Popen([str(sdk / "emulator/emulator.exe"), "-avd", NAME, "-port", str(port),
                                        "-no-window", "-no-snapshot", "-no-audio", "-no-boot-anim", "-no-metrics", "-show-kernel",
                                        "-gpu", "host", "-feature", "-Vulkan",
                                        "-memory", "2560", "-cores", "2"], env=env, stdout=output, stderr=error,
                                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        data["pid"] = process.pid
        registry.write_text(json.dumps(data))
        print("Owned isolated emulator launched; boot readiness must be checked separately")
        return
    if call("emu", "avd", "name").splitlines()[0] != NAME:
        raise RuntimeError("Dedicated emulator identity mismatch; no changes made")
    if args.action == "stop":
        call("emu", "kill")
        data.pop("pid", None)
        registry.write_text(json.dumps(data))
        print("Owned isolated emulator stopped; reusable configuration retained")
        return
    if call("shell", "getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Dedicated emulator is not ready")
    if args.action == "status":
        print(json.dumps({"bootComplete": call("shell", "getprop", "sys.boot_completed") == "1",
                          "api": call("shell", "getprop", "ro.build.version.sdk"),
                          "abis": call("shell", "getprop", "ro.product.cpu.abilist")}))
    elif args.action == "profile":
        width, height, scale = PROFILES[args.profile]
        call("shell", "wm", "density", "160")
        call("shell", "wm", "size", f"{width}x{height}")
        call("shell", "settings", "put", "system", "font_scale", str(scale))
        print(f"Applied isolated {args.profile}: {width}x{height}dp, font scale {scale}")
    elif args.action == "capture":
        if not args.label.replace("-", "").replace("_", "").isalnum():
            raise RuntimeError("Use a simple capture label")
        foreground = call("shell", "dumpsys", "activity", "activities")
        if not any("topResumedActivity" in line and "com.attentionstats.debug/" in line for line in foreground.splitlines()):
            raise RuntimeError("Own app must be foreground for capture")
        raw = call("exec-out", "screencap", "-p", binary=True)
        if not raw.startswith(b"\x89PNG\r\n\x1a\n") or len(raw) > 16 * 1024 * 1024:
            raise RuntimeError("Invalid bounded capture")
        (CACHE / (args.label + ".png")).write_bytes(raw)
        print("Own-app emulator capture saved in ignored local cache")


if __name__ == "__main__":
    main()
