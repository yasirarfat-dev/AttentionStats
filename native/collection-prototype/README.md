# Synthetic collection reconciliation prototype

AS-003 experiment, separate from the analytics domain and absent from application dependencies. It has no Android adapter, scheduler, source grant, durable store, bridge operation or product UI. The injected in-memory coordinator lives only in the tests. F-02/F-09 remain open; see [collection qualification](../../docs/COLLECTION-FEASIBILITY.md).

The experiment processes one pinned half-open time slice wholly inside an enabled, granted, first-unlocked scope and outside pause/deletion fences. The caller must split permitted ranges; the prototype refuses a slice that crosses a gap. Raw iteration, slice duration, scalar lengths and cooperative elapsed-time checks are bounded. A null, denied, failed, cancelled, truncated, invalid or clock-anomalous read commits nothing. A successful empty read can update query health/position; it makes no metric-zero or complete-coverage assertion. Source/port implementations must themselves bound response allocation and blocking work; caller checks cannot interrupt a synchronous platform call.

For each timestamp/package/class/transition signature, keep the maximum multiplicity seen in a completed query. Add local occurrence ordinals only when that multiplicity grows. Shifted windows and reordered groups therefore do not depend on query-local indexes. Preserve prior observations when a later query returns fewer and flag the discrepancy. Never commit a partial equal-time group or move past a truncated slice.

This is an experimental matching policy, **not exact identity or an established count lower bound**. Indistinguishable replacements with equal counts cannot be detected; a growing group cannot identify which real occurrence is new. Overlapping prior groups produce `UNRESOLVED_REPLAY`. Ordinals belong to the supplied source/installation/store scope, not to Android's event API. No causal ordering of equal-time events, historical elapsed time, zone or boot evidence is invented. Durable identity, retention correction and provenance schema remain undecided.

The commit port must atomically compare scope (including generation, installation, source, consent revision, filters and fences) and history revision, then commit facts, query position and health together. Tests simulate refusals and the race after the final caller check. They do not prove Room transactions, disk/crash recovery, source fidelity or timely runtime cancellation. There is no adaptive slice splitting, scheduling cadence, accepted resource budget or cross-package qualification yet.

Run with the existing reviewed Kotlin toolchain, adding no dependency:

```powershell
python tools/test_reconciliation.py --offline
python tools/android_build.py :collection-prototype:verifyReconciliation --arch arm64-v8a
python tools/check_architecture.py
```

Omit `--offline` only to bootstrap the same size/SHA-256-verified compiler archive already recorded for analytics. The Gradle module references these same source/test folders. Both commands execute synthetic JVM fixtures only; they neither query nor install anything on a phone. CI runs the same suite. Physical source/lifecycle qualification and production AS-009/AS-010 admission remain separate.
