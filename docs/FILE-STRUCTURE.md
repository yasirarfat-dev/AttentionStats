# File structure

**Status:** Documentation and separate design study exist; native application tree proposed only. Updated 2026-10-27.
**Responsibility:** Defines file placement and ownership to avoid duplicate stores or rules.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Conventions](CONVENTIONS.md), [Development](DEVELOPMENT.md), [Agent rules](AGENT-RULES.md).
**Unresolved feasibility:** F-03 and F-07 in [Decisions](DECISIONS.md); generated CLI layout must be reconciled after latest-version resolution.

## Current repository

```text
AttentionStats/
  README.md, LICENSE, CONTRIBUTING.md, SECURITY.md
  AGENTS.md, CLAUDE.md
  .gitattributes, .gitignore
  .github/                 documentation checks and public review templates
  docs/                    authoritative specifications and continuity
    evidence/              public dependency metadata, not device traces
    IMPLEMENTATION_LOG/    dated work and validation records
  tools/check_docs.py      standard-library documentation verification
  design/prototype/        synthetic browser study, tokens, tests, isolated manifest/lockfile
```

The repository root is `C:\home\biobot\projects\AttentionStats`. Product display name is “Attention Stats”; public repository is `yasirarfat-dev/AttentionStats`. AS-033 adds a separate [design study](../design/prototype/README.md) with a development-only manifest and lockfile. No React Native application manifest, Gradle wrapper or iOS project exists. Do not mistake the study for the native scaffold or runtime support.

## Proposed implementation tree

```text
src/
  app/                     navigation, composition and startup
  features/
    onboarding/ dashboard/ history/ social/ insights/ protect/ settings/
  domain/                  shared types, pure formatting and view semantics
  platform/
    contracts/             typed request/result schemas and capability IDs
    android/               generated native adapter wrapper
    ios/                   unsupported adapter until contributed
  ui/                      tokens, primitives, charts and accessibility helpers
  testing/                 synthetic fixture builders and adapter fakes
android/
  app/src/main/java/<application-namespace>/
    collection/            per-source collectors and lifecycle coordinator
    detectors/             reviewed versioned surface adapters
    domain/                reducers, aggregates, rule engine and insights
    storage/               Room schema, DAOs, migrations and write coordinator
    bridge/                Codegen module, conversion and invalidation events
    backup/                snapshot, crypto, staging and transport interfaces
    health/                status, gap reasons and redacted diagnostics
  app/src/main/res/xml/    accessibility metadata and backup exclusion rules
  app/src/test/            deterministic domain/native unit tests
  app/src/androidTest/     persistence, lifecycle and device integration
  schemas/                committed Room migration schemas
ios/                       future native adapter and platform project
tests/                     cross-layer scenarios and synthetic history fixtures
```

These directories are not scaffolding instructions for this phase. AS-004 must review the generated latest CLI template, select a permanent application ID in [Decisions](DECISIONS.md), and update this map to the actual layout. Never use an observation target's package as this application's ID.

## Placement rules

Feature folders own screens and view adapters, not a parallel history database. Shared UI primitives cannot request permissions or write rules. Native collectors cannot call React components or depend on JS startup. Data mutations pass through native services; DAOs are internal. Pure rule evaluation belongs under native domain and is separately testable with a supplied clock.

Put contract changes in the contract spec and shared adapter types; generated Codegen output is not manually edited. Keep sensitive runtime data, captures, backups, keys and personal diagnostics outside the repository. Only synthetic fixtures belong under tests. Keep public evidence narrowly scoped to package metadata, verified compatibility reports, and anonymized aggregate test outcomes with no user histories.
