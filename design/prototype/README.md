# Attention Stats interaction study

A working **design prototype** with original synthetic examples. This is not the React Native application: no Android permissions, collectors, blocking, history database, encryption, export or cloud backup are implemented here.

Read the authoritative [human factors](../../docs/HUMAN-FACTORS.md), [design system](../../docs/DESIGN-SYSTEM.md) and [UX](../../docs/UX-SPEC.md) specifications.

## Open the preview

With Node 22.12+ and pnpm available, run from this folder:

```powershell
pnpm run preview
```

Open `http://127.0.0.1:4179`. No package installation is needed to view it. Directly opening `index.html` is unsupported because it uses local ES modules. The server binds only to loopback and serves an explicit asset list. A busy port fails instead of reusing another agent's service. `PORT` can select another port for manual review.

Review controls switch between a sample day, first visit, interrupted access and enabled-but-empty history; themes, reduced motion, 200% text, prompt preview and reset are available. The review studio is not intended for the mobile app. The app-shaped preview contains Today, History, Patterns, Protect and Settings, plus app detail, evidence, source explanation and rule-review dialogs.

Try declining observation, viewing the chart table, explaining the missing bin, inspecting an exposure estimate, editing/discarding/saving a demo rule, starting/ending focus, ordinary override and emergency access. Changes are in memory; reload discards them. No external resources are requested automatically, browser persistence is unused and tracking is never started. Source links navigate only when selected. Static assets are served locally.

“Explore the live counter” demonstrates [visit/today awareness](../../docs/LIVE-OVERLAY-SPEC.md) on an original static illustrated surface. This is an independent fixture: ~18 this visit and ~64 today for the same app/surface. Try a next item, duplicate callback, new visit, signal loss, hide and preset placement. Only explicit simulation controls change counts. No actual feed, playing video, native overlay, gesture recognition or detector is running.

## Verify interactions and accessibility

```powershell
pnpm install --frozen-lockfile --ignore-scripts
pnpm run browser:install
pnpm test
```

Manager pin: **pnpm 12.15.0**, resolved through `pnpm@latest` on 2026-10-27. Bootstrap with `pnpm dlx pnpm@latest --version` if needed; no global update is required. Direct tools were installed with `pnpm dlx pnpm@latest add --save-dev --save-exact @playwright/test@latest @axe-core/playwright@latest`. [Tooling evidence](tooling-evidence.json) records exact registry releases/licenses/engines; `pnpm-lock.yaml` records the install graph. pnpm 12 writes manager/environment and project documents; scanners must read both. [Official lockfile format](https://github.com/pnpm/pnpm.io/blob/main/docs/lockfile.md).

Browser binaries stay in the ignored `.cache/browsers`; failures/traces stay in ignored `test-results`. Playwright owns/stops its server. Tests never attach to or reset a shared emulator, profile, global tool or another project. Local screenshots in `.cache/preview-*.png` are synthetic.

Tests cover consent/refusal, gaps, insufficient data, chart tables, evidence, draft/save, overlaps, elapsed-time focus, emergency access, focus, motion, themes, lack of persistence/external traffic, server allowlist, contrast and 320px/200% text/RTL layout. axe checks cover both themes, main screens, special data states and core dialogs. These are browser results, not native TalkBack, battery, encryption, psychological benefit or device evidence.

Runtime uses only browser/Node built-ins and project code. Development-only tools: `@playwright/test` **1.64.1**, Apache-2.0; `@axe-core/playwright` **4.14.0**, MPL-2.0. Transitive licenses remain in installed package notices; these tools are not shipped in Android. No library code is copied into the repository. Latest pnpm is MIT. Routine installs use the reviewed lockfile; refresh direct tools deliberately with `@latest` and update evidence.

AS-033 owns this study. AS-018 owns native screens; AS-002/AS-004 remain stack/scaffold gates. No runtime capability is marked implemented by the prototype.
