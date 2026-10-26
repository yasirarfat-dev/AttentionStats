# Local development

**Status:** Documentation workflow runnable; application setup is future work. Updated 2026-10-27.
**Responsibility:** Defines local setup, reproducible commands, isolation and debugging.
**Related specifications:** [Dependencies](DEPENDENCIES.md), [Testing](TESTING.md), [Structure](FILE-STRUCTURE.md), [Agent rules](AGENT-RULES.md).
**Unresolved feasibility:** F-02–F-04 in [Decisions](DECISIONS.md); no Android application build has run.

## Work on this foundation

Open `C:\home\biobot\projects\AttentionStats` on this machine, or clone [the public repository](https://github.com/yasirarfat-dev/AttentionStats) elsewhere. Read [Index](INDEX.md), [State](STATE.md), [Tracker](FEATURE-TRACKER.md) and relevant specs. Required now: Git and Python 3.11 or newer. No Node package installation, Android SDK, private device data or credentials are required to edit/verify documentation.

```powershell
Set-Location C:\home\biobot\projects\AttentionStats
python tools/check_docs.py
git diff --check
git status --short
```

The checker verifies expected documents, metadata, relative links/anchors, capability/scenario/task coverage, dependency-ledger consistency and prohibited public artifacts. It does not prove Android behavior or external link availability. Manually review public text and policy interpretation. The CI job performs the same offline documentation checks without application builds or secrets.

## Future application setup: AS-004

1. Complete latest-version compatibility work AS-002 and record F-03 evidence. Review [React Native without a framework](https://reactnative.dev/docs/getting-started-without-a-framework) and its [environment requirements](https://reactnative.dev/docs/set-up-your-environment) at execution time. Do not choose Expo or scaffold during this foundation task.
2. Isolate Node/pnpm and Android tooling per project/session. Use `pnpm@latest` in an isolated launcher or local tool directory if needed; do not globally update other agents' tools. Inspect the current CLI help for pnpm and skip-install options before generating in a temporary sibling directory. Avoid overwriting this documentation tree.
3. Resolve `@react-native-community/cli@latest`, `react-native@latest`, React/TypeScript and every direct template/tooling package at latest. Candidate one-shot launcher: `pnpm dlx @react-native-community/cli@latest --help`. Generation syntax is intentionally not prescribed until CLI/RN range evidence is resolved.
4. Review generated files, permanent application ID, native namespace and exact toolchain. Merge the reviewed scaffold into the repository, preserve documentation, remove alternate package-manager artifacts, pin the resolved pnpm version in `packageManager`, and commit `pnpm-lock.yaml`. Do not use `--force` to mask incompatible peers.
5. Add latest preferred UI libraries with explicit `@latest` selectors; audit scripts/licenses/peers and required native build approvals. Configure only justified project-local pnpm settings. Establish scripts for lint, typecheck, tests and Android builds, then replace proposed commands below with verified ones.

## Routine commands after scaffold

These are **future commands** and do not run in this foundation. AS-004 must supply matching scripts and record first successful execution:

```powershell
pnpm install --frozen-lockfile
pnpm exec react-native doctor
pnpm run lint
pnpm run typecheck
pnpm test -- --runInBand
pnpm run start
pnpm run android
```

From `android/`, once the Gradle wrapper exists:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat bundleRelease
```

Release bundling needs local/CI signing configuration owned by release maintainers; never commit keys. No build output or diagnostic upload is automatic. Record actual test/task names if the generated project differs.

## Debugging procedure

Start with native capability status and last-success time, compare consent/configuration revision to current grant, then inspect bounded redacted local reason codes. Distinguish UI stale cache, missing source events, native process stop, Android force-stop and unsupported detector versions. Test with JS deliberately stopped to prove native independence. Use synthetic fixture apps/node structures; do not capture personal app trees.

Reproduce clock/DST/reboot/revocation failures using injected clocks and dedicated emulators/test devices. Do not change the shared machine timezone, kill shared Node/Java processes, reset shared adb state or alter other repositories. If an emulator/device is in use by another agent, select an isolated target and explicit serial. Keep diagnostics, profiles, traces and real-device captures outside Git. Privacy and data-loss verification takes precedence over making a demo look complete.
