# Roadmap

**Status:** Scoped Android MVP 1.0.0 delivered; maintainer reports closed beta. Full-product completion and broader qualification remain open. Updated 2026-11-03.
**Responsibility:** Defines stage boundaries, admission/exit gates and sequencing.
**Related specifications:** [Tracker](FEATURE-TRACKER.md), [Capabilities](CAPABILITY-MATRIX.md), [Product](PRODUCT.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** F-01–F-09 may change supported delivery scope; no dates or parity promises are implied.

## Current beta continuation

ADR-067 preserves `main` as the beta release line and uses `develop` for integration. [State](STATE.md) and ADR-066 describe the current scoped MVP; the stages below retain the broader product requirements and historical sequencing, rather than instructions to recreate the scaffold.

During testing, address reported correctness/privacy/data-loss failures first, qualify missing source/locale/lifecycle behavior, complete encrypted export/restore and its storage prerequisites, then finish history/accessibility and richer voluntary protection. Keep each change independently reviewable and verified. Testing duration is a planning window, not evidence that all product gates can be completed by its end. Remaining optional analytics and blocked Google backup retain their existing dependencies.

### Product-delivery direction (2026-11-12)

The maintainer explicitly prioritizes finishing useful product features and improving presentation after several qualification-only batches. Continue with reviewable feature increments: selected-day/app history and accessible presentation (AS-014/AS-018/AS-037), chosen focus/richer protection (AS-015–AS-017), then outstanding supported-source/detail and data-ownership work. Pair implementation with bounded checks necessary for that change. Existing correctness/privacy/data-loss gates remain; repeated harness expansion is not itself visible feature completion. Bounded selected-day and searchable app-history explorers, plus experimental all-time source totals/date paging, now exist; voluntary focus/richer protection is next, with full chronology/provenance and scalable rollups still open. No promise of universal source/platform support follows.

## Stage 0: documentation foundation

The [feature vision delivery sequence](FEATURE-VISION.md#delivery-sequence-and-exit-evidence) connects these engineering stages to useful product increments: trustworthy counters, daily diary, chosen protection, complete data ownership, then deeper independently qualified detail. ADR-047 prioritizes entry-route coverage and honest encounter/playback semantics within the existing AS-013/AS-041 work. It does not replace unresolved prerequisites.

AS-001 produces the full linked source-of-truth set, root onboarding/license/agent/security material, exact latest dependency research, capability inventory, decisions, acceptance scenarios, prioritized backlog and accurate current state. Verify links and coverage, inspect public artifacts, publish to the user-designated public repository and verify remote visibility/commit. Exit: documentation prepared; application not yet implemented.

## Stage 1: feasibility and architecture proof

ADR-048 makes AS-048 the immediate delivery milestone: a Facebook/Instagram/YouTube Shorts/TikTok counter MVP before broader diary/protection expansion. This replaces the two-app publishing target, while preserving each subset's feasibility, consent, data preservation, accessibility and release gates. The full-product MVP below remains a later milestone; it is not required to call a truthful, qualified four-app counter slice useful.

The user's design continuation adds AS-033: a synthetic interaction study and [human-factors direction](HUMAN-FACTORS.md) before native UI implementation. This independent design artifact can be reviewed now; it does not clear platform, privacy, latest-stack or enforcement gates. AS-018 later implements native screens with this reference and device evidence.

AS-002 resolves latest package/toolchain compatibility; AS-003 evaluates public Android collection/lifecycle; AS-005 qualifies narrow social detector approaches; AS-006 reviews crypto/database encryption; AS-007 reviews distribution/accessibility strategy. AS-004 scaffolds only after the latest stack is coherent and records exact successful commands. Small isolated throwaway prototypes may establish feasibility, but must not be mistaken for product implementation.

ADR-025/ADR-027 add AS-035: a domain-organized pure Kotlin calculation slice for the expanded [diary and metric catalog](ANALYTICS-SPEC.md), with host and physical-Android synthetic checks. It builds independently before the mobile scaffold and does not clear Android collector/toolchain/store gates. All future mobile TypeScript source uses domain-owned root `src/features`; every implemented screen/behavior requires live phone/emulator evidence, never a browser-study substitute.

Exit requires a viable latest-version path or explicitly recorded blocked incompatibility, demonstrated consent/source/coverage handling on declared devices, evidence for at least one narrow detector if feasible, and a reviewed encrypted archive design. No downgrade or private/system-only API as an undocumented workaround. Unresolved Drive policy does not block offline MVP or portable archive development.

## Stage 2: MVP

User-directed AS-043/ADR-032 takes the narrow Reels preview ahead of full Stage-2 completion: integrate the required native-store/consent/detector/UI/HUD subsets in the main app and install a self-contained development package for both Instagram and Facebook. Detector/version evidence still gates actual counting. This does not close the archive, minimum-API, lifecycle, security or distribution gates for the full MVP; isolated fixture success is not the requested usable counter.

Deliver C-01–C-07 where supported, C-12/C-13, C-16/C-17/C-20/C-21 and C-23–C-25/C-27/C-28/C-30. Reliable app/device history and social-app summaries are the baseline; short-video support remains declared-scope experimental until qualification. Configurable permissions, source controls, basic local insights, budgets/focus schedules, emergency access and encrypted export/restore are required. Unsupported targets display gaps/unavailable status rather than artificial parity.

Exit: relevant [acceptance](TESTING.md), privacy/restore/emergency gates, latest locked clean build, accessible core journeys, real-device lifecycle evidence, truthful source/detector ranges and maintainer-reviewed signed candidate. Ship limited surface support honestly if wider detection is infeasible; revise supported claims with a decision. Do not silently remove consent, archive recovery or deletion to meet a date.

C-32 optional live awareness is an MVP candidate under AS-034, qualified independently after native detectors/consent/store and policy work. A synthetic visit/today-counter study is included in AS-033. If a safe qualified renderer is unavailable, keep counters in-app and report overlay unavailable; do not bypass system protections or expand grants silently.

## Stage 3: expansion

Notification metadata and associations, safe supported browsing/title/URL detail, selective surface protection, bedtime/DND routines, richer cooldowns/commitment controls, widgets and Quick Settings. Expand detector variants only with independent evidence. Manual picker transport gains provider-specific reliability evidence; scheduled picker use is evaluated separately.

The detailed diary adds independently qualified post/story/ad/activity/action metrics, per-app network statistics, custom day/week/month/year retention and summary archives, supported personal activity-file imports and optional user-defined time equivalents (AS-036–AS-041). The core per-platform Reels time/count entry and app/surface diary are part of AS-037's MVP screen scope; unavailable richer metrics remain visibly unavailable. Exact metrics, consent, limits and acceptance are in [Metric catalog](METRIC-CATALOG.md).

Desired automatic encrypted personal-Google-account backup remains blocked under F-05. It advances only after authoritative policy clarification and an approved transport design; no invented equivalence with Android OS backup or picker export. Every source/detail expansion needs an updated disclosure, settings, tests and capability record.

## Stage 4: continuous evolution

Explainable local predictions, optional content-topic patterns and personalized recommendations with enough covered history and calibrated evaluation. No addiction diagnosis or automatic model-directed accessibility action. Additional platform adapters and community-led iOS follow [iOS contribution boundaries](IOS-CONTRIBUTION-GUIDE.md), current platform research and entitlement evidence.

## Sequencing rules

ADR-024 limits product delivery to mobile clients: Android first, iOS later. The completed browser design study remains reference material only. No website, web app, PWA, desktop client or Chrome extension is an active delivery stage. A potential extension is deferred until mobile work is complete and separately prioritized; additional platform-adapter wording above does not authorize web/extension work.

Privacy/ownership and data reliability precede attractive charts or sophisticated forecasts. Priorities and task dependencies live in [Tracker](FEATURE-TRACKER.md); State identifies current work. A stage advances by evidence, not elapsed time. Newly reported ideas are reviewed through Queue and may adjust future scope without rewriting completed history. Keep each stage independently useful and honest about unavailable coverage.


## Comprehensive mobile statistics direction (ADR-085)

The maintainer's long-term scope is the broad [device-wide portfolio](FEATURE-VISION.md#device-wide-statistics-ambition-adr-085), including media beyond Reels and convenient wellbeing tools. Continue current visible product delivery and correctness work. Next establish AS-062's cross-app/source feasibility matrix, then deliver optional independently qualified media/activity slices through existing domain owners. Scale bounded calendar history and source-aware comparisons alongside breadth. Audio/video/local/browser/PiP/background signals must be distinguished; unknown stays unknown. Coverage grows by useful verified subsets, without activating new permissions or collecting all details by default.
