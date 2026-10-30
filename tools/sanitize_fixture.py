#!/usr/bin/env python3
"""Turn a `uiautomator dump` XML into a public, synthetic detection fixture.

Layout (classes, view IDs, bounds, flags) is kept exactly; every human-written label
(names, captions, comments) is replaced by a stable synthetic token so the fixture keeps
the same equal/different relationships without carrying anyone's content. Generic
interface labels ("Like", "Reel", "Sponsored", ...) are kept because detectors rely on them.

    python tools/sanitize_fixture.py raw.xml native/reels-core/src/test/resources/fixtures/instagram/viewer.xml
"""

import hashlib
import re
import sys
import xml.etree.ElementTree as ET

GENERIC = {
    "like", "liked", "unlike", "comment", "comments", "share", "save", "saved", "more", "follow", "following",
    "subscribe", "subscribed", "reel", "reels", "video", "videos", "home", "back", "search", "close", "play",
    "pause", "sponsored", "ad", "ads", "repost", "remix", "friends", "message", "profile", "create", "shorts",
    "you", "subscriptions", "tune", "add a comment", "write a comment…", "see translation", "see more", "hide ad",
    "hide post", "turn sound on", "turn sound off", "unmute sound", "mute sound", "play current reel", "photo",
    "story tray", "seekbar", "react", "send", "watch reel", "play video", "pause video", "tap to show video controls",
    "reel details", "for you", "following", "explore", "inbox", "live", "discover", "spotlight", "stories", "map",
    "chat", "camera", "search and explore", "create a reel", "navigate to your reels profile",
}
KEEP_PREFIXES = ("Reel by ", "Video by ", "Photo by ", "Clip by ", "View reel by ", "Follow ", "Profile picture of ",
                 "Ad•", "Sponsored", "Go to channel ", "Like video. ", "Read or add comments. ")


def token(value: str) -> str:
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()[:8]
    return f"syn{digest}"


def sanitize(value: str) -> str:
    if not value or value.strip().lower() in GENERIC:
        return value
    if re.fullmatch(r"[\d.,:KMB ]+", value):  # bare counters and timestamps
        return value
    for prefix in KEEP_PREFIXES:
        if value.startswith(prefix):
            return prefix + token(value[len(prefix):])
    if re.fullmatch(r"(Home|Reels|Video|Shorts|Friends|Profile|Notifications|Menu|Marketplace)(, [Tt]ab \d+ of \d+.*)?", value):
        return value
    return token(value)


def main() -> int:
    source, target = sys.argv[1], sys.argv[2]
    tree = ET.parse(source)
    for node in tree.iter("node"):
        for attribute in ("text", "content-desc"):
            if attribute in node.attrib:
                node.attrib[attribute] = sanitize(node.attrib[attribute])
    tree.write(target, encoding="utf-8", xml_declaration=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
