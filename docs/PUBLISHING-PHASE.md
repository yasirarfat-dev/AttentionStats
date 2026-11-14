# Next Google Play publishing phase

**Status:** Active maintainer-directed release preparation, started 14/11/26. Planning batch complete; qualification pending. Publication readiness: HOLD.
**Responsibility:** Persists scope, ordered task batches, acceptance gates and handoffs for polishing and publishing the existing Android product before newer features.
**Related specifications:** [Rules](AGENT-RULES.md), [State](STATE.md), [Tracker](FEATURE-TRACKER.md), [Verification](FEATURE-VERIFICATION.md), [Release](RELEASE.md), [Testing](TESTING.md), [Console preparation](../release/PLAY-CONSOLE.md).
**Unresolved feasibility:** History reads, source playback/auto-scroll, recovery, lifecycle, accessibility, resources, compatibility and Console/candidate provenance remain open. This plan clears none of those gates.

## Objective and scope

Maintainer direction on 14/11/26: verify what is already implemented, fix defects, improve polish, publish the next Google Play version, then implement newer features. Complete a few concrete tasks per batch, save evidence and a handoff, compact when appropriate, and resume from this document across sessions.

Scope is the implemented Android experience on `develop`: consent/source choices, counting/classification, app usage and history, Today/Insights, HUD and optional independent auto-scroll/ad skipping, mindful breaks, groups/schedules/blocks/focus and safe exits, Settings/appearance, onboarding, widgets, deletion and existing backup/restore. Batch 1 inventories exact shipped subsets. Code presence and historical passes do not establish current acceptance. Planned expansion is deferred; repairs needed to make existing features reliable remain in scope.

Auto-scroll stays in scope under the existing maintainer decision. Missing source signals must remain explicitly unavailable with safe suspension. Do not remove the capability, relax consent/confirmation fences or invent timer-based completion to obtain clearance. Material scope changes require an explicit recorded decision. Existing AS IDs, task states and normative specifications remain authoritative; batch numbers are execution order, not new feature IDs.

## Starting checkpoint

- Reported Play baseline: 1.0.0 / code 10 closed beta. Exact Console artifact/source mapping, track/access state and review remain uninspected.
- Planning starts from `develop` HEAD `9ca2d16`, after ADR-101. Uncommitted Appearance/Today source, tests, evidence and documentation already exist. Preserve and review that work separately; do not reset or silently include it in the planning commit.
- State records later compact Appearance/Today and independent-provider guidance trials. P8 is an earlier artifact, not proof of the currently installed binary. Re-identify installed product/test hashes and source before runtime work.
- Cold Week/Month Insights may report History Couldn't Refresh / history unavailable. Preserve saved history and investigate the native read/error path first.
- Final focus-review/icon refinements and subsequent UI changes need exact-artifact acceptance. Large-text tab/detail-label issues remain; scoped visual passes do not qualify every screen.
- Facebook repeated end/arrival qualification remains incomplete. YouTube/Snapchat probes have no usable progress/end signal in their bounded scope. Instagram/TikTok historical passes retain their own artifact limits. Require a new hypothesis before repeating negative probes.
- Emulator/other-device work remains deferred under ADR-099. Record the compatibility gap; do not restart it implicitly or infer minimum-API/OEM acceptance from the current phone.
- No main merge, signed candidate or Play submission/rollout has occurred for this phase. The publishing request establishes the objective; the concrete-candidate merge agreement and release gates still apply.

## Ordered work batches

Select two or three related tasks per batch; split larger rows as needed. Advance independent preparation while other gates need elapsed time or external access. Unrun checks stay open.

| Batch / owners | Concrete work | Required completion evidence |
|---|---|---|
| 0 — Plan; AS-046 | Save phase plan, link entrypoints, preserve existing work and record first action. | Documentation/public-artifact and whitespace checks; reviewed scoped commit/push. No product acceptance. |
| 1 — Baseline/history; AS-014/AS-018/AS-037/AS-046 | Inventory implemented surfaces and defects; identify current source/installed artifacts; reproduce and repair cold Insights; verify cold/warm Week, Month, All Time, day/app selection, paging and error/retry states. Start feasible resource observation early. | Scope ledger below; focused regression checks and actual phone results with APK/test hashes; retained-history preservation. |
| 2 — Presentation; AS-018/AS-027/AS-045/AS-049/AS-056/AS-057 | Review pending Appearance/Today work; build final focus/icon refinements; polish existing screens, onboarding, native pages and widgets; repair text/insets/reachability. | Rendered phone review, save/cancel/back/help and unchanged-state checks; applicable themes, large type, TalkBack, RTL, reduced motion and keyboard/insets. List unrun scope. |
| 3 — Counting/HUD/scrolling; AS-011/AS-013/AS-034/AS-044/AS-048/AS-051/AS-054/AS-055/AS-059/AS-064 | Compare independent observations with saved counts/time/classification; qualify source/routes; investigate new playback hypotheses; complete actual Start/end/arrival/Stop, suspension and manual-input negatives. | App/version/locale/route support matrix; exact artifacts and observations versus saved outcomes; unavailable states; repeated mixed-item confirmation and safety checks. |
| 4 — Protection/lifecycle; AS-003/AS-010/AS-015/AS-016/AS-017/AS-053/AS-060/AS-061 | Verify blocks, limits, schedules, focus, allowances/conflicts; reminder count/time/dismiss/reset; revocation, process/restart/lock, clock/day/zone boundaries and safe exits. | Applicable P/L/T scenarios on phone; retained settings/history and cleanup. Separate real-duration trials from synthetic clock checks. |
| 5 — Preservation/security; AS-006/AS-009/AS-019/AS-020/AS-021/AS-058 | Verify upgrade/migration, isolated recovery, corrupt/cancelled/interrupted/low-space/key-loss/maximum-history paths and deletion/no-resurrection; inspect manifests/backup rules, logs/egress, dependencies/licenses and malicious input bounds. | Applicable D/B/S scenarios; isolated synthetic recovery installation; exact artifact audits. Never reinstall or replace the real-history debug store for recovery tests. |
| 6 — Resources/compatibility; AS-002/AS-004/AS-011/AS-014/AS-018/AS-022/AS-027/AS-037/AS-038 | Measure bounded long-history cold/warm latency, memory/storage and resource/soak baseline; widget/launcher lifecycle and release-binary execution; review build warnings and support claims. | Measured dataset/duration/artifact/device scope and applicable L-08 evidence. Deferred device work remains an open gate until completed or explicitly resolved through an approved scope decision. |
| 7 — Candidate/store preparation; AS-022/AS-046 | Inspect actual Console versions/track/testing/access; recheck current official policies and dependency status; select unused higher code/version; reconcile listing, policies, Data safety, accessibility declaration, reviewer video/steps and release notes; build/audit signed candidate and verify update preservation. | Source commit, lock/toolchain, version, hashes and signing provenance; actual release-binary regression/smoke checks; final readiness assessment and truthful store materials. Secrets remain outside Git. |
| 8 — Publish/verify; AS-022/AS-046 | Present concrete candidate/readiness for required main-merge agreement; publish approved source/policies and submit reviewed candidate to selected Play track; monitor review/rollout and verify delivered update. | Separate actual merge, upload, submission, review and rollout status; public URL checks; delivered version/provenance and recovery/support handoff. Submission is not approval or availability. |

