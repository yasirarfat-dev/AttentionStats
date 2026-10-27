# Dependencies and toolchain

**Status:** AS-002 probes retained; AS-004 latest direct packages pinned in an experimental scaffold. Native qualification remains open. Updated 2026-10-27.
**Responsibility:** Owns package manager, version policy, licenses, compatibility evidence and exceptions.
**Related specifications:** [Development](DEVELOPMENT.md), [Architecture](ARCHITECTURE.md), [Security](SECURITY.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** F-03 and F-04; release age explicitly overridden by the user, template tooling conflicts remediated in the scaffold, native build/runtime qualification and Room encryption remain open.

## AS-006 crypto and encrypted-store review

Reviewed **2026-10-27** against primary release docs, Google/Maven Central metadata/POMs and isolated artifact inspection. [Structured evidence](evidence/crypto-storage-review.json) records eight stable candidate resolutions and four artifact hashes checked against repository digests. [ADR-031 design](CRYPTO-ARCHIVE-DESIGN.md) selects Room 3 runtime/compiler **3.0.3** (Apache-2.0), SQLCipher Android Community **4.19.1** (BSD-style plus bundled notices), AndroidX SQLite **2.7.1** (Apache-2.0), KSP **2.3.12** (Apache-2.0), and Tink Android **1.24.0** (Apache-2.0) for prototype qualification. Bouncy Castle bcprov-jdk18on **1.86** (MIT) is an optional passphrase-evaluation candidate only; Room 2.8.5 is comparison evidence, not a fallback. Metadata latest/release tags can name prereleases; native policy selects stable numeric releases, excluding Room 3.1 alpha and SQLCipher 5 beta. Re-resolve before installation.

The inspected Room/SQLCipher AARs declare minSdk 23, below the app's 24; SQLCipher requires compileSdk ≥36, below the app's 37. Its AAR includes SQLCipherDriver and four ABIs. Tink documents Java 11/API-24 support. These checks do not establish Gradle/KSP/Kotlin, native-page alignment, API-floor behavior, connection-pool semantics, encryption, memory limits or release-shrinker compatibility. SQLCipher's upstream tests use earlier Room/KSP than the selected candidates; actual latest-stack compilation remains required. Its tagged README also lags registry versions. Driver key-array retention and default corruption-handler deletion/log branches require explicit adapter handling. No forced versions, older fallback or metadata suppression is authorized.

No candidate was installed into the application or added to its manifest/lock/catalog. The eight rows do not modify the historical npm candidate ledger. Build, device-resource, vectors, corruption/key-loss and crash-recovery outcomes are **not run**. Downloads are ignored local research artifacts, not published binaries or a supply-chain/security audit. License notices, resolved transitives and packaging must be reviewed at actual installation.

## Required policy

Use **pnpm** for application packages, scripts and lockfiles. At introduction or an intentional dependency refresh, resolve every direct runtime and development package with `package@latest`, including React Native, React, TypeScript, CLI and tooling. Record date, selector, exact result, license, engine/peer constraints and actual compatibility evidence. A latest tag is a selection rule, not proof that the latest versions work together. Generated template pins must be audited against latest before accepting the scaffold.

Do not silently use older versions, force peer resolution, disable security checks, or pin a historical Nitro/toolchain version to make a build work. Record the conflict, upstream evidence, proposed remediation and any explicitly authorized exception in [Decisions](DECISIONS.md); until resolved the affected implementation is blocked. Use project-local tool isolation and do not change global pnpm/Node/Java or other agents' projects. All package installation is deferred to tracked implementation tasks.

Commit `pnpm-lock.yaml` and the exact resolved pnpm `packageManager` value once there is an app manifest. Routine builds use `pnpm install --frozen-lockfile`; they do not re-resolve latest on every invocation. Refresh through a dedicated reviewed task with `pnpm update --latest` plus explicit audit of all direct packages. Transitive dependencies follow upstream constraints and the lockfile; do not force all transitives to their own latest against library constraints. If the user requests a stricter transitive policy, surface incompatibilities rather than overriding silently.

## AS-035 native calculation tools

Resolved **2026-10-27**; [machine-readable evidence](evidence/analytics-toolchain.json) pins artifact URLs/sizes/SHA-256, selection sources and compatibility scope. No JS runtime/development dependency is added to the Kotlin slice; its small private pnpm manifest supplies test/example commands and a frozen manager lock.

| Tool | Selected latest stable | License | Actual compatibility evidence |
|---|---|---|---|
| pnpm | 12.15.0 via `pnpm@latest` | MIT | Existing isolated project manager and frozen lock; no alternate package manager |
| Kotlin standalone compiler / matching bundled stdlib | 2.4.21 | Apache-2.0 | Official latest stable release and verified compiler archive; JVM-17 compilation with warnings as errors; 37 calculation checks on local JDK 17 and Android 16 ART |
| R8/D8 standalone development tool | 9.5.28 | BSD-3-Clause | Latest stable numeric Google Maven version, prereleases excluded; upstream SHA-1 checked at resolution and SHA-256 pinned; synthetic DEX compilation with no D8 warnings and 37 physical-device checks |

Use official [Kotlin CLI distribution](https://kotlinlang.org/docs/command-line.html) and [Google Maven D8 instructions](https://r8.googlesource.com/r8/+/refs/heads/main/README.md); tool downloads are development bootstrap, not app network behavior. R8 is not bundled into the product. The machine's older SDK D8 produced Kotlin-metadata warnings; it was left untouched and replaced only for this task by the isolated latest tool. [R8 license](https://r8.googlesource.com/r8/+/refs/heads/main/LICENSE) applies to that tool.

Latest Gradle was 9.8.2 while [Kotlin's fully supported Gradle range](https://kotlinlang.org/docs/gradle-configure-project.html) for 2.4.21 ended at 9.7.0. No Gradle/plugin pairing, older workaround, React Native stack, Android release build, shrinker behavior or application support is established by the standalone calculation tests. F-03 remains open. Registry latest RN 0.88.0 and CLI 20.2.0 were rechecked unchanged on 2026-10-27; that does not resolve their build compatibility.

## Mobile candidate ledger

Resolved from public npm registry `latest` endpoints on **2026-10-26**, before the session crossed into 2026-10-27. Machine-readable peer/engine/source evidence: [dependency-candidates.json](evidence/dependency-candidates.json). These are snapshots, not application pins; re-resolve at implementation time. All rows have install/build status **not run**.

| Package | Exact latest candidate | License | Role / evaluation |
|---|---|---|---|
| pnpm | 12.15.0 | MIT | Required manager; isolate version per project |
| react | 19.3.1 | MIT | Meets RN candidate's declared `^19.3.0` peer |
| @types/react | 19.3.0 | MIT | Meets RN declared `^19.1.1`; compiler check pending |
| react-native | 0.88.0 | MIT | Community CLI/New Architecture; engines `^22.13.0 || ^24.3.0 || >=26.0.0` |
| @react-native-community/cli | 20.2.0 | MIT | Documentation range does not yet establish 0.88 support; unresolved |
| typescript | 7.0.2 | Apache-2.0 | Strict types; latest RN/ESLint/test pipeline compatibility unverified |
| @react-navigation/native | 7.5.1 | MIT | Preferred navigation; declared React/RN peers permit candidates |
| @react-navigation/native-stack | 7.21.0 | MIT | Requires screens ≥4 and safe-area ≥4; candidates satisfy declared minima |
| @react-navigation/bottom-tabs | 7.21.0 | MIT | Same peer minima; device navigation behavior untested |
| react-native-unistyles | 3.6.0 | MIT | New Architecture and Nitro; exact Nitro pairing requires upstream evidence |
| react-native-reanimated | 4.7.1 | MIT | Declared RN 0.86–0.88 and Worklets 0.13.x |
| react-native-gesture-handler | 3.4.0 | MIT | Preferred gestures; wildcard peers are not native compatibility proof |
| @shopify/flash-list | 2.3.3 | MIT | Long-history lists; recycling/accessibility test required |
| react-native-svg | 15.16.0 | MIT | Chart rendering; native build and accessibility wrapper required |
| react-native-mmkv | 4.3.2 | MIT | Non-sensitive UI preferences only; Nitro compatibility pending |
| zod | 4.6.7 | MIT | Typed input validation; never substitutes for native validation |
| react-native-screens | 4.30.0 | MIT | Navigation peer; native/New Architecture validation pending |
| react-native-safe-area-context | 5.10.1 | MIT | Navigation/insets peer; edge-to-edge validation pending |
| react-native-worklets | 0.13.1 | MIT | Meets Reanimated 4.7.1 declared 0.13.x peer |
| react-native-nitro-modules | 0.37.1 | MIT | Candidate only; do not substitute an older release silently |
| react-native-edge-to-edge | 1.8.2 | MIT | Evaluated, **not selected**; upstream Unistyles guide says deprecated in favor of built-in edge-to-edge |
| @react-native/normalize-colors | 0.88.0 | MIT | Declared Unistyles peer; installation need and pairing require check |

The table above remains the historical foundation snapshot. AS-002 now reviews the additional template/build/test/format candidates below; no application pins or working complete stack have been accepted. AS-004 must re-resolve current latest and verify the complete graph before accepting a scaffold.

## AS-002 isolated compatibility probes

Reviewed **2026-10-27 Asia/Dhaka** (2026-10-26 UTC). [Structured evidence](evidence/mobile-stack-probe.json) contains 44 latest registry records, peers/engines/licenses, verified inspection-artifact hashes, original template selections, two observed installation graphs and three reproduction profiles. The original 22 candidates were rechecked unchanged. These are development probes in ignored local caches, not an application manifest, product lockfile or installable build.

| Check | Observed result | Consequence |
|---|---|---|
| Latest template packages, strict peers, install scripts disabled | Failed: Babel 7 plugins require core 7, RN lint config excludes ESLint 10, typescript-eslint excludes TypeScript 7 | A mechanical template upgrade to latest is not accepted |
| Babel core 8.0.9 with RN preset 0.88.0 | Transform failed: plugin requires `^7.0.0-0` | Root Babel 8 cannot drive the current preset |
| ESLint 10.13.0 with RN flat config 0.88.0 | Failed: typescript-eslint does not support TS 7.0 | Changing only the ESLint configuration format does not fix the parser |
| TypeScript 7.0.2 with RN TypeScript config 0.88.0 | Small pure TypeScript fixture passed | Compiler check only; not full application type coverage |
| Reduced direct tooling: remove root Babel core/preset-env and RN ESLint config/ESLint; add Oxlint 1.89.0 | Strict peer install, TypeScript fixture, Oxlint fixture and RN-owned Babel transform passed | Candidate remediation without older direct packages; not yet accepted for the app |
| Installed direct versions against reviewed latest | Both profiles fail: Worklets installed 0.13.0, registry latest is 0.13.1 | Passing installation is insufficient for the latest-only requirement |
| Exact latest Worklets with explicit 1,440-minute age protection | Failed: `ERR_PNPM_NO_MATURE_MATCHING_VERSION` | Wait for eligibility and re-resolve; no age exception or bypass configured |

The reduced probe removes unnecessary **direct** Babel core/preset-env declarations; React Native's transformer retains its own constrained Babel 7 dependencies. This follows the existing transitive-dependency policy. It does not establish a Babel 8 migration. [TypeScript 7 lacks the legacy compiler API](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/#running-side-by-side-with-typescript-6.0) used by typescript-eslint, so [Oxlint](https://oxc.rs/docs/guide/usage/linter) is evaluated as an alternative; the small syntax-lint result does not establish type-aware or feature-boundary rule coverage. Jest, full Metro bundling, application helpers/runtime imports and all native libraries still need tests.

[pnpm defaults to a 24-hour release age](https://pnpm.io/settings/dependency-resolution#minimumreleaseage). Registry evidence dates Worklets 0.13.1 to `2026-10-26T15:24:38.916Z`; its corresponding eligibility is **2026-10-27 21:24:38.916 Asia/Dhaka**, assuming unchanged metadata/settings. This is not an appointment or guaranteed gate clearance: latest may change before the next attempt. Recheck registry versions and the installed graph; preserve peer, age and script protections. The current 0.13.0 probe graphs cannot be accepted as latest-only and their lockfiles remain ignored.

The inspected Unistyles 3.6.0 release README requires Nitro ≥0.37.1 for Unistyles ≥3.4, and its development manifest pins 0.37.1. This resolves the previous **documented minimum** question. Its package marks edge-to-edge as an optional peer, matching built-in `edgeToEdgeEnabled=true`; the deprecated extra library was omitted. MMKV 4.3.2's development manifest uses Nitro 0.35.9 and its public peer is wildcard; neither this nor the Unistyles minimum establishes MMKV/native ABI compatibility with 0.37.1.

CLI 20.2.0 is selected by the RN 0.88.0 template itself, narrowing the CLI README range mismatch to missing documentation/build evidence rather than a demonstrated generation failure. CLI `init --help` ran; it lists npm/yarn/bun, not pnpm. Inspected CLI source installs the temporary template through its package-manager helper even with `--skip-install`. No CLI generation was attempted using an alternate manager. A pnpm-only materialization path needs verification before AS-004; do not assume `pnpm dlx` makes the generator's internal installs use pnpm.

The template specifies Gradle 9.4.1, Kotlin 2.2.0, SDK Build Tools 37.0.0 and NDK 27.1.12297006; its RN Gradle plugin catalog uses AGP 9.2.1. Reviewed latest stable Gradle/AGP/Kotlin are 9.8.2/9.4.1/2.4.21. AGP's [Maven metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) already lists 9.4.1 while its release-notes page describes the 9.4.0 baseline; the registry determines the latest patch candidate. Kotlin lists full support only through Gradle 9.7.0/AGP 9.3.1 and permits newer versions with possible warnings or missing behavior. This is a qualification risk, not a measured build failure. The existing machine has JDK 17.0.20.1, platform android-37.0 and Build Tools 36.0.0, without NDK/CMake. No SDK/tool replacement or native build was attempted while the selection gate is unresolved.

Additional exact latest candidates inspected on this date (all **MIT**; source/engine/peer evidence in the structured report):

| Package | Latest candidate | Scope |
|---|---|---|
| @react-native-community/template | 0.88.0 | Inspected generator input |
| @react-native/gradle-plugin | 0.88.0 | Inspected RN build-plugin catalog |
| @react-native/codegen | 0.88.0 | Reviewed metadata; no Codegen round trip |
| @react-native/babel-preset | 0.88.0 | Template/reduced transform probes |
| @react-native/eslint-config | 0.88.0 | Template probe; rejected with latest tools |
| @react-native/metro-config | 0.88.0 | RN transformer owner in reduced probe |
| @react-native/typescript-config | 0.88.0 | TypeScript fixture |
| @babel/core | 8.0.9 | Template probe; omitted from reduced direct set |
| @babel/preset-env | 8.0.7 | Template probe; omitted from reduced direct set |
| @babel/runtime | 8.0.8 | Both probes; full bundled helper use untested |
| @react-native-community/cli-platform-android | 20.2.0 | Both probes; no native build |
| @react-native-community/cli-platform-ios | 20.2.0 | Template parity only; no iOS work |
| @types/jest | 30.0.0 | Both probes; no Jest suite |
| @types/react-test-renderer | 19.3.0 | Both probes; no renderer test |
| eslint | 10.13.0 | Template probe; omitted from reduced direct set |
| jest | 30.5.2 | Both probes; RN preset integration untested |
| prettier | 3.9.10 | Both probes; no accepted app format setup |
| react-test-renderer | 19.3.1 | Both probes; no renderer test |
| nitrogen | 0.37.1 | Metadata only; not added as a direct probe dependency |
| @react-native/new-app-screen | 0.88.0 | Template parity only; no product screen |
| @react-native/jest-preset | 0.88.0 | Both probes; its Jest 29 transitives need qualification with Jest 30 |
| oxlint | 1.89.0 | Reduced probe; syntax lint only |

`python tools/probe_mobile_stack.py` checks evidence consistency, not runtime support. [Development](DEVELOPMENT.md#as-002-compatibility-probe-reproduction) gives isolated preparation/install/audit commands. AS-002 remains blocked by the latest-selection gate and incomplete native qualification; F-03 and AS-004 remain open/blocked respectively. No runtime capability changes status.

## Primary compatibility evidence

- [Reanimated compatibility](https://docs.swmansion.com/react-native-reanimated/docs/guides/compatibility/) lists 4.7.x with RN 0.86–0.88 and Worklets 0.13.x; this supports the candidate pairing only, not an application build claim.
- [Unistyles prerequisites](https://unistyl.es/v3/start/getting-started/) require New Architecture, RN ≥0.81 and Nitro. Its [3.6.0 release README](https://github.com/jpudysz/react-native-unistyles/blob/v3.6.0/README.md) documents Nitro ≥0.37.1; the inspected package marks edge-to-edge optional. Native pairing still needs a build and device smoke test.
- [Community CLI compatibility](https://github.com/react-native-community/cli#compatibility) lists CLI 20 with RN versions through 0.85, whereas registry latest RN is 0.88. The inspected RN 0.88 template selects CLI 20.2.0 itself. Build compatibility and pnpm-only generation remain unverified; this is not permission to downgrade.
- [FlashList](https://github.com/Shopify/flash-list) and [MMKV](https://github.com/margelo/react-native-mmkv) require native integration review. Inspect the exact installed release and its peers, not generic marketing benchmarks.
- [Room releases](https://developer.android.com/jetpack/androidx/releases/room) list stable 2.8.5 at review. Room is Apache-2.0; validate artifact POM/license and Kotlin/KSP/SQLite-driver compatibility at introduction. Native dependencies use the latest stable vendor release, explicit Gradle version catalog pins and reproducible resolution; `package@latest` applies to npm packages, not Maven coordinates.
- [pnpm installation](https://pnpm.io/installation) identifies pnpm 12 as the current release line. Review its project-local version and build-script approval behavior before enabling native dependency install scripts.

## Compatibility gate

The independent AS-033 [design study](../design/prototype/README.md) has no runtime dependencies. On 2026-10-27 its new direct **development-only** tools resolved at latest: pnpm 12.15.0 (MIT), @playwright/test 1.64.1 (Apache-2.0, Node ≥20) and @axe-core/playwright 4.14.0 (MPL-2.0, Node ≥22.12, playwright-core peer ≥1). [Registry evidence](../design/prototype/tooling-evidence.json) and the study's committed lockfile record this separate graph; Node v26.12.1 on Windows satisfies the engines. Frozen install with scripts disabled passes. Browser verification uses these tools; no Android dependency/build is thereby verified.

pnpm 12's lockfile contains a manager/environment document followed by the project graph; review both for supply-chain inspection. [Official format](https://github.com/pnpm/pnpm.io/blob/main/docs/lockfile.md). The runtime is original HTML/CSS/JS and Node/browser built-ins, with local system fonts and no remote assets. Tools' upstream notices remain in their installed packages; no third-party source/assets are copied into the app or repository. No global tool configuration was changed. Mobile candidate rows above remain the dated foundation snapshot and must be refreshed by AS-002.

Record exact OS/architecture, Node/pnpm/JDK/SDK/NDK/CMake/Gradle/AGP/Kotlin/KSP versions. Use latest stable tooling compatible with the selected latest RN template; an older generated version is a recorded conflict, not silently accepted. Run clean native debug/release compilation with New Architecture, Codegen round trip, navigation/gestures/theme/list/chart smoke tests, background collection with JS absent, MMKV restart behavior, and Room migration/encryption tests. Document outcomes per dependency and artifact.

Review licenses and notices, native ABI/page-size support, package scripts, transitive network behavior and known vulnerabilities. No broad pnpm hoisting or install-script allowance as a reflex; any needed configuration is minimal, project-local and justified. No accepted application lockfile exists; isolated probe lockfiles remain ignored until a coherent stack is accepted.

## AS-004 scaffold dependency review

**Resolved 2026-10-27.** [Installed metadata](evidence/mobile-scaffold.json) records 33 direct application/tool packages and pnpm, their `@latest` selectors, exact versions, licenses, engines, peers and registry integrity. Root manifest pins these results, with both pnpm boot and project graphs in the committed lockfile. Worklets 0.13.1 now installs at reviewed latest under the explicit user-authorized root age-zero exception (ADR-029). The historical age-protected probes above retain their original failures and are not rewritten.

Strict peers and disabled installation scripts remain enabled. No direct Babel 8 core or template ESLint/TypeScript-parser mismatch is introduced. RN owns its constrained transitive Babel 7; latest Oxlint/Prettier/TypeScript provide current tooling. Deprecated transitive glob/inflight warnings remain in upstream graphs and are not silently replaced. The 9-case bootstrap response suite passes with Jest 30; pnpm's isolated paths require the preset transform allowlist to include `.pnpm`. Zod's published CommonJS export is selected with a typed `require`, following [Metro's documented export conditions](https://metrobundler.dev/docs/package-exports/), because its ESM export namespace failed RN's current preset. A production Android Metro bundle passes after that change.

| Direct package | Latest pin | License | Role |
|---|---|---|---|
| react | 19.3.1 | MIT | dependencies |
| react-native | 0.88.0 | MIT | dependencies |
| react-native-safe-area-context | 5.10.1 | MIT | dependencies |
| @react-navigation/native | 7.5.1 | MIT | dependencies |
| @react-navigation/native-stack | 7.21.0 | MIT | dependencies |
| @react-navigation/bottom-tabs | 7.21.0 | MIT | dependencies |
| react-native-unistyles | 3.6.0 | MIT | dependencies |
| react-native-reanimated | 4.7.1 | MIT | dependencies |
| react-native-gesture-handler | 3.4.0 | MIT | dependencies |
| @shopify/flash-list | 2.3.3 | MIT | dependencies |
| react-native-svg | 15.16.0 | MIT | dependencies |
| react-native-mmkv | 4.3.2 | MIT | dependencies |
| zod | 4.6.7 | MIT | dependencies |
| react-native-screens | 4.30.0 | MIT | dependencies |
| react-native-worklets | 0.13.1 | MIT | dependencies |
| react-native-nitro-modules | 0.37.1 | MIT | dependencies |
| @react-native/normalize-colors | 0.88.0 | MIT | dependencies |
| @babel/runtime | 8.0.8 | MIT | devDependencies |
| @react-native-community/cli | 20.2.0 | MIT | devDependencies |
| @react-native-community/cli-platform-android | 20.2.0 | MIT | devDependencies |
| @react-native-community/cli-platform-ios | 20.2.0 | MIT | devDependencies |
| @react-native/babel-preset | 0.88.0 | MIT | devDependencies |
| @react-native/jest-preset | 0.88.0 | MIT | devDependencies |
| @react-native/metro-config | 0.88.0 | MIT | devDependencies |
| @react-native/typescript-config | 0.88.0 | MIT | devDependencies |
| @types/jest | 30.0.0 | MIT | devDependencies |
| @types/react | 19.3.0 | MIT | devDependencies |
| jest | 30.5.2 | MIT | devDependencies |
| prettier | 3.9.10 | MIT | devDependencies |
| typescript | 7.0.2 | Apache-2.0 | devDependencies |
| oxlint | 1.89.0 | MIT | devDependencies |
| @react-native/gradle-plugin | 0.88.0 | MIT | devDependencies |
| @react-native/codegen | 0.88.0 | MIT | devDependencies |
| pnpm | 12.15.0 | MIT | packageManager |

The upstream CLI/template were installed at latest with pnpm in an ignored bootstrap directory. CLI's own copy/rename helpers materialized an empty staging directory; reviewed native/config files were then merged without replacing repository documentation. No npm/yarn/bun installer, template post-init script or package lifecycle script ran. Template MIT terms remain in [the license file](../licenses/react-native-template.txt) and [NOTICE](../NOTICE). Full `init` remains unsupported for pnpm; this is an explicit template-materialization adaptation, not a claim that its installer gained support.

Latest stable native selections are Gradle 9.8.2 (verified official distribution SHA-256), AGP 9.4.1 and Kotlin 2.4.21 from official stable-release metadata, NDK r30 / 30.0.16248370, Build Tools 37.0.0 and CMake 4.1.2. The SDK platform is android-37.0, compile/target major API 37. The repository-local Windows archives were checked against official repository XML sizes/SHA-1; these hashes are resolution evidence, not a complete supply-chain audit. SDK metadata also contains newer minor platform releases; compiling against base major 37.0 is an explicit API baseline, not a claim to use every newest minor SDK. AGP/Kotlin latest exceed Kotlin's fully-supported table; actual builds decide compatibility.

A real unmodified-plugin attempt fails because RNGP 0.88.0 compiles with Kotlin 2.2 and cannot read Gradle 9.8's Kotlin 2.4 metadata. The reviewed [pnpm patch](../patches/@react-native__gradle-plugin@0.88.0.patch) upgrades only RNGP's compiler catalog pin to 2.4.21; no metadata suppression, peer forcing, native source replacement or downgrade is used. Its hash is audited and lockfile patch identity committed. The patched RNGP compiles locally; full app/ABI qualification is separate. All Android submodules receive the reviewed NDK/CMake pins so Unistyles cannot silently inherit AGP's older default NDK. Upstream AGP 9 legacy DSL/Kotlin plugin deprecations remain visible; Gradle 10 is unsupported.

`python tools/check_mobile.py --installed` checks the dated review against root direct pins, the final lockfile importer and actual installed versions. It is not a live latest check. Native Codegen/debug/release/live outcomes and remaining gates are recorded in [State](STATE.md), [Testing](TESTING.md) and the [dated log](IMPLEMENTATION_LOG/2026-10-27.md#continuation--as-004-mobile-scaffold). No Room/crypto dependency is installed or qualified by this slice.

The pnpm layout also requires `react.hermesCommand` to resolve `hermes-compiler` through RN's own package dependency. RN 0.88 pins compiler 260318099.0.4; keeping that transitive pairing avoids arbitrary bytecode/compiler replacement. Debug development uses adb loopback and explicitly removes framework-inherited local-network/overlay permissions. Platform API-37 debug/unsigned release builds pass on x86_64; ARM64 debug builds through Community CLI and the subsequent ARM64 unsigned release qualification also pass. Round-trip/navigation checks passed on an API-36 ARM64 phone and an API-37 emulator before the user's phone-only testing instruction. Full library functionality, minimum-API runtime and iOS remain open; the subsequent clean ARM64 native CI run passes. The continuation below configures core library desugaring; API-24/25 runtime qualification still needs a matching physical device.

## Android core library desugaring

AS-002 continuation resolves `com.android.tools:desugar_jdk_libs` **2.1.5** as the latest stable release from [Google Maven metadata](https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs/maven-metadata.xml) on 2026-10-27. The [published POM](https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs/2.1.5/desugar_jdk_libs-2.1.5.pom) declares GPL-2.0 with the Classpath Exception and the matching 2.1.5 configuration dependency. Artifact size/SHA-256 and upstream notice source/hashes are retained in [scaffold evidence](evidence/mobile-scaffold.json); license text is retained under `licenses/` and referenced by NOTICE. No native binary is published.

The app's Gradle version catalog pins this native dependency and `compileOptions` enables core library desugaring. [Android's Java API guidance](https://developer.android.com/studio/write/java8-support#library-desugaring) describes the backport mechanism. The pure Kotlin module remains JVM/domain-only; the consuming Android application owns DEX rewriting and runtime packaging. A test-only instrumentation runner compiles the existing synthetic suite through that same pipeline. Build, transformed DEX and current-phone execution evidence do not replace a minimum-API physical-device test; Android 24/25 remain unqualified.

The ARM64 native CI job uses the existing immutable action pins plus latest stable `actions/setup-java` 6.1.0 (MIT), resolved from its official GitHub release/tag on 2026-10-27 and pinned to `b843ebf9b913146b38da2eaa7aeddf76074ae1ad`. JDK 17 remains the existing Android toolchain baseline. The job installs exact platform 37.0, Build Tools 37.0.0, NDK 30.0.16248370 and CMake 4.1.2 on a disposable runner, audits the frozen pnpm graph, compiles debug/unsigned release/test APKs and runs Gradle calculations/app lint. No emulator, device access, signing credentials or binary publication is configured. The first [clean native CI run](https://github.com/yasirarfat-dev/AttentionStats/actions/runs/40449896581) passed all compilation/calculation/lint/artifact checks on Linux; runtime acceptance and remaining gates stay separate in State/log.
