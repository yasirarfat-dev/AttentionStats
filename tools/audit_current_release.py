#!/usr/bin/env python3
"""Read-only current release artifact audit. Never signs, archives, installs or uploads.

Explicit expected versions do not reserve a Console version or establish runtime acceptance.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

import release_candidate as release


def xmltree(text, *, manifest=False):
    """Decode the limited aapt XML-tree format used by our backup rule resources.

    Reject unfamiliar nodes/types instead of silently dropping security-relevant data.
    """
    root = None
    stack = []
    namespaces = {}
    for line in text.splitlines():
        if not line.strip():
            continue
        namespace = re.fullmatch(r"N: (\w+)=(\S+)", line)
        if manifest and namespace:
            prefix, uri = namespace.groups()
            if prefix in namespaces:
                raise ValueError("Duplicate XML namespace")
            namespaces[prefix] = uri
            continue
        node = re.fullmatch(r"( *)E: ([\w-]+) \(line=\d+\)", line)
        if node:
            indent, name = node.groups()
            depth = len(indent)
            while stack and stack[-1][0] >= depth:
                stack.pop()
            element = ET.Element(name)
            if stack:
                if depth != stack[-1][0] + 2:
                    raise ValueError("Unexpected compiled XML nesting")
                stack[-1][1].append(element)
            elif root is None and depth == (2 if manifest else 0):
                root = element
            else:
                raise ValueError("Unexpected compiled XML root")
            stack.append((depth, element))
            continue
        attr = re.fullmatch(r'( *)A: ([\w:-]+)(?:\(0x[0-9a-f]+\))?=(.*)', line)
        if not attr or not stack:
            raise ValueError("Unsupported compiled backup XML")
        indent, name, encoded = attr.groups()
        if ":" in name:
            prefix, local = name.split(":", 1)
            if not manifest or prefix not in namespaces:
                raise ValueError("Unknown XML namespace")
            name = "{" + namespaces[prefix] + "}" + local
        if len(indent) != stack[-1][0] + 2 or name in stack[-1][1].attrib:
            raise ValueError("Unexpected compiled XML attribute")
        string = re.fullmatch(r'"([^"\n]*)" \(Raw: "([^"\n]*)"\)', encoded)
        typed = re.fullmatch(r'\(type 0x(10|11|12)\)0x([0-9a-f]+)', encoded)
        if string:
            value, raw = string.groups()
            if value != raw:
                raise ValueError("Ambiguous compiled XML attribute")
        elif typed:
            kind, number = typed.groups()
            if kind == "12" and number in {"0", "ffffffff"}:
                value = "true" if number == "ffffffff" else "false"
            elif manifest and kind in {"10", "11"}:
                value = str(int(number, 16))
            else:
                raise ValueError("Unsupported compiled XML type")
        elif manifest and re.fullmatch(r'@0x[0-9a-f]+', encoded):
            value = encoded
        else:
            raise ValueError("Unsupported compiled XML value")
        stack[-1][1].set(name, value)
    if root is None:
        raise ValueError("Empty compiled XML")
    return root


def audit_backup(legacy, modern):
    old, rules = xmltree(legacy), xmltree(modern)
    if old.tag != "full-backup-content" or old.attrib or len(old) != 1 or old[0].tag != "include" or old[0].attrib != {
        "domain": "file", "path": "system-backup/history.attentionbackup", "requireFlags": "clientSideEncryption"
    } or len(old[0]):
        raise ValueError("Unexpected compiled legacy backup allowlist")
    if rules.tag != "data-extraction-rules" or rules.attrib or [x.tag for x in rules] != ["cloud-backup", "device-transfer"]:
        raise ValueError("Unexpected compiled extraction rules")
    cloud, transfer = rules
    if cloud.attrib != {"disableIfNoEncryptionCapabilities": "true"} or len(cloud) != 1 or cloud[0].tag != "include" or cloud[0].attrib != {
        "domain": "file", "path": "system-backup/history.attentionbackup"
    } or len(cloud[0]):
        raise ValueError("Unexpected compiled protected cloud allowlist")
    domains = {"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"}
    if transfer.attrib or len(transfer) != len(domains) or {x.get("domain") for x in transfer} != domains or any(
        x.tag != "exclude" or x.attrib != {"domain": x.get("domain"), "path": "."} or len(x) for x in transfer
    ):
        raise ValueError("Unexpected compiled device-transfer exclusions")
    return {"oneLogicalFileOnly": True, "protectedCloudTransportRequired": True, "deviceTransferExcluded": True}


def compiled_resource(apk, name, resources, sdk):
    matches = re.findall(r'(?m)^\s*resource 0x[0-9a-f]+ attention\.stats:xml/' + re.escape(name) +
                         r':[^\n]*\n\s*\(string8\) "(res/[^"\n]+\.xml)"', resources)
    if len(matches) != 1:
        raise ValueError("Missing or ambiguous compiled XML resource")
    return release.call(sdk / "aapt.exe", "dump", "xmltree", apk, matches[0])


def bundle_resource(aab, name, resources):
    matches = re.findall(r'(?m)^(0x[0-9a-f]+) - xml/' + re.escape(name) +
                         r'\n\s*\(default\) - \[FILE\] (res/[^\n]+\.xml)', resources)
    if len(matches) != 1:
        raise ValueError("Missing or ambiguous bundle XML resource")
    resource_id, path = matches[0]
    xml = release.call("java", "-Dfile.encoding=UTF-8", "--class-path", release.BUNDLETOOL,
                       release.ROOT / "tools/DecodeBundleXml.java", aab, "base/" + path)
    return resource_id, ET.fromstring(xml)


def shape(node):
    return (node.tag, tuple(sorted(node.attrib.items())), tuple(shape(child) for child in node))


def service_resource_reference(app):
    services = [x for x in app.findall("service") if x.get(release.ANDROID + "name") == "com.attentionstats.reels.ReelsAccessibilityService"]
    if len(services) != 1:
        raise ValueError("Ambiguous accessibility service")
    metadata = [x for x in services[0].findall("meta-data") if x.get(release.ANDROID + "name") == "android.accessibilityservice"]
    if len(metadata) != 1:
        raise ValueError("Missing or ambiguous accessibility metadata")
    return metadata[0].get(release.ANDROID + "resource")


def payload(archive, prefix):
    names = archive.namelist()
    if len(names) != len(set(names)):
        raise ValueError("Duplicate archive entries")
    return {n.removeprefix(prefix): hashlib.sha256(archive.read(n)).hexdigest() for n in names
            if n.startswith(prefix) and not n.endswith("/")}


def compare_assets(packaged, bundle):
    apk_assets, bundle_assets = payload(packaged, "assets/"), payload(bundle, "base/assets/")
    # AGP installs baseline profiles as APK assets but stores their AAB originals
    # in bundle metadata. Compare that exact known mapping instead of ignoring them.
    for name in ("baseline.prof", "baseline.profm"):
        apk_name = "dexopt/" + name
        bundle_name = "BUNDLE-METADATA/com.android.tools.build.profiles/" + name
        if apk_name in apk_assets or bundle_name in bundle.namelist():
            if apk_name not in apk_assets or bundle_name not in bundle.namelist() or apk_assets.pop(apk_name) != hashlib.sha256(bundle.read(bundle_name)).hexdigest():
                raise ValueError("APK/AAB baseline profiles differ")
    if apk_assets != bundle_assets or not packaged.read("assets/index.android.bundle"):
        raise ValueError("APK/AAB assets differ or JS bundle is empty")


def audit(apk, aab, version_name, version_code):
    if not release.BUNDLETOOL.is_file() or release.digest(release.BUNDLETOOL) != release.BUNDLETOOL_SHA:
        raise ValueError("Reviewed bundletool is missing or changed")
    sdk = release.ROOT / ".cache/as-004/sdk/build-tools/37.0.0"
    tool = ["java", "-Dfile.encoding=UTF-8", "-jar", release.BUNDLETOOL]
    release.call(*tool, "validate", "--bundle=" + str(aab))
    manifest = release.call(*tool, "dump", "manifest", "--bundle=" + str(aab), "--module=base")
    metadata = release.audit_manifest(manifest, version_code, version_name=version_name, profile="current")
    # Compare runtime code and assets; independently audit both manifests.
    with zipfile.ZipFile(apk) as packaged, zipfile.ZipFile(aab) as bundle:
        libraries = release.native_alignment(bundle, "base/lib/")
        apk_libraries = release.native_alignment(packaged, "lib/")
        if {x["name"].removeprefix("base/"): x["sha256"] for x in libraries} != {x["name"]: x["sha256"] for x in apk_libraries}:
            raise ValueError("APK/AAB native libraries differ")
        compare_assets(packaged, bundle)
        apk_dex = {n: hashlib.sha256(packaged.read(n)).hexdigest() for n in packaged.namelist() if re.fullmatch(r"classes(?:\d+)?\.dex", n)}
        bundle_dex = payload(bundle, "base/dex/")
        if not apk_dex or apk_dex != bundle_dex:
            raise ValueError("APK/AAB DEX differ")
    # Decode the APK binary XML and AAB protobuf XML independently.
    tree = release.call(sdk / "aapt.exe", "dump", "xmltree", apk, "AndroidManifest.xml")
    apk_manifest = xmltree(tree, manifest=True)
    apk_metadata = release.audit_manifest(ET.tostring(apk_manifest, encoding="unicode"), version_code,
                                          version_name=version_name, profile="current")
    if apk_metadata != metadata:
        raise ValueError("APK/AAB manifest audit results differ")
    resources = release.call(sdk / "aapt.exe", "dump", "--values", "resources", apk)
    app = apk_manifest.find("application")
    for attr, name in [("fullBackupContent", "backup_rules"), ("dataExtractionRules", "data_extraction_rules")]:
        resource = re.findall(r'(?m)^\s*resource (0x[0-9a-f]+) attention\.stats:xml/' + name + ':', resources)
        if len(resource) != 1 or app.get(release.ANDROID + attr) != "@" + resource[0]:
            raise ValueError("APK manifest does not reference audited backup rules")
    legacy = compiled_resource(apk, "backup_rules", resources, sdk)
    modern = compiled_resource(apk, "data_extraction_rules", resources, sdk)
    backup = audit_backup(legacy, modern)
    bundle_resources = release.call(*tool, "dump", "resources", "--bundle=" + str(aab), "--values")
    bundle_app = ET.fromstring(manifest).find("application")
    for attr, name, decoded in [("fullBackupContent", "backup_rules", xmltree(legacy)),
                                 ("dataExtractionRules", "data_extraction_rules", xmltree(modern))]:
        resource_id, bundle_xml = bundle_resource(aab, name, bundle_resources)
        if bundle_app.get(release.ANDROID + attr) not in {"@xml/" + name, "@" + resource_id}:
            raise ValueError("Bundle manifest does not reference audited backup rules")
        if shape(bundle_xml) != shape(decoded):
            raise ValueError("APK/AAB compiled backup rules differ")
    service = compiled_resource(apk, "reels_accessibility_service", resources, sdk)
    service_id = re.findall(r'(?m)^\s*resource (0x[0-9a-f]+) attention\.stats:xml/reels_accessibility_service:', resources)
    if len(service_id) != 1 or service_resource_reference(app) != "@" + service_id[0]:
        raise ValueError("APK service does not reference audited accessibility rules")
    for key, expected in [("canPerformGestures", "ffffffff"), ("isAccessibilityTool", "0")]:
        if not re.search(re.escape(key) + r'\([^)]*\)=\(type 0x12\)0x' + expected + r'\b', service):
            raise ValueError("Unexpected compiled accessibility declaration")
    bundle_service_id, bundle_service = bundle_resource(aab, "reels_accessibility_service", bundle_resources)
    if service_resource_reference(bundle_app) not in {"@xml/reels_accessibility_service", "@" + bundle_service_id}:
        raise ValueError("Bundle service does not reference audited accessibility rules")
    if bundle_service.tag != "accessibility-service" or bundle_service.get(release.ANDROID + "canPerformGestures") != "true" or bundle_service.get(release.ANDROID + "isAccessibilityTool") != "false":
        raise ValueError("Unexpected bundle accessibility declaration")
    release.call(sdk / "zipalign.exe", "-c", "-P", "16", "4", apk)
    config = release.call(*tool, "dump", "config", "--bundle=" + str(aab))
    if "PAGE_ALIGNMENT_16K" not in config:
        raise ValueError("AAB missing 16-KiB ZIP alignment")
    return {"status": "static-audit-only", "manifest": metadata, "apkAndAabCompiledBackup": backup,
            "apkAabAssetsDexNativeMatch": True, "nativeLibraryCount": len(libraries),
            "artifacts": {p.suffix: release.digest(p) for p in (apk, aab)},
            "sourceHead": release.call("git", "rev-parse", "HEAD").strip(),
            "sourceDirty": bool(release.call("git", "status", "--porcelain").strip()),
            "limitations": ["Full APK/AAB resource equivalence beyond backup rules is not established",
                            "Signatures, device/runtime, OS recovery, egress, Console and policy acceptance are separate"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    args = parser.parse_args()
    print(json.dumps(audit(args.apk, args.aab, args.version_name, args.version_code), indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, ET.ParseError, zipfile.BadZipFile) as error:
        raise SystemExit("Current candidate audit stopped: " + (str(error) if isinstance(error, ValueError) else type(error).__name__))
