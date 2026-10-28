# Attention Stats: the complete product vision

**Status:** Accepted direction and staged feature portfolio; runtime acceptance remains in the tracker. Updated 2026-10-28.
**Responsibility:** Connects the long-term experience, enriched feature list and delivery priorities without promising unsupported tracking.
**Related specifications:** [Vision](VISION.md), [Product](PRODUCT.md), [Capabilities](CAPABILITY-MATRIX.md), [Metrics](METRIC-CATALOG.md), [Roadmap](ROADMAP.md), [Tracker](FEATURE-TRACKER.md).
**Unresolved feasibility:** F-01–F-10 remain applicable; a portfolio entry does not enable a source or clear a gate.

## What people should get

An open-source, ad-free, account-optional personal attention diary: understand a day, explore a habit, choose a boundary and keep ownership of the evidence. The ambition is broad everyday usefulness, including people who only want a counter. Success is a useful answer and a comfortable return to life outside the app, not more time spent checking statistics.

The same product should serve four intentions without forcing a personality test or a permanent mode. **Keep count** offers quiet in-context counters. **Understand** offers a rich diary with explanations. **Change a routine** offers a chosen goal and measured reflection. **Protect time** offers predictable boundaries and immediate safe escape. A person can mix these intentions, change them or use observation alone.

The installed preview is an early slice: five tabs, saved viewer-page estimates and an optional counter. Whole-app time, complete history, protection, recovery and the features below are not all implemented. [State](STATE.md) records the actual build. This document is the product map; domain specifications own semantics, the capability matrix owns admission, and the tracker owns execution status.

## The experience

Today answers three questions in order: what has been observed, how complete is the record, and what action would be useful now? Lead with a few chosen metrics and a finite daily timeline. Keep advanced detail one tap away. People can favor counts, time or selected apps without changing the underlying definitions.

History offers day, week, month and custom ranges, app/category/surface filters, accessible charts and equivalent tables. Every figure opens its explanation: definition, source, coverage, exclusions and known uncertainty. Patterns offers descriptive comparisons before forecasts. Protect contains intentional rules with a preview before activation. Settings makes observation, display, storage and permissions independently understandable.

Use the existing Material-style native direction: strong typography, generous spacing, restrained semantic colors, clear hierarchy and gentle optional motion. Support large text, screen readers, reduced motion, RTL, light/dark themes and one-handed operation. An unavailable metric should explain what is missing and how to resolve it when possible. Avoid a wall of disabled cards; progressively reveal the useful supported subset.

## Enriched feature portfolio

The owner column maps to existing tasks; it is not a claim that they are finished. New ideas below the table remain intake until promoted. Each shipped subset must have its own source, consent, evidence and failure behavior.

| Area | Intended features and practical value | Owners and delivery gate |
|---|---|---|
| Device and app diary | Foreground time, sessions, opens, screen/keyguard transitions, time-of-day distribution, editable app categories and gaps | AS-003/AS-011/AS-012; public Android source and lifecycle qualification |
| Rich social diary | Separate Home-feed, dedicated viewer and feed-opened viewer contexts; video/photo/post/ad/unclassified encounters; stories and supported interaction outcomes later | AS-005/AS-013/AS-041; independent app/version/locale/entry-route evidence |
| Honest video measures | Settled encounters first; separately qualified visible dwell, playback activity and completion only where reliable signals exist; scoped distinct/repeat breakdowns | AS-013/AS-041; no player-presence-to-watch conversion, no invented identity |
| Live awareness | Current visit and Today, all observed apps or current app, icon choice, drag, expanded detail, pause/hide; later readable presets, size/contrast/placement and saved preferences | AS-034/AS-043/AS-018; committed counts, privacy removal and accessible controls |
| Time at several scales | App versus surface time, sessions/day/week/month/custom ranges, midnight/timezone handling and comparable covered periods | AS-011/AS-014/AS-037; conservation and coverage before charts |
| Metric explorer | Choose useful cards and dimensions, inspect timeline/table/chart, explain a figure, inspect unknown portions and compare like-for-like intervals | AS-018/AS-037; typed bounded queries and real device screen evidence |
| Local descriptive patterns | Reopening clusters, lengthy visits, late-use patterns and notification associations with denominators; explain why a pattern appeared | AS-014/AS-023; enough covered observations, association is not causation |
| Local recommendations | Optional forecasts, confidence and calibration, editable assumptions, dismiss/correct controls and user-chosen next steps | AS-030; opt-in, sufficient history, no automatic rule changes or diagnosis |
| Intentional boundaries | Per-app/category budgets, schedules, focus sessions, reminders, conflict preview, bounded optional friction and clear override records | AS-015–AS-017/AS-026; safe enforcement, independent consent and emergency access |
| Selective protection | Restrict a qualified distracting surface while preserving useful parts of the app | AS-025; unknown contexts release rather than overblock; version regression tests |
| Rest and routines | Optional bedtime schedules, self-chosen study/work/rest periods and carefully scoped DND integration | AS-026; ownership/restoration and platform feasibility, no medical claims |
| Notification reflection | Counts and supported lifecycle/response associations without storing private message bodies | AS-023; separate grant and metadata-only source choice |
| Network perspective | Coarse OS per-app traffic with delayed availability and UID/profile attribution limits | AS-036; no traffic interception, content inspection or bytes-per-video inference |
| Optional context | Supported domain/content metadata and local topic patterns only with separate detailed consent, corrections and explicit unknown portions | AS-024/AS-031; sensitive-context exclusion, no private input capture |
| User-owned history | Encrypted local storage, selective deletion, retention preview, durable summaries, storage use and migration recovery | AS-009/AS-019/AS-038; crash, key-loss, deletion and no-resurrection tests |
| Portability | Encrypted export/restore through the system picker; supported provider archives in a separate imported lane | AS-020/AS-039; safe parser, preview, atomic recovery; direct automatic Drive remains blocked AS-028 |
| Everyday access | Widgets and Quick Settings for pause/status/useful summaries, lock-screen redaction and honest stale-state handling | AS-027; underlying native state and privacy qualification |
| Personal reflection | Optional user-defined time equivalents that respect leisure and make assumptions explicit | AS-040; arithmetic, not claims about achievement or wellbeing |
| Trust and health | Source health, last verified adapter revision, permission explanations, coverage gaps, exact data inventory and clear stop/delete actions | AS-008/AS-010/AS-021; no telemetry, hidden account or speculative grants |
| Open ecosystem | Public adapter contracts, synthetic reproduction fixtures, support matrix, translation/accessibility contributions and signed reproducible release work | AS-002/AS-022/AS-046; no private traces or executable remote selectors |
| Future platforms | Independent iPhone capability research; additive relevant social-app adapters on Android | AS-032/AS-041; no assumed parity; mobile remains the product |
| Separate convenience | Optional actual-completion-triggered scrolling with its own enable/start/stop controls | AS-044; completion, action and distribution gates; never required for statistics |

