#!/usr/bin/env python3
"""Run synthetic AS-003 JVM experiments with the existing verified Kotlin compiler."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

from test_analytics import compiler

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "native/collection-prototype"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    java = shutil.which("java")
    if not java:
        raise RuntimeError("An existing JDK 17 or newer is required")
    record = json.loads((ROOT / "docs/evidence/analytics-toolchain.json").read_text(encoding="utf-8"))
    home = compiler(record["kotlin"], args.offline)
    output = ROOT / ".cache/as-003/reconciliation"
    output.mkdir(parents=True, exist_ok=True)
    jar = output / "reconciliation-tests.jar"
    sources = sorted((MODULE / "src").rglob("*.kt"))
    if not sources:
        raise RuntimeError("No reconciliation sources found")
    subprocess.run([
        java, "-cp", str(home / "lib/*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-kotlin-home", str(home), "-Xjdk-release=17", "-Werror", "-no-reflect",
        "-include-runtime", "-d", str(jar), *map(str, sources),
    ], cwd=MODULE, check=True, timeout=180)
    subprocess.run([java, "-jar", str(jar)], cwd=MODULE, check=True, timeout=120)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError, zipfile.BadZipFile) as exc:
        print(f"Synthetic reconciliation failed: {exc}", file=sys.stderr)
        sys.exit(1)
