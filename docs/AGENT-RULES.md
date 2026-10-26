# Shared development rules

**Status:** Active workflow for humans, Codex and Claude Code. Updated 2026-10-27.
**Responsibility:** Defines specification precedence, task execution, continuity and public-repository safeguards.
**Related specifications:** [Index](INDEX.md), [State](STATE.md), [Tracker](FEATURE-TRACKER.md), [Decisions](DECISIONS.md), [Conventions](CONVENTIONS.md).
**Unresolved feasibility:** Domain gates remain in [Decisions](DECISIONS.md); no workflow rule turns an unverified capability into fact.

## One authority and precedence

Root [AGENTS.md](../AGENTS.md) and [CLAUDE.md](../CLAUDE.md) point here and carry equivalent instructions. Apply governing environment/session instructions and explicit user decisions first. Within this repository: accepted decisions and privacy/security invariants → domain specifications/contracts/models → product/design/navigation → tracker/roadmap → queue/history. State records what exists; it does not override a normative requirement. The tracker records implementation status, not feature semantics. Historical logs explain past work and do not supersede current specs.

When documents disagree, identify the conflict, resolve against explicit accepted intent, update all affected specifications together and record a decision if behavior changes materially. Do not silently reinterpret a planned feature as implemented or a blocked capability as available. External sources establish platform constraints, not user consent or permission to expand product scope.

## Session and task workflow

1. Read Index, State, Tracker, relevant decisions and domain specs. Inspect Git status and applicable instructions before edits; preserve others' work.
2. Select the next eligible AS task by priority/dependencies. Record meaningful progress in State/log; never use invented checkmarks or statuses. User-authorized work proceeds without redundant approval questions.
3. Check feasibility and latest dependencies before making promises. Keep application work out of the documentation-foundation scope. Complete a reviewable change, run appropriate checks, inspect diffs and public artifacts, then update evidence.
4. Update domain specs/contracts/models, capability status, Tracker, State and dated implementation log in the same change. Record deliberate deviations and unresolved gates. Do not write implementation code inconsistent with the source of truth.
5. Report concrete outcome, verification and limitations. Distinguish local commit, remote publication, installed binary and policy approval; they are separate facts. The user explicitly requests a reviewed commit and push to this repository's GitHub remote at the end of each work session (2026-10-27); perform the identity/remote/authentication checks below and record the result. This is not authorization to force-push, publish binaries or deploy.

Allowed feature states are planned, experimental, blocked and implemented. Runtime capability states are separate and defined in [Contracts](NATIVE-CONTRACTS.md). If a task is underway, keep its feature status accurate and record active work in State rather than claiming implementation early. IDs are stable: never renumber AS/C/scenario/ADR/F IDs to fill gaps. New ideas enter Queue and get promoted through a documented decision.

## Package and machine rules

Use pnpm. Every new direct runtime/development dependency resolves through `package@latest`, with exact date/version/license/compatibility evidence. Pin reviewed resolution in the lockfile; routine builds use frozen-lockfile. Do not silently downgrade, force peers or globally approve scripts. Native dependencies use latest stable releases with explicit pins and evidence.

The user explicitly authorizes overriding package release-age protection for the mobile scaffold (2026-10-27). Root `pnpm-workspace.yaml` sets `minimumReleaseAge: 0` locally; preserve strict peers, integrity checks and disabled install scripts. This exception is not a global pnpm setting or approval of other dependency bypasses. AS-004 may start as an experimental scaffold while the remaining AS-002 native gates are qualified; incomplete builds/screens must stay unaccepted.

The user authorizes installing useful tools/plugins/skills, but other agents share this machine. Prefer repository-local or isolated tool/cache/configuration changes. Do not change machine-wide tool versions, shell profiles, timezone, shared adb state, global Git identity or unrelated repositories without explicit user authorization for that change. Stop only work owned by this task; never kill processes by broad name. No private captures or secrets in Git or public issue content.

