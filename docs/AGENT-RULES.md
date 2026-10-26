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
5. Report concrete outcome, verification and limitations. Distinguish local commit, remote publication, installed binary and policy approval; they are separate facts.

Allowed feature states are planned, experimental, blocked and implemented. Runtime capability states are separate and defined in [Contracts](NATIVE-CONTRACTS.md). If a task is underway, keep its feature status accurate and record active work in State rather than claiming implementation early. IDs are stable: never renumber AS/C/scenario/ADR/F IDs to fill gaps. New ideas enter Queue and get promoted through a documented decision.

## Package and machine rules

Use pnpm. Every new direct runtime/development dependency resolves through `package@latest`, with exact date/version/license/compatibility evidence. Pin reviewed resolution in the lockfile; routine builds use frozen-lockfile. Do not silently downgrade, force peers or globally approve scripts. Native dependencies use latest stable releases with explicit pins and evidence.

The user authorizes installing useful tools/plugins/skills, but other agents share this machine. Prefer repository-local or isolated tool/cache/configuration changes. Do not change machine-wide tool versions, shell profiles, timezone, shared adb state, global Git identity, or unrelated repositories. Stop only work owned by this task; never kill processes by broad name. No private captures or secrets in Git or public issue content.

## Agent continuity

For long sessions, aim to compact between **100k and 150k context tokens** when the session exposes compaction controls. If no manual compaction control exists, maintain a concise checkpoint and allow the environment's automatic compaction; do not pretend to have invoked unavailable controls. Checkpoints retain original objective, latest corrections, authoritative path/repository, decisions/constraints, completed changes and checks, outstanding tasks, blockers and next concrete action. Continue without redoing completed work. This applies to all agents working on the project.

Do not spawn/delegate to other agents unless the user or applicable instructions explicitly request parallel agent work. Permission to install tools is not a request for delegation. If parallel work is later requested, partition write ownership and preserve these same rules for every agent.

## Hard boundaries

All tracking and content detail is user-controlled; source grants do not equal consent. No hidden backend/telemetry or autonomous accessibility actions. Predictions are local recommendations and never diagnoses. Never describe unknown coverage as zero, a proposal as a build, a mock test as a device result, or local recording as “no collection.” Marketing uses the precise [privacy promise](PRIVACY.md).

UI work follows [Human factors](HUMAN-FACTORS.md): calm, supportive, accessible and useful by choice. Optimize for clarity, agency and peace of mind; no guilt/streak pressure, infinite engagement loops, invented wellness scores or emotional dependency. The synthetic design study is a reference, not a native implementation or evidence that tracking/enforcement works.

The public repository must exclude private reference material, reference-app names/comparisons/assets, personal phone history, credentials, device identifiers and secrets. Target social services may be named only as candidate observation surfaces; no private implementation is inferred. Use synthetic app identifiers/fixtures. User explicitly authorized this foundation's publication as a public `yasirarfat-dev/AttentionStats` repository; no extra publication approval is required for that scoped work.
