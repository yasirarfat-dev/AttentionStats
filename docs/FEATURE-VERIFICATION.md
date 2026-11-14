# Feature implementation and verification checklist

**Status:** Release held; Facebook video-end auto-scroll remains unresolved. Updated 2026-11-15.
**Responsibility:** Provides a scannable implementation/verification record, known regressions and links to exact evidence. Task acceptance remains owned by the tracker.
**Related specifications:** [Tracker](FEATURE-TRACKER.md), [State](STATE.md), [Testing](TESTING.md), [Release](RELEASE.md), [Auto-scroll](AUTOSCROLL-SPEC.md).
**Unresolved feasibility:** Source playback signals, remaining lifecycle/accessibility/data-preservation gates and store approval are not established by a successful build.

## How to read and maintain this list

`[x] Code` means the named bounded product behavior has executable source; it does not promote its entire AS task to implemented. `[x] Verified` means the stated scope passed on the dated artifact linked in that row. It never means every device, route or later build is accepted. `[ ]` means incomplete, failed, unrun or unavailable; the note distinguishes them. Dates use **DD/MM/YY**. A version number alone cannot identify a development APK: retain its SHA-256 and test artifact in the linked evidence.

Update affected rows in the same change as code and evidence. Record failures and invalidate an affected verification check immediately; preserve earlier passing trials as history. For a changed artifact, explicitly state whether a check was rerun, whether an unchanged payload was compared, or whether verification is still required. Do not repeat a completed trial merely because its record is difficult to find. Retest after relevant changes, reported regressions, changed source versions, missing artifact provenance or an uncovered scenario. Never transfer counting acceptance to playback or auto-scroll.

The product rows cover delivered and proposed user-facing behavior. The complete AS task checklist below maps the broader portfolio, including research and infrastructure. Its completion boxes mirror the authoritative tracker, not the narrower Code boxes above.

## Publishing baseline — 14/11/26

[Baseline evidence](evidence/publishing-baseline.json) identifies installed/local preview `f9da99d1703dd96ecf3f9766998bfb18d23e1c117612c4db5b1d828d4ae3b60a`, code10/API36. Its bundled JS matches 58 current mapped app source files, including pending UI work; native source provenance is not reconstructed. Three cold Week launches, six warm range switches and direct cold Month/All Time loads pass, plus scoped calendar cancellation/day/app navigation and 37 host tests. Earlier P8 failure is not reproduced, its cause remains unknown, and full history/page-boundary/error/retry acceptance stays unchecked. No runtime repair, install or status promotion.

## Publishing presentation batch — 14/11/26

[Large-text evidence](evidence/publishing-large-text.json) binds preview `5754370c325eb939e56fb01175af017d29c4cc49f74c003e0098f6c30b07b90b`, code10/API36, to 59 matching bundled app sources including pending work. Tab labels and Viewing Details render at 100%/200%; 200% nested/Android/repeated-tab Back passes. Pending Templates/Customization grouping/help is visually reviewed at 100%, with saved appearance/backup unchanged. 139 Jest and preview assembly/lint pass (zero errors, 170 warnings). This accepts only those presentation checks; history, source, broad accessibility and publication gates remain open.

## Publishing history diagnostics — 14/11/26

**[x] Code; [x] Verified for injected scope only.** [Evidence](evidence/publishing-history-diagnostics.json) binds preview `4cf0f60fd1806c47331c6bc650c26b2577ca21e59763314f959d5df232792b2b` / test `d81786b56f3e72cb04df0ab2b463b16d2d28b932faadff712f98ee2bd6021c10`, code10/API36. Seven injected native/parser stages pass 71 error/help/redaction/retry/recovery assertions with explicit -1. Native busy/validation error presentation is visually reviewed in dark theme at 100%; fresh direct Insights entry loads after test removal. 148 host tests/build/lint pass; saved appearance/backup/font preserved. Original regression cause, real failure recovery, page boundaries/stale-screen behavior and broader accessibility remain unchecked. No full history acceptance or release clearance.

## Artifact key

