# Kotlin analytics core

AS-035 implements a **standalone calculation slice for the mobile app**. This is not an Android application, collector, detector, database, UI, permission flow or web client. No device history is read. All test/example input is synthetic.

The source implements interval union and civil-day splitting; a disjoint app/surface time breakdown; qualified-exposure replay deduplication; separate interaction intents and observed UI outcomes; canonical UID/transport byte totals; calendar rollup previews; and user-chosen time-equivalent arithmetic. See [analytics semantics](../../docs/ANALYTICS-SPEC.md), [metric catalog](../../docs/METRIC-CATALOG.md) and [current state](../../docs/STATE.md).

Run with JDK 17 or newer, Python 3.11 or newer and pnpm:

```powershell
pnpm install --frozen-lockfile --ignore-scripts
pnpm test
pnpm run example
```

The first test run downloads the latest stable Kotlin compiler resolved on 2026-10-27 (2.4.21), checks its reviewed size and SHA-256, and extracts it only into ignored repository `.cache/analytics-tools`. Optional Android execution also uses isolated latest stable R8/D8 9.5.28, verified against its pinned hash; it does not use/modify the machine's older SDK D8. No global install, PATH change or Gradle version substitution occurs. Compiler standard library/runtime comes from that same verified distribution. [Tool evidence](../../docs/evidence/analytics-toolchain.json) records sources, licenses and the unresolved latest Gradle/Kotlin documented-range mismatch. pnpm is exactly pinned; there are no JavaScript dependencies.

From the repository root, `python tools/test_analytics.py --offline` repeats compilation/tests without downloads once the tool archive is cached. Test bytecode/output stays in ignored `.cache/analytics-build`. The test runner is a small executable synthetic suite using checked assertions, requiring no additional test framework. Failures return a nonzero process status, and compiler warnings fail the build.

The source uses domain-specific packages under `src/main/kotlin` and tests under `src/test/kotlin`. Run `python tools/check_architecture.py` at root for package/import boundaries. `python tools/test_analytics.py --device --sdk <existing-sdk-path>` additionally runs synthetic bytecode on exactly one authorized Android target, without app installation, grants, phone history access or shared adb changes; its uniquely named remote file is cleaned up. This is calculation evidence, not screen/collector verification. D8 warnings fail the command, with bounded console output and a local ignored diagnostics file.

Callers must supply consented, normalized, bounded snapshot inputs with stable identities. The core does not establish Android source fidelity, permissions, completeness, detector qualification, privacy, durability or successful server actions. Exposure results are estimates. Interaction outcomes mean observed UI state, not platform acknowledgement. Network totals do not identify URLs, content or bytes per Reels surface. Rollups preview additive facts only; they do not delete, persist, restore or encrypt anything. Missing daily partitions are not synthesized; the future query layer must explicitly provide coverage/unknown partitions for every eligible period.

These files will move into the Android domain module during AS-004/AS-008 integration after the latest toolchain gate clears. Room remains the planned single history/configuration authority. This library creates no second persistent store and bypasses no Android release gate.
