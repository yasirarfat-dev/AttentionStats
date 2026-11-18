# Contributing

Thanks for helping. Read [docs/STATUS.md](docs/STATUS.md) for what is current and [docs/DECISIONS.md](docs/DECISIONS.md) before changing behavior. These rules apply to everyone, human or automated.

## Setup

- Node 26.12+ and pnpm 12.15 (the exact version is in `package.json` `packageManager`).
- JDK 17, Python 3.11+.
- Android SDK with platform `android-37.0`, Build Tools 37.0.0, NDK 30.0.16248370 and CMake 4.1.2. Gradle does not download SDK parts automatically.
- Point Gradle at the SDK with `ANDROID_HOME`, `--sdk` on `tools/android_build.py`, or an ignored `android/local.properties` (`sdk.dir=C\:/path/to/sdk` on Windows; escape the colon).

```powershell
pnpm install --frozen-lockfile --ignore-scripts
python tools/check_mobile.py --installed     # installed packages match the reviewed pins
```

## Build and run

```powershell
python tools/android_build.py :app:assemblePreview --arch arm64-v8a   # bundled-JS preview APK, no Metro needed
python tools/android_build.py :app:assembleDebug --arch arm64-v8a     # debug APK for Metro
pnpm start                                                            # Metro on 127.0.0.1:8081
adb reverse tcp:8081 tcp:8081
```

`tools/android_build.py` runs the Gradle wrapper with a repository-local Gradle cache (`.cache/gradle-user-home`) and creates a local debug key in `.cache/` on first use; it changes no global configuration. Use `--arch` for your device ABI, `--workers` to limit parallelism and `--reuse-daemon` while iterating. Debug and preview builds install as `com.attentionstats.debug`, separate from the Play app `attention.stats`.

## Checks

Run these before opening a pull request:

| Command | What it checks |
|---|---|
| `pnpm typecheck` | TypeScript |
| `pnpm lint` | oxlint, `check_architecture.py` (module boundaries), `check_backup_policy.py`, palette drift |
| `pnpm format:check` | Prettier |
| `pnpm test` | Jest unit tests |
| `python tools/android_build.py :reels-core:verifyReels :protection-core:verifyProtection :analytics-core:verifyAnalytics` | Pure Kotlin suites, including detection fixtures |
| `python tools/android_build.py :app:lintDebug` | Android lint |
| `py -I tools/check_docs.py` | Required docs, local links and anchors, no secrets or private artifacts in tracked files |
| `python -m unittest discover -s tools/tests` | Safety tests for the release and phone tools |

Host-only Kotlin runners `tools/test_analytics.py`, `tools/test_autoscroll.py`, `tools/test_backup_files.py` and `tools/test_usage_timeline.py` compile product code with the pinned compiler from `tools/kotlin-toolchain.json` (downloaded once into `.cache/`).

Run one heavy suite (Gradle build, instrumentation, full Jest) at a time; in parallel they time out on ordinary laptops.

GitHub workflows run only when started manually (`workflow_dispatch`). Do not add push, pull-request or scheduled triggers (ADR-030).

## Testing approach

1. **Fixtures first for detection.** Every supported screen and key negative has a sanitised layout in `native/reels-core/src/test/resources/fixtures/<app>/`. To fix a missed or miscounted screen, dump it with `adb shell uiautomator dump`, sanitise it with `python tools/sanitize_fixture.py raw.xml <fixture>.xml`, add an assertion in `FixtureTests.kt`, and make `:reels-core:verifyReels` pass. Details in [docs/DETECTION.md](docs/DETECTION.md#testing-detection-fixtures-first).
2. **Then a real phone.** Install the preview build and repeat the scenario on the real app. `python tools/reels_phone.py install` upgrades the preview without clearing history; `qualify` runs scoped synthetic instrumentation on one connected physical phone and removes its test package afterwards.
3. **Behavior in pure modules gets JVM tests;** storage, migrations and overlays get instrumented tests under `android/app/src/androidTest`.
4. A passing build or a mock is not device evidence. Say exactly what was run and on which build; record results in [docs/STATUS.md](docs/STATUS.md).

## Rules

- **pnpm only.** New direct dependencies resolve at their latest stable version, pinned exactly; record the license and update `tools/mobile-scaffold.json` (`check_mobile.py` enforces it). Use the frozen lockfile. If the latest version cannot be used, record why.
- **Privacy first.** No network code, analytics, ads, crash upload or tracking SDKs. Everything is off until the person agrees. New data or permissions need an ADR, an in-app disclosure and updates to both privacy documents. Never store or log screen text, captions, names or typed input.
- **Never commit** secrets, keystores, `.env` files, machine-specific paths, device serials or identifiers, personal usage history, real screenshots or unsanitised screen dumps. Tooling output belongs in `.cache/` (gitignored); evidence files written by tools default there.
- **Never name other digital-wellbeing apps** in code, docs, issues or commits. The supported social apps may be named.
- **Decisions are ADRs.** A material change in behavior, data handling, dependencies or workflow gets a new entry in [docs/DECISIONS.md](docs/DECISIONS.md); never renumber.
- **Keep docs current** in the same change: [FEATURES](docs/FEATURES.md) for behavior, [DETECTION](docs/DETECTION.md) for counting, [ARCHITECTURE](docs/ARCHITECTURE.md) for structure and storage, [STATUS](docs/STATUS.md) for verification.
- **Respect module boundaries:** pure Kotlin domain logic in `native/`, Android adapters in `android/app`, one folder per feature in `src/features` with a public `index.ts`.
- **Database changes** add a schema version with a migration and a populated-fixture test; never use destructive fallbacks (ADR-106, ADR-108).
- **Safety of protection:** never block Android Settings, Home, the dialer or uninstalling; no autonomous accessibility actions.
- **Design:** calm, honest wording; no streaks, guilt or scores; unknown is not zero (ADR-019).
- Work on `develop`. Merging to `main` and publishing are maintainer decisions (ADR-068).

## Issues and pull requests

- Use synthetic examples. Do not attach personal history, screenshots of other apps, account details or device identifiers. For detection bugs, the in-app **Settings → Report a problem** pre-fills only phone model and app versions.
- Security problems go through [SECURITY.md](SECURITY.md), not public issues.
- Pull requests describe the behavior change, the checks run and their results, and any limits.

By contributing you agree that your contribution is licensed under [GPL-3.0-only](LICENSE).
