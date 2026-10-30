#!/usr/bin/env python3
"""Prepare local Play materials and audit/archive an UNSIGNED ARM64 candidate.

Never signs, installs, uploads, changes Console settings or starts a workflow.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DEST = Path("C:/home/biobot/AppReleases/AttentionStats")
BUNDLETOOL = ROOT / ".cache/release-tools/bundletool-all-1.18.3.jar"
BUNDLETOOL_SHA = "a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "attention.stats"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def call(*args):
    result = subprocess.run([str(x) for x in args], cwd=ROOT, capture_output=True,
                            text=True, timeout=180)
    if result.returncode:
        raise RuntimeError("Release tool failed; inspect local build/tool setup")
    return result.stdout


def audit_manifest(text, version_code=1):
    """Audit the decoded AAB manifest, not source intent."""
    manifest = ET.fromstring(text)
    def need(condition, message):
        if not condition:
            raise ValueError(message)
    need(manifest.get("package") == PACKAGE, "Wrong production package")
    need(manifest.get(ANDROID + "versionCode") == str(version_code), "Unexpected version code")
    need(manifest.get(ANDROID + "versionName") == "0.1.0", "Unexpected version name")
    sdk = manifest.find("uses-sdk")
    need(sdk is not None and sdk.get(ANDROID + "minSdkVersion") == "24" and
         sdk.get(ANDROID + "targetSdkVersion") == "37", "Unexpected SDK baseline")
    app = manifest.find("application")
    need(app is not None, "Missing application")
    need(app.get(ANDROID + "debuggable", "false") == "false", "Debuggable release")
    need(app.get(ANDROID + "testOnly", "false") == "false", "Test-only release")
    need(app.get(ANDROID + "allowBackup") == "false", "Backup must be disabled")
    need(app.get(ANDROID + "usesCleartextTraffic") == "false", "Cleartext must be disabled")
    need(app.get(ANDROID + "fullBackupContent") is not None and
         app.get(ANDROID + "dataExtractionRules") is not None, "Missing backup exclusions")
    need(manifest.find("instrumentation") is None, "Test instrumentation in release")
    permissions = sorted(x.get(ANDROID + "name") for x in manifest.findall("uses-permission"))
    need("android.permission.PACKAGE_USAGE_STATS" in permissions and
         set(permissions) <= {PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                              "android.permission.PACKAGE_USAGE_STATS"},
         "Unexpected permission in release")
    queries = manifest.find("queries")
    need(queries is not None and {x.get(ANDROID + "name") for x in queries.findall("package")} ==
         {"com.instagram.android", "com.facebook.katana", "com.google.android.youtube", "com.zhiliaoapp.musically"} and
         len(queries.findall("package")) == 4 and len(queries) == 5, "Unexpected package visibility")
    intents = queries.findall("intent")
    need(len(intents) == 1 and len(intents[0]) == 2 and
         len(intents[0].findall("action")) == 1 and len(intents[0].findall("category")) == 1 and
         intents[0].find("action").attrib == {ANDROID + "name": "android.intent.action.MAIN"} and
         intents[0].find("category").attrib == {ANDROID + "name": "android.intent.category.LAUNCHER"} and
         not intents[0].attrib, "Unexpected launcher visibility query")
    exported = set()
    for component in app:
        if component.tag in {"activity", "activity-alias", "service", "receiver", "provider"}:
            if component.get(ANDROID + "exported") == "true":
                exported.add(component.get(ANDROID + "name"))
    required = {"com.attentionstats.MainActivity", "com.attentionstats.reels.ReelsAccessibilityService"}
    profiler = "androidx.profileinstaller.ProfileInstallReceiver"
    need(required <= exported <= required | {profiler}, "Unexpected exported components")
    if profiler in exported:
        receiver = next(x for x in app.findall("receiver") if x.get(ANDROID + "name") == profiler)
        need(receiver.get(ANDROID + "permission") == "android.permission.DUMP", "Unprotected profile receiver")
    service = next((x for x in app.findall("service") if
                    x.get(ANDROID + "name") == "com.attentionstats.reels.ReelsAccessibilityService"), None)
    need(service is not None and service.get(ANDROID + "permission") ==
         "android.permission.BIND_ACCESSIBILITY_SERVICE", "Unprotected accessibility service")
    return {"package": PACKAGE, "versionName": "0.1.0", "versionCode": version_code,
            "minSdk": 24, "targetSdk": 37, "debuggable": False, "testOnly": False,
            "backupDisabled": True, "permissions": permissions,
            "exportedComponents": sorted(exported)}


def native_alignment(archive, prefix):
    libraries = []
    for name in sorted(archive.namelist()):
        if not name.startswith(prefix) or not name.endswith(".so"):
            continue
        if not name.startswith(prefix + "arm64-v8a/"):
            raise ValueError("Candidate scope must be ARM64 only")
        data = archive.read(name)
        if data[:6] != b"\x7fELF\x02\x01":
            raise ValueError("Unexpected native format")
        offset = struct.unpack_from("<Q", data, 32)[0]
        entry, count = struct.unpack_from("<HH", data, 54)
        segments = [struct.unpack_from("<IIQQQQQQ", data, offset + n * entry) for n in range(count)]
        minimum = min(x[7] for x in segments if x[0] == 1)
        if minimum < 16_384:
            raise ValueError("Native library is not aligned for 16-KiB pages")
        libraries.append({"name": name, "minLoadAlignment": minimum,
                          "sha256": hashlib.sha256(data).hexdigest()})
    if not libraries:
        raise ValueError("Missing native libraries")
    return libraries


def listing_blocks():
    text = (ROOT / "release/PLAY-CONSOLE.md").read_text(encoding="utf-8")
    blocks = re.findall(r"```text\n(.*?)\n```", text, re.S)
    if len(blocks) != 4:
        raise ValueError("Expected title, short/full descriptions and release notes")
    for block, limit in zip(blocks, [30, 80, 4000, 500]):
        if not 1 <= len(block) <= limit:
            raise ValueError("Play text exceeds field limit")
    return blocks


def prepare(dest):
    if dest.resolve().is_relative_to(ROOT.resolve()):
        raise ValueError("Release archive must stay outside source workspace")
    dest.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / "release/PLAY-CONSOLE.md", dest / "PLAY-CONSOLE.md")
    shutil.copyfile(ROOT / "release/PERMISSION-PLAN.md", dest / "PERMISSION-PLAN.md")
    for directory in ["store/en-US/graphics", "store/en-US/screenshots", "policies", "testing", "private/signing"]:
        (dest / directory).mkdir(parents=True, exist_ok=True)
    for source in (ROOT / "policies").glob("*.md"):
        shutil.copyfile(source, dest / "policies" / source.name)
    for name, value in zip(["title.txt", "short-description.txt", "full-description.txt", "release-notes.txt"], listing_blocks()):
        (dest / "store/en-US" / name).write_text(value + "\n", encoding="utf-8")
    (dest / "README.md").write_text(
        "# Attention Stats releases\n\nPermanent Android package: **attention.stats**. "
        "Source/build workspace: `C:\\home\\biobot\\projects\\AttentionStats`.\n\n"
        "Start with [PLAY-CONSOLE.md](PLAY-CONSOLE.md). Store text/policy copies can be refreshed; "
        "each `releases/<version>+<code>` archive is immutable. Gradle output stays in the source project. "
        "Unsigned candidates are not uploadable. No Console rollout or test-period start is implied.\n\n"
        "`private/signing` is reserved for private upload-key material and its recovery plan; never publish "
        "it or mix it with artifacts. See the local recovery instructions for provisioning status. `testing` is for private feedback and "
        "actual Console dates, never fabricated engagement.\n", encoding="utf-8")
    print("Prepared release folders, root PLAY-CONSOLE.md, policy copies and bounded store text")


def archive(dest):
    if dest.resolve().is_relative_to(ROOT.resolve()):
        raise ValueError("Release archive must stay outside source workspace")
    target = dest / "releases/0.1.0+1"
    if target.exists():
        raise ValueError("Existing release preserved; choose a new version/code, never overwrite")
    if not BUNDLETOOL.is_file() or digest(BUNDLETOOL) != BUNDLETOOL_SHA:
        raise ValueError("Install the reviewed bundletool before auditing")
    apk = ROOT / "android/app/build/outputs/apk/release/app-release-unsigned.apk"
    aab = ROOT / "android/app/build/outputs/bundle/release/app-release.aab"
    tool = ["java", "-jar", BUNDLETOOL]
    call(*tool, "validate", "--bundle=" + str(aab))
    manifest = call(*tool, "dump", "manifest", "--bundle=" + str(aab), "--module=base")
    config = call(*tool, "dump", "config", "--bundle=" + str(aab))
    audit = audit_manifest(manifest)
    if "PAGE_ALIGNMENT_16K" not in config:
        raise ValueError("AAB does not request 16-KiB ZIP alignment")
    with zipfile.ZipFile(aab) as bundle, zipfile.ZipFile(apk) as packaged:
        for z in [bundle, packaged]:
            if any(re.search(r"(?i)^META-INF/.*\.(RSA|DSA|EC|SF)$", n) for n in z.namelist()):
                raise ValueError("This command archives unsigned investigation artifacts only")
        if not bundle.read("base/assets/index.android.bundle") or not packaged.read("assets/index.android.bundle"):
            raise ValueError("Missing self-contained JS bundle")
        libs = native_alignment(bundle, "base/lib/")
        apk_libs = native_alignment(packaged, "lib/")
        if {x["name"].removeprefix("base/"): x["sha256"] for x in libs} != \
           {x["name"]: x["sha256"] for x in apk_libs}:
            raise ValueError("APK/AAB native libraries differ")
    sdk = ROOT / ".cache/as-004/sdk/build-tools/37.0.0"
    call(sdk / "zipalign.exe", "-c", "-P", "16", "4", apk)
    tree = call(sdk / "aapt.exe", "dump", "xmltree", apk, "AndroidManifest.xml")
    if not re.search(r'package="attention\.stats"', tree) or \
       re.search(r'(?:debuggable|testOnly)\([^)]*\)=\(type 0x12\)0xffffffff', tree):
        raise ValueError("APK identity/release mode differs")
    resources = call(sdk / "aapt.exe", "dump", "--values", "resources", apk)
    def compiled_xml(name):
        # Release resource optimization shortens ZIP paths; resolve the resource table.
        matches = re.findall(r'(?m)^\s*resource 0x[0-9a-f]+ attention\.stats:xml/' + re.escape(name) +
                             r':[^\n]*\n\s*\(string8\) "(res/[^"\n]+\.xml)"', resources)
        if len(matches) != 1:
            raise ValueError("Missing or ambiguous compiled XML resource")
        return call(sdk / "aapt.exe", "dump", "xmltree", apk, matches[0])
    service = compiled_xml("reels_accessibility_service")
    for key, expected in [("canPerformGestures", "ffffffff"), ("isAccessibilityTool", "0")]:
        if not re.search(re.escape(key) + r'\([^)]*\)=\(type 0x12\)0x' + expected + r'\b', service):
            raise ValueError("Unexpected accessibility action/tool declaration")
    if compiled_xml("backup_rules").count("E: exclude") != 5 or \
       compiled_xml("data_extraction_rules").count("E: exclude") != 18:
        raise ValueError("Missing compiled backup/transfer exclusions")
    for directory in ["android", "metadata", "notes"]:
        (target / directory).mkdir(parents=True, exist_ok=False)
    entries = []
    for source, name in [(apk, "attention.stats-0.1.0+1-arm64-UNSIGNED.apk"),
                         (aab, "attention.stats-0.1.0+1-arm64-UNSIGNED.aab")]:
        shutil.copyfile(source, target / "android" / name)
        entries.append({"file": "android/" + name, "bytes": source.stat().st_size, "sha256": digest(source)})
    evidence = {"createdUtc": datetime.now(timezone.utc).isoformat(), "status": "unsigned-not-uploadable",
                "sourceHead": call("git", "rev-parse", "HEAD").strip(),
                "sourceDirty": bool(call("git", "status", "--porcelain").strip()),
                "manifest": audit, "bundleValidated": True, "bundleZipAlignment": "PAGE_ALIGNMENT_16K",
                "apkZipAlignment16KiB": True, "nativeLibraries": libs,
                "bundletool": {"version": "1.18.3", "sha256": BUNDLETOOL_SHA},
                "artifacts": entries, "runtimeQualification": "not established by this audit",
                "playSubmission": "not submitted"}
    (target / "metadata/audit.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    (target / "metadata/AndroidManifest.xml").write_text(manifest, encoding="utf-8")
    (target / "metadata/BundleConfig.json").write_text(config, encoding="utf-8")
    (target / "metadata/SHA256SUMS.txt").write_text(
        "".join(f'{x["sha256"]}  {x["file"]}\n' for x in entries), encoding="utf-8")
    shutil.copyfile(ROOT / "release/PLAY-CONSOLE.md", target / "notes/PLAY-CONSOLE-at-archive.md")
    (target / "notes/STATUS.md").write_text(
        "# Unsigned investigation candidate\n\nPackage: **attention.stats**; version 0.1.0 (1); ARM64 only. "
        "Not signed, not uploadable, not submitted and no testing period started. "
        "Static manifest, bundle and 16-KiB alignment checks do not establish runtime fidelity, "
        "full security or policy approval. See the captured Play document for blockers. "
        "Keep this archive immutable; use a new version/code for subsequent builds.\n", encoding="utf-8")
    print(json.dumps({"archived": True, "status": evidence["status"], "manifest": audit,
                      "artifacts": entries, "nativeLibraryCount": len(libs)}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "archive"])
    parser.add_argument("--dest", type=Path, default=DEFAULT_DEST)
    args = parser.parse_args()
    if args.action == "prepare":
        prepare(args.dest)
    else:
        archive(args.dest)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, ET.ParseError, zipfile.BadZipFile) as exc:
        print(str(exc) if isinstance(exc, (ValueError, RuntimeError)) else
              "Release preparation failed: " + type(exc).__name__, file=sys.stderr)
        sys.exit(1)
