#!/usr/bin/env python3
"""Run the committed Android wrapper with task-local SDK and Gradle cache settings."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tasks", nargs="+", choices=[
        "assembleDebug", "assembleRelease", "bundleRelease", "lintDebug",
        ":app:assembleDebug", ":app:assembleRelease", ":app:assembleDebugAndroidTest", ":app:lintDebug",
        "testDebugUnitTest", ":analytics-core:verifyAnalytics", "generateCodegenArtifactsFromSchema",
        ":collection-prototype:verifyReconciliation",
        "help", "dependencies",
    ])
    local_sdk = ROOT / ".cache/as-004/sdk"
    default_sdk = local_sdk if local_sdk.is_dir() else os.environ.get("ANDROID_HOME", local_sdk)
    parser.add_argument("--sdk", type=Path, default=Path(default_sdk))
    parser.add_argument("--arch", default="arm64-v8a", choices=["arm64-v8a", "x86_64", "armeabi-v7a", "x86"])
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    if not (sdk / "platforms").is_dir():
        parser.error("Set --sdk or ANDROID_HOME to an SDK with the documented platform/NDK/tools")
    env = os.environ.copy()
    env.update(ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
               GRADLE_USER_HOME=str(ROOT / ".cache/gradle-user-home"))
    if "JAVA_HOME" not in env:
        java = shutil.which("java")
        if java:
            env["JAVA_HOME"] = str(Path(java).resolve().parents[1])
    if any(task in args.tasks for task in ["assembleDebug", ":app:assembleDebug", ":app:assembleDebugAndroidTest"]):
        debug_key = ROOT / ".cache/android-debug.keystore"
        if not debug_key.exists():
            debug_key.parent.mkdir(parents=True, exist_ok=True)
            keytool = Path(env["JAVA_HOME"]) / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")
            subprocess.run([
                str(keytool), "-genkeypair", "-keystore", str(debug_key),
                "-alias", "androiddebugkey", "-storepass", "android", "-keypass", "android",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                "-dname", "CN=Android Debug,O=Android,C=US",
            ], check=True, env=env)
    wrapper = ROOT / "android" / ("gradlew.bat" if os.name == "nt" else "gradlew")
    return subprocess.run([str(wrapper), *args.tasks, "--no-daemon", "--console=plain",
                           "--max-workers=4", "-Pandroid.builder.sdkDownload=false",
                           f"-PreactNativeArchitectures={args.arch}"],
                          cwd=ROOT / "android", env=env).returncode


if __name__ == "__main__":
    sys.exit(main())
