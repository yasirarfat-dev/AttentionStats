#!/usr/bin/env python3
"""Audit the mobile manifest, lockfile and installed graph against dated latest evidence."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def review(root: Path, installed: bool = False) -> list[str]:
    errors: list[str] = []
    manifest = json.loads((root / "package.json").read_text(encoding="utf-8"))
    evidence = json.loads((root / "docs/evidence/mobile-scaffold.json").read_text(encoding="utf-8"))
    records = {item["package"]: item for item in evidence["packages"]}
    expected_names = {"pnpm"}
    lock = (root / "pnpm-lock.yaml").read_text(encoding="utf-8")
    # pnpm 12 writes a boot/manager document followed by the project graph.
    project_lock = re.split(r"(?m)^---\s*$", lock)[-1]
    importer = project_lock.split("importers:\n", 1)[1].split("\npackages:", 1)[0]
    for group in ["dependencies", "devDependencies"]:
        for name, version in manifest[group].items():
            expected_names.add(name)
            record = records.get(name, {})
            if not re.fullmatch(r"\d+\.\d+\.\d+", version):
                errors.append(f"{name}: direct dependencies require exact stable pins")
            if (record.get("version"), record.get("selector"), record.get("group")) != (
                version, name + "@latest", group,
            ):
                errors.append(f"{name}: manifest/review mismatch")
            if not record.get("license") or not str(record.get("integrity", "")).startswith("sha512-"):
                errors.append(f"{name}: missing reviewed license/integrity")
            if record.get("source") != "https://registry.npmjs.org/" + name + "/latest" or record.get("deprecated"):
                errors.append(f"{name}: unexpected source or deprecated direct package")
            pattern = (rf"(?m)^      ['\"]?{re.escape(name)}['\"]?:\n"
                       rf"        specifier: {re.escape(version)}\n"
                       rf"        version: {re.escape(version)}(?:\(|\n)")
            if not re.search(pattern, importer):
                errors.append(f"{name}: lockfile importer does not match manifest pin")
            if installed:
                path = root / "node_modules" / name / "package.json"
                if not path.is_file() or json.loads(path.read_text(encoding="utf-8"))["version"] != version:
                    errors.append(f"{name}: installed version mismatch or missing package")
    if set(records) != expected_names or len(records) != len(evidence["packages"]):
        errors.append("Review must contain exactly the direct packages and package manager, without duplicates")
    if manifest["packageManager"] != "pnpm@" + records["pnpm"]["version"]:
        errors.append("Package manager/review mismatch")
    policy = (root / "pnpm-workspace.yaml").read_text(encoding="utf-8")
    for setting in ["minimumReleaseAge: 0", "strictPeerDependencies: true", "autoInstallPeers: false", "ignoreScripts: true"]:
        if setting not in policy:
            errors.append(f"Missing reviewed install policy: {setting}")
    patches = evidence.get("patches", [])
    if len(patches) != 1 or patches[0]["package"] != "@react-native/gradle-plugin@0.88.0":
        errors.append("The native compiler patch requires an exact reviewed record")
    for patch in patches:
        path = (root / patch["path"]).resolve()
        if not path.is_relative_to((root / "patches").resolve()):
            errors.append("Patch leaves reviewed patch directory")
        elif not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != patch["sha256"]:
            errors.append(f"Patch review hash mismatch: {patch['path']}")
        if patch["path"] not in policy:
            errors.append(f"Patch is not registered in workspace policy: {patch['path']}")
    gradle = evidence["native"]["gradle"]
    wrapper = root / "android/gradle/wrapper/gradle-wrapper.jar"
    if hashlib.sha256(wrapper.read_bytes()).hexdigest() != gradle["wrapperChecksum"]:
        errors.append("Gradle wrapper JAR differs from reviewed official checksum")
    properties = (root / "android/gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    if (f"gradle-{gradle['version']}-bin.zip" not in properties or
            f"distributionSha256Sum={gradle['checksum']}" not in properties):
        errors.append("Gradle distribution/version checksum differs from review")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installed", action="store_true")
    args = parser.parse_args()
    try:
        errors = review(ROOT, args.installed)
    except (OSError, ValueError, KeyError, IndexError) as exc:
        errors = [str(exc)]
    if errors:
        print("Mobile dependency verification failed:\n" + "\n".join("  - " + error for error in errors))
        return 1
    print("PASS: mobile direct pins, lockfile importer, dated latest/license/integrity evidence and local install policy")
    print("This is a dated review, not a live registry check or a native/runtime qualification.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
