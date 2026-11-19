#!/usr/bin/env python3
"""Offline checks for the public documentation set and tracked artifacts; no Android behavior is tested."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = (
    "README.md", "CONTRIBUTING.md", "SECURITY.md", "LICENSE", "NOTICE", "AGENTS.md", "CLAUDE.md",
    "policies/PRIVACY.md", "policies/DATA-DELETION.md", "policies/SUPPORT.md",
    "docs/ARCHITECTURE.md", "docs/DETECTION.md", "docs/FEATURES.md", "docs/PRIVACY.md",
    "docs/DECISIONS.md", "docs/STATUS.md", "docs/RELEASE.md", "docs/ROADMAP.md",
)
# docs/ holds only the documents above; evidence and logs belong in .cache/ (git history keeps old records).
DOCS_ALLOWED_EXTRA = {"docs/evidence/publication-smoke-2026-11-18.json"}
SECRET_PATTERNS = (
    r"gh[pousr]_[A-Za-z0-9]{30,}",
    r"github_pat_[A-Za-z0-9_]{40,}",
    r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
)
MACHINE_PATH = re.compile(r"\b[A-Za-z]:\\(?:Users|home)\\|/(?:home|Users)/[a-z][\w.-]*/(?!\.\.)", re.I)
FORBIDDEN_SUFFIXES = {".jks", ".keystore", ".p12", ".pem", ".key", ".db", ".sqlite", ".attentionbackup", ".hprof"}
FORBIDDEN_NAMES = {".env", "local.properties", "google-services.json", "GoogleService-Info.plist"}
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
            & {".git", "node_modules", "__pycache__", ".pnpm-store", ".cache"}
        )


def without_code(text: str) -> str:
    text = re.sub(r"^```[^\n]*\n.*?^```\s*$", "", text, flags=re.M | re.S)
    return re.sub(r"`[^`\n]*`", "", text)


def anchors(text: str) -> set[str]:
    """GitHub-style heading anchors, with -1, -2 suffixes for repeated headings."""
    found: set[str] = set()
    seen: dict[str, int] = {}
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", re.sub(r"^```[^\n]*\n.*?^```\s*$", "", text, flags=re.M | re.S), re.M):
        slug = re.sub(r"[^\w\- ]", "", heading.replace("`", "").lower()).replace(" ", "-")
        count = seen.get(slug, 0)
        seen[slug] = count + 1
        found.add(f"{slug}-{count}" if count else slug)
    return found


def check_markdown(path: Path, content: str) -> None:
    if content.count("```") % 2:
        fail(path, "unbalanced fenced code blocks")
    for number, line in enumerate(content.splitlines(), 1):
        if line.rstrip() != line:
            fail(path, f"trailing whitespace at line {number}")
    if MACHINE_PATH.search(without_code(content)):
        fail(path, "machine-specific path; use a placeholder")
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


def check_decisions(files: list[Path], markdown: dict[Path, str]) -> int:
    path = ROOT / "docs/DECISIONS.md"
    text = markdown.get(path, "")
    defined = re.findall(r"^- \*\*(ADR-\d{3})\*\* \d{4}-\d{2}-\d{2} — \S", text, re.M)
    if len(defined) != len(set(defined)):
        fail(path, "duplicate ADR ID")
    numbers = sorted(int(adr[4:]) for adr in set(defined))
    if numbers != list(range(1, len(numbers) + 1)):
        fail(path, "ADR IDs must run from ADR-001 without gaps")
    known = set(defined)
    for source in files:
        if not source.is_file() or source.suffix.lower() not in {".md", ".kt", ".ts", ".tsx", ".py", ".mjs", ".java", ".xml", ".gradle", ".kts"}:
            continue
        try:
            content = source.read_text(encoding="utf-8")
        except UnicodeError:
            continue
        for adr in set(re.findall(r"\bADR-\d{3}\b", content)) - known:
            fail(source, f"references undefined {adr}")
    gates = set(re.findall(r"^\| (F-\d{2}) \|", text, re.M))
    for gate in set(re.findall(r"\bF-\d{2}\b", "\n".join(markdown.values()))) - gates:
        fail(path, f"referenced feasibility gate undefined: {gate}")
    return len(defined)


def main() -> int:
    files = public_files()
    markdown: dict[Path, str] = {}
    for path in files:
        if not path.exists():
            continue  # deleted in the working tree but still in the index
        if path.suffix.lower() == ".md":
            try:
                markdown[path] = path.read_text(encoding="utf-8")
            except UnicodeError:
                fail(path, "must be UTF-8")
    for name in REQUIRED:
        if not (ROOT / name).is_file():
            fail(ROOT / name, "required document missing")
    allowed_docs = {name for name in REQUIRED if name.startswith("docs/")} | DOCS_ALLOWED_EXTRA
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        if path.exists() and relative.startswith("docs/") and relative not in allowed_docs:
            fail(path, "not part of the documentation set; merge it into an existing document or move it to .cache/")
    for path, content in markdown.items():
        check_markdown(path, content)
    adr_count = check_decisions(files, markdown)

    if (ROOT / "AGENTS.md").read_text(encoding="utf-8") != (ROOT / "CLAUDE.md").read_text(encoding="utf-8"):
        fail(ROOT / "AGENTS.md", "AGENTS.md and CLAUDE.md must be identical")
    license_text = (ROOT / "LICENSE").read_text(encoding="utf-8")
    if "GNU GENERAL PUBLIC LICENSE" not in license_text or "Version 3, 29 June 2007" not in license_text:
        fail(ROOT / "LICENSE", "GPL-3.0 license missing")
    release = markdown.get(ROOT / "docs/RELEASE.md", "")
    if len(re.findall(r"^```text\n", release, re.M)) != 4:
        fail(ROOT / "docs/RELEASE.md", "needs exactly four text blocks: title, short and full description, release notes")

    for path in files:
        if not path.is_file():
            continue
        if path.suffix.lower() in FORBIDDEN_SUFFIXES or path.name in FORBIDDEN_NAMES:
            fail(path, "private/runtime artifact must not be published")
        if path.stat().st_size > 2_000_000:
            fail(path, "large artifact requires explicit public-content review")
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeError:
            continue
        for pattern in SECRET_PATTERNS:
            if re.search(pattern, content):
                fail(path, "possible credential/private key; inspect without printing the match")

    if ERRORS:
        print("Documentation verification failed:")
        for error in ERRORS:
            print(f"  - {error}")
        return 1
    print(f"PASS: {len(markdown)} Markdown files; {len(REQUIRED)} required documents; {adr_count} ADRs.")
    print("Checked local links/anchors, whitespace, ADR references, machine paths, secrets and private artifacts.")
    print("This checker does not test application builds, Android/device behavior or external links.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
