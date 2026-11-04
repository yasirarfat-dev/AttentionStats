#!/usr/bin/env python3
"""Check the explicit one-file system-backup boundary; this is not a cloud-upload test."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
ARCHIVE = "system-backup/history.attentionbackup"


def audit(manifest: str, legacy: str, modern: str) -> None:
    app = ET.fromstring(manifest).find("application")
    assert app is not None
    assert app.get(ANDROID + "allowBackup") == "true"
    assert app.get(ANDROID + "backupAgent") == ".backup.SystemBackupAgent"
    assert app.get(ANDROID + "fullBackupOnly") == "true"
    assert app.get(ANDROID + "fullBackupContent") == "@xml/backup_rules"
    assert app.get(ANDROID + "dataExtractionRules") == "@xml/data_extraction_rules"
    old = ET.fromstring(legacy)
    assert old.tag == "full-backup-content" and len(old) == 1
    assert old[0].tag == "include" and old[0].attrib == {
        "domain": "file", "path": ARCHIVE, "requireFlags": "clientSideEncryption"}
    rules = ET.fromstring(modern)
    assert rules.tag == "data-extraction-rules" and [x.tag for x in rules] == ["cloud-backup", "device-transfer"]
    cloud, transfer = rules
    assert cloud.attrib == {"disableIfNoEncryptionCapabilities": "true"} and len(cloud) == 1
    assert cloud[0].tag == "include" and cloud[0].attrib == {"domain": "file", "path": ARCHIVE}
    domains = {"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"}
    assert len(transfer) == len(domains) and not transfer.attrib
    assert all(x.tag == "exclude" and x.attrib == {"domain": x.get("domain"), "path": "."} for x in transfer)
    assert {x.get("domain") for x in transfer} == domains


if __name__ == "__main__":
    base = ROOT / "android/app/src/main"
    audit((base / "AndroidManifest.xml").read_text(),
          (base / "res/xml/backup_rules.xml").read_text(),
          (base / "res/xml/data_extraction_rules.xml").read_text())
    print("PASS: one logical archive only; protected cloud transport; device transfer excluded")
    print("Static policy checks do not establish opt-in behavior, a Google upload or fresh-device recovery.")
