# Decisions and feasibility register

**Status:** Accepted foundation decisions; feasibility gates open. Updated 2026-10-27.
**Responsibility:** Owns deliberate architectural/product choices, evidence and unresolved gates.
**Related specifications:** [Product](PRODUCT.md), [Architecture](ARCHITECTURE.md), [Dependencies](DEPENDENCIES.md), [Backup](BACKUP-RESTORE.md), [Tracker](FEATURE-TRACKER.md).
**Unresolved feasibility:** F-01–F-09 below; none has application/device verification yet.

## Accepted decisions

| ID / date | Decision | Rationale and consequences |
|---|---|---|
| ADR-001 / 2026-10-26 | Product “Attention Stats”; open source Apache-2.0; Android first | Local personal wellbeing around observe → track → categorize → predict → protect; iPhone is future contribution scope |
| ADR-002 / 2026-10-26 | React Native Community CLI, TypeScript, New Architecture | Native extensibility for permitted Android capabilities; no Expo scaffold in this task |
| ADR-003 / 2026-10-26 | Kotlin collectors/rules and one authoritative Room-backed SQLite store | Collection/protection independent of JS; MMKV for non-sensitive interface preferences only |
| ADR-004 / 2026-10-26 | All sources off until explicit consent; separate content/topic controls | OS grant alone is not enablement; app/source/category exclusions and pause/retention remain user-owned |
| ADR-005 / 2026-10-26 | Preserve history until deletion by default | Optional pruning/export/selective deletion/storage visibility; no hidden space-triggered purge |
| ADR-006 / 2026-10-26 | Local interpretation, no backend/account/ads/analytics/automatic uploads | No developer recipient; optional approved transport is explicit and ciphertext-only |
| ADR-007 / 2026-10-26 | Deterministic user rules; predictions recommend only | Static accessibility actions require narrow user definition and policy review; no model-directed automated protection |
| ADR-008 / 2026-10-26 | Portable encrypted archive independent of provider | Recovery credentials user-held; staged authenticated restore; MVP explicit replacement, no merge |
| ADR-009 / 2026-10-26 | Desired automatic personal Google backup; direct Drive blocked | Published Drive policy conflicts with app-content backup; no assumed exception or reusable private service mechanism |
| ADR-010 / 2026-10-26 | pnpm; every new direct package resolves at latest | Record exact versions/licenses/compatibility; lockfile for builds; no silent older-version workaround |
| ADR-011 / 2026-10-26 | Dedicated versioned app/surface detectors, counts are exposures/estimates | Package identifies app; structure identifies surface; confirmed transitions dedup repeated callbacks; unsupported stays unknown |
| ADR-012 / 2026-10-26 | Optional local content-topic patterns, no addiction diagnosis | Needs separate consented supported metadata; unknown fraction and correction; no screen/audio capture or sensitive-trait inference |
| ADR-013 / 2026-10-26 | Privacy message “No data sent to us. Your history stays on your device. Open source.” with transfer qualification | Local tracking records data; do not claim zero collection or conceal user-selected provider transfers |
| ADR-014 / 2026-10-26 | Equivalent agent/human rules and continuity | Same precedence/status/update duties; compact around 100k–150k context tokens when supported; preserve checkpoint and continue |
| ADR-015 / 2026-10-26 | Tools may be added in isolation; preserve shared-machine workflows | No global version/profile/timezone/process changes for unrelated agents; no delegation unless requested |
| ADR-016 / 2026-10-26 | Final path `C:\home\biobot\projects\AttentionStats`; repo `yasirarfat-dev/AttentionStats`, **public** | Supersedes initial path/repository suggestions; only the project folder moved, no other projects touched |
| ADR-017 / 2026-10-27 | Documentation foundation only in this delivery | Complete/verify/publish docs and onboarding; all runtime capabilities remain unimplemented |
| ADR-018 / 2026-10-27 | Explicit exclusions for Android OS cloud backup/device transfer | allowBackup flag alone insufficient evidence; sensitive data must not move through undocumented defaults |
| ADR-019 / 2026-10-27 | Peace of mind, self-understanding and voluntary useful engagement govern design | [Human factors](HUMAN-FACTORS.md) defines autonomy, supportive voice, finite summaries, inspectable trust, thoughtful aesthetics/motion and no guilt/streak loops; no dopamine/addiction measurement or proven-benefit claim |
| ADR-020 / 2026-10-27 | Continue after the completed foundation with AS-033 design study before native UI work | Supersedes ADR-017's completed-delivery stopping point for this continuation; a synthetic, isolated browser prototype makes the user's design direction concrete without bypassing AS-002/AS-004/AS-018 or claiming runtime support |
| ADR-021 / 2026-10-27 | Optional live visit/day surface-awareness display, separate from collection and protection | User requests in-context reels/shorts counts and thoughtful support; [overlay spec](LIVE-OVERLAY-SPEC.md), C-32/AS-034 define native scope; AS-033 demonstrates synthetic interaction only. Accessibility detects, a chosen renderer displays; battery/background grants are neither bundled consent nor permanence guarantees |
| ADR-022 / 2026-10-27 | Small top-center HUD is the live-counter default | Explicit user placement preference; visit/day text and detail on tap, safe insets, no decorative mascot, alternate presets if needed; no private phone data or reference assets in public artifacts |
| ADR-023 / 2026-10-27 | Short-video counts exclude positively identified ads and other non-qualifying media contexts | Clarifies ADR-011 eligibility: a finger swipe is not a qualifying video exposure; ambiguous transitions are withheld with incomplete coverage; returning from excluded contexts needs continuity/dedup evidence; a future all-feed/ad count would be separately specified |
| ADR-024 / 2026-10-27 | Mobile clients only; Android first, iOS later; potential Chrome extension deferred | Explicit user clarification: no website/web app/PWA/desktop client or extension in current delivery. AS-033 is a completed non-shipping design reference, not an ongoing web track or embedded runtime. Next implementation follows native feasibility/scaffold tasks. Any Chrome extension waits until mobile work is complete and is separately prioritized |

