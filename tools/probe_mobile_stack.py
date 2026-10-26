#!/usr/bin/env python3
"""Prepare isolated AS-002 probes or audit them against the dated registry review.

Preparation does not install packages. Installs retain strict peers and release-age
checks. Nothing here builds an app, accesses a device, or changes global settings.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "mobile-stack-probe.json"
PACKAGE_NAME = re.compile(r"(?:@[a-z0-9._-]+/)?[a-z0-9._-]+")

FIXTURE = "export const durationMs = (startMs: number, endMs: number): number => Math.max(0, endMs - startMs);\n"
BABEL_SMOKE = """(async () => {
  try {
    const babel = await import('@babel/core');
    const result = await babel.transformAsync('export const value: number = 1;', {
      filename: 'fixture.ts', babelrc: false, configFile: false,
      presets: [require.resolve('@react-native/babel-preset')],
    });
    if (!result.code) throw new Error('No transformed code');
    console.log('PASS: Babel core with React Native preset');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
})();
"""
LINT_SMOKE = """(async () => {
  try {
    const {ESLint} = await import('eslint');
    const eslint = new ESLint({overrideConfigFile: true,
      overrideConfig: require('@react-native/eslint-config/flat')});
    const results = await eslint.lintFiles(['fixture.ts']);
    if (results.some(result => result.errorCount || result.warningCount)) {
      throw new Error('Lint diagnostics found');
    }
    console.log('PASS: React Native flat lint config');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
})();
"""
TRANSFORM_SMOKE = """const {createRequire} = require('node:module');
const owner = createRequire(require.resolve('@react-native/metro-config'));
const transformer = owner('@react-native/metro-babel-transformer');
(async () => {
  try {
    const result = await transformer.transform({filename: 'fixture.ts',
      src: 'export const value: number = 1;',
      options: {dev: true, enableBabelRCLookup: false,
        projectRoot: process.cwd(), enableBabelRuntime: true}});
    if (!result.ast) throw new Error('No transformed AST');
    console.log('PASS: React Native-owned Babel transformer');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
})();
"""


def review() -> dict:
    data = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    if data["schemaVersion"] != 1:
        raise ValueError("Unknown mobile-stack evidence schema")
    if set(data["profiles"]) != {"template", "reduced", "age"}:
        raise ValueError("Expected template, reduced and age probe profiles")
    records = {}
    for row in data["packages"]:
        name = row["package"]
        if not PACKAGE_NAME.fullmatch(name) or name in records:
            raise ValueError("Duplicate or invalid package name")
        if (row["selector"] != f"{name}@latest" or not row["license"]
                or row["source"] != f"https://registry.npmjs.org/{name}/latest"
                or not row["integrity"].startswith("sha512-")):
            raise ValueError(f"Incomplete latest selection evidence: {name}")
        records[name] = row
    for profile_name, profile in data["profiles"].items():
        manifest = profile["manifest"]
        direct = {**manifest.get("dependencies", {}), **manifest.get("devDependencies", {})}
        if not manifest["private"] or manifest["packageManager"] != f"pnpm@{records['pnpm']['version']}":
            raise ValueError("Probe must be private and use the reviewed pnpm")
        for name, selector in direct.items():
            if name not in records or selector not in {"latest", records[name]["version"]}:
                raise ValueError(f"Unevidenced probe selection: {name}")
        if "strictPeerDependencies: true" not in profile["settings"]:
            raise ValueError("Probe must retain strict peer checks")
        for row in profile["installed"]:
            if row["package"] not in direct:
                raise ValueError("Installed observation outside the probe manifest")
        installed_names = [row["package"] for row in profile["installed"]]
        if len(installed_names) != len(set(installed_names)):
            raise ValueError("Duplicate installed observation")
        if profile_name != "age" and set(installed_names) != set(direct):
            raise ValueError("Incomplete observed direct installation graph")
    for artifact in data["artifacts"]:
        row = records[artifact["package"]]
        if (artifact["version"] != row["version"] or artifact["integrity"] != row["integrity"]
                or artifact["tarball"] != row["tarball"] or artifact["bytes"] <= 0
                or not re.fullmatch(r"[a-f0-9]{64}", artifact["sha256"])):
            raise ValueError("Inspected artifact differs from registry review")
    return data


def cache_path(path: Path) -> Path:
    resolved = path.resolve()
    if not resolved.is_relative_to((ROOT / ".cache").resolve()):
        raise ValueError("Use a task-owned probe inside this repository's .cache")
    return resolved


def prepare(data: dict, profile: str) -> None:
    target = cache_path(ROOT / ".cache" / "mobile-stack-probe" / profile)
    if target.exists():
        raise ValueError(f"Probe already exists; preserving it: {target.relative_to(ROOT)}")
    target.mkdir(parents=True)
    record = data["profiles"][profile]
    (target / "package.json").write_text(json.dumps(record["manifest"], indent=2) + "\n", encoding="utf-8")
    (target / "pnpm-workspace.yaml").write_text(record["settings"], encoding="utf-8")
    if profile != "age":
        (target / "fixture.ts").write_text(FIXTURE, encoding="utf-8")
        (target / "tsconfig.json").write_text(json.dumps({
            "extends": "@react-native/typescript-config", "include": ["fixture.ts"],
        }, indent=2) + "\n", encoding="utf-8")
        (target / "babel.config.js").write_text(
            "module.exports = {presets: ['module:@react-native/babel-preset']};\n", encoding="utf-8")
        for name, content in (("babel-smoke.cjs", BABEL_SMOKE), ("lint-smoke.cjs", LINT_SMOKE),
                              ("transform-smoke.cjs", TRANSFORM_SMOKE)):
            (target / name).write_text(content, encoding="utf-8")
    print(f"Prepared {target.relative_to(ROOT).as_posix()}; no packages installed.")
    print("Selectors re-resolve at install time; audit against the dated review afterward.")


def audit(data: dict, target: Path) -> int:
    target = cache_path(target)
    manifest = json.loads((target / "package.json").read_text(encoding="utf-8"))
    records = {row["package"]: row for row in data["packages"]}
    errors = []
    direct = {**manifest.get("dependencies", {}), **manifest.get("devDependencies", {})}
    if manifest.get("packageManager") != f"pnpm@{records['pnpm']['version']}":
        errors.append("packageManager differs from reviewed pnpm")
    for name in direct:
        if name not in records:
            errors.append(f"Unreviewed package: {name}")
            continue
        package_file = target / "node_modules" / name / "package.json"
        if not package_file.is_file():
            errors.append(f"Missing installation: {name}")
            continue
        installed = json.loads(package_file.read_text(encoding="utf-8"))["version"]
        wanted = records[name]["version"]
        if installed != wanted:
            errors.append(f"{name}: installed {installed}; reviewed latest {wanted}")
    for error in errors:
        print(f"FAIL: {error}")
    print(f"Audited {len(direct)} direct packages against {data['resolvedDate']} metadata; not a build result.")
    return int(bool(errors))


def smoke(target: Path) -> int:
    target = cache_path(target)
    manifest = json.loads((target / "package.json").read_text(encoding="utf-8"))
    pnpm = shutil.which("pnpm")
    if not pnpm:
        raise ValueError("pnpm is required")
    commands = [["tsc", "--project", "tsconfig.json"]]
    if "oxlint" in manifest.get("devDependencies", {}):
        commands += [["oxlint", "--deny-warnings", "fixture.ts"], ["node", "transform-smoke.cjs"]]
    else:
        commands += [["node", "babel-smoke.cjs"], ["node", "lint-smoke.cjs"]]
    failed = False
    for command in commands:
        result = subprocess.run([pnpm, "--dir", str(target), "exec", *command],
                                capture_output=True, text=True, timeout=120)
        print(f"{'FAIL' if result.returncode else 'PASS'}: pnpm exec {' '.join(command)} (exit {result.returncode})")
        # Keep local diagnostics local; committed evidence uses concise reason codes.
        (target / f"{command[-1].replace('.', '-')}-smoke.log").write_text(
            result.stdout + result.stderr, encoding="utf-8")
        failed |= result.returncode != 0
    return int(failed)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--prepare", choices=("template", "reduced", "age"))
    actions.add_argument("--audit", type=Path)
    actions.add_argument("--smoke", type=Path)
    args = parser.parse_args()
    try:
        data = review()
        if args.prepare:
            prepare(data, args.prepare)
        elif args.audit:
            return audit(data, args.audit)
        elif args.smoke:
            return smoke(args.smoke)
        else:
            print(f"PASS: {len(data['packages'])} reviewed package records and three isolated probe profiles.")
            print("Evidence consistency only; latest parity, native builds and device behavior are separate checks.")
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
