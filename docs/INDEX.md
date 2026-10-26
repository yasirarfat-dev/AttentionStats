# Documentation index

**Status:** Authoritative documentation foundation; application not yet implemented. Updated 2026-10-27.
**Responsibility:** Maps every source-of-truth document, reading order and ownership boundary.
**Related specifications:** [Current state](STATE.md), [Shared rules](AGENT-RULES.md), [Tracker](FEATURE-TRACKER.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** F-01–F-09 in [Decisions](DECISIONS.md); specifications do not constitute device, policy or build verification.

## Read first

Attention Stats is a local-first, open-source personal digital wellbeing app: **observe → track → categorize → predict → protect**. Android launches first using React Native Community CLI, TypeScript and New Architecture; Kotlin owns collection/rules and Room-backed local SQLite. Future iPhone work uses explicit adapters. Public repository: [yasirarfat-dev/AttentionStats](https://github.com/yasirarfat-dev/AttentionStats). Local root: `C:\home\biobot\projects\AttentionStats`.

**Mobile clients only.** The browser study is a completed, non-shipping design reference, not a website/web-app product. A possible Chrome extension is deferred until mobile work is complete and separately prioritized; there is no active extension implementation (ADR-024).

**Documentation prepared; application not yet implemented.** A separate [interactive design study](../design/prototype/README.md) uses synthetic data and implements no tracking or native protection. Application runtime behavior remains a specification. No capability is implemented and no Android scaffold/binary exists. Mobile dependencies remain unverified candidates; the design study has its own latest test tools and pnpm lockfile.

| Reader/task | Reading order |
|---|---|
| New contributor | Vision → Product → Capabilities → State → Roadmap → Tracker → Contributing |
| Every human/agent session | Agent rules → State → Tracker → relevant decisions and domain specs |
| Native tracking | Tracking → Social → Permissions → Models → Native contracts → Testing |
| Protection | Protection → Permissions → Architecture → Contracts → Testing |
| Interface work | Human factors → UX → Navigation → Design system → Settings → Contracts |
| Data/privacy/backup | Privacy → Security → Models → Storage → Backup → Testing |
| Dependency/scaffold | Dependencies → Development → Conventions → Structure → Architecture |
| Release/iPhone work | Release or iOS guide → Capabilities → current platform evidence → contribution workflow |

## Source-of-truth map

| Area | Document | Responsibility |
|---|---|---|
| Entry | [INDEX.md](INDEX.md) | Reading map and ownership |
| Direction | [VISION.md](VISION.md) | Mission, audience and principles |
| Product | [PRODUCT.md](PRODUCT.md) | Scope, journeys, non-goals, acceptance |
| Inventory | [CAPABILITY-MATRIX.md](CAPABILITY-MATRIX.md) | Every capability's source/access/controls/limits/phase/evidence/scenario |
| Observation | [TRACKING-SPEC.md](TRACKING-SPEC.md) | Sessions, opens, screen/keyguard, notifications, coverage/time |
| Surfaces | [SOCIAL-MEDIA-SPEC.md](SOCIAL-MEDIA-SPEC.md) | App versus surface detection, exposures, safe optional metadata |
| In-context awareness | [LIVE-OVERLAY-SPEC.md](LIVE-OVERLAY-SPEC.md) | Optional visit/day counters, native snapshots, renderer safety and permission boundaries |
| Interpretation | [BEHAVIOR-INTELLIGENCE.md](BEHAVIOR-INTELLIGENCE.md) | Categories, patterns, predictions, optional topics and confidence |
| Protection | [PROTECTION-SPEC.md](PROTECTION-SPEC.md) | Limits, focus, schedules, conflicts, overrides and safe enforcement |
| Experience | [UX-SPEC.md](UX-SPEC.md) | Screen inventory, onboarding, charts, errors and accessibility |
| Human experience | [HUMAN-FACTORS.md](HUMAN-FACTORS.md) | Supportive engagement, psychological rationale, language, trust and review |
| Routes | [NAVIGATION.md](NAVIGATION.md) | Hierarchy, typed routes, native flows and back behavior |
| Visuals | [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) | Proposed tokens/components, themes, charts and motion |
| Controls | [SETTINGS-SPEC.md](SETTINGS-SPEC.md) | Complete settings catalog, defaults and persistence |
| System | [ARCHITECTURE.md](ARCHITECTURE.md) | Native/JS boundaries, data flow, lifecycle and adapters |
| Placement | [FILE-STRUCTURE.md](FILE-STRUCTURE.md) | Existing docs tree versus proposed implementation tree |
| Interfaces | [NATIVE-CONTRACTS.md](NATIVE-CONTRACTS.md) | Operations, types, paging, errors, events and revisions |
| Schema | [DATA-MODELS.md](DATA-MODELS.md) | Entities, time, provenance, relations and migrations |
| Durability | [STORAGE-AND-RETENTION.md](STORAGE-AND-RETENTION.md) | Preservation, pruning, deletion, dedup and growth |
| Privacy | [PRIVACY.md](PRIVACY.md) | Inventory, consent, transfers, no-developer-data claims |
| Security | [SECURITY.md](SECURITY.md) | Threat model, keys, encryption target and release gates |
| Access | [PERMISSIONS.md](PERMISSIONS.md) | Declarations, grants, separate disclosures and revocation |
| Portability | [BACKUP-RESTORE.md](BACKUP-RESTORE.md) | Encrypted archives, restore atomicity, blocked Drive and OS exclusions |
| Standards | [CONVENTIONS.md](CONVENTIONS.md) | Coding/state/logging/dependency rules and done criteria |
| Stack | [DEPENDENCIES.md](DEPENDENCIES.md) | pnpm/latest policy, exact candidates/licenses/compatibility evidence |
| Quality | [TESTING.md](TESTING.md) | T/L/P/D/B/S/X scenarios and evidence levels |
| Setup | [DEVELOPMENT.md](DEVELOPMENT.md) | Runnable docs commands, future isolated app setup and debugging |
| Publishing | [RELEASE.md](RELEASE.md) | Public docs and future Android signing/store rollout |
| Community | [CONTRIBUTING.md](CONTRIBUTING.md) | Public issues/PRs, review, fixtures and licensing |
| iPhone | [IOS-CONTRIBUTION-GUIDE.md](IOS-CONTRIBUTION-GUIDE.md) | Future capability/entitlement/adapter boundaries |
| Workflow | [AGENT-RULES.md](AGENT-RULES.md) | Equal agent/human rules, precedence and continuity |
| Stages | [ROADMAP.md](ROADMAP.md) | Foundation, feasibility, MVP, expansion and evolution gates |
| Backlog | [FEATURE-TRACKER.md](FEATURE-TRACKER.md) | Stable prioritized AS tasks and evidence |
| Truth now | [STATE.md](STATE.md) | Current artifacts, checks, limitations and next work |
| Intake | [QUEUE.md](QUEUE.md) | Reviewed ideas and unscheduled design questions |
| Choices | [DECISIONS.md](DECISIONS.md) | Accepted ADRs and unresolved F gates |
| History | [IMPLEMENTATION_LOG/README.md](IMPLEMENTATION_LOG/README.md) | Dated execution evidence, starting [2026-10-27](IMPLEMENTATION_LOG/2026-10-27.md) |

## Supporting material

[Dependency metadata](evidence/dependency-candidates.json) is public registry evidence, not an installed manifest. [Documentation checker](../tools/check_docs.py) and [CI](../.github/workflows/docs.yml) verify structural consistency. Root [README](../README.md), [license](../LICENSE), [contributor entrypoint](../CONTRIBUTING.md), [security entrypoint](../SECURITY.md), [AGENTS](../AGENTS.md) and [CLAUDE](../CLAUDE.md) provide repository onboarding.

Each authoritative document declares status, responsibility, related specifications and unresolved feasibility. Avoid duplicating normative definitions: contracts own interface shapes, data models own entity meaning, domain specs own behavior, matrix owns capability admission, tracker owns completion and State owns current truth. Update them together when implementation changes the design. Use synthetic examples and maintain public safety.
