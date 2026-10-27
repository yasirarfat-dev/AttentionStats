#!/usr/bin/env python3
"""Check pure Kotlin domains and mobile TypeScript feature/public API boundaries."""

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


def check_mobile_source(source: Path, errors: list[str]) -> int:
    paths = sorted([*source.rglob("*.ts"), *source.rglob("*.tsx")])
    for path in paths:
        label = path.relative_to(source).as_posix()
        owner = path.relative_to(source).parts
        text = path.read_text(encoding="utf-8")
        imports = re.findall(
            r"(?:\bfrom\s*|\bimport\s*|\brequire\s*\(\s*|\bimport\s*\(\s*)['\"]([^'\"]+)['\"]",
            text,
        )
        for imported in imports:
            if not imported.startswith("."):
                if imported.startswith(("@/", "src/")):
                    errors.append(f"{label}: undeclared alias bypasses boundary checks")
                continue
            base = (path.parent / imported).resolve()
            if not base.is_relative_to(source.resolve()):
                errors.append(f"{label}: source import leaves src: {imported}")
                continue
            target = next((candidate for candidate in [
                base.with_suffix(".ts"), base.with_suffix(".tsx"),
                base / "index.ts", base / "index.tsx",
            ] if candidate.is_file()), None)
            if target is None:
                errors.append(f"{label}: unresolved source import {imported}")
                continue
            parts = target.relative_to(source.resolve()).parts
            if parts[0] == "features" and owner[:2] != parts[:2] and (
                len(parts) != 3 or parts[2] not in {"index.ts", "index.tsx"}
            ):
                errors.append(f"{label}: import feature through its public index: {imported}")
            if owner[0] == "ui" and parts[0] in {"app", "features", "platform"}:
                errors.append(f"{label}: shared UI cannot depend on {parts[0]}")
            if owner[0] == "platform" and parts[0] in {"app", "features", "ui"}:
                errors.append(f"{label}: platform cannot depend on {parts[0]}")
            if owner[0] == "features" and parts[0] == "app":
                errors.append(f"{label}: feature cannot depend on app composition")
    return len(paths)


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
    prototype = ROOT / "native/collection-prototype/src/main/kotlin"
    prototype_paths = sorted(prototype.rglob("*.kt"))
    if not prototype_paths:
        errors.append("No collection prototype source found")
    for path in prototype_paths:
        text = path.read_text(encoding="utf-8")
        label = path.relative_to(ROOT).as_posix()
        expected = ".".join(path.parent.relative_to(prototype).parts)
        declaration = re.search(r"^package ([\w.]+)$", text, re.M)
        if declaration is None or declaration[1] != expected or expected != "org.attentionstats.collection.reconciliation":
            errors.append(f"{label}: invalid collection prototype package boundary")
        for imported in re.findall(r"^import ([\w.*]+)$", text, re.M):
            if not imported.startswith(("kotlin.", "org.attentionstats.collection.reconciliation.")):
                errors.append(f"{label}: non-domain prototype dependency {imported}")
        if re.search(r"\b(?:android\.|androidx\.|java\.(?:io|net|sql)\.|com\.facebook\.|kotlinx\.coroutines\.)", text):
            errors.append(f"{label}: platform/storage/side-effect dependency in prototype")
    # The experimental JVM module is deliberately absent from application dependencies.
    app_build = (ROOT / "android/app/build.gradle").read_text(encoding="utf-8")
    if "collection-prototype" in app_build:
        errors.append("Application must not depend on the collection prototype")
    app_settings = (ROOT / "android/settings.gradle").read_text(encoding="utf-8")
    if "storage-qualification" in app_build or "storage-qualification" in app_settings:
        errors.append("Storage qualification must remain outside the product Gradle build")
    for path in (ROOT / "android/app/src").rglob("*"):
        if path.suffix in {".kt", ".java", ".xml"} and "org.attentionstats.qualification.storage" in path.read_text(encoding="utf-8"):
            errors.append(f"{path.relative_to(ROOT)}: product source must not import the storage fixture")
    mobile_count = check_mobile_source(ROOT / "src", errors)
    if errors:
        print("Architecture verification failed:")
        print("\n".join(f"  - {error}" for error in errors))
        return 1
    print(f"PASS: {len(paths)} Kotlin source files; package paths and declared domain import boundaries")
    print(f"PASS: {len(prototype_paths)} collection prototype source files; pure boundary and no app dependency")
    print("PASS: standalone storage qualification excluded from product settings/dependencies/source")
    print(f"PASS: {mobile_count} mobile TypeScript files; relative imports, feature public APIs and layer direction")
    print("Static checks do not establish native compilation, UI or runtime source support.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
