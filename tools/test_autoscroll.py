#!/usr/bin/env python3
"""Run actual auto-scroll completion decisions on the host using cached verified Kotlin.

No downloads, Android access, installation, provider content or gestures are used.
Bootstrap the existing reviewed compiler with tools/test_analytics.py if absent.
"""

from __future__ import annotations

import json
from pathlib import Path
import shutil
import subprocess
import sys

from test_analytics import compiler

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    java = shutil.which("java")
    if not java:
        raise RuntimeError("A local JDK 17 or newer is required")
    evidence = json.loads((ROOT / "docs/evidence/analytics-toolchain.json").read_text(encoding="utf-8"))
    home = compiler(evidence["kotlin"], offline=True)
    output = ROOT / ".cache" / "autoscroll-build"
    output.mkdir(parents=True, exist_ok=True)
    jar = output / "completion-tests.jar"
    source = ROOT / "android/app/src/main/java/com/attentionstats/autoscroll/domain/CompletionGate.kt"
    tests = ROOT / "android/app/src/hostTest/java/com/attentionstats/autoscroll/domain/CompletionGateTests.kt"
    subprocess.run([java, "-cp", str(home / "lib" / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-kotlin-home", str(home), "-Xjdk-release=17", "-Werror", "-no-reflect",
                    "-include-runtime", "-d", str(jar), str(source), str(tests)],
                   cwd=ROOT, check=True, timeout=180)
    subprocess.run([java, "-jar", str(jar)], cwd=ROOT, check=True, timeout=30)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as exc:
        print(f"Auto-scroll host verification failed: {exc}", file=sys.stderr)
        sys.exit(1)
