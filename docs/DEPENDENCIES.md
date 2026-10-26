# Dependencies and toolchain

**Status:** Latest registry candidates evaluated, not installed or build verified. Updated 2026-10-27.
**Responsibility:** Owns package manager, version policy, licenses, compatibility evidence and exceptions.
**Related specifications:** [Development](DEVELOPMENT.md), [Architecture](ARCHITECTURE.md), [Security](SECURITY.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** F-03 and F-04; CLI/RN documented range mismatch, Nitro pairing, TypeScript/tooling and Room encryption need checks.

## Required policy

Use **pnpm** for application packages, scripts and lockfiles. At introduction or an intentional dependency refresh, resolve every direct runtime and development package with `package@latest`, including React Native, React, TypeScript, CLI and tooling. Record date, selector, exact result, license, engine/peer constraints and actual compatibility evidence. A latest tag is a selection rule, not proof that the latest versions work together. Generated template pins must be audited against latest before accepting the scaffold.

Do not silently use older versions, force peer resolution, disable security checks, or pin a historical Nitro/toolchain version to make a build work. Record the conflict, upstream evidence, proposed remediation and any explicitly authorized exception in [Decisions](DECISIONS.md); until resolved the affected implementation is blocked. Use project-local tool isolation and do not change global pnpm/Node/Java or other agents' projects. All package installation is deferred to tracked implementation tasks.

Commit `pnpm-lock.yaml` and the exact resolved pnpm `packageManager` value once there is an app manifest. Routine builds use `pnpm install --frozen-lockfile`; they do not re-resolve latest on every invocation. Refresh through a dedicated reviewed task with `pnpm update --latest` plus explicit audit of all direct packages. Transitive dependencies follow upstream constraints and the lockfile; do not force all transitives to their own latest against library constraints. If the user requests a stricter transitive policy, surface incompatibilities rather than overriding silently.

## Candidate ledger

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

Additional template/build/test/format packages are not selected yet. AS-004 must resolve them with `@latest`, extend this ledger, review licenses and confirm compatibility before committing the application scaffold. Do not present incomplete tooling as a verified complete stack.

## Primary compatibility evidence

- [Reanimated compatibility](https://docs.swmansion.com/react-native-reanimated/docs/guides/compatibility/) lists 4.7.x with RN 0.86–0.88 and Worklets 0.13.x; this supports the candidate pairing only, not an application build claim.
- [Unistyles prerequisites](https://unistyl.es/v3/start/getting-started/) require New Architecture, RN ≥0.81 and Nitro; its guide asks for exact Nitro pairing. Registry wildcard peers cannot prove that the newest Nitro works. The guide also prefers built-in edge-to-edge over a deprecated extra library; reconcile the registry peer metadata during the spike.
- [Community CLI compatibility](https://github.com/react-native-community/cli#compatibility) currently lists CLI 20 with RN versions through 0.85, whereas registry latest RN is 0.88. Treat this documentation mismatch as unresolved evidence, not proof of incompatibility and not permission to downgrade. Review the current template/CLI source and perform isolated build checks.
- [FlashList](https://github.com/Shopify/flash-list) and [MMKV](https://github.com/margelo/react-native-mmkv) require native integration review. Inspect the exact installed release and its peers, not generic marketing benchmarks.
- [Room releases](https://developer.android.com/jetpack/androidx/releases/room) list stable 2.8.5 at review. Room is Apache-2.0; validate artifact POM/license and Kotlin/KSP/SQLite-driver compatibility at introduction. Native dependencies use the latest stable vendor release, explicit Gradle version catalog pins and reproducible resolution; `package@latest` applies to npm packages, not Maven coordinates.
- [pnpm installation](https://pnpm.io/installation) identifies pnpm 12 as the current release line. Review its project-local version and build-script approval behavior before enabling native dependency install scripts.

## Compatibility gate

Record exact OS/architecture, Node/pnpm/JDK/SDK/NDK/CMake/Gradle/AGP/Kotlin/KSP versions. Use latest stable tooling compatible with the selected latest RN template; an older generated version is a recorded conflict, not silently accepted. Run clean native debug/release compilation with New Architecture, Codegen round trip, navigation/gestures/theme/list/chart smoke tests, background collection with JS absent, MMKV restart behavior, and Room migration/encryption tests. Document outcomes per dependency and artifact.

Review licenses and notices, native ABI/page-size support, package scripts, transitive network behavior and known vulnerabilities. No broad pnpm hoisting or install-script allowance as a reflex; any needed configuration is minimal, project-local and justified. No application lockfile exists in the documentation foundation because no application packages were installed.
