"""Regenerate release attributions; run with --check to detect stale outputs.

Requires pnpm install and Python 3.11+. Metro resolves the release JavaScript
graph; no Gradle tasks run. Native metadata is reviewed in licenses/sources.json.
"""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def package_directory(path):
    for directory in (path, *path.parents):
        manifest = directory / "package.json"
        if manifest.is_file():
            package = read_json(manifest)
            if "name" in package and "version" in package:
                return directory, package
    raise ValueError(f"No package manifest for {path}")


def map_sources(source_map):
    yield from source_map.get("sources", [])
    for section in source_map.get("sections", []):
        yield from map_sources(section["map"])


def project_url(package):
    url = package.get("homepage") or package.get("repository")
    if isinstance(url, dict):
        url = url["url"]
    if not url:
        raise ValueError(f"Missing project URL: {package['name']}")
    url = re.sub(r"^git\+", "", url).replace("git://", "https://")
    url = re.sub(r"\.git(?:#.*)?$", "", url).replace("http://", "https://")
    if re.fullmatch(r"[\w.-]+/[\w.-]+", url):
        url = "https://github.com/" + url
    if not url.startswith("https://"):
        raise ValueError(f"Invalid project URL: {url}")
    return url


def npm_components(source_map, sources):
    directories = {ROOT / "node_modules" / name for name in read_json(ROOT / "package.json")["dependencies"]}
    for filename in map_sources(source_map):
        if filename.startswith("__"):
            continue
        path = Path(filename)
        if not path.is_absolute():
            path = ROOT / path
        if not path.is_file():
            raise ValueError(f"Source map references a missing file: {filename}")
        directory, package = package_directory(path.parent)
        if package["name"] != "attention-stats":
            directories.add(directory)
    components, notices = {}, {}
    for directory in sorted(directories):
        package = read_json(directory / "package.json")
        name, version = package["name"], package["version"]
        license_id = package.get("license")
        if license_id != "MIT":
            raise ValueError(f"Review new npm license and text: {name}@{version}: {license_id}")
        components[name, version] = dict(name=name, version=version, license=license_id, url=project_url(package))
        candidates = sorted(path for path in directory.iterdir() if path.is_file() and path.name.lower().startswith(("license", "licence", "copying")))
        if candidates:
            text = candidates[0].read_text(encoding="utf-8-sig")
            if "Permission is hereby granted" not in text:
                raise ValueError(f"Review nonstandard MIT text: {name}")
            attribution = text.split("Permission is hereby granted", 1)[0].strip()
            attribution = re.sub(r"^\s*(?:The )?MIT License(?: \(MIT\))?\s*", "", attribution, flags=re.I).strip()
            suffix = text.split("SOFTWARE.", 1)
            if len(suffix) == 2 and suffix[1].strip():
                attribution += "\n\n" + suffix[1].strip()
        else:
            attribution = sources["npmNotices"].get(name)
            if not attribution:
                raise ValueError(f"Missing npm attribution text: {name}@{version}")
        notices[f"{name} {version}"] = attribution
    return list(components.values()), notices


def native_components(sources):
    current_inputs = dependency_inputs()
    if current_inputs != sources["dependencyInputs"]:
        changed = sorted(key for key in current_inputs.keys() | sources["dependencyInputs"].keys() if current_inputs.get(key) != sources["dependencyInputs"].get(key))
        raise ValueError(f"Review changed native dependency declarations, then refresh dependencyInputs in licenses/sources.json: {changed}")
    locked = set()
    for line in (ROOT / "android/app/gradle.lockfile").read_text(encoding="utf-8").splitlines():
        coordinate, separator, configurations = line.partition("=")
        if separator and "releaseRuntimeClasspath" in configurations.split(",") and not coordinate.split(":")[1].endswith("-bom"):
            locked.add(coordinate)
    reviewed = {f"{row['name']}:{row['version']}" for row in sources["maven"]}
    if locked != reviewed:
        raise ValueError(f"Review native metadata for lock changes: added={sorted(locked - reviewed)}, removed={sorted(reviewed - locked)}")
    catalog = tomllib.loads((ROOT / "android/gradle/libs.versions.toml").read_text(encoding="utf-8"))
    app = (ROOT / "android/app/build.gradle").read_text(encoding="utf-8")
    for alias in re.findall(r"(?:implementation|coreLibraryDesugaring)\s*\(?\s*libs\.([\w.]+)", app):
        library = catalog["libraries"][alias.replace(".", "-")]
        version = catalog["versions"][library["version"]["ref"]]
        coordinate = f"{library['module']}:{version}"
        if coordinate not in locked and library["module"] != "com.android.tools:desugar_jdk_libs":
            raise ValueError(f"Native direct dependency is not in the release lock: {coordinate}")
    rn_catalog = tomllib.loads((ROOT / "node_modules/react-native/gradle/libs.versions.toml").read_text(encoding="utf-8"))
    components = []
    for source in [*sources["maven"], *sources["bundled"]]:
        row = {key: source[key] for key in ("name", "version", "license", "url")}
        if "versionKey" in source:
            actual = rn_catalog["versions"][source["versionKey"]].replace("_", ".")
            if row["version"] != actual:
                raise ValueError(f"Review bundled C++ update: {row['name']}: {actual}")
        if row["name"] == "com.android.tools:desugar_jdk_libs" and row["version"] != catalog["versions"]["desugar-jdk-libs"]:
            raise ValueError("Review desugaring license/source update")
        components.append(row)
    return components