No decision here establishes a permanent Android application ID, signing identity, cloud authorization scope or a reviewed cryptographic suite. Those require tracked implementation evidence. Supersede decisions by adding a new dated record and linking the predecessor; do not silently erase rationale.

## Feasibility register

| Gate | Status / owner task | Question / required evidence to clear | Safe interim behavior |
|---|---|---|---|
| F-01 Surface/content fidelity | Open; AS-005/AS-013/AS-024 | Per-target package/version/locale selectors and item transitions, precision/recall, private/sensitive exclusion; no raw private traces in Git | App totals only; surface/count/content unknown; no fabricated selectors |
| F-02 Android lifecycle/enforcement | Open; AS-003/AS-007/AS-021 | Permitted background/source strategy, reboot/unlock, OEM battery restrictions, force-stop, renderer/exclusion/OS-transfer tests | Visible coverage gaps, no guarantee of permanent tracking/blocking |
| F-03 Latest stack compatibility | Open; AS-002 | CLI latest documented range vs RN 0.88 registry, exact Nitro pairing, TypeScript/tooling, pnpm scripts and clean native builds | No older fallback, no scaffold presented as working |
| F-04 Crypto/storage/local models | Open; AS-006/AS-030 | Audited archive suite/KDF/framing, Room encryption driver and key lifecycle, device performance, local model calibration | No implemented-encryption or forecast claims; documented design only |
| F-05 Direct Drive policy | **Blocked**; AS-028 | Dated authoritative clarification permitting this intended app-content backup use, approved scopes/transport and security obligations | No OAuth/upload; manual portable encrypted archive independent |
| F-06 Picker/provider durability | Open; AS-020/AS-029 | SAF destination behavior, URI grants, cancellation, quota, offline status, completion and safe replacement; scheduling separately evaluated | Manual transport candidate; never advertise equivalent automatic Google backup |
| F-07 iOS capabilities | Open; AS-032 | Current Apple public API/entitlement/distribution review, native design and physical-device evidence | Typed unsupported adapter; no parity or dates promised |
| F-08 Accessibility/distribution | Open; AS-007 | Current store policy, narrow deterministic rules, in-app separate consent, declaration/review and safe emergency routes | No autonomous action; no accessibility-tool claim; blocked release of unapproved scope |
| F-09 Metric quality | Open; AS-003/AS-011/AS-014 | App opens/multi-window/keyguard semantics, missing events, coverage denominators and calibrated insight thresholds | Distinguish observations/estimates; withhold unsupported inference |

## Sources and review discipline

Reviewed public primary sources: [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager), [UsageEvents](https://developer.android.com/reference/android/app/usage/UsageEvents.Event), [accessibility metadata](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo), [Android FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en), [Workspace API policy](https://developers.google.com/workspace/workspace-api-user-data-developer-policy), [Android backup](https://developer.android.com/identity/data/autobackup), and [latest dependency evidence](DEPENDENCIES.md). Policy research occurred 2026-10-26; dependency review continued 2026-10-27. These sources support boundaries, not completed feasibility tests.

Recheck volatile policy and package facts at implementation/release. A contradictory source produces an open question, not an invented resolution. Add authoritative evidence and update affected specs, matrix, task status and log when clearing a gate. No gate is closed in this foundation merely because documentation is comprehensive.
