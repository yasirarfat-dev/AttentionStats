# Engineering conventions

**Status:** Active implementation standards; AS-035 Kotlin source exists, mobile application pending. Updated 2026-10-27.
**Responsibility:** Defines coding, state ownership, errors, dependency practice and review expectations.
**Related specifications:** [Agent rules](AGENT-RULES.md), [Structure](FILE-STRUCTURE.md), [Contracts](NATIVE-CONTRACTS.md), [Dependencies](DEPENDENCIES.md), [Testing](TESTING.md).
**Unresolved feasibility:** F-03 in [Decisions](DECISIONS.md); exact formatter/linter/test tool versions resolved at scaffold time.

## Languages and boundaries

TypeScript strict mode; prefer explicit domain types and exhaustive tagged states. No unvalidated `any`, non-null assertions across native boundaries or implicit unit conversions. Name instants `...AtMs`, durations `...DurationMs`/`...Ms`, zone IDs explicitly, and counts `...Count`. Use nullable-with-reason semantics for unavailable measurements. Parse/validate external/native data at the boundary; Zod schemas and native validation must agree.

Kotlin uses immutable domain values, structured coroutines, injected clock/source/dispatcher, cancellation and bounded queues. No disk/network work on UI/service callback main threads. Pure reducers/rule evaluation are deterministic for a supplied snapshot. Android Context and Room DAOs stay out of pure domain logic. No JavaScript background timers as collectors, no native dependency on React UI startup, no MMKV behavioral history.

Use clear feature folders, PascalCase components/types, camelCase functions, descriptive Kotlin classes, and one-way dependency flow from UI through contracts to native services. Avoid a generic global utility folder for unrelated business rules. Shared primitives remain source-agnostic and do not request permissions.

All mobile TypeScript source uses root `src/`, with domain-specific feature APIs and subfolders under [Structure](FILE-STRUCTURE.md). Native modules use `src/main/kotlin` and `src/test/kotlin`, with package paths matching declarations. Use domain vocabulary consistently: exposure is not completed video, observed UI outcome is not server acknowledgement, summary fact is not a disposable cache. [Analytics](ANALYTICS-SPEC.md) owns these invariants.

`python tools/check_architecture.py` enforces pure Kotlin package/import boundaries and current TypeScript relative-import direction/feature-public-API rules. Oxlint, Prettier and TypeScript supply syntax/format/type checks; the static Python check is deliberately narrow and does not replace compiler/Gradle or runtime verification. New alias/dynamic import mechanisms must extend boundary enforcement before adoption.

## State and side effects

Native storage owns consent, filters, history, rules, schedules and job state. UI uses disposable bounded query caches and local drafts. Small visual preferences may use MMKV. A mutation requires explicit user intent, native validation and success/revision before reporting saved. Handle conflicts rather than last-write-wins. Invalidation events trigger bounded re-queries, not duplicated event storage in JS.

Never conflate observed, estimated, classified and predicted outputs. Labels and formatting preserve evidence/coverage. A missing grant means unknown, not zero. Category and date formatting do not rewrite original timestamps. Invariant emergency exclusions and consent gates must be centralized, not copied per screen.

## Errors, logging and security

Use stable typed error codes with localized neutral messages and recovery actions. Retry only retryable errors with bounded backoff; no infinite permission, provider or crypto loops. Logs contain timing, operation IDs and reason codes, never title/URL/body, node trees, credential/token, account address, hardware identifier or private database content. Debug builds obey the same privacy exclusions. User-controlled diagnostics are reviewed/redacted; public fixtures are synthetic.

No screenshots or recordings of real accounts in tests or PRs. Tests use `com.example.*` identifiers, synthetic timestamps and artificial node structures. Never fabricate current third-party view selectors or claim unsupported APIs. No reference-app assets, copied product prose or comparison notes in this repository.

## Dependencies and reproducibility

pnpm is required. Resolve all new direct runtime/development dependencies from `@latest`, record exact versions/date/licenses/evidence, commit the lockfile and use frozen-lockfile routine builds. Latest conflicts are explicit blockers; never silently downgrade, bypass peers, or add global machine changes. Native libraries use latest stable verified artifacts with explicit version catalog pins. See [dependency policy](DEPENDENCIES.md).

Use standard formatting/linting and maintain native schema/contract generations from their source. Do not hand-edit generated output. Keep commits coherent with task IDs when helpful; describe behavior and validation in review text. Add meaningful tests for reducers, data loss, consent and rule boundaries; do not add tests that merely mirror constant strings or trivial presentational edits.

Check effective author/committer identities before each commit under [Agent rules](AGENT-RULES.md#package-and-machine-rules). Repository-local settings override machine-global defaults. Never infer authorship from the active GitHub login alone. Prefer the intended account's no-reply address for email privacy and preserve genuine community authorship.

## Definition of done

Implementation, appropriate passing verification, accurate capability/tracker status, matching domain specs/contracts/models, state update, dated log and any decision/evidence changes. Every implemented mobile screen, state and behavior needs live debug-phone or dedicated-emulator verification, including navigation, loading/empty/partial/error and accessibility states relevant to it. Native source/lifecycle behavior additionally needs physical-device evidence. Record not-run/blocked honestly and keep the affected task incomplete. A passing mock or headless calculation test does not establish live-screen or source support. A security-sensitive feature is not done until its failure and privacy gates pass. Changes must preserve other contributors' uncommitted work and avoid unrelated refactors.