The nine gates in [Release](RELEASE.md#readiness-assessment-2026-11-07) remain the closing checklist. This sequence does not weaken them or require unfinished future roadmap features to ship. Version 1.1.0 remains an existing recommendation only; no code/version is reserved. Confirm Console use before selecting either. Start elapsed-time observations early where feasible; no delivery date is promised.

## Scope and defect ledger

Batch 1 expands this to cover every implemented release surface and links to existing Verification rows instead of duplicating historical results. Each failed/uncovered scope needs an owner and next action. These are work dispositions, not AS status promotions.

| Surface / issue | Current disposition | Next action |
|---|---|---|
| Cold Insights history | Release blocker; reported regression | Reproduce cold reads and inspect native error/query path; AS-014/AS-037. |
| Appearance/Today/focus/icons | Existing source; artifact-specific partial acceptance | Review pending diff/evidence and identify installed binary; AS-018/AS-045/AS-057. |
| Auto-scroll reliability | Release blocker; historical partial passes | New correlated-signal hypothesis and supported negative/arrival matrix; AS-044/AS-059/AS-064. |
| Recovery/security/lifecycle/resources/accessibility | Open gates with substantial scoped historical evidence | Map remaining applicable scenarios before reruns; owners in batches 2–6. |
| Compatibility beyond current phone | Deferred work; acceptance gap | Preserve deferral; resolve explicitly before readiness; AS-002/AS-022. |
| Play state/signed provenance | Externally unverified | Inspect authorized Console access or obtain exact maintainer records when needed; AS-046. |

## Per-batch execution and continuity

1. Read this plan, Rules, State, Tracker, Verification and relevant domain specs/decisions. Inspect branch/status and pending-edit ownership. Select two or three eligible tasks and state acceptance scope.
2. Consult existing evidence first. Repair root causes and update affected contracts/specs. Use pnpm and frozen locked builds; new direct dependencies resolve through latest with dated version/license/compatibility evidence. Upgrades require purpose and appropriate regression checks.
3. Run meaningful host checks and actual authorized phone/native-render/source-sensitive trials. Target adb explicitly, preserve real history and original grants/preferences, remove owned diagnostics and keep private payloads/captures/device identifiers out of Git.
4. Update affected Verification rows, Tracker, State, this ledger/handoff and the dated log. Record DD/MM/YY, source/version, APK/test hash, source-app version, exact passes, failures/unrun cases and cleanup. Runner completion alone is not feature acceptance; invalidate affected acceptance after regressions.
5. Review diffs/public artifacts, run applicable checks, and commit/push only reviewed owned changes on `develop` after identity/remote/authentication checks. Workflows remain manual and undispatched unless explicitly requested. Distinguish source push, installed binary and Play status.
6. Save a handoff after each small batch. Follow the 100k–150k context checkpoint/compact rule when readings are available; never invent readings or claim unavailable compaction controls. Use available controls or leave a saved checkpoint for user/app compaction. Resume the next action without repeating trials unless changes or coverage gaps warrant it.

## Latest handoff — 14/11/26, planning batch

- **Completed:** phase scope, ordered batches, gates, defects and continuity procedure documented and linked from entrypoints. Product behavior and feature acceptance unchanged.
- **Checks/publication:** actual results belong in the [dated planning record](IMPLEMENTATION_LOG/2026-11-14.md#next-google-play-publishing-phase-plan).
- **Preserved work:** existing Appearance/Today source/tests/evidence and associated documentation are separate from this batch.
- **Readiness:** HOLD. Physical/source/data/accessibility/resource/compatibility/Console gates remain open. New features wait until after the next published release.
- **Next concrete action:** batch 1: inspect latest artifact/evidence and cold Insights read implementation, reproduce the native read failure on the authorized connected debug phone, then make a targeted preservation-safe repair. If required phone access is absent, request connection and continue independent source/evidence review.
- **After publication:** record actual delivered version/track and supported limits, then select newer features from the tracker/queue.
