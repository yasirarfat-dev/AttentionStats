# Release and distribution

**Status:** Maintainer confirms uploaded beta 1.0.0 / 10; broader Console details have not been independently inspected. Production readiness remains on hold. Updated 2026-11-14.
**Responsibility:** Defines public repository publishing, Android distribution, signing and release gates.
**Related specifications:** [Testing](TESTING.md), [Security](SECURITY.md), [Dependencies](DEPENDENCIES.md), [Contributing](CONTRIBUTING.md), [State](STATE.md).
**Unresolved feasibility:** F-01–F-04 and F-08 in [Decisions](DECISIONS.md); exact Console artifact/source mapping, review status and broader qualification remain to be verified separately from the publication report.

## Production-access continuation (2026-11-12)

**Active execution plan (14/11/26):** [Next Google Play publishing phase](PUBLISHING-PHASE.md) organizes verification and polish of implemented features into small checkpointed batches before the next Play release. New features follow publication; existing readiness gates and the concrete-candidate merge agreement still apply.

The maintainer confirms the uploaded closed-test version is **1.0.0 / versionCode 10**, and expects the test period to end on November 13. This confirms the reported version, not artifact/source mapping, continuous tester eligibility, Console warnings or approval. The source on `develop` contains subsequent changes which are not established as distributed to those testers.

