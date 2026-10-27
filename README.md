# Attention Stats

An open-source personal digital wellbeing application built around **observe → track → categorize → predict → protect**.

**Mobile clients only: Android first, iOS later.** The product is not a website, web app, PWA or desktop app. A possible Chrome extension is deferred until mobile work is complete and separately authorized; no extension is being developed.

**An experimental Android Reels preview is built and installed for development.** React Native Community CLI/TypeScript owns the main UI; Kotlin supplies passive Android accessibility, encrypted Room/SQLCipher/Keystore storage and optional native HUD infrastructure. Both Instagram and Facebook detectors still require live per-version qualification, so this is not yet a working counter or the complete MVP. [Current state](docs/STATE.md) separates installed packaging, synthetic checks and remaining gates. No protection or archive flow is implemented. Future iPhone contributions will use explicit platform adapters; the generated iOS project is untested.

The goal is to help you understand the digital behavior your device exposes and you choose to track, then act on that knowledge. Observation-only use is welcome. History stays on your device; limits and interventions are your choice. Predictions will be local and explainable. The project will not claim to measure dopamine, diagnose addiction, or read your attention state.

Start with the [documentation index](docs/INDEX.md), [product scope](docs/PRODUCT.md), [capability inventory](docs/CAPABILITY-MATRIX.md), and [current state](docs/STATE.md). Android APIs and distribution policies impose real limits: supported social surfaces and short-video counts must be verified, and automatic personal Google-account backup remains blocked pending policy clarification. Portable encrypted export and restore are separately planned.

The detailed [digital diary](docs/ANALYTICS-SPEC.md) and [64-metric catalog](docs/METRIC-CATALOG.md) cover candidate app/activity/short-video/interaction/network statistics, evidence limits and flexible day/week/month/year summaries. Every platform/metric requires qualification; unknown stays unknown. Tracking remains explicitly chosen and local.

Development uses **pnpm** and resolves new direct packages with `package@latest`. [Dependency evidence](docs/DEPENDENCIES.md) separates historical candidates, installed scaffold packages and isolated Kotlin tools. The [native analytics core](native/analytics-core/README.md) passed 37 synthetic calculation checks on a host JVM and connected Android ART; this does not verify collection or UI.

Application source uses domain-owned root `src/features`, a typed bootstrap adapter and a separate pure Kotlin analytics module. Full native contracts and infrastructure modules remain planned. See [architecture](docs/ARCHITECTURE.md#domain-oriented-modular-design) and [file structure](docs/FILE-STRUCTURE.md). Every implemented screen/behavior must be verified on a live authorized debug phone or dedicated emulator.

A completed [interaction study](design/prototype/README.md) is retained solely as a non-shipping mobile-design reference. Its browser preview is not the product or a web implementation track. Mobile screens will use React Native components and native platform adapters. [Human factors](docs/HUMAN-FACTORS.md) governs the experience; next development follows the native tasks in the [tracker](docs/FEATURE-TRACKER.md).

The app uses the standard Community CLI commands `pnpm start`, `pnpm android`, `pnpm build` and `pnpm run android:release`. See [local setup](docs/DEVELOPMENT.md#application-setup-as-004) for isolated SDK requirements. Python supplies repository validators and synthetic native tooling; it is not the mobile runtime.

Run documentation checks with Python 3.11 or newer:

```powershell
python tools/check_docs.py
```

Contributions: [contributor guide](CONTRIBUTING.md). Security reports: [security entrypoint](SECURITY.md). Agents and humans follow [the same rules](docs/AGENT-RULES.md). All fixtures and examples must be synthetic.

Public repository: [yasirarfat-dev/AttentionStats](https://github.com/yasirarfat-dev/AttentionStats). Licensed under [Apache-2.0](LICENSE). Copyright 2026 Attention Stats contributors.
