# Roadmap

**Status:** Prioritized delivery plan; no application milestones complete. Updated 2026-10-27.
**Responsibility:** Defines stage boundaries, admission/exit gates and sequencing.
**Related specifications:** [Tracker](FEATURE-TRACKER.md), [Capabilities](CAPABILITY-MATRIX.md), [Product](PRODUCT.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** F-01–F-09 may change supported delivery scope; no dates or parity promises are implied.

## Stage 0: documentation foundation

AS-001 produces the full linked source-of-truth set, root onboarding/license/agent/security material, exact latest dependency research, capability inventory, decisions, acceptance scenarios, prioritized backlog and accurate current state. Verify links and coverage, inspect public artifacts, publish to the user-designated public repository and verify remote visibility/commit. Exit: documentation prepared; application not yet implemented.

## Stage 1: feasibility and architecture proof

The user's design continuation adds AS-033: a synthetic interaction study and [human-factors direction](HUMAN-FACTORS.md) before native UI implementation. This independent design artifact can be reviewed now; it does not clear platform, privacy, latest-stack or enforcement gates. AS-018 later implements native screens with this reference and device evidence.

AS-002 resolves latest package/toolchain compatibility; AS-003 evaluates public Android collection/lifecycle; AS-005 qualifies narrow social detector approaches; AS-006 reviews crypto/database encryption; AS-007 reviews distribution/accessibility strategy. AS-004 scaffolds only after the latest stack is coherent and records exact successful commands. Small isolated throwaway prototypes may establish feasibility, but must not be mistaken for product implementation.

ADR-025/ADR-027 add AS-035: a domain-organized pure Kotlin calculation slice for the expanded [diary and metric catalog](ANALYTICS-SPEC.md), with host and physical-Android synthetic checks. It builds independently before the mobile scaffold and does not clear Android collector/toolchain/store gates. All future mobile TypeScript source uses domain-owned root `src/features`; every implemented screen/behavior requires live phone/emulator evidence, never a browser-study substitute.

Exit requires a viable latest-version path or explicitly recorded blocked incompatibility, demonstrated consent/source/coverage handling on declared devices, evidence for at least one narrow detector if feasible, and a reviewed encrypted archive design. No downgrade or private/system-only API as an undocumented workaround. Unresolved Drive policy does not block offline MVP or portable archive development.

## Stage 2: MVP

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
