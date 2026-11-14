#!/usr/bin/env python3
"""Operator-only, bounded Meta UI research; never a production scrolling backend.

Requires an explicitly selected authorized phone and already-open source screen.
Reports fixed interface tokens and switch states only. No content or raw XML is
written locally. A private temporary phone dump is removed even on failure.
"""

import argparse
import json
import re
import subprocess
import uuid
import xml.etree.ElementTree as ET

PACKAGES = {"facebook": "com.facebook.katana", "instagram": "com.instagram.android"}
TOKENS = {
    "auto scroll", "auto-scroll", "autoscroll", "playback", "playback settings",
    "more", "more options", "options", "reel options", "reels", "reels tab",
    "pause", "play", "captions", "full screen", "speed", "playback speed",
    "save", "report", "not interested", "interested", "remix", "manage",
    "try again", "retry", "loading", "something went wrong", "no internet connection",
    "couldn't refresh feed", "log in", "sign up", "update app",
}


def inspect(xml, package):
    root = ET.fromstring(xml)
    nodes = list(root.iter("node"))
    if not nodes or len(nodes) > 512:
        raise ValueError("empty or over-budget screen; no action")
    if any(n.get("password") == "true" or "EditText" in n.get("class", "") for n in nodes):
        raise ValueError("editable/private context; no action")
    if any(n.get("package") not in (package, "com.android.systemui", "") for n in nodes):
        raise ValueError("foreign window; no action")
    matches = []
    for node in nodes:
        if node.get("package") != package:
            continue
        for field in ("text", "content-desc"):
            label = " ".join(node.get(field, "").casefold().split())
            if label in TOKENS:
                matches.append((label, node))
                break
            if re.search(r"\bauto[ -]?scroll\b", label):
                # Discovery only: captions can contain the same words. This
                # marker is deliberately ineligible for all navigation actions.
                matches.append(("auto-scroll phrase (unqualified)", node))
                break
    return matches


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--source", required=True, choices=PACKAGES)
    parser.add_argument("--operator-authorized", required=True, action="store_true")
    args = parser.parse_args()
    package = PACKAGES[args.source]

    def phone(*parts):
        result = subprocess.run(["adb", "-s", args.serial, *parts], capture_output=True, timeout=35)
        if result.returncode:
            raise ValueError("scoped phone operation failed; private output withheld")
        return result.stdout.decode("utf-8", errors="replace")

    # Never navigate to a source or modify a provider preference implicitly.
    focus = phone("shell", "dumpsys", "window")
    focused = [line for line in focus.splitlines() if "mCurrentFocus=" in line]
    if len(focused) != 1 or not re.search(r"\b" + re.escape(package) + r"/", focused[0]):
        raise ValueError("selected source must already be foreground")
    path = "/data/local/tmp/attentionstats-builtin-" + uuid.uuid4().hex + ".xml"
    try:
        phone("shell", "uiautomator", "dump", path)
        matches = inspect(phone("shell", "cat", path), package)
    finally:
        phone("shell", "rm", "-f", path)
    print(json.dumps({"source": args.source, "fixedControls": [
        {"token": label, "class": node.get("class"),
         "clickable": node.get("clickable") == "true",
         "checkable": node.get("checkable") == "true",
         "checked": (node.get("checked") == "true") if node.get("checkable") == "true" else None,
         "selected": node.get("selected") == "true"}
        for label, node in matches], "menuActions": 0,
        "providerToggleActions": 0, "rawCaptureRetained": False}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, ET.ParseError, subprocess.TimeoutExpired):
        raise SystemExit("Probe unavailable; no provider action, private output withheld")