| Key | Attention Stats version / artifact | Evidence scope |
|---|---|---|
| P0 | 1.0.0-reels-preview / code 10; SHA-256 `006c5981b640d6c567f865df0a6962da908de6a6265ad0272b524659c22c6241` | Historical candidate; [compact focus](evidence/compact-focus.json). Not an uploaded release. |
| P1 | 1.0.0-reels-preview / code 10; final `577943f18782b28cdf86633b266e4c7e8f22e6728258b773cb786f58cb4c97d9`, native-tested predecessor `fc3abc6badbb006f2d01d1bceda160f5eca883deea29f2104a5887b72d2fc642` | [Cohesive screens](evidence/cohesive-screen-refinement.json); each check retains its actual artifact. |
| A1 | Historical development preview `59c6b2dcf5258493624f526dc0e5173f72bb405beafd65d90cacf41a90dc80fe` | [Auto-scroll tracing](evidence/confirmation-trace-continuation.json), 12/11/26; no implied rerun on P0. |
| B0 | Historical 1.0.0 / code 10 baseline; Console-to-source mapping not independently established | [30 October runtime scope](STATE.md#verification-2026-10-30). Baseline evidence, not final-candidate qualification. |
| P2 | 1.0.0-reels-preview / code 10; `2608e5cd843a00f3826feb5363b44ec1748412cc440166d0b97d6822a844b644` | Historical standalone-ad candidate; gesture executes but arrival confirmation fails. [Evidence](evidence/standalone-ad-continuation.json). |
| P3 | 1.0.0-reels-preview / code 10; `23e529964443eec1d513f17589ceaaea98585908671eaf2a1b15012ec41a38bb` | Current appearance/widget/renderer candidate; one standalone-ad native and independent arrival/Stop passes. [Evidence](evidence/facebook-appearance-widget-continuation.json). |
| P4 | 1.0.0-reels-preview / code 10; `3a10513915f5460cc5d17230d618fa8b0fa77e1ceeb3c515e49872c7cad47620` | Concluding appearance candidate. All 52 DEX/native-library payloads byte-identical to the P3 ad-trial APK; UI touch/render scope recorded separately. [Evidence](evidence/facebook-appearance-widget-continuation.json). |
| R1 | Unsigned inspection 1.0.0 / code 10; APK `b043bb1e08ac76ee0ee8432846ecc10da3fd41fd8c9ea07ed1e5a40a22a04113`; AAB `f4a1c8bab54a9b78cba15dd3851f436fffd79989bbb3f92953c8c7961740c2ae` | Static release inspection only; not installed, signed, approved or uploadable. Code 10 is already reported uploaded. [Evidence](evidence/publication-sequence.json). |
| P6 | 1.0.0-reels-preview / code 10; `c00595b222dd72ebe358c24fc6ca5211188b5dbfa0ff1a071c4c4538660960b7` | Compact-information/calendar candidate. Native 93/469 checks retain test E6 binding; actual Insights/Protect scope and failed/unrun UI gates are explicit in [evidence](evidence/compact-information.json). |
| P7 | 1.0.0-reels-preview / code10; `412b320f17ec1500e4a80a1e5ce410d6d0948e5f331d2d25ec87b6f895c8a00f` | Own-information predecessor; modes retain their individual artifact binding. [Evidence](evidence/day-insights-continuation.json). |
| P8 | 1.0.0-reels-preview / code10; `4d47323563167b78afbfee58d01cba7707c1fd903f8956bdee23026ed9cdf17f` | Installed day/icon candidate; final two JS refinements are not installed/accepted. Cold history failure remains open. [Evidence](evidence/day-insights-continuation.json). |
| P5 | 1.0.0-reels-preview / code 10; `5297690115455a8336f089c302242facb56ea019b69966e87201ba078522a218` | Final provider-refresh repair. Packaged JS/UI assets byte-identical to P4; new native widget guard and final phone scope recorded in [evidence](evidence/facebook-appearance-widget-continuation.json). |


## Product behavior

| Feature / task owners | Code | Verified | Date; artifact / source version where relevant | Exact verified scope and remaining work |
|---|---|---|---|---|
| Large-text primary tabs and Viewing Details; AS-018/AS-045 | [x] | [x] | 14/11/26; API36 working-tree preview | Full labels, two-row tabs and stacked details at 200%; normal layout and scoped navigation/Back/help pass. Other accessibility/device scopes remain open. [Evidence](evidence/publishing-large-text.json). |
| Disclosure, onboarding and independent source choices; AS-010/AS-049 | [x] | [x] | 30/10/26; B0 | Baseline onboarding/enable flow; broader revocation, accessibility and final-candidate onboarding still open. [State](STATE.md#verification-2026-10-30). |
| Direct accessibility setup and service label; AS-056 | [x] | [ ] | Historical partial evidence | Label observed; final direct-navigation path needs acceptance. [Tracker](FEATURE-TRACKER.md). |
| Instagram Reels counting; AS-013/AS-048 | [x] | [x] | 30/10/26; B0 | Five manual swipes, +5 encounters; broader routes/source updates and negative contexts remain. [State](STATE.md#verification-2026-10-30). |
| Facebook Reels counting; AS-013/AS-048 | [x] | [x] | 30/10/26; B0 | Five manual swipes, +5 encounters; does not verify video-end scrolling. [State](STATE.md#verification-2026-10-30). |
| YouTube Shorts counting; AS-013/AS-048 | [x] | [x] | 30/10/26; B0 | Five manual swipes, +5 encounters; does not verify playback. [State](STATE.md#verification-2026-10-30). |
| TikTok counting; AS-013/AS-048 | [x] | [x] | 30/10/26; B0 | Scoped five-swipe run; one separate run had no feed advance on one swipe. Regional/lite and changed versions need independent coverage. [State](STATE.md#verification-2026-10-30). |
| Snapchat Spotlight counting; AS-054 | [x] | [x] | 04/11/26; historical artifact | Narrow live Spotlight pass; Stories/broader fidelity and later zero-viewer playback probe remain unqualified. [Dated log](IMPLEMENTATION_LOG/2026-11-04.md). |
| Instagram/Facebook inline feed videos; AS-041/AS-048 | [x] | [x] | 30/10/26; B0 | Baseline supported feed observations; mixed/loading/entry-route fidelity remains open. [State](STATE.md#verification-2026-10-30). |
| Photo, album and ad classification; AS-054 | [x] | [x] | 05/11/26; Facebook 585.0.0.47.54 | Narrow repaired-layout visual/count comparison; earlier failed sweeps retained. No universal precision/recall claim. [Evidence](evidence/beta-continuation.json). |
| Encounter/revisit dedup and session continuity; AS-011/AS-051 | [x] | [x] | 30/10/26; B0 | Cross-app session until screen-off and scoped counting; broader lifecycle/source fidelity remains. [State](STATE.md#verification-2026-10-30). |
| App usage time and observed opens; AS-011/AS-050 | [x] | [x] | Historical development artifact | Scoped physical app-entry/time checks; not exact launches, attention or complete device union. [Evidence](evidence/diary-preview.json). |
| Floating counter, categories, pause/resume, hide and drag; AS-034/AS-059 | [x] | [x] | 10–12/11/26; linked artifacts | Scoped HUD/control/scheduler and actual source-return trials; broader pause/lifecycle/accessibility remain. [HUD evidence](evidence/surface-recovery-continuation.json). |
| Instagram video-end auto-scroll; AS-044/AS-059 | [x] | [ ] | Last scoped passes 12/11/26; A1; Instagram 455.0.0.51.72 | Three actual Start/end/arrival/Stop passes; earlier intermittent confirmation loss unresolved, P0 end matrix unrun. [Evidence](evidence/confirmation-trace-continuation.json). |
| Facebook video-end auto-scroll; AS-044/AS-059 | [x] | [ ] | 14/11/26; predecessor + P3; Facebook 586.0.0.52.53 | Fresh P5 mixed trial confirms one ad skip then one numeric video end, both with native/independent arrival and actual HUD Stop. Three-arrival goal remains unmet when the next reel lacks progress; separate unavailable and bounded no-end trials also remain unaccepted. Publication held. [Sequential evidence](evidence/publication-sequence.json). |
| TikTok video-end auto-scroll; AS-044/AS-059 | [x] | [ ] | Last scoped pass 12/11/26; A1; TikTok 47.7.3 | One confirmed end/arrival/Stop trial; prior failures/repeated-end/negative cases and P0 matrix remain. [Evidence](evidence/confirmation-trace-continuation.json). |
| YouTube/Snapchat video-end auto-scroll; AS-044/AS-059 | [x] | [ ] | 11/11/26; historical probes | End support unavailable/unqualified; YouTube has recognized viewers without usable progress; zero-viewer Snapchat probe accepts nothing. [Auto-scroll scope](AUTOSCROLL-SPEC.md). |
| Explicit photo/image-ad five-second auto-scroll; AS-044 | [x] | [ ] | Historical synthetic coverage | Positively classified photos only; no fallback video timer. Final live source matrix remains. [Auto-scroll](AUTOSCROLL-SPEC.md). |
| Auto-scroll Stop, consent, service/restart/lock fences; AS-010/AS-044 | [x] | [x] | 12/11/26; linked artifacts | Bounded actual trials plus synthetic cancellation checks; active-unlock, spontaneous death, in-flight cancellation and full source matrix remain. [Restart](evidence/source-restart-continuation.json), [lock](evidence/lock-break-continuation.json). |
| Count/time mindful breaks and delayed dismissal; AS-016/AS-060 | [x] | [x] | 12/11/26; linked artifact | Two real-minute cycles and real 310-second absence reset on Instagram/API36; source pause and broader fidelity remain. [Minute](evidence/live-minute-continuation.json), [absence](evidence/absence-continuation.json). |
| Break invitations, four visuals and zero-delay continuation; AS-060/AS-061 | [x] | [x] | 13/11/26; P1/P0 as bound | 36 invitation variants, synthetic attached controls and visual review; actual per-source interruption matrix remains. [Screens](evidence/cohesive-screen-refinement.json), [P0 controls](evidence/compact-focus.json). |
| Pause source playback before a break; AS-061 | [x] | [ ] | Experimental | Explicit Pause candidate and fallback reminder exist; source-specific automatic-pause qualification open. [Tracker](FEATURE-TRACKER.md). |
| Per-app limits and viewer-block escape; AS-051 | [x] | [x] | 30/10/26; B0 | Scoped Instagram daily limit and Facebook Back-to-Home behavior; broader sources/lifecycle remain. [State](STATE.md#verification-2026-10-30). |
| App groups and recurring full/selective schedules; AS-015/AS-053 | [x] | [x] | 07/11/26; Instagram/API36 | Narrow selective cover/reentry/allowance trial plus editor checks; expiry/conflicts/broader sources and devices remain. [Evidence](evidence/selective-schedule.json). |
| Voluntary focus, Block Apps / Allow Only, review and early End; AS-015/AS-017 | [x] | [x] | 13/11/26; P1 and P0 menus | Live countdown/early End on P1; P0 menu/review/navigation checked without starting. Reboot/real expiry/full conflict/override gates remain. [Focus](evidence/focus-session.json), [screens](evidence/cohesive-screen-refinement.json), [menus](evidence/compact-focus.json). |
| Compact Appearance and Today count/source-detail layouts; AS-018/AS-045/AS-057 | [x] | [x] | 14/11/26; scoped API36 artifacts | Four count cases pass at 100%/200%; targeted renders and compact Appearance/48dp help targets reviewed. Final two-group refinement, artifact boundaries and remaining unrelated large-text issues are explicit in [evidence](evidence/compact-appearance-today.json). |
| History range/app/day navigation, stale tab return and foreground generation; AS-014/AS-018/AS-037 | [x] | [x] | 15/11/26; scoped API36 preview code10 | Ten actual UI, 43 isolated native aggregate/page and 19 stale/foreground/retry checks pass; raw native -1, settled errors reviewed. Synthetic paging is separate from real-history UI. Original P8 cause, long-history UI races and process death stay open. [Exact scope/artifacts](evidence/publishing-count-only.json). |
| Today count-only source empty-copy repair; AS-018/AS-045 | [x] | [x] | 15/11/26; API36, preview code10 | Seven-video/zero-time row no longer says Nothing yet today; zero source retains empty copy. Actual assertions at 100%/200%, raw -1; count-only native rendering reviewed at both scales, zero-copy visual scope normal only. [Exact artifacts and retained harness failures](evidence/publishing-count-only.json). |
| Appearance Templates/Customization, draft save/discard/reset and large-text selector; AS-018/AS-045/AS-057 | [x] | [x] | 15/11/26; API36, preview code10 | Final 51 checks each at 100%/200% with raw -1, plus eight actual default-reset/tab-draft/discard checks. Native Save and exact original-preference restoration verified; rendered selector/preview/help/RGB/layout/actions reviewed. Broad themes/keyboard/TalkBack/RTL/device scope remains open. [Evidence](evidence/publishing-appearance-today.json). |
| Today, Quick Actions and Protect navigation; AS-018/AS-045 | [x] | [x] | 13/11/26; P0/P1 | Recorded entry/catalog/Back/Close/render scopes; broad accessibility/performance remain. [Menus](evidence/compact-focus.json), [screens](evidence/cohesive-screen-refinement.json). |
| Compact Insights hierarchy and bounded calendar/app-date navigation; AS-014/AS-018/AS-037 | [x] | [ ] | 14/11/26; P8 | Actual All Time select/Cancel/Back/Previous/Next and four historical date scopes pass. Later cold Week shows history unavailable; final Week/Month/cold-read acceptance is withheld. [Evidence](evidence/day-insights-continuation.json). |
| Selected-day group, existing daily statistics and full-label navigation; AS-014/AS-018/AS-037 | [x] | [x] | 14/11/26; P8/API36 | Six daily metrics, shared outline and repeated dates visually reviewed. Normal phone width/text size shows Previous Day/Next Day on one line. Cold reads, broad access/device scope remain open. [Evidence](evidence/day-insights-continuation.json). |
| Consistent left-side app icons; AS-018/AS-027/AS-045 | [x] | [ ] | 14/11/26; P8 subset | Range/selected-app/expanded members/editor/focus review traversed; 477 widget checks and synthetic light/dark renders pass. Final focus-review/placeholder-ink refinements and broad row/device scope remain unverified. [Evidence](evidence/day-insights-continuation.json). |
| Card-owned information across app/settings/appearance/native pages; AS-018/AS-045/AS-057 | [x] | [ ] | 14/11/26; P7/P8 | Own-scroll repair passes P7 Settings 21/Today 15/editors 15/native 9/tour 4 and P8 Insights 27/editors 15, each -1. Final source/render and cold-read gates remain; no universal current acceptance. [Evidence](evidence/day-insights-continuation.json). |
| Insights week/month/all-time, dates and source detail; AS-014/AS-037 | [x] | [ ] | 14/11/26; P8 regression | Earlier P1 cold/warmed passes remain historical. Current cold Week displays history unavailable; cause unestablished, saved history retained. Current read acceptance invalidated. [Evidence](evidence/day-insights-continuation.json). |
| Searchable app history, day and range explorers; AS-014/AS-037 | [x] | [ ] | 14/11/26; P8 | Populated By App/list/selected-app/daily views pass within bounded traversal; current cold-history failure withholds full explorer availability acceptance. Historical successes retained; whole-history ranking/rollups remain. [Evidence](evidence/day-insights-continuation.json). |
| Optional numerical confirmed-playback metrics; AS-055 | [x] | [ ] | Experimental | Opt-in model/store/UI exist; per-layout/source playback fidelity unqualified. [Tracker](FEATURE-TRACKER.md). |
| Actual widget source names and refresh without an Overview instance; AS-027 | [x] | [x] | 14/11/26; P5/API36 | Existing Reels By Apps launcher instance displays Facebook and its actual count after native refresh repair; no recreation or placeholder source names. Other source mappings have scoped synthetic rendering evidence. [Evidence](evidence/facebook-appearance-widget-continuation.json). |
| Five widget types and progressive per-instance configuration; AS-027 | [x] | [x] | 13/11/26; P1 native predecessor | 458 projection/configuration/attached-host checks and native visual review; actual placement/resize/launcher lifecycle remain. [Evidence](evidence/cohesive-screen-refinement.json). |
| Full-spectrum Color / Background / Accent scopes and grouped presets; AS-057/AS-018 | [x] | [x] | 14/11/26; P4/API36 | Actual hue/canvas endpoint matches expected RGB exactly; three separate scopes save/read back through UI/native preferences. Group headings, unwrapped Graphite/Background and applied dark charcoal/teal style visually reviewed. Large text/RTL/TalkBack and broader dragging remain open. [Evidence](evidence/facebook-appearance-widget-continuation.json). |
| Theme, custom RGB/hex accent and layout controls; AS-057 | [x] | [x] | 13/11/26; P1 | Actual draft/save/native persistence/restoration, attached validation; full combinations/reduced motion/large text remain. [Evidence](evidence/cohesive-screen-refinement.json). |
| Three-stage local erase; AS-019 | [x] | [x] | 30/10/26 B0; 13/11/26 P1 cancellation | B0 actual erase/re-onboarding; P1 all stages cancelled safely. Current real-history deletion deliberately not repeated. External copies remain separate. [State](STATE.md#verification-2026-10-30), [screens](evidence/cohesive-screen-refinement.json). |
| Manual archive export/restore; AS-020 | [x] | [x] | 05/11/26; historical bounded evidence | Isolated archive checks and recorded picker scope; fresh-install/max-logical-restore/scalability gates remain. Public app key is not a secret password. [Evidence](evidence/beta-continuation.json). |
| Android-managed automatic backup/recovery; AS-058 | [x] | [x] | 04–11/11/26; linked artifacts | App-scoped transport handoff and isolated storage/recovery checks; actual framework fresh-install recovery/overlap/max restore remain. [Evidence](evidence/automatic-backup.json). |
| Immediate standalone Facebook ad swipe; AS-064 | [x] | [x] | 14/11/26; P5; Facebook 586.0.0.52.53/API36 | Bounded English-layout standalone ad: actual HUD Start, one validated/completed swipe, different native plus independent next page and actual Stop pass. Missing next-video progress suspends further actions. No provider-button or continuous-source acceptance. [Evidence](evidence/facebook-appearance-widget-continuation.json). |
| Current-release manifest and compiled backup boundaries; AS-021/AS-046 | [x] | [x] | 14/11/26; R1 ARM64 | Independent APK/AAB manifest, referenced compiled one-file/encrypted-cloud/device-transfer rules, matching code/assets/baseline profiles and 16-KiB checks pass. Static scope only; device recovery, egress/logs, signatures and full security acceptance open. [Evidence](evidence/publication-sequence.json). |
| Direct Google Drive API backup; AS-028 | [ ] | [ ] | Blocked F-05 | Separate from Android-managed backup; policy clearance remains required. [Tracker](FEATURE-TRACKER.md). |
| Offline protection PIN/recovery/supervised exceptions; AS-063 | [ ] | [ ] | Planned ADR-092 | Design only; no runtime PIN/uninstall-resistance promise. [Protection](PROTECTION-SPEC.md). |
| Broader device-wide statistics, network/content/action outcomes; AS-023/AS-036/AS-041/AS-062 | [ ] | [ ] | Planned/experimental subsets | Research/calculation fixtures do not establish collectors. Text posts/likes/reactions/submitted comments not measured. [Tracker](FEATURE-TRACKER.md), [metric catalog](METRIC-CATALOG.md). |
| Personal equivalents, forecasts/topics and iOS; AS-030/AS-031/AS-032/AS-040 | [ ] | [ ] | Planned/experimental | No accepted complete end-user implementation from research/studies. [Tracker](FEATURE-TRACKER.md). |


## Candidate release checklist

- [ ] Resolve Facebook video-end failure, or explicitly agree a changed release scope and truthful unavailable behavior.
- [ ] Bind each claimed source/route to exact candidate, installed source version and actual pass/failure evidence.
- [ ] Close applicable counting, auto-scroll negative/lifecycle, protection escape, accessibility and history-preservation gates in [Release](RELEASE.md).
- [ ] Qualify applicable migrations, archive/backup recovery and restore failures on the concrete candidate.
- [ ] Recheck current Play declarations/policy and exact Console artifact/source/version mapping.
- [ ] Select a new display version and unused higher versionCode after Console inspection; P0 is still development 1.0.0 / 10.
- [ ] Obtain concrete-candidate maintainer merge agreement after agent readiness assessment.
- [ ] Build/review the signed artifact and obtain separate publication authorization.

## Complete task acceptance checklist

Completion here mirrors tracker task acceptance. Experimental tasks can contain working scoped product subsets above. The tracker owns prerequisites, capability mapping and detailed completion evidence. Repository/design/domain-library tasks are not app runtime features. This inventory is not a completion percentage.

| Task | Status | Accepted complete | Evidence |
|---|---|---|---|
| AS-001 Documentation foundation and public repository | implemented | [x] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-002 Latest pnpm/RN/toolchain compatibility | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-003 Native collection/lifecycle prototype | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-004 React Native Community CLI scaffold | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-005 Surface detector feasibility per target | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-006 Encryption and archive design review | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-007 Accessibility/distribution feasibility | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-008 Native contracts and platform adapter | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-009 Authoritative Room store and migrations | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-010 Consent, filters and permission health | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-011 App/device session and open reducers | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-012 Categories and social-app totals | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-013 Qualified short-video detectors | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-014 Aggregates, history and local insights | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-015 Native budget/schedule/focus engine | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-016 Reminders/intervention renderer | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-017 Overrides, exceptions, emergency access | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-018 Accessible app screens and settings | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-019 Retention, deletion and storage views | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-020 Portable export/restore | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-021 OS transfer exclusions/privacy audit | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-022 MVP distribution candidate | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-023 Notification lifecycle and association | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-024 Safe browsing/content adapters | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-025 Selective surface protection | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-026 Bedtime and richer commitment routines | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-027 Widgets and Quick Settings | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-028 Automatic personal Google backup | blocked | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-029 Scheduled picker-destination evaluation | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-030 Local forecasts/recommendations | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-031 Optional local content-topic patterns | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-032 Community iOS capability research/adapter | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-033 Humane design direction and interactive study | implemented | [x] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-034 Native live surface awareness overlay | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-035 Detailed metric semantics and pure Kotlin calculations | implemented | [x] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-036 Native network-statistics collector and attribution | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-037 Detailed mobile diary and metric explorer | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-038 Durable multi-resolution retention and archive scope | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-039 Supported personal activity archive import | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-040 Optional personal time-equivalent reflection | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-041 Per-platform activity and interaction qualification | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-042 Git identity correction and author preflight | implemented | [x] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-043 Installed Reels counter developer preview | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-044 Optional completion-triggered Reels auto-scroll | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-045 Complete native main-navigation shell | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-046 Google Play closed-test release preparation | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-047 Complete wellbeing feature vision and route-aware measurement plan | implemented | [x] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-048 Four-app counter MVP | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-049 Transparent single-screen onboarding | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-050 Data-driven MVP screens, timers and basic reminders | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-051 Unlock-session continuity, free Reels blocking and ad-count choice | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-052 Beta-preserving development branch and current handoff | implemented | [x] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-053 Editable app groups and recurring blocking periods | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-054 Photo classification repairs and Snapchat live qualification | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-055 Separate encounters and optional confirmed playback measures | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-056 Accessibility service name and direct permission navigation | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-057 Selectable appearance, accents and gentle motion | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-058 No-key Android-managed cloud backup and preserved fact origins | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-059 Stable media HUD and one explicit auto-scroll toggle | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-060 Reliable mindful-break dismissal and delayed button actions | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-061 Pause-before-breather safety and accessible playback fallback | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-062 Device-wide usage statistics and cross-app media feasibility | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-063 Offline protection PIN and supervised exceptions | planned | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |
| AS-064 Immediate standalone-ad scrolling and genuine Skip Ad feasibility | experimental | [ ] | [Scope and evidence](FEATURE-TRACKER.md) |


## Built-in scrolling investigation — ADR-101 (14/11/26)

- [x] Code: independent existing completion/gesture backend retained; conditional built-in Reels guidance row/help added.
- [x] Code: bounded operator-only fixed-token/menu probe; eight host privacy/context tests pass. This is not a production provider-control adapter.
- [x] Verified: guidance opens/dismisses on physical API36; 20 actual Settings information checks pass with appearance/backup unchanged. Actual guidance dialog visually reviewed at current phone size/text. Exact product/test hashes and separate source failures are in [evidence](evidence/builtin-autoscroll-investigation.json); broader accessibility/layout scope remains open.
- [ ] Provider auto-scroll state read/write/read-back, persistence/Stop semantics and comparative reliability. No toggle is claimed implemented.
- [ ] Independent YouTube/Snapchat completion, automatic next-item arrival and negative-case qualification. Existing no-signal findings are not passes.
