# File structure

**Status:** Experimental mobile tree, pure analytics source, documentation and retained design reference exist; downstream feature folders remain proposed. Updated 2026-10-27.
**Responsibility:** Defines file placement and ownership to avoid duplicate stores or rules.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Conventions](CONVENTIONS.md), [Development](DEVELOPMENT.md), [Agent rules](AGENT-RULES.md).
**Unresolved feasibility:** F-03 and F-07 in [Decisions](DECISIONS.md); generated CLI layout must be reconciled after latest-version resolution.

## Current repository

```text
AttentionStats/
  README.md, LICENSE, CONTRIBUTING.md, SECURITY.md
  AGENTS.md, CLAUDE.md
  .gitattributes, .gitignore
  .github/                 documentation/mobile/native CI and public review templates
  docs/                    authoritative specifications and continuity
    evidence/              public dependency metadata, not device traces
    IMPLEMENTATION_LOG/    dated work and validation records
  tools/check_docs.py      standard-library documentation verification
  tools/test_analytics.py  verified local Kotlin compiler and synthetic JVM/ART tests
  tools/check_architecture.py  current native package/dependency boundary checks
  tools/probe_mobile_stack.py  AS-002 isolated tooling preparation, parity audit and smoke checks
  tools/check_mobile.py    manifest/lock/installed-version, native catalog/notices and patch audit
  tools/android_build.py   task-local SDK/Gradle wrapper runner
  tools/test_collection_probe.py  bounded self-package test on one physical phone
  tools/storage_qualification.py  standalone AS-006 build and synthetic phone runner
  tools/test_reconciliation.py  synthetic JVM source/commit experiments, reviewed compiler reuse
  tools/tests/             synthetic phone-runner safety checks
  package.json, pnpm-lock.yaml, pnpm-workspace.yaml
  src/                     experimental mobile composition/features/UI/bootstrap adapter
  android/                 application, analytics-core and isolated collection-prototype Gradle modules
    app/src/debug/         unexported synthetic AS-003 activities, excluded from release
    app/src/androidTest/   selectable calculation/self-package instrumentation
  ios/                     generated, untested future platform project
  patches/                 reviewed RNGP compiler compatibility patch
  licenses/, NOTICE        template MIT attribution
  native/analytics-core/   AS-035 pure Kotlin mobile-domain calculation slice
  native/storage-qualification/  standalone debug-only synthetic Room/SQLCipher APK, excluded from product settings
    src/main/kotlin/org/attentionstats/analytics/
      time/ activity/ observation/ network/ summary/
    src/test/kotlin/       synthetic deterministic test program
  native/collection-prototype/  AS-003 pure experiment, no application dependency
    src/main/kotlin/org/attentionstats/collection/reconciliation/  slice/replay ports, planner/retry, receipts and correction proposals
    src/test/kotlin/       synthetic coordinators, interval/receipt/correction oracles, density/failure/restart fixtures
  design/prototype/        synthetic browser study, tokens, tests, isolated manifest/lockfile
```

The repository root is `C:\home\biobot\projects\AttentionStats`. Product display name is “Attention Stats”; public repository is `yasirarfat-dev/AttentionStats`. AS-033 adds a separate [design study](../design/prototype/README.md) with a development-only manifest and lockfile. Root `package.json`, two-document pnpm lockfile, Community CLI Android/iOS projects and `src/` now exist under AS-004. Do not mistake the browser study or unaccepted scaffold for runtime capability support.

## Mobile structure and planned expansion

```text
src/
  app/                     navigation, composition and startup
  features/
    onboarding/ usage/ social/ interactions/ network/ diary/
    insights/ protection/ privacy/ backup/ settings/
      index.ts             intentional public feature API
      screens/             mobile route views
      components/          feature-specific accessible components
      application/         query/mutation orchestration and view models
      domain/              feature view semantics, types and pure transforms
      data/                typed platform mapping, never a history database
      hooks/               feature-level UI integration
      __tests__/           domain/interaction tests where useful
  shared/                  small shared types/formatting, no unrelated business rules
  platform/
    contracts/             typed request/result schemas and capability IDs
    android/               generated native adapter wrapper
    ios/                   unsupported adapter until contributed
  ui/                      tokens, primitives, charts and accessibility helpers
  testing/                 synthetic fixture builders and adapter fakes
android/
  app/src/main/java/<application-namespace>/
    collection/            per-source collectors and lifecycle coordinator
    detectors/             registry and shared minimized structural evidence
      social/
        facebook/          independently qualified surface/item/action adapters
        instagram/         independently qualified surface/item/action adapters
        youtube/           independently qualified Shorts/long-video adapters
        tiktok/            independently qualified media/region variants
      browsers/            separately consented safe navigation adapters
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

Actual mobile source currently contains `src/app/App.tsx`, onboarding/privacy feature public APIs and screens, `src/ui` primitives/tokens and `src/platform` bootstrap adapter/spec/tests. Other feature folders above remain proposed and were not generated empty. `android/app` uses `com.attentionstats`, `.debug` application suffix and the read-only `bridge` package; `android/analytics-core` references existing pure source/test directories. Android XML excludes backup/transfer, debug alone declares Internet for Metro, and release stays unsigned. `ios/` is generated and untested. [ADR-029](DECISIONS.md) records development identity and adaptations; store identity/signing and native acceptance remain gates. No collectors/storage/detector modules exist yet.

## Placement rules

Feature folders own screens and view adapters, not a parallel history database. Shared UI primitives cannot request permissions or write rules. Native collectors cannot call React components or depend on JS startup. Data mutations pass through native services; DAOs are internal. Pure rule evaluation belongs under native domain and is separately testable with a supplied clock.

Platform-specific observation adapters live in dedicated service-specific native subpackages, with their own synthetic fixtures/support records and a narrow registry interface. A Facebook detector must not reach into Instagram implementation details or share assumed selectors. Common normalized evidence/reducer contracts are reusable; per-platform feasibility, version/locale support and consent remain independent. These folders are proposed, not validated or generated detector implementations.

Put contract changes in the contract spec and shared adapter types; generated Codegen output is not manually edited. Keep sensitive runtime data, captures, backups, keys and personal diagnostics outside the repository. Only synthetic fixtures belong under tests. Keep public evidence narrowly scoped to package metadata, verified compatibility reports, and anonymized aggregate test outcomes with no user histories.
