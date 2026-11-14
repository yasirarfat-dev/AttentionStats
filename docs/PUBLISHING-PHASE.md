# Next Google Play publishing phase

**Status:** Active maintainer-directed release preparation, started 14/11/26. Plan and initial artifact/surface inventory complete; bounded history checks recorded, regression investigation pending. Publication readiness: HOLD.
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

Initial inventory covers the existing release surfaces below. [Feature verification](FEATURE-VERIFICATION.md#product-behavior) retains historical artifact-specific results; this table records disposition and ownership, not fresh acceptance or AS status promotions. [Baseline evidence](evidence/publishing-baseline.json) identifies the actually installed preview and the narrow new checks. Planned features outside the implemented subsets stay deferred.

| Surface / issue | Current disposition | Next action |
|---|---|---|
| Onboarding, disclosures, source choices and permission health | Implemented subset; earlier scoped evidence | Batch 2 entry/Back/help review and batch 4 revoke/restore fences; AS-010/AS-018. |
| App usage, opens and retained counting store | Implemented subset; broader accuracy/lifecycle open | Independent time/entry comparison, upgrade and clock/epoch boundaries; AS-009/AS-011/AS-050, batches 3–5. |
| Five-source viewers, supported inline feeds, media/ad/revisit classification | Implemented adapters; route/version-specific historical acceptance | Current source/version/locale and negative matrix; AS-013/AS-041/AS-048/AS-054, batch 3. No blanket source support claim. |
| Today, source/app identities and details | Pending source refinements; installed JS now matched | Review pending diff and actual render; repair 200% detail-label wrapping; AS-018/AS-045/AS-057, batch 2. |
| Insights Week/Month/All Time and bounded day/app explorers | Cold-history regression remains open; initial checks do not reproduce it | Three cold Week launches and six warm switches plus direct cold Month/All Time load on current preview. Isolate failure code/payload-validation stage before a repair; full paging, retry/error and coverage remain; AS-014/AS-037, batch 1. |
| HUD count/detail, drag, pause, hide and source return | Historical scoped control evidence | Current-artifact source/control/lifecycle negatives; AS-034/AS-059, batches 3–4. |
| Independent auto-scroll and standalone-ad skipping | Release blocker; partial source-specific arrivals | New correlated-signal hypothesis and supported negative/arrival matrix; AS-044/AS-059/AS-064, batch 3. Provider-control guidance is conditional help, not provider-setting control. |
| Mindful-break count/time, dismissal and pause fallback | Earlier bounded count/time/dismissal evidence | Current trigger/reset/dismiss/late-action and source pause/unavailable matrix; AS-016/AS-060/AS-061, batches 3–4. |
| Blocks, limits, groups and recurring full/selective schedules | Implemented subset; conflict/expiry matrix open | Safe exits, allowances, overlapping rules, expiry and recovery exclusions; AS-015/AS-017/AS-051/AS-053, batch 4. |
| Voluntary focus, review, Block Apps / Allow Only, early End | Implemented subset; actual expiry/reboot open | Verify final review render; real expiry/reboot/conflict matrix; AS-015/AS-017, batches 2/4. |
| Settings, appearance, compact help and native pages | Pending Appearance grouping; partial actual render evidence | Review saved/cancel/help unchanged-state and final grouping; AS-018/AS-045/AS-057, batch 2. |
| Launcher widgets, gallery and per-instance configuration | Implemented widget subset; launcher/lifecycle gaps | Current-artifact placement/resize/reconfigure/stale/unknown values and resource checks; AS-027, batches 2/6. Quick Settings stays deferred. |
| Local erase, portable backup/restore and Android-managed backup | Existing paths; destructive/recovery acceptance incomplete | Preserve live store; isolated synthetic failure/recovery/no-resurrection matrix; AS-019/AS-020/AS-058, batch 5. Direct Drive API backup stays blocked AS-028. |
| Report-a-problem/privacy and published support materials | Existing UI/materials; candidate consistency unverified | Inspect outbound destinations, disclosures and candidate claims; AS-018/AS-021/AS-046, batches 2/5/7. |
| Security, lifecycle, accessibility and resources across surfaces | Open gates with scoped historical evidence | Apply D/B/S/L/X scenarios; 200% tab-label ellipsis remains; batches 2–6. No seven-day soak or new performance benchmark started in this baseline batch. |
| Compatibility beyond current phone | Deferred work; acceptance gap | Preserve deferral; resolve explicitly before readiness; AS-002/AS-022. |
| Play state/signed provenance | Externally unverified | Inspect authorized Console access or obtain exact maintainer records when needed; AS-046. |

## Per-batch execution and continuity

1. Read this plan, Rules, State, Tracker, Verification and relevant domain specs/decisions. Inspect branch/status and pending-edit ownership. Select two or three eligible tasks and state acceptance scope.
2. Consult existing evidence first. Repair root causes and update affected contracts/specs. Use pnpm and frozen locked builds; new direct dependencies resolve through latest with dated version/license/compatibility evidence. Upgrades require purpose and appropriate regression checks.
3. Run meaningful host checks and actual authorized phone/native-render/source-sensitive trials. Target adb explicitly, preserve real history and original grants/preferences, remove owned diagnostics and keep private payloads/captures/device identifiers out of Git.
4. Update affected Verification rows, Tracker, State, this ledger/handoff and the dated log. Record DD/MM/YY, source/version, APK/test hash, source-app version, exact passes, failures/unrun cases and cleanup. Runner completion alone is not feature acceptance; invalidate affected acceptance after regressions.
5. Review diffs/public artifacts, run applicable checks, and commit/push only reviewed owned changes on `develop` after identity/remote/authentication checks. Workflows remain manual and undispatched unless explicitly requested. Distinguish source push, installed binary and Play status.
6. Save a handoff after each small batch. Follow the 100k–150k context checkpoint/compact rule when readings are available; never invent readings or claim unavailable compaction controls. Use available controls or leave a saved checkpoint for user/app compaction. Resume the next action without repeating trials unless changes or coverage gaps warrant it.

## Latest handoff — 14/11/26, initial baseline/history batch

- **Completed:** installed-artifact identification, implemented-surface inventory, bounded cold/warm history checks and focused host regressions. [Exact results and limits](evidence/publishing-baseline.json). This is an investigation checkpoint, not completion of all batch 1 acceptance.
- **Artifact:** installed/local preview `f9da99d1703dd96ecf3f9766998bfb18d23e1c117612c4db5b1d828d4ae3b60a`, code10, physical API36. All 58 mapped app TS/TSX/JS files match current working files; native build/source provenance is not independently reconstructed. Source checkpoint starts at `45c93ad` plus preserved uncommitted work. Neither P8 nor a committed-only tree identifies this preview.
- **History outcome:** current bounded cold/warm loads pass; P8 failure is not reproduced and its cause is unknown. No runtime fix, data reset, version bump, install, migration or new release acceptance. No need to repeat this unchanged matrix without a new hypothesis.
- **Checks/publication:** 37 focused Jest tests pass; remaining documentation/commit/push results in the [dated baseline record](IMPLEMENTATION_LOG/2026-11-14.md#publishing-baseline-and-history-investigation). No Play publication.
- **Preserved work:** existing Appearance/Today source/tests/evidence and associated documentation remain separate from this batch. Phone history stays in place; only own-app launches/navigation and calendar cancellation, with no setting/save/erase/restore/provider command.
- **Readiness:** HOLD. Physical/source/data/accessibility/resource/compatibility/Console gates remain open. New features wait until after the next published release.
- **Next concrete action:** distinguish native rejection (`BUSY`/`INTERRUPTED`/`STORAGE_UNAVAILABLE`) from strict JS history validation using bounded content-free diagnostic evidence when the failure next occurs; keep personal payloads off public artifacts. Then repair the established cause and cover retry/error and page-boundary replacement. Independently proceed with batch 2's known 200% bottom-tab ellipsis/detail-label wrapping and review pending Appearance grouping. Do not restart emulator work or source-negative probes implicitly.
- **After publication:** record actual delivered version/track and supported limits, then select newer features from the tracker/queue.
