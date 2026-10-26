# Product specification

**Status:** Accepted scope; all application capabilities planned, experimental, or blocked. Updated 2026-10-26.
**Responsibility:** Defines product behavior, journeys, delivery scope, and success criteria.
**Related specifications:** [Vision](VISION.md), [Capabilities](CAPABILITY-MATRIX.md), [UX](UX-SPEC.md), [Roadmap](ROADMAP.md).
**Unresolved feasibility:** F-01 through F-09 in [Decisions](DECISIONS.md); no universal observation or enforcement guarantee.

## Scope

Attention Stats is an open-source, Android-first personal digital wellbeing app. React Native Community CLI provides the interface; Kotlin owns collection, deterministic protection, and Room-backed SQLite. The intended public home is `yasirarfat-dev/AttentionStats`, licensed Apache-2.0. The initial repository contains documentation and review tooling only.

The product covers device and app activity, social-app summaries, supported surfaces and short-video observations, editable categories, local descriptive insights, optional predictions, and user-defined protection. Later capabilities add notification behavior, supported browsing context and content history, richer routines, widgets, Quick Settings, and community-led iPhone support. The [capability matrix](CAPABILITY-MATRIX.md) is the exhaustive capability inventory and admission gate for additions.

Experience quality is part of the product: a trustworthy, calm companion for peace of mind, clear self-understanding and chosen boundaries. [Human factors](HUMAN-FACTORS.md) governs supportive engagement, copy and evaluation. A finite snapshot, honest evidence and optional reversible next steps are preferable to maximizing time in this app. The [interactive design study](../design/prototype/README.md) is synthetic and implements no product collection/enforcement.

All observation sources start off. Users choose sources, tracked applications and categories, exclusions, content detail, retention, and timed pauses. Optional titles and URLs require separate opt-in. Exclusions win over inclusions; password fields and sensitive input surfaces never qualify. Enablement applies from consent time forward: no silent historical import. A later explicit backfill feature would require its own specification and consent.

Users may choose observation only, reminders, immediate override, or voluntarily stricter rules per protection rule. No rule may hide emergency access, obstruct Android permission settings, or automatically escalate based on a prediction. Local history remains until deleted unless the user selects pruning. Export, selective deletion, storage visibility, and an all-data reset are core controls.

## Primary journeys

1. **Understand a day:** Skip permissions during onboarding; see an honest empty state. Enable app usage, choose synthetic `com.example.reader` and `com.example.social`, then see sessions, totals, opens, provenance, and gaps after activity occurs.
2. **Explore a pattern:** Open the social summary, inspect a supported short-video surface, and see “observed transitions,” confidence, and detector coverage. Correct a category without rewriting the underlying event.
3. **Act on a goal:** Set a daily social-category budget and weekday focus schedule, choose reminder or blocking behavior, review required access, preview overlaps, and activate explicitly. An override grants a scoped, expiring exception.
4. **Handle a gap:** Revoke a permission in Android. The health screen changes to degraded; charts show an unknown interval and affected rules report unavailable enforcement. Granting it again begins a new coverage interval.
5. **Own history:** Review per-source storage, export an encrypted archive through the system picker, test recovery credentials, and restore to a fresh installation. A failed restore preserves existing data.
6. **Keep less:** Disable content history and optionally delete prior content rows. Pause all collection for an hour while retaining past history. Inspect deletion scope before confirming.

## Delivery and non-goals

MVP includes reliable app/device history, social-app totals, experimentally supported short-video observation, configurable permissions, basic local insights, limits, focus schedules, and encrypted portable export/restore. An unsupported detector must not delay truthful app-level summaries. Each candidate surface needs its own evidence before being enabled.

An optional [live visit/day awareness counter](LIVE-OVERLAY-SPEC.md) is a user-requested MVP candidate (C-32/AS-034), dependent on qualified detectors, a permitted safe renderer and lifecycle evidence. It makes supported counts understandable in context; it is off by default, removable and distinct from blocking. Its feasibility must not delay truthful app totals or make unsupported counts look exact.

Expansion includes notification behavior, optional browsing/content history, richer protection and routines, widgets, and Quick Settings. Continuous evolution adds explainable local predictions and additional platform adapters. Automatic encrypted backup to a selected personal Google account is desired but direct Drive integration is blocked; it is not an MVP dependency.

Non-goals: application backend, mandatory product identity, ads, session replay, automatic diagnostic upload, hidden monitoring, root-only collection, reading encrypted app databases, reconstructing unavailable history, autonomous accessibility actions, and guaranteed circumvention-resistant blocking. Platform-prohibited functions will be omitted.

## Acceptance and success criteria

Foundation acceptance requires every document linked from the index, every capability assigned source/access/control/limits/phase/scenario, a prioritized stable-ID backlog, resolved cross-links, source evidence, and an accurate current state. Public content contains only original specifications and synthetic examples.

MVP release requires [testing gates](TESTING.md): exact deterministic fixture results; zero duplicate normalized events on repeated ingestion; visible coverage gaps; recovery after permitted restarts; no invented counts on unsupported surfaces; documented rule conflict behavior; and all failed-restore cases preserving live data. Privacy gates require no unexpected network traffic, sensitive logs, or OS backup leakage. Usability review must demonstrate onboarding without grants, accessible chart alternatives, an emergency bypass, and deletion without coercion.

Performance targets are provisional: first summary within two seconds with a one-year synthetic history, bounded paging, and a seven-day real-device battery assessment against collection disabled. These are engineering targets, not measured claims. Record device, dataset, version, and uncertainty before reporting improvement. Product success is evaluated through voluntary local user review and public synthetic feedback, never remote behavioral telemetry.
