# Feature tracker

**Status:** Documentation foundation published and separate AS-033 study implemented; native app work unimplemented. Updated 2026-10-27.
**Responsibility:** Owns stable task IDs, priorities, dependencies, capability mapping and completion evidence.
**Related specifications:** [Roadmap](ROADMAP.md), [State](STATE.md), [Capabilities](CAPABILITY-MATRIX.md), [Tests](TESTING.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** F-01–F-09 gate relevant tasks; dependencies below are required, not optional recommendations.

Status vocabulary: **planned** = specified future work; **experimental** = feasibility/evidence required; **blocked** = explicit unmet gate; **implemented** = code/artifact and acceptance evidence recorded. Active work is described in State; no app feature is implemented. Priority P0 precedes P1; order within a dependency-ready priority is the listed order. Documentation status does not imply an installable product.

User-directed design continuation prioritizes AS-033 as a separate interaction study under ADR-019/ADR-020. It does not clear native feasibility prerequisites. Resume AS-002 after this reviewable study; AS-018 adopts the direction when its native dependencies are satisfied.

| Task | Priority / stage | Status | Capability coverage | Prerequisites / concrete completion evidence |
|---|---|---|---|---|
| AS-001 Documentation foundation and public repository | P0 / foundation | implemented | All specifications, no runtime capability | 50 reviewed files, passing local checks and GitHub CI; public push/Apache-2.0 verified; [evidence](IMPLEMENTATION_LOG/2026-10-27.md) |
| AS-002 Latest pnpm/RN/toolchain compatibility | P0 / feasibility | experimental | Architecture prerequisites | AS-001; resolve all direct packages at latest, CLI range mismatch, Nitro pairing, TS/tooling; clean debug/release and exact ledger |
| AS-003 Native collection/lifecycle prototype | P0 / feasibility | experimental | C-01–C-04, C-23 | AS-001; public API event/reboot/background/force-stop/OEM evidence, viable permitted service strategy; F-02/F-09 |
| AS-004 React Native Community CLI scaffold | P0 / foundation | blocked | C-30 foundation | AS-002; latest-only coherent stack, pnpm lockfile, namespace decision, working commands; no scaffold in AS-001 |
| AS-005 Surface detector feasibility per target | P0 / feasibility | experimental | C-06, C-07 | AS-003 and AS-007; Facebook/YouTube/Instagram/TikTok separately, no invented selectors; T-06/T-07 |
| AS-006 Encryption and archive design review | P0 / feasibility | experimental | C-24, C-25 | AS-001; crypto suite/KDF/framing, encrypted Room driver, key loss/rotation and resource bounds ADR; F-04 |
| AS-007 Accessibility/distribution feasibility | P0 / feasibility | experimental | C-06, C-16–C-22 | AS-001; static-rule policy review, disclosures, public API renderers, no system suspension; F-08 |
| AS-008 Native contracts and platform adapter | P1 / MVP | planned | C-23, C-31 unsupported behavior | AS-004; Codegen, validated types, revision/idempotency/cursor/event recovery; D-03/X-03 |
| AS-009 Authoritative Room store and migrations | P1 / MVP | planned | C-24 | AS-006, AS-008; schema, encrypted integration, write coordinator, migration/disk/key failure; D-02 |
| AS-010 Consent, filters and permission health | P1 / MVP | planned | C-23 | AS-003, AS-009; independent source/grant states, exclusions, in-flight fences and gaps; T-16/L-06 |
| AS-011 App/device session and open reducers | P1 / MVP | planned | C-01, C-02, C-03, C-04 | AS-009/AS-010; T-01–T-04, L-01–L-08 and replay accuracy |
| AS-012 Categories and social-app totals | P1 / MVP | planned | C-05, C-12 | AS-011; manual mapping/history scope, known coverage; T-05/T-12 |
| AS-013 Qualified short-video detectors | P1 / MVP | experimental | C-06, C-07 | AS-005/AS-010; per-target/version/locale qualification, false-positive/negative evidence; T-06/T-07 |
| AS-014 Aggregates, history and local insights | P1 / MVP | planned | C-13 | AS-011/AS-012; bounded queries, timezone bins, gaps, thresholds; T-13/L-08 |
| AS-015 Native budget/schedule/focus engine | P1 / MVP | planned | C-16, C-17 | AS-007/AS-011; deterministic conflicts, time boundaries, native restart; P-01/P-02 |
| AS-016 Reminders/intervention renderer | P1 / MVP | experimental | C-20 | AS-007/AS-015; truthful unavailable states, duplicate prompt control; P-05/P-08 |
| AS-017 Overrides, exceptions, emergency access | P1 / MVP | planned | C-21 | AS-015/AS-016; scoped tokens, invariant exclusions, immediate bypass; P-06 |
| AS-018 Accessible app screens and settings | P1 / MVP | planned | C-30 | AS-008/AS-010/AS-014; AS-033 visual reference, native consent/save/health flows, human-factors review; X-02/X-04–X-07 and actual Android accessibility/frame pacing |
| AS-019 Retention, deletion and storage views | P1 / MVP | planned | C-24 | AS-009/AS-014; preview/fences, derivative removal, no resurrection; D-01/S-05 |
| AS-020 Portable encrypted export/restore | P1 / MVP | planned | C-25, C-27 | AS-006/AS-009/AS-019; verified format, manual SAF provider tests, atomic staged replacement; B-01–B-05/B-07 |
| AS-021 OS transfer exclusions/privacy audit | P1 / MVP | planned | C-28 | AS-004/AS-009/AS-020; actual merged manifest/device/egress/log/dependency/deletion evidence; S-01–S-06 |
| AS-022 MVP distribution candidate | P1 / MVP | planned | All MVP gates | AS-011–AS-021 applicable scope; signing identity/Play declarations/current policy/closed testing, source support matrix |
| AS-023 Notification lifecycle and association | P2 / expansion | planned | C-08, C-09 | AS-010/AS-014; separate grant/metadata-only consent, no causal claims; T-08/T-09 |
| AS-024 Safe browsing/content adapters | P2 / expansion | experimental | C-10, C-11 | AS-013/AS-019; private/sensitive exclusion evidence and separate consent; T-10/T-11 |
| AS-025 Selective surface protection | P2 / expansion | experimental | C-19 | AS-013/AS-017; release unknown surfaces, preserve unrelated app use; P-04 |
| AS-026 Bedtime and richer commitment routines | P2 / expansion | planned | C-18, C-22, C-20 | AS-017; optional DND ownership, bounded friction/emergency; P-03/P-07 |
| AS-027 Widgets and Quick Settings | P2 / expansion | planned | C-29 | AS-018/AS-021; lock-screen redaction, stale/native pause state; X-01 |
| AS-028 Automatic personal Google backup | P2 / desired expansion | blocked | C-26 | F-05 authoritative policy clarification; AS-020; no OAuth/upload before gate; B-06 |
| AS-029 Scheduled picker-destination evaluation | P2 / expansion | experimental | C-27 future scheduling | AS-020; provider URI/durability/policy/background evidence; not a substitute for AS-028 |
| AS-030 Local forecasts/recommendations | P3 / evolution | experimental | C-14 | AS-014/AS-021; opt-in, local calibration, sufficient data, no autonomous rules; T-14 |
| AS-031 Optional local content-topic patterns | P3 / evolution | experimental | C-15 | AS-024/AS-030; separate topic consent, unknown fraction/correction, no addiction claim; T-15 |
| AS-032 Community iOS capability research/adapter | P3 / evolution | experimental | C-31 | F-07; current Apple research/entitlements, platform proposal, unsupported semantics; X-03 |
| AS-033 Humane design direction and interactive study | P0 / design continuation | implemented | C-30/C-32 design reference only | Original synthetic study; 37 browser tests pass, contrast/axe/320px/200%/RTL/keyboard/motion/visit-day checks, screenshots reviewed, latest pnpm/test tools locked; [dated evidence](IMPLEMENTATION_LOG/2026-10-27.md#continuation--humane-design-and-awareness-study); no native capability |
| AS-034 Native live surface awareness overlay | P1 / MVP candidate | experimental | C-32 | AS-013/AS-007/AS-009/AS-010; AS-033 design reference; least-privilege renderer, committed visit/day snapshots, unknown/sensitive-context removal, placement/accessibility and lifecycle/battery evidence; X-08/X-09; no native implementation yet |

## Completion protocol

For any status change record scope, commit/artifact, date, checks and limitations in State and the dated log. Per-target detector qualification can advance independently; keep C-06/C-07 experimental until their supported subset is explicitly documented. A blocked task names the exact dependency/decision; never clear a gate because it is inconvenient. No task may be marked implemented merely because its acceptance tests were written.
