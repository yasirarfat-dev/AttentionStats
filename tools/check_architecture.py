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


def check_detectors(source: Path, errors: list[str]) -> int:
    """Adapters consume values; only the registry knows individual platform implementations."""
    paths = sorted(source.rglob("*.kt"))
    for path in paths:
        label = path.relative_to(source).as_posix()
        value = path.read_text(encoding="utf-8")
        parts = path.relative_to(source).parts
        expected = "com.attentionstats.detectors" + ("." + ".".join(parts[:-1]) if len(parts) > 1 else "")
        if not re.search(rf"^package {re.escape(expected)}$", value, re.M):
            errors.append(f"{label}: detector package must match its path")
        platform = parts[1] if len(parts) > 2 and parts[0] == "social" else None
        # Inspect qualified references too; an adapter may not reach across a sibling boundary.
        for target in re.findall(r"\bcom\.attentionstats\.detectors\.social\.([\w*]+)", value):
            if target != platform and path.name != "DetectorRegistry.kt":
                errors.append(f"{label}: only registry may reference another social adapter")
        if platform is not None:
            for imported in re.findall(r"^import ([\w.*]+)$", value, re.M):
                if not imported.startswith(("kotlin.", "org.attentionstats.reels.", "com.attentionstats.detectors.")):
                    errors.append(f"{label}: infrastructure dependency in social adapter")
            if re.search(r"\b(?:android\.|androidx\.|org\.json\.|java\.|com\.facebook\.|com\.attentionstats\.(?:reels|storage|bridge)\.)", value):
                errors.append(f"{label}: social adapter must consume immutable evidence only")
            if re.search(r"\bcom\.attentionstats\.detectors\.(?:accessibility\b|DetectorRegistry\b)", value):
                errors.append(f"{label}: adapter cannot depend on acquisition or registry composition")
        if re.search(r"\bcom\.attentionstats\.(?:reels\.|storage\.|bridge\.|MainActivity\b)", value):
            errors.append(f"{label}: detectors cannot depend on counting infrastructure or app UI")
        if parts[0] != "accessibility" and re.search(r"\b(?:android\.|androidx\.|org\.json\.)", value):
            errors.append(f"{label}: Android acquisition belongs in detectors/accessibility")
    return len(paths)


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
    reels = ROOT / "native/reels-core/src/main/kotlin"
    reels_paths = sorted(reels.rglob("*.kt"))
    if not reels_paths:
        errors.append("No Reels domain source found")
    for path in reels_paths:
        source_text = path.read_text(encoding="utf-8")
        expected = ".".join(path.parent.relative_to(reels).parts)
        if not re.search(rf"^package {re.escape(expected)}$", source_text, re.M) or expected != "org.attentionstats.reels":
            errors.append(f"{path.relative_to(ROOT)}: invalid Reels domain package")
        for imported in re.findall(r"^import ([\w.*]+)$", source_text, re.M):
            if not imported.startswith(("kotlin.", "org.attentionstats.reels.")):
                errors.append(f"{path.relative_to(ROOT)}: infrastructure import in Reels domain")
        # A package identifier used as data (e.g. YouTube) is not a platform dependency.
        # Only plain identifier literals are skipped; interpolation and imports remain checked.
        code = re.sub(r'"[A-Za-z0-9_.]+"', '""', source_text)
        if re.search(r"\b(?:android\.|androidx\.|java\.(?:io|net|sql|security)\.|com\.facebook\.react\.|kotlinx\.|javax\.crypto\.)", code):
            errors.append(f"{path.relative_to(ROOT)}: side effect dependency in Reels domain")
    counter_source = ROOT / "android/app/src/main/java/com/attentionstats/reels"
    detector_source = ROOT / "android/app/src/main/java/com/attentionstats/detectors"
    detector_count = check_detectors(detector_source, errors)
    if not detector_count:
        errors.append("No isolated social detector adapters found")
    for path in [*counter_source.rglob("*.kt"), *detector_source.rglob("*.kt")]:
        source_text = path.read_text(encoding="utf-8")
        # Only this reviewed acquisition helper may inspect descriptions, behind its own gates.
        description_helper = detector_source / "accessibility/InterfaceLabelMatcher.kt"
        identity_helper = detector_source / "accessibility/TransientContentIdentity.kt"
        forbidden = "beforeText|getBeforeText" if path == identity_helper else "text|beforeText|getText|getBeforeText"
        if path not in [description_helper, identity_helper]:
            forbidden += "|contentDescription|getContentDescription"
        if re.search(rf"\b(?:node|root|source|event)\s*\??\.\s*(?:{forbidden})\b", source_text):
            errors.append(f"{path.relative_to(ROOT)}: counter must not read captured text/descriptions")
        if re.search(r"\b(?:performGlobalAction|performAction|dispatchGesture)\s*\(", source_text):
            errors.append(f"{path.relative_to(ROOT)}: counter must remain passive")
    app_build = (ROOT / "android/app/build.gradle").read_text(encoding="utf-8")
    if "collection-prototype" in app_build:
        errors.append("Application must not depend on the collection prototype")
    app_settings = (ROOT / "android/settings.gradle").read_text(encoding="utf-8")
    for fixture in ["storage-qualification", "archive-qualification"]:
        if fixture in app_build or fixture in app_settings:
            errors.append(f"{fixture} must remain outside the product Gradle build")
    for path in (ROOT / "android/app/src").rglob("*"):
        if path.suffix in {".kt", ".java", ".xml"}:
            for fixture in ["storage", "archive"]:
                if f"org.attentionstats.qualification.{fixture}" in path.read_text(encoding="utf-8"):
                    errors.append(f"{path.relative_to(ROOT)}: product source must not import the {fixture} fixture")
    mobile_count = check_mobile_source(ROOT / "src", errors)
    if errors:
        print("Architecture verification failed:")
        print("\n".join(f"  - {error}" for error in errors))
        return 1
    print(f"PASS: {len(paths)} Kotlin source files; package paths and declared domain import boundaries")
    print(f"PASS: {len(prototype_paths)} collection prototype source files; pure boundary and no app dependency")
    print(f"PASS: {len(reels_paths)} Reels domain source files; pure boundary and passive metadata-only source guard")
    print(f"PASS: {detector_count} detector files; registry, per-platform adapters and Android acquisition boundaries")
    print("PASS: standalone storage/archive qualification excluded from product settings/dependencies/source")
    print(f"PASS: {mobile_count} mobile TypeScript files; relative imports, feature public APIs and layer direction")
    print("Static checks do not establish native compilation, UI or runtime source support.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