def dependency_inputs():
    paths = [ROOT / "android/gradle/libs.versions.toml", ROOT / "node_modules/react-native/gradle/libs.versions.toml"]
    app = (ROOT / "android/app/build.gradle").read_text(encoding="utf-8")
    modules = ["app", *re.findall(r"implementation\s+project\([\"']:([^\"']+)[\"']\)", app)]
    for module in modules:
        paths += list((ROOT / "android" / module).glob("build.gradle*"))
    inputs = {}
    for path in paths:
        content = path.read_text(encoding="utf-8")
        if path.suffix != ".toml":
            content = "\n".join(re.findall(r"^dependencies\s*\{.*?^\}", content, re.M | re.S))
        inputs[path.relative_to(ROOT).as_posix()] = hashlib.sha256(content.encode()).hexdigest()
    root = (ROOT / "android/build.gradle").read_text(encoding="utf-8")
    inputs["android/build.gradle"] = hashlib.sha256("\n".join(re.findall(r'^\s*(?:kotlinVersion|ndkVersion)\s*=.*$', root, re.M)).encode()).hexdigest()
    properties = (ROOT / "android/gradle.properties").read_text(encoding="utf-8")
    inputs["android/gradle.properties"] = hashlib.sha256("\n".join(re.findall(r'^hermesEnabled=.*$', properties, re.M)).encode()).hexdigest()
    for name in read_json(ROOT / "package.json")["dependencies"]:
        directory = ROOT / "node_modules" / name / "android"
        for path in directory.glob("build.gradle*"):
            inputs[path.relative_to(ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return dict(sorted(inputs.items()))


def artifacts(source_map):
    sources = read_json(ROOT / "licenses/sources.json")
    npm, notices = npm_components(source_map, sources)
    components = sorted(npm + native_components(sources), key=lambda row: (row["name"].casefold(), row["version"]))
    if len({(row["name"], row["version"]) for row in components}) != len(components):
        raise ValueError("Duplicate component")
    texts = {}
    for license_id, files in sources["licenseTexts"].items():
        texts[license_id] = "\n\n".join((ROOT / filename).read_text(encoding="utf-8").strip() for filename in files)
    missing = {row["license"] for row in components} - texts.keys()
    if missing:
        raise ValueError(f"Missing license texts: {sorted(missing)}")
    notice = ["Attention Stats", "Copyright 2026 Attention Stats contributors", "", "Third-party software distributed with the Android release application.", "Generated by tools/generate_licenses.py; metadata and upstream notices are", "maintained in licenses/. Full license terms are bundled in Settings >", "Open-source licenses and retained in the files listed below.", "", "The scaffold is adapted from @react-native-community/template 0.88.0", "(Meta Platforms, Inc. and affiliates), MIT; licenses/react-native-template.txt.", "Development tools, BOMs and standalone qualification APKs are excluded.", ""]
    for license_id in sorted({row["license"] for row in components}):
        notice += [license_id, "License text: " + ", ".join(sources["licenseTexts"][license_id])]
        for row in components:
            if row["license"] == license_id:
                notice += [f"  {row['name']} {row['version']}", f"    {row['url']}"]
        notice.append("")
    notice += ["Attribution notices", "===================", ""]
    for title, text in sorted({**notices, **sources["notices"]}.items()):
        notice += [title, text.strip(), ""]
    notice_text = "\n".join(notice).rstrip() + "\n"
    for title, files in sources.get("documents", {}).items():
        texts[title] = "\n\n".join((ROOT / filename).read_text(encoding="utf-8").strip() for filename in files)
    texts["NOTICE"] = notice_text
    return {
        "NOTICE": notice_text,
        "src/features/settings/licenses.json": json.dumps(components, indent=2, ensure_ascii=False) + "\n",
        "src/features/settings/licenseTexts.json": json.dumps(texts, indent=2, ensure_ascii=False) + "\n",
    }, Counter(row["license"] for row in components)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--sourcemap", type=Path, help="Use an existing Metro release source map instead of running Metro")
    args = parser.parse_args()
    if args.sourcemap:
        outputs, counts = artifacts(read_json(args.sourcemap))
    else:
        pnpm = shutil.which("pnpm")
        if not pnpm:
            raise ValueError("pnpm is required to resolve the shipped JavaScript graph")
        with tempfile.TemporaryDirectory(prefix="attentionstats-licenses-") as temporary:
            bundle = Path(temporary) / "index.android.bundle"
            source_map = Path(temporary) / "index.android.bundle.map"
            subprocess.run([pnpm, "exec", "react-native", "bundle", "--platform", "android", "--dev", "false", "--entry-file", "index.js", "--bundle-output", str(bundle), "--sourcemap-output", str(source_map), "--max-workers", "2"], cwd=ROOT, check=True)
            outputs, counts = artifacts(read_json(source_map))
    stale = []
    for filename, content in outputs.items():
        path = ROOT / filename
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                stale.append(filename)
        else:
            path.write_text(content, encoding="utf-8", newline="\n")
    print(f"{sum(counts.values())} components: " + ", ".join(f"{key}={value}" for key, value in sorted(counts.items())))
    if stale:
        raise SystemExit("Stale license artifacts: " + ", ".join(stale))


if __name__ == "__main__":
    main()
