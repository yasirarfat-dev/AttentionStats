# Incoming ideas and questions

**Status:** Intake active; no unreviewed idea is a product commitment. Updated 2026-10-28.
**Responsibility:** Captures future proposals and routes decisions into stable tasks/specifications.
**Related specifications:** [Product](PRODUCT.md), [Roadmap](ROADMAP.md), [Tracker](FEATURE-TRACKER.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** Each promoted idea needs its own source/access/controls/limits/evidence; existing gates remain F-01–F-09.

## Intake rules

An idea states user need, proposed behavior, affected capability, source, permission/entitlement, local data, user controls, limitations, phase and synthetic acceptance scenario. Use a stable Q-ID and date. Never paste private app comparisons, personal histories, device identifiers, screenshots or secrets. For external APIs cite primary documentation and mark inferred behavior explicitly.

Promotion requires a decision, domain-spec/matrix update and AS task/dependencies. Rejected or deferred ideas remain documented with a reason; they do not silently enter MVP. Queue does not override accepted privacy boundaries or enable a permission.

## Reviewed intake

| ID / received | Idea | Disposition |
|---|---|---|
| Q-001 / 2026-10-26 | Understand separate Facebook/YouTube/Instagram/TikTok short-video surfaces and counts | Promoted: ADR-011, C-06/C-07, AS-005/AS-013; experimental per target/version/locale |
| Q-002 / 2026-10-26 | Optional insight into frequently viewed content types | Promoted with boundaries: ADR-012, C-15, AS-031; local consented metadata, unknown fraction/correction; no addiction diagnosis |
| Q-003 / 2026-10-26 | Open-source and no-developer-data positioning | Promoted: ADR-013 and Privacy; local recording and optional transfers described honestly |
| Q-006 / 2026-10-27 | Thoughtful, pleasing wellbeing companion centered on peace of mind and chosen behavior change | Promoted: ADR-019/ADR-020, Human factors, AS-033; native adoption AS-018; useful voluntary engagement, no compulsive app-engagement loops |
| Q-007 / 2026-10-27 | Optional current-visit plus today stats over supported short-video surfaces | Promoted: ADR-021, [Live overlay](LIVE-OVERLAY-SPEC.md), C-32/AS-034; AS-033 synthetic demo; grants scoped by role, no real detector/renderer or permanent-background claim |
| Q-009 / 2026-10-27 | Maximum detailed user-owned per-app diary, Reels/activity/action counts, network bytes, flexible long-term summaries and personal time comparisons | Promoted with feasibility/consent limits: ADR-025/ADR-026, [Analytics](ANALYTICS-SPEC.md), [64-metric catalog](METRIC-CATALOG.md), C-33–C-38, AS-035–AS-041; pure calculations implemented, actual collectors/screens/store unimplemented |
| Q-010 / 2026-10-27 | Enterprise-style domain modularity, feature-specific root src folders, every screen/behavior verified live on phone/emulator | Accepted ADR-027; Architecture/Structure/Conventions and X-10; current Kotlin package/import boundaries executable, mobile TS/Gradle enforcement and screen evidence required at implementation |
| Q-011 / 2026-10-27 | Correct commit attribution and machine-wide Git defaults to the intended account | Accepted ADR-028/AS-042: correct initial commit metadata, set explicitly authorized global/local Git identity and verify author preflight. User withdraws creator/owner notice request; normal contributor attribution retained. No organization transfer requested |
| Q-012 / 2026-10-28 | Separate whole-app/Reels duration, session/day/week/month history and compact draggable stats | Accepted ADR-035/ADR-036; AS-043 counter control, existing AS-011/AS-013/AS-014/AS-037 for time/history; no fabricated duration in preview |
| Q-013 / 2026-10-28 | Optional auto-scroll at each video's actual end | Accepted as separate bonus scope in ADR-036/AS-044; off by default in app settings, independent of statistics. Completion-signal and action/policy qualification required before implementation |
| Q-014 / 2026-10-28 | Broad overlay customization: content/layout, transparency/color/font/size/accents/background and saved profiles | User design question; recommended progressive app settings with presets, advanced controls, reset and readable/touch-safe limits. Existing AS-034/AS-018 settings work owns future implementation; the current compact draggable counter has no customization editor or saved profiles |
| Q-015 / 2026-10-28 | Full native five-tab app shell now, then iterative feature integration | Accepted ADR-037/AS-045, experimental screens with actual supported Reels counts and honest unavailable states; no website or blanket permissions |
| Q-016 / 2026-10-28 | Home-feed videos/photos/posts/ads should be observed independently of dedicated Reels viewers | Accepted qualification priority ADR-038/AS-041 after shell verification; independent post/surface identity, dwell, carousel/ad/privacy evidence before recording; no generic swipe counter |
| Q-017 / 2026-10-28 | Distinguish total views from unique Reels and repeat viewing | Accepted ADR-039: retain settled exposures including return visits, dedupe same-page loops/redraws, separately qualify scoped distinct/repeat breakdowns without hidden content IDs. Preview explains the semantics; repeat history is unavailable until identity evidence exists |

## Unscheduled evaluation questions

| ID / date | Question | Current treatment |
|---|---|---|
| Q-004 / 2026-10-27 | Would aggregate-only archive export meet users' portability needs while reducing detail? | Promoted by explicit user request under ADR-026/AS-038; summary-only scope with loss/resolution manifest, distinct from local compaction; base archive AS-020 still a prerequisite |
| Q-005 / 2026-10-27 | Which additional locales or regional/lite target variants should be qualified first? | Defer to AS-005 evidence and contributor capacity; no assumed shared selectors |
| Q-008 / 2026-10-27 | Possible Chrome extension after mobile delivery | Deferred by ADR-024 until mobile work is complete and the user separately prioritizes it. No extension task, manifest, implementation or website/web-app product is authorized now |

These are design questions, not requests waiting on the current user. Proceed with the accepted foundation and tracker. Record new user corrections directly in decisions/state rather than leaving them as unresolved queue entries.
