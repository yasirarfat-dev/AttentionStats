#!/usr/bin/env python3
"""Audit a locally upload-signed candidate. Does not approve, install or upload it."""

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

import release_candidate as release


def certificate_digest(pem):
    certificates = re.findall(r"-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+)-----END CERTIFICATE-----", pem)
    if len(certificates) != 1:
        raise ValueError("Expected one upload certificate")
    return hashlib.sha256(base64.b64decode(re.sub(r"\s", "", certificates[0]), validate=True)).hexdigest()


def verify_jar(path):
    result = subprocess.run(["jarsigner", "-J-Duser.language=en", "-verify", "-strict", str(path)],
                            capture_output=True, text=True, timeout=180)
    # A normal Android upload certificate is self-signed (strict warning bit 4).
    # Reject unsigned entries (16), invalid signatures (1) and all other warning bits.
    if result.returncode not in (0, 4) or "jar verified" not in result.stdout.lower():
        raise ValueError("AAB signature verification failed or entries are unsigned")


def audit(certificate, version_code):
    if not release.BUNDLETOOL.is_file() or release.digest(release.BUNDLETOOL) != release.BUNDLETOOL_SHA:
        raise ValueError("Reviewed bundletool is missing or changed")
    apk = release.ROOT / "android/app/build/outputs/apk/release/app-release.apk"
    aab = release.ROOT / "android/app/build/outputs/bundle/release/app-release.aab"
    fingerprint = certificate_digest(certificate.read_text(encoding="ascii"))
    verify_jar(aab)
    signed_pem = release.call("keytool", "-printcert", "-jarfile", aab, "-rfc")
    if certificate_digest(signed_pem) != fingerprint:
        raise ValueError("AAB upload certificate differs")
    sdk = release.ROOT / ".cache/as-004/sdk/build-tools/37.0.0"
    signatures = release.call(sdk / "apksigner.bat", "verify", "--verbose", "--print-certs", apk)
    apk_fingerprints = re.findall(r"(?m)^(?:Signer #\d+|V[1-4](?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-f]{64})$", signatures)
    if "Number of signers: 1" not in signatures or not apk_fingerprints or set(apk_fingerprints) != {fingerprint}:
        raise ValueError("APK upload certificate differs or has additional signers")
    tool = ["java", "-jar", release.BUNDLETOOL]
    release.call(*tool, "validate", "--bundle=" + str(aab))
    manifest = release.call(*tool, "dump", "manifest", "--bundle=" + str(aab), "--module=base")
    metadata = release.audit_manifest(manifest, version_code=version_code)
    config = release.call(*tool, "dump", "config", "--bundle=" + str(aab))
    if "PAGE_ALIGNMENT_16K" not in config:
        raise ValueError("AAB is missing 16-KiB page alignment")
    release.call(sdk / "zipalign.exe", "-c", "-P", "16", "4", apk)
    with zipfile.ZipFile(apk) as packaged, zipfile.ZipFile(aab) as bundle:
        if packaged.read("assets/index.android.bundle") != bundle.read("base/assets/index.android.bundle"):
            raise ValueError("APK/AAB bundled JavaScript differs")
        libraries = release.native_alignment(bundle, "base/lib/")
        apk_libraries = release.native_alignment(packaged, "lib/")
        if {x["name"].removeprefix("base/"): x["sha256"] for x in libraries} != {x["name"]: x["sha256"] for x in apk_libraries}:
            raise ValueError("APK/AAB native libraries differ")
        if not libraries or any(not x["name"].startswith("base/lib/arm64-v8a/") for x in libraries):
            raise ValueError("Unexpected candidate ABI scope")
    return {"recordedAtUtc": datetime.now(timezone.utc).isoformat(), "status": "upload-signed-awaiting-acceptance",
            "manifest": metadata, "uploadCertificateSha256": fingerprint, "bundleValidated": True,
            "signatureIntegrityVerified": True, "apkAndBundlePayloadsMatch": True,
            "static16KiBAlignmentPassed": True, "nativeLibraries": libraries,
            "sourceHead": release.call("git", "rev-parse", "HEAD").strip(),
            "sourceDirty": bool(release.call("git", "status", "--porcelain").strip()),
            "artifacts": [{"file": path.name, "bytes": path.stat().st_size, "sha256": release.digest(path)} for path in (apk, aab)],
            "runtimeAcceptance": "not established by this static audit", "playUploaded": False}, (apk, aab)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--certificate", type=Path, required=True)
    parser.add_argument("--version-code", type=int, default=2)
    parser.add_argument("--archive", type=Path, help="New external version directory; never overwritten")
    args = parser.parse_args()
    if args.archive:
        destination = args.archive.resolve()
        if destination == release.ROOT or release.ROOT in destination.parents or destination.exists():
            raise ValueError("Archive must be a new directory outside the repository")
    evidence, artifacts = audit(args.certificate, args.version_code)
    output = release.ROOT / ".cache/release-tools/signed-audit.json"
    output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    if args.archive:
        args.archive.mkdir(parents=True, exist_ok=False)
        for path in artifacts:
            shutil.copyfile(path, args.archive / path.name)
        shutil.copyfile(output, args.archive / "audit.json")
        (args.archive / "STATUS.md").write_text(
            "# Signed candidate awaiting acceptance\n\n"
            "Local upload-key signatures and static artifact checks pass. This is not an accepted MVP "
            "or evidence of Play approval/upload. Source/runtime/coverage, recovery backup, listing assets "
            "and Console declarations must pass before submission. No private signing credentials are in this directory.\n",
            encoding="utf-8")
    print(json.dumps({key: value for key, value in evidence.items() if key != "nativeLibraries"}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        raise SystemExit("Signed-candidate audit stopped: " + (str(error) if isinstance(error, ValueError) else type(error).__name__))