[Google's testing guidance](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en), rechecked November 12, requires eligible new personal accounts to have at least 12 testers continuously opted in for the preceding 14 days when applying. The application asks about engagement, feedback, improvements and readiness; review usually takes seven days or less but can take longer. Eligibility to apply, access approval and release rollout are separate milestones. Continue closed testing while resolving issues; do not present develop-only device trials as feedback on an uploaded beta update.

The current readiness decision remains **hold** against the nine gates below. Recent scoped source, HUD, queue-pressure and key-loss checks reduce particular uncertainties; they do not close intermittent auto-scroll confirmation loss, framework fresh-install recovery, minimum-device qualification, accessibility or resource/soak gates. Keep the existing **4–8 focused engineering week** allowance as a low-confidence planning estimate for the current Android scope, with test/review waiting possibly extending elapsed time. No completion percentage or full-vision date is justified by current task states.

| Work package to finish | Concrete next deliverable |
|---|---|
| Source and convenience reliability | Reproduce the traced arrival failure; complete supported source/route and pause/seek/manual-input negative matrix; explicitly retain unavailable sources |
| Protection and lifecycle | Permission/service/process/lock trials, five-minute reset and time-trigger checks, overlaps/allowances and safe exits |
| Recovery and preservation | Isolated actual framework fresh-install recovery, maximum-history restore and interruption/low-space preservation; never reinstall the real-history debug package |
| Usability and compatibility | Core TalkBack/large-type/RTL/theme journeys, widget configuration, minimum API and declared additional devices/locales |
| Resource and security evidence | Bounded long-history measurements, seven-day resource comparison, candidate manifest/egress/log/dependency and deletion audits |
| Candidate and application | Source/artifact/signature mapping, truthful declarations/reviewer video, tester feedback summary and reviewed signed build |

The [Console preparation draft](../release/PLAY-CONSOLE.md) now removes universal-route and no-transmission claims, describes Android-managed backup and external-copy deletion limits, and leaves final Data safety answers pending review. Lack of Internet permission does not establish absence of OS/provider transfers. These edits update repository preparation only; Console, public `main` policy URLs and the private release archive are not changed by a develop push.

Recommended next display version is **1.1.0** for the accumulated features; **1.0.1** fits a smaller fixes-only candidate. This is a recommendation, not a version reservation or source change. [Android versioning](https://developer.android.com/studio/publish/versioning) requires a higher internal versionCode for successive releases and prohibits reusing an uploaded code; 11 is a candidate only after confirming no higher/used code in Console. Version naming does not qualify an untested binary. A concrete candidate still needs maintainer agreement before main merge, and signing/upload/rollout remain separate actions.

Current develop backup exception (ADR-073/ADR-079/AS-058): `allowBackup=true` is paired with an automatic-by-default protected-transport agent that preserves saved opt-outs, and a one-file include list; live databases/keys/preferences and device transfer remain excluded. Qualify the merged manifest/resources and actual recovery before release. The default 0.1.0 `tools/release_candidate.py` profile still guards the historical all-backup-disabled candidate. A separate current profile and `tools/audit_current_release.py` now audit explicit expected versions, current package visibility/components and compiled APK/AAB backup rules. Static inspection never establishes actual framework recovery or complete release acceptance. No release upload or main merge is authorized by the current checks.

## Current public policy review (2026-11-14)

[Accessibility guidance](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) still requires narrow, understandable actions, a declaration/review and separate prominent disclosure for apps that are not accessibility tools; its deterministic automation allowance is not this candidate’s approval. [Personal-account testing guidance](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-en) still specifies 12 continuously opted-in testers for 14 days for eligible new personal accounts; actual account category, eligibility and Console records remain uninspected.

[Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) makes the external-backup exception conditional, including that the app never accesses the data. This app constructs a logical history copy, so do not assume that exception automatically covers it. Final answers require review of the actual uploaded implementation and transport behavior; no final form or approval is inferred.

Maintainer November 14 scope decision: keep auto-scroll in the release and continue qualification with publication held. No capability exclusion, merge or binary publication is authorized by this decision.

## Readiness assessment (2026-11-07)

The current Android beta is functional within its recorded scope; it is **not cleared for a fully qualified production release**. Readiness requires a declared supported scope, passing applicable scenarios on the concrete candidate, and maintainer acceptance. No amount of unit-test passing establishes universal device/source reliability or guarantees defect-free software. This assessment uses repository evidence, not a new Play-policy or Console inspection.

The tracker contains 61 tasks: six implemented, 43 experimental, eleven planned and one blocked. These are task states, **not a completion percentage**: old scaffold rows retain broad qualification gates while later milestones implement useful overlapping subsets. Conversely, a published beta does not close those gates. Do not estimate remaining effort by subtracting six from 61.

| Remaining release gate | Owners | Required closing evidence |
|---|---|---|
| Detector/counting fidelity | AS-013/AS-048/AS-054/AS-055 | Declared app/version/locale/route matrix; independently observed settled items versus saved counts, ads/photos/revisits/negative contexts, explicit unsupported playback metrics. |
| Protection and safe escape | AS-015–AS-017/AS-053/AS-060/AS-061 | Live selective and whole-app rules, overlaps/allowances, time-trigger/reset, delayed/repeated actions, safe navigation and pause/fallback behavior. Historical unexplained reminder failure remains separately tracked. |
| Data preservation and recovery | AS-006/AS-009/AS-019/AS-020/AS-058 | Upgrade/migration, interruption/low-space/key-loss preservation, actual fresh-install recovery on an isolated installation, corrupt/cancelled replacement and maximum logical-history restore. Envelope readback and transport handoff alone are insufficient. |
| Lifecycle and time correctness | AS-003/AS-010/AS-011/AS-014 | Applicable L-01–L-07 on declared supported devices: process death, restart, lock, revoked permission, offline use, clock/zone/day boundaries and honest gaps. |
| History, coverage and bounded resources | AS-011/AS-014/AS-037/AS-038 | Correct totals/unknown coverage, bounded queries and declared long-history dataset; measured cold/warm latency, memory/storage and seven-day battery/CPU baseline comparison (L-08). |
| Accessibility and visual polish | AS-018/AS-045/AS-056/AS-057/AS-059 | Core journeys with TalkBack, large type, light/dark themes, reduced motion, RTL, keyboard/insets and relevant loading/empty/error/partial states on a real phone; widget configuration included. |
| Privacy and security | AS-006/AS-007/AS-021 | Applicable S-01–S-06: merged component/permission/backup audit, release egress/log inspection, deletion/no-resurrection, malicious input/resource bounds, dependency/license/vulnerability review. Public-package files must not be described as secret encrypted archives. |
| Device compatibility and build maintenance | AS-002/AS-004/AS-018/AS-022 | Minimum supported API plus declared OEM/API/locale matrix and release-binary execution; disposition of existing AGP/Kotlin/CMake warnings. The current API-36 phone cannot qualify older APIs. |
| Release provenance and distribution | AS-022/AS-046 | Exact source/artifact/signature mapping, current signed candidate and declarations, verified Console testing/review state, current official policy review and recovery/release notes; explicit maintainer agreement before main merge. |

Recommended sequence: close selective protection and reminder timing first; prove isolated recovery/data-preservation next; complete the declared source/device matrix and accessibility/lifecycle checks; then review the exact signed release candidate. Fixes may reopen affected checks. No remote workflow, merge, signing or publication follows automatically from this plan.

For planning only, reserve roughly **4–8 focused engineering weeks** for the current Android beta's remaining qualification, fixes and polish, assuming the required devices and Console evidence are available and no major detector/storage redesign emerges. This is a low-confidence effort allowance, not measured velocity or a delivery promise; elapsed tester/review/soak time can extend it. Re-estimate after the protection and recovery gates expose actual defects. A single chat session cannot clear the multi-device, seven-day resource and distribution gates.

The broader product vision adds unfinished diary/budget/focus behavior and optional notifications, richer interactions/network analytics, retention/import, predictions, actual synchronization and later iOS. Those are additional development milestones with independent feasibility gates, not final QA of today's beta. Direct Drive API remains blocked under F-05. No credible full-vision completion date is established yet; scope and feasibility must be resolved before scheduling it.

## Beta release and development branches

The maintainer reports that closed beta is published (2026-11-03). Source currently declares versionName **1.0.0**, versionCode **10**, package **attention.stats**. Do not infer the uploaded artifact checksum, source commit, tester start dates, production eligibility or policy approval from that report. Earlier preparation details below are historical; consult actual Console records before selecting a new version code or changing store claims.

Under ADR-067/ADR-068, `main` is the beta release line; `develop` starts at source commit `1fc538221adfa3e66c1d94bdb9a0258509dc188c`. Use small task branches from `develop` when useful and integrate reviewed, locally checked changes there. A merge to `main` requires both the maintainer's explicit agreement to the concrete candidate and the agent's readiness assessment after applicable phone, data-preservation and release checks. Passing tests or standing push authorization is insufficient. Branching does not configure GitHub protection rules or establish the source of the already uploaded binary. No new tag is assigned to that binary without artifact/source evidence.

Urgent beta fixes may branch from `main`; propagate the reviewed fix into `develop` so it is retained. Preserve history, avoid force pushes, and use forward fixes where schema changes prevent safe binary rollback. All workflows remain manual only; merging/pushing does not authorize a workflow run, signing, upload or rollout.

## Historical closed-test preparation

ADR-043 sets the production package exactly **attention.stats**, versionName 0.1.0 / initial versionCode 1, subject to Console uniqueness. Keep the `com.attentionstats` source namespace and existing `com.attentionstats.debug` development identity/history; neither changes the Play package. Release snapshot contract 3 hides development-only structure controls and native code rejects those sessions.

ADR-064 reserves versionCode 2 for the resumed signed 0.1.0 candidate; code 1's unsigned investigation archive remains immutable. Check actual Console use before uploading any version code.

[Play Console information](../release/PLAY-CONSOLE.md) supplies listing text, app-content declarations, reviewer steps, assets, signing/tester/rollout fields and current blockers. Public user-facing [privacy](../policies/PRIVACY.md), [support](../policies/SUPPORT.md), [local deletion](../policies/DATA-DELETION.md) and [testing](../policies/CLOSED-TEST.md) documents are separate from internal specifications. The app links the policy. Publishing these GitHub documents is within the user's instruction; no GitHub Pages or automatic pipeline is needed. Signed-out URL accessibility must be verified after publication.

Code/builds remain in this repository. The separate local archive is the maintainer's private release folder (outside the repository), with `PLAY-CONSOLE.md` at its root and versioned `releases/<versionName>+<versionCode>/{android,metadata,notes}`, plus store, policies, testing and private/signing folders. Keep credentials and personal tester/feedback material outside Git. Never overwrite an archived release or represent an unsigned investigation bundle as uploadable.

Closed testing can begin before the broad roadmap is complete only with a working, accurately described scoped candidate and applicable source/privacy/security/policy checks. ADR-066 and State supersede the earlier Facebook/Home availability restriction for the documented MVP subset. Recheck [Google's testing guidance](https://support.google.com/googleplay/android-developer/answer/14151465) and the actual account's Console requirements when assessing production eligibility; no current policy review was performed for this branch setup. Documents, sideloads and internal tests do not establish a Play test period. No production-approval date is promised.

## Documentation publication

The designated public repository is [yasirarfat-dev/AttentionStats](https://github.com/yasirarfat-dev/AttentionStats). The user explicitly authorized public publication of this foundation. Review all files, run documentation checks and whitespace validation, inspect the staged tree for secrets/private material, commit and push without application scaffolding. Verify repository visibility and the remote commit; record actual evidence in the dated log. Do not claim a binary release or publish invented badges/download links.

GitHub documentation CI uses read-only repository permission, Python and no release credentials. Under ADR-030 all CI/CD workflows are manual only (`workflow_dispatch`); publishing a commit or opening/updating a pull request starts no pipeline. Run appropriate local checks before publication; a remote run requires an explicit user request. Public issue/PR templates require synthetic reports. Private vulnerability reporting should be enabled when repository administration permits. Documentation “implemented” means the documents/checks exist; it does not change any application capability's status.

## Android release ownership

The maintainer authorized conventional Play App Signing on 2026-10-30: Google should generate/hold the delivery app-signing key, with a separate local upload key used for the AAB. `python tools/release_signing.py provision` refuses existing key material and keeps it outside the repository; its Windows current-user DPAPI password file is not a portable backup. Key readback and protection/rejection safeguards pass. Independent maintainer-controlled encrypted recovery backup, Console enrollment and exact signed-artifact/runtime checks remain distinct gates. To build with the external configuration, pass `--signing-config <external-upload-signing.json>` to `tools/android_build.py` with release tasks only. Without explicit credentials, release inspection builds remain unsigned. Secrets never appear as command-line password values or enter Git.

Release maintainers own permanent application ID/namespace, package signing, Play Console credentials, store declarations and rollout decisions. Contributors may build debug artifacts using local debug signing, but must never commit keystores/passwords or release signatures. Generate release credentials outside the repository with encrypted maintainer-controlled backup and access recovery. A signing key is not the archive recovery key or device DB key.

Select and document Play App Signing and upload-key responsibilities before publishing to a store. Record certificate fingerprints only if needed as public distribution metadata; no private key paths/secrets in logs. Every artifact has versionName/versionCode, source commit, exact locked dependency/toolchain manifest, license notices and checksum. Never reuse or decrement Android version codes.

## Binary release gates

**2026-11-14 latest assessment: HOLD.** Fresh P5 mixed-item testing passes one Facebook standalone-ad swipe followed by one numerical video-end swipe, each with native and independent arrival and actual HUD Stop. The three-arrival/180-second target remains unmet because the next reel has no usable progress; another current-item trial has no numerical signal, and a bounded numerical trial never reaches the end. Earlier zero-confirmation failures remain historical, superseded only within the scoped later passes. No timer fallback or broader continuous-source claim. [Sequential results](evidence/publication-sequence.json), [handoff](IMPLEMENTATION_LOG/2026-11-14.md). The remaining recovery, lifecycle, accessibility, resources, security, compatibility and distribution gates remain open; no merge/publication approval is inferred.

**2026-11-13 assessment: hold the next merge/publication candidate.** The maintainer reports Facebook auto-scroll still failing; current-artifact physical investigation finds no readable numeric playback signal in 150 qualified viewer samples and no selected-source media session in a separate 30-sample probe. No fix or actual end/arrival/Stop acceptance is established. Existing source schedules are preserved when the live trial refuses its precondition. See [investigation evidence](evidence/publication-readiness.json) and the [complete feature/verification checklist](FEATURE-VERIFICATION.md). A build can succeed while this release gate remains open. Do not relabel historical baseline/source passes as current candidate readiness. A changed release scope requires an explicit decision and truthful unavailable behavior; it is not inferred from these negative probes.

1. Select candidate from passing code/review and accurate tracker/specs. Re-resolve latest only in a reviewed update task; release builds consume the reviewed lockfile.
2. Build signed release AAB/APK reproducibly from clean state using frozen-lockfile and pinned native toolchain. Record any reproducibility limitation, including signing timestamps. Inspect merged manifest, exported components, permissions, backup exclusions and dependency notices.
3. Pass relevant [acceptance scenarios](TESTING.md), including real-device lifecycle, enforcement escape routes, crypto/restore failure, egress/log checks and OS cloud/device transfer exclusions. Unsupported detectors remain disabled with declared ranges.
4. Recheck current Play rules for target API, accessibility, package visibility, foreground services, permission disclosures, privacy and Data safety. Rules change; use current primary documentation rather than hardcoded target deadlines. The product must not claim accessibility-tool eligibility or autonomous actions.
5. Publish an accurate public privacy notice and release notes describing data stored locally, optional transfers, coverage, known gaps, experimental detectors and rollback/recovery. Marketing follows [Privacy](PRIVACY.md), not “collects no data.” Direct Drive backup remains unavailable while F-05 is blocked.
6. Maintainer reviews the concrete candidate and store forms, signs off, and starts internal/closed testing before staged public rollout. Monitor voluntary synthetic feedback; no remote telemetry pipeline. Do not infer policy approval from another application's listing.

## Recovery and updates

Schema changes need tested migration paths and a pre-update encrypted-export option where appropriate. Never force a destructive reset to recover from migration failure. Rollback may be limited by schema/signing/version constraints; document a forward-fix strategy and archival compatibility. If a detector breaks after a target app update, mark its support unavailable and ship a reviewed fix; no remote silent data-collection expansion.

Security incidents follow [reporting policy](../SECURITY.md). Withdraw unsafe release links, disable an affected feature in the next reviewed release when necessary, preserve user history where possible and state affected versions. No over-the-air executable code, hidden policy bypass or automatic diagnostic upload is part of release operations.


November 14 concluding development checkpoint: one source-qualified standalone Facebook ad now passes actual HUD Start/automatic different-page confirmation/Stop, with exact artifact and native payload comparison in [continuation evidence](evidence/facebook-appearance-widget-continuation.json). This removes the earlier zero-confirmation result only for that bounded trial; repeated Facebook end scrolling, unknown-progress/overlay behavior, genuine provider controls and broader release gates remain open. Appearance/widget delivery is separate from scroll acceptance. No main merge, signing, binary publication or remote workflow follows this checkpoint.


November 14 day/icon concluding checkpoint remains HOLD: installed P8 has scoped UI/native evidence, but fresh cold Insights shows history unavailable and the final two JS refinements await build/render acceptance. Facebook current trials stop before Start on the settled-viewer precondition; no earlier repeated-end gate is cleared. Emulator/other-device work is explicitly deferred until product completion, with zero accepted size profiles. No merge, version/signing/Console or binary publication clearance. [Exact scope and cleanup](evidence/day-insights-continuation.json).
