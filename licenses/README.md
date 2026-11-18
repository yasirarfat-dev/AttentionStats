# Android third-party attribution

`NOTICE`, `src/features/settings/licenses.json` and
`src/features/settings/licenseTexts.json` are generated. Run:

```powershell
python tools/generate_licenses.py
python tools/generate_licenses.py --check
```

Use pnpm to install the locked packages first. Python 3.11 or later is required.
The generator runs Metro with `--platform android --dev false`, reads its source
map and installed package manifests, and includes direct production dependencies
for their autolinked native code. Babel's emitted runtime and Metro's runtime
polyfills are included even when their installation originates in dev dependencies.
Tooling that is absent from the release bundle is excluded. It runs no Gradle
tasks and does not contact a device. `--sourcemap <path>` can reuse a release
Metro source map, but it must be regenerated after source or dependency changes.
The license check is intentionally separate from `pnpm lint`.

`sources.json` records reviewed native metadata from the release runtime entries
in `android/app/gradle.lockfile`, the version catalogs and build declarations.
Each Maven entry retains its upstream POM URL and SHA-256. BOMs, compiler/KSP,
Gradle plugins, lint/test configurations, the collection prototype and the
standalone `native/*-qualification` apps are excluded. AndroidX multiplatform
coordinates and their Android/JVM variants are listed separately as locked.
Shared license terms are referenced by SPDX identifier or expression; copyright
and required acknowledgement text is retained in NOTICE and bundled in the app.
`LicenseRef-Public-Domain` identifies the upstream SQLite/LibTomCrypt public-domain
dedications, which do not have a standard SPDX public-domain identifier.

Native metadata is reviewed rather than guessed when coordinates change: update
the release lock through the project's usual build process, inspect the matching
POMs and AAR/JAR notices, update `sources.json` and any affected license texts, then
regenerate. The check rejects unreviewed lock/catalog changes and unknown npm
licenses or missing attributions. Sources for embedded components are recorded
alongside their parent artifacts; Hermes components without a separate version
use the parent release version. React Native's C++ pins come from its installed
version catalog. The libjpeg-turbo version is embedded in Fresco's
`libnative-imagetranscoder.so`; SQLCipher's SQLite/LibTomCrypt pins come from the
[4.20.0 release notes](https://www.zetetic.net/blog/2026/10/27/sqlcipher-4.20.0-release/).

AndroidX AARs include Apache-2.0 `META-INF/androidx/.../LICENSE.txt` files.
Their terms are covered by the shared Apache text; the inspected artifacts have
no separate NOTICE. SQLCipher Android's upstream LICENSE is BSD-3-Clause; its
Android SQLite bindings also carry Apache-2.0 AOSP notices. Its release does not
contain a separate NOTICE file. OkHttp's embedded public-suffix NOTICE and
MMKV's OpenSSL acknowledgements are retained. Existing supplier license files
are preserved; `upstream/` retains additional supplier terms where needed.
The MPL-2.0 Public Suffix List rules are extracted without semantic changes from
OkHttp's distributed resource into `public-suffix-list.dat`, with exception
markers restored. Both its license and its readable source are bundled offline.

These are source and artifact metadata checks, not a new APK build or device UI
qualification. Recheck native resolution and bundled-library versions when the
release dependencies or toolchain change.
