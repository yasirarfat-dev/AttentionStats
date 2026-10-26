#!/usr/bin/env python3
"""Check current pure Kotlin package boundaries; not a future app build/lint claim."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "native/analytics-core/src/main/kotlin"
PREFIX = "org.attentionstats.analytics"
DEPENDENCIES = {
    "time": set(),
    "activity": {"time"},
    "observation": {"time"},
    "network": {"time"},
    "summary": {"time"},
}


def main() -> int:
    errors: list[str] = []
    paths = sorted(SOURCE.rglob("*.kt"))
    if not paths:
        errors.append("No analytics domain source found")
    for path in paths:
        text = path.read_text(encoding="utf-8")
        label = path.relative_to(ROOT).as_posix()
        expected = ".".join(path.parent.relative_to(SOURCE).parts)
        declaration = re.search(r"^package ([\w.]+)$", text, re.M)
        if declaration is None or declaration[1] != expected:
            errors.append(f"{label}: package must match its source path")
            continue
        domain = expected.removeprefix(PREFIX + ".")
        if domain not in DEPENDENCIES:
            errors.append(f"{label}: undeclared domain boundary {domain}")
            continue
        for imported in re.findall(r"^import ([\w.*]+)$", text, re.M):
            if imported.startswith(PREFIX + "."):
                target = imported.removeprefix(PREFIX + ".").split(".")[0]
                if target != domain and target not in DEPENDENCIES[domain]:
                    errors.append(f"{label}: forbidden domain dependency {domain} -> {target}")
            elif not imported.startswith(("java.time.", "kotlin.")):
                errors.append(f"{label}: non-domain dependency {imported}")
        # Reject qualified bypasses as well as imports for common infrastructure.
        if re.search(r"\b(?:android\.|androidx\.|java\.(?:io|net|sql)\.|com\.facebook\.|kotlinx\.coroutines\.)", text):
            errors.append(f"{label}: platform/storage/side-effect dependency in pure calculations")
    if errors:
        print("Architecture verification failed:")
        print("\n".join(f"  - {error}" for error in errors))
        return 1
    print(f"PASS: {len(paths)} Kotlin source files; package paths and declared domain import boundaries")
    print("No TypeScript/mobile UI/Gradle module checks exist yet; those remain AS-004 work.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
