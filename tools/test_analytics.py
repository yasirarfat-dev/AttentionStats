#!/usr/bin/env python3
"""Compile/test the pure Kotlin mobile-domain slice with isolated, verified tools.

Only first-time development tool bootstrap uses the network. The compiled core
and synthetic test program have no network, Android, database or capture code.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "native" / "analytics-core"


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def compiler(record: dict, offline: bool) -> Path:
    cache = ROOT / ".cache" / "analytics-tools" / f"kotlin-{record['version']}"
    archive = cache / "compiler.zip"
    cache.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        if offline:
            raise RuntimeError("Compiler cache absent. Run once without --offline to bootstrap.")
        print(f"Downloading Kotlin {record['version']} into the repository cache...", flush=True)
        partial = cache / "compiler.zip.part"
        request = urllib.request.Request(record["url"], headers={"User-Agent": "AttentionStats-development"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response, partial.open("wb") as output:
                total = 0
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > record["size"]:
                        raise RuntimeError("Compiler download exceeds the reviewed size")
                    output.write(chunk)
            if partial.stat().st_size != record["size"] or digest(partial) != record["sha256"]:
                raise RuntimeError("Compiler download failed size/SHA-256 validation")
            partial.replace(archive)
        finally:
            partial.unlink(missing_ok=True)
    if digest(archive) != record["sha256"]:
        raise RuntimeError("Compiler cache failed SHA-256 validation; inspect/remove that exact cache file")
    # Re-extract from the verified archive, avoiding trust in executable cache state.
    with zipfile.ZipFile(archive) as bundle:
        if sum(info.file_size for info in bundle.infolist()) > 500_000_000:
            raise RuntimeError("Compiler archive exceeds the extraction limit")
        for info in bundle.infolist():
            destination = (cache / info.filename).resolve()
            if not destination.is_relative_to(cache.resolve()):
                raise RuntimeError("Compiler archive path escapes its cache")
        bundle.extractall(cache)
    return cache / "kotlinc"


def run_on_device(java: str, sdk: Path, jar: Path, output: Path, record: dict, offline: bool) -> None:
    adb = sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    platforms = [p for p in (sdk / "platforms").glob("android-*")
                 if re.fullmatch(r"android-\d+(?:\.\d+)?", p.name) and (p / "android.jar").is_file()]
    if not adb.is_file() or not platforms:
        raise RuntimeError("--device requires an existing SDK with adb and platform android.jar")
    platform = max(platforms, key=lambda p: tuple(map(int, p.name.removeprefix("android-").split("."))))
    listing = subprocess.run([str(adb), "devices"], capture_output=True, text=True, check=True, timeout=30)
    targets = [row.split()[0] for row in listing.stdout.splitlines()[1:] if row.strip().endswith("\tdevice")]
    if len(targets) != 1:
        raise RuntimeError(f"--device needs exactly one authorized target, found {len(targets)}; no target chosen")
    serial = targets[0]  # Never print/persist this value or include it in exception commands.

    def device(*args: str) -> str:
        result = subprocess.run([str(adb), "-s", serial, *args], capture_output=True, text=True, timeout=120)
        if result.returncode:
            raise RuntimeError("Synthetic Android command failed: " + result.stderr.replace(serial, "[target]")[:1000])
        return result.stdout

    api = int(device("shell", "getprop", "ro.build.version.sdk").strip())
    if api < 26:
        raise RuntimeError("Synthetic device suite requires Android API 26 or newer")
    r8 = ROOT / ".cache" / "analytics-tools" / f"r8-{record['version']}" / "r8.jar"
    r8.parent.mkdir(parents=True, exist_ok=True)
    if not r8.exists():
        if offline:
            raise RuntimeError("R8 tool absent. Run once without --offline to bootstrap.")
        partial = r8.with_suffix(".part")
        try:
            with urllib.request.urlopen(record["url"], timeout=60) as response, partial.open("wb") as stream:
                total = 0
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > record["size"]:
                        raise RuntimeError("R8 download exceeds reviewed size")
                    stream.write(chunk)
            if partial.stat().st_size != record["size"] or digest(partial) != record["sha256"]:
                raise RuntimeError("R8 download size/SHA-256 validation failed")
            partial.replace(r8)
        finally:
            partial.unlink(missing_ok=True)
    if digest(r8) != record["sha256"]:
        raise RuntimeError("R8 tool cache failed SHA-256 validation")
    dex_jar = output / "analytics-device-tests.jar"
    dex = subprocess.run([java, "-cp", str(r8), "com.android.tools.r8.D8",
                          "--release", "--min-api", "26", "--lib", str(platform / "android.jar"),
                          "--output", str(dex_jar), str(jar)], capture_output=True, text=True, timeout=180)
    diagnostics = dex.stdout + dex.stderr
    (output / "d8-diagnostics.txt").write_text(diagnostics, encoding="utf-8")
    if dex.returncode or re.search(r"\b(?:Warning|Error):", diagnostics):
        raise RuntimeError("D8 failed or emitted warnings; inspect .cache/analytics-build/d8-diagnostics.txt\n" + diagnostics[:1200])
    remote = f"/data/local/tmp/attentionstats-analytics-{uuid.uuid4().hex}.jar"
    try:
        device("push", str(dex_jar), remote)
        if device("shell", "sha256sum", remote).split()[0] != digest(dex_jar):
            raise RuntimeError("Synthetic bytecode transfer hash mismatch")
        print(f"Android ART synthetic execution: API {api}; isolated D8 {record['version']}; no D8 warnings", flush=True)
        print(device("shell", "dalvikvm", "-cp", remote, "org.attentionstats.analytics.AnalyticsTestsKt"), end="")
    finally:
        device("shell", "rm", "-f", remote)  # Exact task-owned file only; no app install/grant/reset.


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="Require an already downloaded tool archive")
    parser.add_argument("--example", action="store_true", help="Print the synthetic diary after running tests")
    parser.add_argument("--device", action="store_true", help="Also run synthetic bytecode on one authorized Android target")
    parser.add_argument("--sdk", type=Path, help="Existing SDK for --device; no SDK packages are installed")
    args = parser.parse_args()
    java = shutil.which("java")
    if not java:
        raise RuntimeError("A local JDK 17 or newer is required; no global installation is performed")
    evidence = json.loads((ROOT / "docs/evidence/analytics-toolchain.json").read_text(encoding="utf-8"))
    home = compiler(evidence["kotlin"], args.offline)
    output = ROOT / ".cache" / "analytics-build"
    output.mkdir(parents=True, exist_ok=True)
    jar = output / "analytics-tests.jar"
    sources = sorted((MODULE / "src").rglob("*.kt"))
    if not sources:
        raise RuntimeError("No Kotlin sources found")
    command = [java, "-cp", str(home / "lib" / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
               "-kotlin-home", str(home), "-Xjdk-release=17", "-Werror", "-no-reflect",
               "-include-runtime", "-d", str(jar), *map(str, sources)]
    environment = os.environ.copy()
    # JVM options are inherited; no user/global tool configuration is rewritten.
    subprocess.run(command, cwd=MODULE, env=environment, check=True, timeout=180)
    subprocess.run([java, "-jar", str(jar), *(["--example"] if args.example else [])],
                   cwd=MODULE, env=environment, check=True, timeout=120)
    if args.device:
        sdk = args.sdk or Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
        run_on_device(java, sdk, jar, output, evidence["r8"], args.offline)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError, zipfile.BadZipFile) as exc:
        print(f"Analytics verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
