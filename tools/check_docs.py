#!/usr/bin/env python3
"""Offline structural and public-artifact checks; no Android behavior is tested."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

from probe_mobile_stack import review as review_mobile_stack
from check_mobile import review as review_mobile_scaffold

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
REQUIRED = (
    "INDEX VISION PRODUCT CAPABILITY-MATRIX ANALYTICS-SPEC METRIC-CATALOG TRACKING-SPEC SOCIAL-MEDIA-SPEC "
    "BEHAVIOR-INTELLIGENCE PROTECTION-SPEC LIVE-OVERLAY-SPEC UX-SPEC HUMAN-FACTORS NAVIGATION DESIGN-SYSTEM "
    "SETTINGS-SPEC ARCHITECTURE FILE-STRUCTURE NATIVE-CONTRACTS DATA-MODELS "
    "STORAGE-AND-RETENTION PRIVACY SECURITY CRYPTO-ARCHIVE-DESIGN PERMISSIONS BACKUP-RESTORE "
    "CONVENTIONS DEPENDENCIES TESTING DEVELOPMENT RELEASE CONTRIBUTING "
    "IOS-CONTRIBUTION-GUIDE AGENT-RULES ROADMAP FEATURE-TRACKER STATE QUEUE DECISIONS"
).split()
METADATA = ("Status", "Responsibility", "Related specifications", "Unresolved feasibility")
ERRORS: list[str] = []


def fail(path: Path, message: str) -> None:
    ERRORS.append(f"{path.relative_to(ROOT).as_posix()}: {message}")


def public_files() -> list[Path]:
    try:
        result = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
            cwd=ROOT, capture_output=True, check=True,
        )
        return sorted({ROOT / p.decode("utf-8") for p in result.stdout.split(b"\0") if p})
    except (FileNotFoundError, subprocess.CalledProcessError):
        return sorted(
            p for p in ROOT.rglob("*")
            if p.is_file() and not set(p.relative_to(ROOT).parts)
            & {".git", "node_modules", "__pycache__", ".pnpm-store"}
        )


def without_code(text: str) -> str:
    return re.sub(r"^```[^\n]*\n.*?^```\s*$", "", text, flags=re.M | re.S)


def anchors(text: str) -> set[str]:
    found: set[str] = set()
    occurrences: dict[str, int] = {}
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", without_code(text), re.M):
        slug = re.sub(r"[^\w\- ]", "", heading.lower()).replace(" ", "-")
        occurrence = occurrences.get(slug, 0)
        occurrences[slug] = occurrence + 1
        found.add(f"{slug}-{occurrence}" if occurrence else slug)
    return found


def references(text: str, prefix: str) -> set[str]:
    found: set[str] = set()
    pattern = rf"\b({prefix})-(\d{{2,3}})(?:[–-](?:({prefix})-)?(\d{{2,3}}))?\b"
    for first_prefix, start, end_prefix, end in re.findall(pattern, text):
        first = int(start)
        last = int(end) if end and (not end_prefix or end_prefix == first_prefix) else first
        if last < first or last - first > 200:
            continue
        found.update(f"{first_prefix}-{n:0{len(start)}d}" for n in range(first, last + 1))
    return found


def main() -> int:
    files = public_files()
    markdown: dict[Path, str] = {}
    for path in files:
        if not path.exists():
            fail(path, "listed but missing from working tree")
            continue
        if path.suffix.lower() == ".md":
            try:
                markdown[path] = path.read_text(encoding="utf-8")
            except UnicodeError:
                fail(path, "must be UTF-8")
    for name in REQUIRED:
        path = DOCS / f"{name}.md"
        if path not in markdown:
            fail(path, "required authoritative document missing")
    for name in ("README.md", "LICENSE", "AGENTS.md", "CLAUDE.md", "CONTRIBUTING.md", "SECURITY.md"):
        if not (ROOT / name).is_file():
            fail(ROOT / name, "required root entrypoint missing")
    index = markdown.get(DOCS / "INDEX.md", "")
    for path, content in markdown.items():
        if DOCS in path.parents:
            for field in METADATA:
                if f"**{field}:**" not in content:
                    fail(path, f"missing {field} metadata")
            if path.parent == DOCS and f"]({path.name})" not in index:
                fail(path, "not linked from docs/INDEX.md")
        if content.count("```") % 2:
            fail(path, "unbalanced fenced code blocks")
        for number, line in enumerate(content.splitlines(), 1):
            if line.rstrip() != line:
                fail(path, f"trailing whitespace at line {number}")
        for target in re.findall(r"(?<!!)\[[^\]\n]+\]\(([^)\n]+)\)", without_code(content)):
            target = target.strip().strip("<>")
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc:
                continue
            resolved = (path.parent / unquote(parsed.path)).resolve() if parsed.path else path
            if not resolved.is_relative_to(ROOT):
                fail(path, f"link escapes repository: {target}")
            elif not resolved.exists():
                fail(path, f"broken local link: {target}")
            elif parsed.fragment and resolved.suffix.lower() == ".md":
                if unquote(parsed.fragment) not in anchors(resolved.read_text(encoding="utf-8")):
                    fail(path, f"broken heading anchor: {target}")

    matrix_path = DOCS / "CAPABILITY-MATRIX.md"
    tracker_path = DOCS / "FEATURE-TRACKER.md"
    testing_path = DOCS / "TESTING.md"
    matrix = markdown.get(matrix_path, "")
    tracker = markdown.get(tracker_path, "")
    testing = markdown.get(testing_path, "")
    capabilities = re.findall(r"^\| (C-\d{2})\b", matrix, re.M)
    if len(capabilities) != len(set(capabilities)):
        fail(matrix_path, "duplicate capability ID")
    expected_caps = {f"C-{n:02d}" for n in range(1, 32)}
    if not expected_caps.issubset(capabilities):
        fail(matrix_path, "foundation capability C-01 through C-31 missing")
    tracked_caps = references(tracker, "C")
    for capability in capabilities:
        if capability not in tracked_caps:
            fail(tracker_path, f"no task maps capability {capability}")
    for row in re.findall(r"^\| C-\d{2}\b.*$", matrix, re.M):
        cells = row.split("|")[1:-1]
        if len(cells) != 6 or any(not cell.strip() for cell in cells):
            fail(matrix_path, f"capability row requires six populated columns: {row[:35]}")
        if not re.search(r"/ (planned|experimental|blocked|implemented)\b", row):
            fail(matrix_path, f"capability missing explicit status: {row[:35]}")
        if not references(row, "T|P|D|B|S|X"):
            fail(matrix_path, f"capability missing acceptance scenario: {row[:35]}")

    catalog_path = DOCS / "METRIC-CATALOG.md"
    catalog = markdown.get(catalog_path, "")
    metrics = re.findall(r"^\| (M-\d{3})\b", catalog, re.M)
    if len(metrics) != len(set(metrics)) or not {f"M-{n:03d}" for n in range(1, 65)}.issubset(metrics):
        fail(catalog_path, "duplicate or missing expanded-diary metric ID")
    for row in re.findall(r"^\| M-\d{3}\b.*$", catalog, re.M):
        cells = row.split("|")[1:-1]
        if len(cells) != 6 or any(not cell.strip() for cell in cells):
            fail(catalog_path, f"metric requires definition/source, controls, limits, phase/status and scenario: {row[:30]}")
        if not re.search(r"/ (planned|experimental|blocked|implemented)\b", row) or not references(row, "T|L|D|X"):
            fail(catalog_path, f"metric missing explicit status/scenario: {row[:30]}")

    scenario_ids = re.findall(r"^\| ((?:T|L|P|D|B|S|X)-\d{2})\b", testing, re.M)
    if len(scenario_ids) != len(set(scenario_ids)):
        fail(testing_path, "duplicate scenario ID")
    all_text = "\n".join(markdown.values())
    for scenario in references(all_text, "T|L|P|D|B|S|X") - set(scenario_ids):
        fail(testing_path, f"referenced scenario undefined: {scenario}")
    tasks = re.findall(r"^\| (AS-\d{3})\b", tracker, re.M)
    if len(tasks) != len(set(tasks)):
        fail(tracker_path, "duplicate task ID")
    for task in references(all_text, "AS") - set(tasks):
        fail(tracker_path, f"referenced task undefined: {task}")
    for row in re.findall(r"^\| AS-\d{3}\b.*$", tracker, re.M):
        if not re.search(r"\| (planned|experimental|blocked|implemented) \|", row):
            fail(tracker_path, f"task missing explicit status: {row[:40]}")
    decisions = markdown.get(DOCS / "DECISIONS.md", "")
    feasibility_ids = set(re.findall(r"^\| (F-\d{2})\b", decisions, re.M))
    for gate in references(all_text, "F") - feasibility_ids:
        fail(DOCS / "DECISIONS.md", f"referenced feasibility gate undefined: {gate}")

    evidence = DOCS / "evidence" / "dependency-candidates.json"
    try:
        ledger = json.loads(evidence.read_text(encoding="utf-8"))
        dependency_text = markdown.get(DOCS / "DEPENDENCIES.md", "")
        seen: set[str] = set()
        for package in ledger["packages"]:
            name, version, license_name = package["package"], package["version"], package["license"]
            if name in seen or package["selector"] != f"{name}@latest":
                fail(evidence, f"duplicate/non-latest selection: {name}")
            seen.add(name)
            expected_row = f"| {name} | {version} | {license_name} |"
            if expected_row not in dependency_text:
                fail(evidence, f"candidate differs from dependency table: {name}")
            if not package["source"].startswith("https://registry.npmjs.org/"):
                fail(evidence, f"unexpected registry evidence source: {name}")
        if "pnpm" not in seen or ledger["resolvedDate"] not in dependency_text:
            fail(evidence, "pnpm/date missing from dependency documentation")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        fail(evidence, f"invalid dependency evidence: {exc}")

    # The feasibility probes can fail installation/parity while their recorded
    # evidence remains valid. Do not turn a consistent report into build support.
    try:
        mobile_review = review_mobile_stack()
        if mobile_review["resolvedDate"] not in dependency_text:
            fail(DOCS / "DEPENDENCIES.md", "mobile probe review date missing")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        fail(DOCS / "evidence" / "mobile-stack-probe.json", f"invalid mobile probe evidence: {exc}")

    try:
        for error in review_mobile_scaffold(ROOT):
            fail(ROOT / "package.json", error)
    except (OSError, ValueError, KeyError, IndexError, TypeError) as exc:
        fail(ROOT / "package.json", f"invalid mobile scaffold evidence: {exc}")

    # The synthetic study has its own installed test tools; mobile candidates
    # above remain a historical, uninstalled snapshot. Never conflate them.
    study = ROOT / "design" / "prototype"
    try:
        manifest = json.loads((study / "package.json").read_text(encoding="utf-8"))
        installed = json.loads((study / "tooling-evidence.json").read_text(encoding="utf-8"))
        direct = {**manifest.get("dependencies", {}), **manifest.get("devDependencies", {})}
        manager_name, manager_version = manifest["packageManager"].split("@")
        direct[manager_name] = manager_version
        records = {p["package"]: p for p in installed["packages"]}
        if set(direct) != set(records) or manifest.get("dependencies"):
            fail(study / "package.json", "study must have only evidenced development tools and pnpm")
        for name, version in direct.items():
            record = records[name]
            if record["version"] != version or record["selector"] != f"{name}@latest" or not record["license"]:
                fail(study / "tooling-evidence.json", f"tool pin/evidence mismatch: {name}")
        lock = (study / "pnpm-lock.yaml").read_text(encoding="utf-8")
        if "packageManagerDependencies:" not in lock or "devDependencies:" not in lock:
            fail(study / "pnpm-lock.yaml", "pnpm environment and project graphs must both be committed")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        fail(study / "package.json", f"invalid design-study tooling evidence: {exc}")

    core = ROOT / "native" / "analytics-core"
    native_evidence = DOCS / "evidence" / "analytics-toolchain.json"
    try:
        manifest = json.loads((core / "package.json").read_text(encoding="utf-8"))
        native_tools = json.loads(native_evidence.read_text(encoding="utf-8"))
        if manifest.get("dependencies") or manifest.get("devDependencies"):
            fail(core / "package.json", "Kotlin calculation slice has no JavaScript dependencies")
        if manifest["packageManager"] != f"pnpm@{native_tools['pnpm']['version']}" or native_tools["pnpm"]["selector"] != "pnpm@latest":
            fail(core / "package.json", "pnpm resolution must match native tool evidence")
        for name in ("kotlin", "r8"):
            tool = native_tools[name]
            if not re.fullmatch(r"[0-9a-f]{64}", tool["sha256"]) or tool["size"] <= 0 or not tool["license"]:
                fail(native_evidence, f"missing verified artifact/size/license: {name}")
            if tool["version"] not in tool["url"] or not tool["url"].startswith("https://"):
                fail(native_evidence, f"native tool needs pinned HTTPS release artifact: {name}")
        if native_tools["resolvedDate"] not in markdown.get(DOCS / "DEPENDENCIES.md", ""):
            fail(native_evidence, "native resolution date missing from dependency documentation")
        if "packageManagerDependencies:" not in (core / "pnpm-lock.yaml").read_text(encoding="utf-8"):
            fail(core / "pnpm-lock.yaml", "pnpm environment lock must be committed")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        fail(native_evidence, f"invalid native analytics tool evidence: {exc}")

    if (ROOT / "AGENTS.md").read_text(encoding="utf-8") != (ROOT / "CLAUDE.md").read_text(encoding="utf-8"):
        fail(ROOT / "AGENTS.md", "agent entrypoints must carry identical instructions")
    license_text = (ROOT / "LICENSE").read_text(encoding="utf-8")
    if "Apache License" not in license_text or "Version 2.0, January 2004" not in license_text:
        fail(ROOT / "LICENSE", "Apache-2.0 license missing")

    secret_patterns = (
        r"gh[pousr]_[A-Za-z0-9]{30,}",
        r"github_pat_[A-Za-z0-9_]{40,}",
        r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
        r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
    )
    forbidden_suffixes = {".jks", ".keystore", ".p12", ".pem", ".key", ".db", ".sqlite", ".attentionbackup", ".hprof"}
    for path in files:
        if not path.is_file():
            continue
        if path.suffix.lower() in forbidden_suffixes or path.name in {".env", "local.properties", "google-services.json", "GoogleService-Info.plist"}:
            fail(path, "private/runtime artifact must not be published")
        if path.stat().st_size > 2_000_000:
            fail(path, "large artifact requires explicit public-content review")
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeError:
            continue
        for pattern in secret_patterns:
            if re.search(pattern, content):
                fail(path, "possible credential/private key; inspect without printing the match")

    if ERRORS:
        print("Documentation verification failed:")
        for error in ERRORS:
            print(f"  - {error}")
        return 1
    print(
        f"PASS: {len(markdown)} Markdown files; {len(REQUIRED)} core documents; "
        f"{len(capabilities)} capabilities; {len(metrics)} detailed metrics; {len(tasks)} tasks; {len(scenario_ids)} scenarios; "
        f"{len(ledger['packages'])} historical dependency candidates; "
        f"{len(mobile_review['packages'])} mobile tooling probe records."
    )
    print("Local links/anchors, metadata, ID coverage, ledger and public artifacts checked.")
    print("This checker does not test application builds, Android/device behavior or external links.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
