#!/usr/bin/env python3
"""Provision a local Play upload key. Never upload, print secrets or overwrite a key."""

import argparse
import base64
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DEST = Path(os.environ.get("ATTENTION_RELEASE_ROOT", Path.home() / "AppReleases" / "AttentionStats")) / "private" / "signing"


def outside_repository(path):
    path = Path(path).resolve()
    if path == ROOT or ROOT in path.parents:
        raise ValueError("Signing material must stay outside the source repository")
    return path


def dpapi(value, decrypt=False):
    """Windows current-user protection; recovery still requires an independent backup."""
    if os.name != "nt":
        raise RuntimeError("Local credential protection requires Windows")

    class Blob(ctypes.Structure):
        _fields_ = [("size", wintypes.DWORD), ("data", ctypes.POINTER(ctypes.c_ubyte))]

    buffer = ctypes.create_string_buffer(value)
    source = Blob(len(value), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    output = Blob()
    operation = (ctypes.windll.crypt32.CryptUnprotectData if decrypt
                 else ctypes.windll.crypt32.CryptProtectData)
    operation.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                         ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    operation.restype = wintypes.BOOL
    if not operation(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(output)):
        raise RuntimeError("Current-user credential protection failed")
    try:
        return ctypes.string_at(output.data, output.size)
    finally:
        ctypes.windll.kernel32.LocalFree.argtypes = [ctypes.c_void_p]
        ctypes.windll.kernel32.LocalFree(ctypes.cast(output.data, ctypes.c_void_p))
        ctypes.memset(buffer, 0, len(buffer))


def run(args, env=None):
    result = subprocess.run([str(a) for a in args], env=env, capture_output=True,
                            text=True, timeout=90)
    if result.returncode:
        raise RuntimeError("Signing operation failed; credentials and tool output withheld")
    return result.stdout


def signing_environment(config):
    path = outside_repository(config)
    value = json.loads(path.read_text(encoding="utf-8"))
    if value.get("version") != 1 or value.get("protection") != "windows-current-user-dpapi":
        raise ValueError("Unsupported signing configuration")
    keystore = outside_repository(value["keystore"])
    if not keystore.is_file() or value.get("alias") != "attention-stats-upload":
        raise ValueError("Upload key is missing or has an unexpected alias")
    password = dpapi(base64.b64decode(value["password"], validate=True), decrypt=True).decode("ascii")
    return {"ATTENTION_UPLOAD_KEYSTORE": str(keystore), "ATTENTION_UPLOAD_ALIAS": value["alias"],
            "ATTENTION_UPLOAD_STORE_PASSWORD": password, "ATTENTION_UPLOAD_KEY_PASSWORD": password}


def provision(destination):
    destination = outside_repository(destination)
    keytool = shutil.which("keytool")
    if os.name != "nt" or not keytool:
        raise RuntimeError("Windows and the installed JDK keytool are required")
    destination.mkdir(parents=True, exist_ok=True)
    # This task-owned directory holds only this app's signing files.
    sid_output = run(["whoami", "/user", "/fo", "csv", "/nh"])
    import re
    sids = re.findall(r"S-1-5-[0-9-]+", sid_output)
    if len(sids) != 1:
        raise RuntimeError("Could not establish the current signing-directory owner")
    run(["icacls", destination, "/inheritance:r", "/grant:r", f"*{sids[0]}:(OI)(CI)F",
         "*S-1-5-18:(OI)(CI)F"])
    key = destination / "attention-stats-upload.p12"
    config = destination / "upload-signing.json"
    certificate = destination / "upload-certificate.pem"
    if any(p.exists() for p in (key, config, certificate)):
        raise ValueError("Existing signing material preserved; use its configuration")
    password = secrets.token_urlsafe(48)
    protected = dpapi(password.encode("ascii"))
    environment = os.environ.copy()
    environment["ATTENTION_PROVISION_PASSWORD"] = password
    # Persist protected recovery metadata before generating the key. Never overwrite it.
    with config.open("x", encoding="utf-8") as target:
        json.dump({"version": 1, "protection": "windows-current-user-dpapi", "keystore": str(key),
                   "alias": "attention-stats-upload", "password": base64.b64encode(protected).decode("ascii")}, target, indent=2)
        target.write("\n")
    run([keytool, "-genkeypair", "-keystore", key, "-storetype", "PKCS12",
         "-alias", "attention-stats-upload", "-storepass:env", "ATTENTION_PROVISION_PASSWORD",
         "-keypass:env", "ATTENTION_PROVISION_PASSWORD", "-keyalg", "RSA", "-keysize", "4096",
         "-sigalg", "SHA256withRSA", "-validity", "10000", "-dname", "CN=Attention Stats Upload", "-noprompt"], environment)
    run([keytool, "-exportcert", "-rfc", "-keystore", key, "-alias", "attention-stats-upload",
         "-storepass:env", "ATTENTION_PROVISION_PASSWORD", "-file", certificate], environment)
    public = certificate.read_text(encoding="ascii")
    der = base64.b64decode("".join(line for line in public.splitlines() if not line.startswith("---")))
    fingerprint = hashlib.sha256(der).hexdigest()
    (destination / "RECOVERY.md").write_text(
        "# Attention Stats upload-key recovery\n\n"
        "Google Play should generate and retain the app-signing key. This separate RSA-4096 upload key "
        "authenticates bundles sent to Play. No Console enrollment or upload has occurred.\n\n"
        "The PKCS12 file is password protected. upload-signing.json protects its random password with "
        "Windows current-user DPAPI. Both require this Windows profile; copying these files alone to "
        "another machine is not a complete recovery backup. Store an independent encrypted copy of the "
        "PKCS12 and its password in a maintainer-controlled password manager/offline backup. "
        "No off-machine backup is claimed. Never send the private key or password to GitHub or chat.\n\n"
        "To retrieve the password locally for your password manager, run Python from the source root, "
        "import tools.release_signing and call signing_environment with the full path to upload-signing.json. "
        "The returned ATTENTION_UPLOAD_STORE_PASSWORD is sensitive: do not print it into shared logs. "
        "Use your password manager's local import workflow. The PEM is the public upload certificate.\n\n"
        f"Upload certificate SHA-256: {fingerprint}\n", encoding="utf-8")
    # Readback verifies the encrypted password can actually open the generated keystore.
    check_env = os.environ.copy()
    check_env.update(signing_environment(config))
    run([keytool, "-list", "-keystore", key, "-alias", "attention-stats-upload",
         "-storepass:env", "ATTENTION_UPLOAD_STORE_PASSWORD"], check_env)
    return {"created": True, "configuration": str(config), "certificateSha256": fingerprint,
            "privateKeyUploaded": False, "independentBackupVerified": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["provision"])
    parser.add_argument("--dest", type=Path, default=DEFAULT_DEST)
    args = parser.parse_args()
    try:
        print(json.dumps(provision(args.dest), indent=2))
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError):
        print("Upload-key provisioning stopped; existing files preserved and secrets withheld", file=sys.stderr)
        sys.exit(1)
