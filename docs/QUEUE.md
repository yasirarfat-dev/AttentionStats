# Incoming ideas and questions

**Status:** Intake active; no unreviewed idea is a product commitment. Updated 2026-10-27.
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

## Unscheduled evaluation questions

| ID / date | Question | Current treatment |
|---|---|---|
| Q-004 / 2026-10-27 | Would aggregate-only archive export meet users' portability needs while reducing detail? | Defer product evaluation to AS-020; must preserve clear scope/omission labels |
| Q-005 / 2026-10-27 | Which additional locales or regional/lite target variants should be qualified first? | Defer to AS-005 evidence and contributor capacity; no assumed shared selectors |
| Q-008 / 2026-10-27 | Possible Chrome extension after mobile delivery | Deferred by ADR-024 until mobile work is complete and the user separately prioritizes it. No extension task, manifest, implementation or website/web-app product is authorized now |

These are design questions, not requests waiting on the current user. Proceed with the accepted foundation and tracker. Record new user corrections directly in decisions/state rather than leaving them as unresolved queue entries.
