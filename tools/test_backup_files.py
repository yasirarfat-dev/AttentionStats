#!/usr/bin/env python3
"""Run synthetic pending-restore filesystem checks without Android or cloud access."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess

from test_analytics import compiler

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    java = shutil.which("java")
    if not java:
        raise RuntimeError("An existing JDK 17 or newer is required")
    record = json.loads((ROOT / "docs/evidence/analytics-toolchain.json").read_text(encoding="utf-8"))
    home = compiler(record["kotlin"], args.offline)
    output = ROOT / ".cache/backup-files"
    output.mkdir(parents=True, exist_ok=True)
    jar = output / "tests.jar"
    sources = [ROOT / "android/app/src/main/java/com/attentionstats/backup/PendingRestoreFiles.kt",
               ROOT / "android/app/src/hostTest/java/com/attentionstats/backup/PendingRestoreFilesTest.kt"]
    subprocess.run([
        java, "-cp", str(home / "lib/*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-kotlin-home", str(home), "-Xjdk-release=17", "-Werror", "-no-reflect",
        "-include-runtime", "-d", str(jar), *map(str, sources),
    ], cwd=ROOT, check=True, timeout=180)
    subprocess.run([java, "-jar", str(jar)], cwd=ROOT, check=True, timeout=120)


if __name__ == "__main__":
    main()
