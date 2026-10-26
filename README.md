# Attention Stats

An open-source personal digital wellbeing application built around **observe → track → categorize → predict → protect**.

**Mobile clients only: Android first, iOS later.** The product is not a website, web app, PWA or desktop app. A possible Chrome extension is deferred until mobile work is complete and separately authorized; no extension is being developed.

**Documentation prepared; application not yet implemented.** There is no installable app, application scaffold, or verified device support yet. Android will launch first with React Native Community CLI, TypeScript, and the New Architecture. Future iPhone contributions will use explicit platform adapters.

The goal is to help you understand the digital behavior your device exposes and you choose to track, then act on that knowledge. Observation-only use is welcome. History stays on your device; limits and interventions are your choice. Predictions will be local and explainable. The project will not claim to measure dopamine, diagnose addiction, or read your attention state.

Start with the [documentation index](docs/INDEX.md), [product scope](docs/PRODUCT.md), [capability inventory](docs/CAPABILITY-MATRIX.md), and [current state](docs/STATE.md). Android APIs and distribution policies impose real limits: supported social surfaces and short-video counts must be verified, and automatic personal Google-account backup remains blocked pending policy clarification. Portable encrypted export and restore are separately planned.

Development uses **pnpm** and resolves new direct packages with `package@latest`. Exact dependency candidates are [recorded](docs/DEPENDENCIES.md); no application dependencies are installed in this phase.

A completed [interaction study](design/prototype/README.md) is retained solely as a non-shipping mobile-design reference. Its browser preview is not the product or a web implementation track. Mobile screens will use React Native components and native platform adapters. [Human factors](docs/HUMAN-FACTORS.md) governs the experience; next development follows the native tasks in the [tracker](docs/FEATURE-TRACKER.md).

Run documentation checks with Python 3.11 or newer:

```powershell
python tools/check_docs.py
```

Contributions: [contributor guide](CONTRIBUTING.md). Security reports: [security entrypoint](SECURITY.md). Agents and humans follow [the same rules](docs/AGENT-RULES.md). All fixtures and examples must be synthetic.

Public repository: [yasirarfat-dev/AttentionStats](https://github.com/yasirarfat-dev/AttentionStats). Licensed under [Apache-2.0](LICENSE). Copyright 2026 Attention Stats contributors.