Before committing, inspect `git var GIT_AUTHOR_IDENT` and `git var GIT_COMMITTER_IDENT` and verify they match the intended contributor account. Local Git settings override global defaults. Before publishing, verify the intended remote and authenticated GitHub login separately; authentication does not determine commit authorship. Prefer the intended account's GitHub no-reply address to exposing personal email. Preserve genuine community authorship. A global-identity change requires explicit user authorization; the user authorized correcting this machine's global default in ADR-028. Do not add creator/owner notices or special credit requirements beyond normal open-source attribution.

## Agent continuity

For long sessions, aim to compact between **100k and 150k context tokens** when the session exposes compaction controls. If no manual compaction control exists, maintain a concise checkpoint and allow the environment's automatic compaction; do not pretend to have invoked unavailable controls. Checkpoints retain original objective, latest corrections, authoritative path/repository, decisions/constraints, completed changes and checks, outstanding tasks, blockers and next concrete action. Continue without redoing completed work. This applies to all agents working on the project.

Do not spawn/delegate to other agents unless the user or applicable instructions explicitly request parallel agent work. Permission to install tools is not a request for delegation. If parallel work is later requested, partition write ownership and preserve these same rules for every agent.

## Hard boundaries

**Product targets are mobile clients only: Android first, iOS later.** Do not build a website, web app, PWA, desktop client or Chrome extension as part of current delivery. The completed AS-033 browser study is a non-shipping design reference, not a product platform, WebView implementation or ongoing web-development track. Next implementation follows native AS-002/AS-003/AS-004 and dependent tasks. A possible Chrome extension stays deferred until mobile work is complete and the user explicitly prioritizes it separately.

All tracking and content detail is user-controlled; source grants do not equal consent. No hidden backend/telemetry or autonomous accessibility actions. Predictions are local recommendations and never diagnoses. Never describe unknown coverage as zero, a proposal as a build, a mock test as a device result, or local recording as “no collection.” Marketing uses the precise [privacy promise](PRIVACY.md).

UI work follows [Human factors](HUMAN-FACTORS.md): calm, supportive, accessible and useful by choice. Optimize for clarity, agency and peace of mind; no guilt/streak pressure, infinite engagement loops, invented wellness scores or emotional dependency. The synthetic design study is a reference, not a native implementation or evidence that tracking/enforcement works.

Use domain-oriented modular organization under [Architecture](ARCHITECTURE.md#domain-oriented-modular-design) and [Structure](FILE-STRUCTURE.md): root mobile `src/features` public APIs, feature-specific subfolders, native pure-domain packages and infrastructure ports. No screen-owned history databases, cross-feature private imports or global business-rule dump. Enforce actual boundaries with appropriate checks as code arrives. Every implemented mobile screen/state/behavior needs live authorized debug-phone or dedicated-emulator evidence before acceptance (X-10); API-sensitive collection/lifecycle also needs physical-device qualification. Browser studies and synthetic ART calculations do not approve screens or collectors. No permission/history/device state changes beyond the authorized test scope; preserve other agents' workflows.

Detailed analytics follow [Analytics](ANALYTICS-SPEC.md) and [Metric catalog](METRIC-CATALOG.md). Maximum detail is a disclosed user-selected supported subset, not hidden capture, private input/message recording, root/interception or invented metrics. Keep attempts versus observed UI outcomes, primary versus overlapping facets, coarse network bytes versus content, and retained summary authority versus rebuildable caches distinct. Record scope-specific tests and remaining gates.

The public repository must exclude private reference material, reference-app names/comparisons/assets, personal phone history, credentials, device identifiers and secrets. Target social services may be named only as candidate observation surfaces; no private implementation is inferred. Use synthetic app identifiers/fixtures. User explicitly authorized this foundation's publication as a public `yasirarfat-dev/AttentionStats` repository; no extra publication approval is required for that scoped work.