## Measurement promises

An **encounter** is an item meeting the qualified surface, visibility, continuity and settled-dwell rules. It can be paused. A **playback observation** needs an independently validated playback-state signal. A **completion** needs a validated end signal and clear seek/loop semantics. None proves human attention. A generic player, seek bar, thumbnail, scroll callback or paused video is insufficient to claim watched content.

The current 500 ms settling rule is a deduplication/calibration rule, not a watching threshold. Do not delay all counts until the person scrolls away: when first-item eligibility and stable evidence are established, an encounter can count while visible. After uncertain restoration the reducer may suppress the initial item to avoid duplicates. Explain this difference rather than fabricating an initial view.

Entry route belongs in the support matrix: Home thumbnail → viewer, dedicated tab → viewer, page/profile → viewer and shared entry may expose different interfaces. A missing dedicated-tab title cannot by itself establish that a feed-opened viewer is outside; an alternative detector still needs positive independent evidence and negative-context tests. Home media remains a separate surface even when it contains the same video.

Existing saved viewer-page rows must retain their meaning. New surface or playback metrics require reviewed contracts, migration/provenance and disclosure; do not reinterpret old counts as watched videos or silently combine Home encounters with Reels. Counts, app time and surface time need compatible scopes before comparison.

## Delivery sequence and exit evidence

1. **Finish the four-app counter MVP.** ADR-048/AS-048 prioritize Facebook, Instagram, YouTube Shorts and TikTok before the broader diary. Recover interrupted work, qualify dedicated and Home-opened viewers separately, verify idle/return/exclusion behavior, preserve history and make counter settings reliable. Ship only the supported version/locale/routes; neither broad Facebook coverage nor four-app support is established today. Preserve the essential privacy, deletion, lifecycle and release gates for this narrower milestone.
2. **Make the daily diary useful.** Complete app/session collection and native durable history; connect Today and History to real time and covered intervals. Add viewer occupancy as a separately qualified subset. Validate restart, lock, permission loss, dates and battery cost on physical devices.
3. **Make chosen protection dependable.** Add budgets, focus, schedules and reminders with conflict preview, immediate exceptions and transparent unavailable enforcement. Validate emergency routes and lifecycle before expanding rules.
4. **Make ownership complete.** Finish selective deletion, encrypted export/restore, recovery, retention and release privacy/security review. These remain core product obligations, not indefinitely deferred polish.
5. **Deepen and broaden.** Add independently qualified mixed feeds/apps, richer patterns, widgets, network detail and optional local models. Maintain a per-version support matrix, bounded resource budgets and regression fixtures.

Each slice includes visual/accessibility polish and physical-phone evidence. Release acceptance includes source precision/recall and sample sizes, supported entry routes, excluded-context behavior, no duplicate redraw counts, permission withdrawal, migration preservation and measured resource costs. Do not optimize an installation target or a composite wellness score. Evaluate task clarity, trustworthy explanations, voluntary usefulness, and user control.

## Further ideas to evaluate

Q-019 in [Queue](QUEUE.md) holds three deliberately uncommitted extensions: private manual intention/reflection notes independent of content capture; optional personal routine experiments with user-selected comparisons; and a permission-free demonstration using explicitly synthetic data kept outside real history. Each needs its own minimal specification and user research before promotion. A broad vision is permission to plan carefully, not to turn on every sensor or ship an endless settings maze.

## Latest lifetime and route priorities

User steering on 2026-10-29 reinforces lifetime per-encounter facts and origin-aware future portability, efficient native calendar summaries, all four apps’ viewer entry routes including Home, and independently verified video/photo/carousel/text/ad counters in expanded interactions. [Lifetime architecture](STORAGE-AND-RETENTION.md#lifetime-event-history-and-future-multi-origin-merging) and [route matrix](evidence/counter-route-matrix.json) make those obligations concrete. Counter MVP readiness still requires real source/route fidelity and release gates; a rich vision or Console app record is not a passing implementation.
