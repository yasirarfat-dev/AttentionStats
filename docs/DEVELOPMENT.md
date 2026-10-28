# Local development

**Status:** Experimental mobile scaffold and tooling commands exist; native build/live acceptance tracked in State. Updated 2026-10-28.
**Responsibility:** Defines local setup, reproducible commands, isolation and debugging.
**Related specifications:** [Dependencies](DEPENDENCIES.md), [Testing](TESTING.md), [Structure](FILE-STRUCTURE.md), [Agent rules](AGENT-RULES.md).
**Unresolved feasibility:** F-02–F-04 in [Decisions](DECISIONS.md); attempted builds are not accepted binaries; see current evidence.

## Work on this foundation

Open `C:\home\biobot\projects\AttentionStats` on this machine, or clone [the public repository](https://github.com/yasirarfat-dev/AttentionStats) elsewhere. Read [Index](INDEX.md), [State](STATE.md), [Tracker](FEATURE-TRACKER.md) and relevant specs. Required now: Git and Python 3.11 or newer. No Node package installation, Android SDK, private device data or credentials are required to edit/verify documentation.

```powershell
Set-Location C:\home\biobot\projects\AttentionStats
python tools/check_docs.py
git diff --check
git status --short
```

The checker verifies expected documents, metadata, relative links/anchors, capability/scenario/task coverage, dependency-ledger consistency and prohibited public artifacts. It does not prove Android behavior or external link availability. Manually review public text and policy interpretation. The documentation CI job performs these offline checks; separate mobile jobs compile and verify the experimental scaffold without signing secrets. All GitHub workflows are manual only under ADR-030: pushes and pull requests start no pipeline. Run appropriate checks locally before publishing. Start a remote run only on explicit user request through GitHub Actions → select workflow → Run workflow ([GitHub instructions](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)).

## Native analytics slice: AS-035

This domain implementation is at [native/analytics-core](../native/analytics-core/README.md), using `src/main/kotlin` and `src/test/kotlin`. It has no Android application or JS runtime. Existing JDK 17+, Python 3.11+ and pnpm are sufficient. First execution downloads reviewed latest-stable compiler tools into ignored local caches, validating pinned hashes; routine offline repetition is supported.

```powershell
python tools/check_architecture.py
Set-Location native/analytics-core
pnpm install --frozen-lockfile --ignore-scripts
pnpm test
pnpm run example
```

From repository root: `python tools/test_analytics.py --offline` repeats the JVM checks. Optional synthetic ART execution on an existing authorized debug target: `python tools/test_analytics.py --device --sdk C:\opt\Android\SDK`. Requires exactly one target, uses isolated latest stable D8, copies/runs/removes one task-owned file and installs no app or permissions. It is not live-source/UI/lifecycle verification. See [Testing](TESTING.md#kotlin-analytics-calculation-verification) for limitations. No global SDK/Java/adb configuration changes are required.

## Application setup: AS-004

Root is the experimental mobile project, generated from reviewed Community CLI/template 20.2.0/0.88.0 with unmodified copy/rename helpers and pnpm-only installation. Full CLI `init` was not used because its installer does not support pnpm. [ADR-029](DECISIONS.md) records this adaptation, namespace, the explicit age override and the narrow compiler patch. iOS source is generated but untested; do not infer iPhone support.

Use Node 26.12.1+, the pinned pnpm 12.15.0 and Python 3.11+. From root:

```powershell
pnpm install --frozen-lockfile --ignore-scripts
python tools/check_mobile.py --installed
pnpm run lint
pnpm run typecheck
pnpm run format:check
pnpm test
pnpm run bundle:android
pnpm build
```

The lockfile contains pnpm's boot/manager document and the project graph. The audit reads the final project document, checks direct pins against the dated registry review and checks reviewed patch hashes. It does not query live latest. Dependency refreshes must repeat metadata/license/peer review. Strict peers, integrity and disabled install scripts remain enabled; root release age is explicitly zero by user request. Nested Kotlin tools and the retained browser reference have independent workspace roots/lockfiles.

`pnpm start`, `pnpm android`, `pnpm build` and `pnpm run android:release` invoke React Native Community CLI directly. pnpm is the package/script runner; CLI invokes the committed Gradle wrapper for native Android compilation. The Python build helper below is optional SDK/cache isolation and deep-task tooling, not the application framework.

For Android, use JDK 17 and a **repository-local SDK** with platform android-37.0, Build Tools 37.0.0, NDK 30.0.16248370 and CMake 4.1.2. [Evidence](evidence/mobile-scaffold.json) records official package URLs and verified download hashes. Install required SDK components into an isolated SDK before invoking Gradle; automatic SDK installation is disabled. Set ignored `android/local.properties` entries `sdk.dir` and `cmake.dir` to your local SDK/CMake directories so direct CLI commands select the intended SDK. The build runner prefers `.cache/as-004/sdk` if present, otherwise `ANDROID_HOME`; explicit `--sdk` is recommended. It supplies task-local environment variables and a repository-local Gradle user home, without changing global configuration.

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.cache/gradle-user-home'
pnpm build --extra-params '-PreactNativeArchitectures=arm64-v8a --max-workers=4 --no-daemon'
pnpm run android:release --extra-params '-PreactNativeArchitectures=x86_64 --max-workers=4 --no-daemon'
```

In Windows `local.properties`, escape drive-letter colons and use forward slashes, for example `sdk.dir=C\:/path/to/sdk`. Lint checks these properties even when Gradle can resolve an unescaped path. If lint reuses a stale result after correcting this ignored file, rerun the app's `lintAnalyzeDebug` task explicitly before `lintDebug`.

Optional lower-level qualification commands use the same Gradle wrapper:

```powershell
python tools/android_build.py --sdk .cache/as-004/sdk --arch x86_64 generateCodegenArtifactsFromSchema
python tools/android_build.py --sdk .cache/as-004/sdk --arch x86_64 :analytics-core:verifyAnalytics
python tools/android_build.py --sdk .cache/as-004/sdk --arch x86_64 assembleDebug assembleRelease
```

AS-002 also has an ARM64 native CI job using exact SDK/NDK/CMake components on a disposable Linux runner. It compiles debug, unsigned release and synthetic Android test APKs, runs the shared Gradle analytics suite and app lint, and uploads no binaries. Workflow configuration and a passing remote run are separate evidence.

The app enables core library desugaring for the pure module's `java.time` calls on the declared API-24/25 baseline. The latest stable reviewed native dependency is pinned in `android/gradle/libs.versions.toml`; its license and artifact evidence are recorded in [Dependencies](DEPENDENCIES.md#android-core-library-desugaring). After a native build with the task-local Gradle cache, `python tools/check_mobile.py --native-cache` checks the resolved JAR's size/SHA-256. Native CI performs this check too when manually dispatched on explicit user request; source/toolchain changes do not trigger it.

Current user instruction requires runtime checks on the connected debug phone. Do not start/use an emulator; ask the user to connect the phone with USB debugging enabled if unavailable. Build the application and synthetic test APK for the phone's ABI:

```powershell
python tools/android_build.py --sdk .cache/as-004/sdk --arch arm64-v8a :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s <authorized-phone> install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s <authorized-phone> install -r -t android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <authorized-phone> shell am instrument -w -r com.attentionstats.debug.test/com.attentionstats.qualification.AnalyticsInstrumentation
```

The custom instrumentation runner reuses the original synthetic calculation suite through the app's actual Android DEX/desugaring pipeline. Require the success stream and `INSTRUMENTATION_CODE: -1`; adb exit status alone is insufficient. A current-API phone result does not establish Android 24/25 runtime compatibility. Installation touches only the task app/test packages and requests no source grants or phone-setting changes.

Before the first debug build, create a task-local key at `.cache/android-debug.keystore` using the JDK's `keytool` (alias `androiddebugkey`, standard Android debug credentials), or use the optional isolation helper once, which creates it. Preserve the existing key and never commit it. Debug ID is `com.attentionstats.debug`; release ID/namespace is `com.attentionstats`, chosen for development, with signing/store registration still a release-maintainer gate. Release artifacts are unsigned; the template's debug release-signing fallback has been removed. Use `--arch arm64-v8a` for an appropriate phone; an x86_64 build is not ARM qualification.

One-time debug key command from repository root (after creating `.cache`):

```powershell
keytool -genkeypair -keystore .cache/android-debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
```

`pnpm run start` binds Metro to loopback. On an authorized debug target, use `adb -s <target> reverse tcp:8081 tcp:8081` and the React Native developer menu’s **Change Bundle Location → localhost:8081** when needed. Android 17 restricts LAN access for target API 37; [official guidance](https://developer.android.com/privacy-and-security/local-network-permission) explains the boundary. This development flow uses loopback and grants no local-network/source access. `pnpm run android` uses the normal CLI, so choose an authorized isolated target explicitly when multiple devices exist. Do not reset adb, change grants or install on another agent's target. Actual build/screen results and remaining blockers are in [State](STATE.md) and the [dated log](IMPLEMENTATION_LOG/2026-10-27.md#continuation--as-004-mobile-scaffold).

The standalone [browser reference](../design/prototype/README.md) is retained for design research only; it has no role in native product acceptance.

## AS-002 compatibility probe reproduction

[Results and blockers](DEPENDENCIES.md#as-002-isolated-compatibility-probes) distinguish the failed upgraded template from a reduced-tooling candidate. The [probe tool](../tools/probe_mobile_stack.py) uses only Python's standard library and prepares synthetic tooling fixtures under ignored `.cache/mobile-stack-probe`. It installs nothing during preparation, refuses to overwrite an existing probe, and restricts audit/smoke paths to this repository's cache. There is no mobile app, permission flow or device access.

From repository root, prepare the profiles once:

```powershell
python tools/probe_mobile_stack.py
python tools/probe_mobile_stack.py --prepare template
python tools/probe_mobile_stack.py --prepare reduced
python tools/probe_mobile_stack.py --prepare age
$as002Store = Join-Path $PWD '.cache/mobile-stack-probe/store'
$as002State = Join-Path $PWD '.cache/mobile-stack-probe/state'
```

Install the template profile with normal release-age protection and strict peers. Its first reviewed result was **failure**, so do not treat this sequence as a passing setup recipe:

```powershell
pnpm --dir .cache/mobile-stack-probe/template --store-dir $as002Store --state-dir $as002State install --ignore-scripts
python tools/probe_mobile_stack.py --audit .cache/mobile-stack-probe/template
python tools/probe_mobile_stack.py --smoke .cache/mobile-stack-probe/template
```

The reduced profile removes redundant root Babel core/preset-env declarations and replaces the incompatible template linter with latest Oxlint. It keeps TypeScript latest, React Native's own constrained transitive tooling, strict peers and script protection:

```powershell
pnpm --dir .cache/mobile-stack-probe/reduced --store-dir $as002Store --state-dir $as002State install --ignore-scripts
python tools/probe_mobile_stack.py --audit .cache/mobile-stack-probe/reduced
python tools/probe_mobile_stack.py --smoke .cache/mobile-stack-probe/reduced
```

Both first observed graphs installed Worklets 0.13.0 while the reviewed latest was 0.13.1; the audit must fail that mismatch even though reduced install/smokes pass. `--audit` compares actual installed direct versions to the **dated committed review**, not live registry latest or native build support. Preparation retains `latest` selectors, so later installs can resolve differently. Before accepting new results, re-resolve every direct dependency from registry latest, review metadata/licenses/peers and update the evidence together; a stale snapshot is not authority for newer packages.

The age profile pins the reviewed latest Worklets and explicitly retains a strict 1,440-minute minimum age. At the recorded time this lockfile-only diagnostic failed with `ERR_PNPM_NO_MATURE_MATCHING_VERSION`:

```powershell
pnpm --dir .cache/mobile-stack-probe/age --store-dir $as002Store --state-dir $as002State install --lockfile-only --ignore-scripts
```

These historical profiles retain their original age checks. Do not alter their settings to rewrite past results. The separately authorized root scaffold uses age zero; preserve strict peers, integrity checks and disabled scripts in both contexts. Probe lockfiles, modules and detailed local smoke logs stay ignored. No frozen-lockfile claim is made for a failed/unaccepted app graph; accepted application setup must commit its own reviewed lockfile. The current first-run results are recorded in [dated evidence](IMPLEMENTATION_LOG/2026-10-27.md#continuation--as-002-latest-stack-compatibility-probes).

## AS-006 isolated encrypted-store qualification

The [standalone fixture](../native/storage-qualification/README.md) uses the existing wrapper, task-local SDK and debug signing key without being included in the product Android settings. Run `python tools/storage_qualification.py build`, then `python -m unittest discover -s tools/tests -p test_storage_qualification.py` and `python tools/storage_qualification.py run`. Builds use committed dependency locks; only a reviewed introduction/refresh uses `build --write-locks`. The runner requires exactly one authorized physical ARM64 phone, refuses an existing fixture package and removes the package it installs on success/failure. No emulator, grants/settings, product app, history, Keystore or shared adb-server changes. Results are bounded aggregates in ignored `.cache/as-006`; public evidence is separately reviewed. A missing phone requires reconnection, not an emulator fallback.

## AS-006 isolated archive-envelope qualification

The separate [Tink fixture](../native/archive-qualification/README.md) uses the same local tooling and its own generated locks, with no product inclusion. Run `python tools/archive_qualification.py build`, `python -m unittest discover -s tools/tests -p test_archive_qualification.py`, `node --test tools/tests/archive_reader.test.mjs`, then `python tools/archive_qualification.py run`. The same phone/ownership safeguards apply. The runner reads only two fixed public synthetic-vector outputs from its installed fixture, checks bounded file sizes and independent Node readback, then removes its package/data. Three compact public golden vectors are packaged and required; fresh/large outputs remain ignored. No new JS dependency, history parser, production crypto API, provider or Keystore integration. The independent reader's manual construction is test-only. See [public evidence](evidence/archive-qualification.json) for scope, source/lock/artifact hashes and remaining gates.

## Debugging procedure

### AS-043 self-contained Android preview

The category preview uses Room schema 2 with an explicit 1→2 auto-migration, retaining old exposures as unclassified. Snapshot contract 2 and observation notice revision 2 accompany ADR-034; old source choices do not silently authorize category recording. Review the updated in-app notice manually before a live trial. The Instagram candidate is version-pinned; Facebook and normal-feed posts remain unavailable. Totals/HUD count viewer pages including ads, not a qualified short-video metric. Keep package changes and instrumentation outside active ten-minute diagnostic sessions.

Build with `python tools/android_build.py :app:assemblePreview :app:assembleDebugAndroidTest :app:lintPreview :reels-core:verifyReels --arch arm64-v8a`. Use `--write-locks` only for a reviewed native dependency introduction/refresh; routine builds consume committed locks. Preview uses the existing development key/debug identity, debug-matched C++ dependencies, bundled JS/Hermes and disabled dev support; Metro is unnecessary. It is not a release-signed distribution APK.

`python -m unittest discover -s tools/tests -p test_reels_phone.py` verifies host ownership/failure safeguards. `python tools/reels_phone.py qualify --adb <existing-adb>` requires one authorized physical ARM64 phone, verifies APK identity/signatures/bundle/permissions, upgrades the main preview without clearing history, refuses a pre-existing test package, runs unique-key/directory synthetic storage checks and own-window React Native navigation/capture, then removes only its installed test package. It leaves the preview installed. Private screenshots/results stay under ignored `.cache/reels-mvp`; no system/target UI capture or raw phone output. No grants/settings/server resets or emulator fallback.

After the person manually enables the disclosed sources/accessibility and separately starts the structure check, `python tools/reels_phone.py structure --label reels-entry` reads only the fixed private metadata file through `run-as`, strictly validates bounded fields and saves an ignored per-context snapshot. A missing file is not consent. Labels identify the person's manually confirmed context; use target navigation/swipes only within explicit user authorization and never infer accuracy from synthetic fixtures. The production service performs no actions. `install` upgrades only the bundled main preview. See [State](STATE.md) for actual results and remaining detector work.

The updated temporary notice discloses scroll-source bounds, event sequence and relative timing in addition to bounded media/ancestor geometry. Obtain a fresh manual diagnostic choice before a target trial; do not carry a prior check across installation/instrumentation. The host accepts historical flat/geometry-1 files and the exact association-1 extension. Association stability brackets delivered scroll callbacks during one traversal; it is not an atomic Android snapshot or qualified post identity. Do not sum relative deltas into exposure counts. No window ID or absolute timing is exported. [The adapter guide](SOCIAL-ADAPTERS.md#home-feed-diagnostic-increment) defines bounds and remaining qualification.

ADR-042 subsequently authorizes the developer to operate already-disclosed debug-phone controls through adb, including relevant permission/source/display/diagnostic steps, without repeated handoffs. Keep product consent flows and preserve other services/grants. For an explicitly authorized dedicated-viewer progression trial, the test-only `reels-live-progress` instrumentation mode ends the completed temporary structure check, verifies source/service/display readiness, signals `progress_ready=1`, and compares own aggregate snapshots over twelve seconds. Only then may the host perform the authorized controlled gestures. It returns a bounded exposure delta and category-conservation flag, never personal totals; the test itself reads no target and performs no gesture. Require unchanged day/configuration, explicit success/result `-1`, owned test cleanup and a visible counter afterward. This is live progress evidence, not full detector accuracy or a synthetic fixture result.

Start with native capability status and last-success time, compare consent/configuration revision to current grant, then inspect bounded redacted local reason codes. Distinguish UI stale cache, missing source events, native process stop, Android force-stop and unsupported detector versions. Test with JS deliberately stopped to prove native independence. Use synthetic fixture apps/node structures; do not capture personal app trees.

Reproduce clock/DST/reboot/revocation failures using injected clocks and dedicated emulators/test devices. Do not change the shared machine timezone, kill shared Node/Java processes, reset shared adb state or alter other repositories. If an emulator/device is in use by another agent, select an isolated target and explicit serial. Keep diagnostics, profiles, traces and real-device captures outside Git. Privacy and data-loss verification takes precedence over making a demo look complete.
