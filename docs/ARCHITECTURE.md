# Architecture

## Overview

Attention Stats is a React Native 0.88 (New Architecture, TypeScript) app with a Kotlin native layer. Kotlin owns everything that must keep working without the UI: the accessibility service, detection, counting, rules, overlays, storage and backup. React Native renders the screens and talks to Kotlin through three TurboModules. There is no server and no network code; release builds have no `INTERNET` permission.

| Layer | Location | Role |
|---|---|---|
| UI | `src/` | React Native screens, one folder per feature |
| Bridge | `android/app/.../bridge/`, `src/platform/specs/` | TurboModules `NativeBootstrap`, `NativeDiary`, `NativeReels` |
| Android app | `android/app/src/main/java/com/attentionstats/` | Service, overlays, storage, backup, widgets, protection lock |
| `reels-core` | `native/reels-core/` | Pure Kotlin: per-app adapters, encounter tracker, playback evidence |
| `protection-core` | `native/protection-core/` | Pure Kotlin: groups, time windows, focus sessions, intervention rules |
| `analytics-core` | `native/analytics-core/` | Pure Kotlin calculation library (interval union, civil-day splitting, rollups); linked into the build and unit-tested, not yet called by app code |

The pure modules have no Android imports and run on the JVM. Their Gradle wrappers live in `android/<module>/` and point at the `native/` sources. `tools/check_architecture.py` (part of `pnpm lint`) enforces package paths, import boundaries, isolated per-app adapters, gesture code confined to one dispatcher, and feature public APIs in `src/`.

## File structure

```text
src/
  app/            App shell, navigation, tab bar
  features/       today, insights, diary, protection, settings, sources, onboarding
                  (each exports a public index.ts; no cross-feature private imports)
  platform/       typed native module specs and response parsers
  ui/             shared components, theme, charts, formatting
android/
  app/src/main/java/com/attentionstats/
    reels/        ReelsAccessibilityService, ReelsCoordinator, counter HUD, break/block overlays
    detection/    SnapshotReader (the only code touching live accessibility nodes)
    diary/        DiaryEngine (Today/Insights queries), UsageTimeline (Usage Access)
    protection/   schedules owner, Protection Lock credentials/biometrics, pause dispatcher
    autoscroll/   session, completion gate, gesture dispatcher
    storage/      Room database, SQLCipher driver, key envelope
    backup/       archive envelope, logical archive, Android backup agent, restore staging
    widget/       five widget providers and configuration
    appearance/   theme palette, app icons
    bridge/       TurboModule implementations
  app/schemas/    exported Room schemas 1-9 (migration tests rely on them)
  app/src/hostTest, androidTest   JVM and instrumented tests
native/           pure Kotlin modules (see above) with tests and detection fixtures
tools/            build, release, checks and fixture tools (see CONTRIBUTING)
policies/         public privacy, deletion and support pages
design/brand/     icon source used by tools/make_brand_assets.py
licenses/         third-party license texts shipped with the app
```

## Data flow

1. Android delivers accessibility events for the seven supported package names only. `SnapshotReader` copies the foreground window into a value-only snapshot.
2. The app's adapter turns the snapshot into a frame; `EncounterTracker` decides whether it is a new encounter or a revisit ([DETECTION](DETECTION.md)).
3. `ReelsCoordinator` serialises all writes on one queue: encounters, surface episodes and timers go to the encrypted database; the unlock-session counter lives in memory.
4. The same coordinator evaluates break and block rules and asks the service to draw the counter, break screen or cover as `TYPE_ACCESSIBILITY_OVERLAY` windows.
5. With Usage Access on, `UsageTimeline` reads Android usage events (package, timestamp, event kind) from the consent time onward to build app time and opens.
6. React Native reads snapshots and history pages as JSON over the bridge. JS never holds the authoritative state.

Whole-app protection rules read only the foreground package. Short-video rules require a recognised player.

## Native contract

All bridge calls are asynchronous and return JSON strings validated by parsers in `src/platform/`. Mutations carry an `expectedRevision`; a stale revision is rejected so two screens cannot overwrite each other. Consent changes carry the current notice revision (6). Key calls:

| Module | Calls |
|---|---|
| `NativeReels` | `getSnapshot`, `configure`/`configureAll` (source consent), `setPaused`, `setOverlay`, `deleteHistory`, `configureAutoScroll`, `stopAutoScroll`, `openAccessibilitySettings` |
| `NativeDiary` | `getSnapshot(days)`, `getHistoryPage(endDay)`, `getAppSchedules`/`saveAppSchedules`, `stopFocus`, `getProtectableApps`, `getAppIcons`, appearance get/set, `openBackup`, `openProtectionLock`, settings shortcuts |
| `NativeBootstrap` | `getBootstrapStatus` |

Protection Lock, backup/restore and widget configuration run in their own non-exported native activities; secrets never cross the bridge.

## Storage and encryption

- **Database:** Room 3 over SQLCipher (`net.zetetic:sqlcipher-android`) through `OwnedCipherDriver`. A random 32-byte key per database generation is wrapped with an installation-specific Android Keystore AES-256-GCM key (`LocalKeyEnvelope`) and stored in no-backup app storage. A missing or invalid key fails closed: no plaintext fallback and no silent reset.
- **Durability:** WAL mode; the primary writer uses `synchronous=FULL` and foreign keys are enforced on every pooled connection (ADR-107). The corruption handler preserves files instead of deleting them.
- **Preferences:** small device-local settings (appearance, widgets, backup choice, detection salt) in private SharedPreferences, excluded from backup.
- **Main tables (schema 9):** `Exposure` (one encounter: time, app, route, category, media kind, dwell, revisit, app version, item key, unlock session), `SurfaceEpisode`, `ReelsTimer`, `AppUsageEvent`, `InterventionEvent`, `ConsentRecord`, `CoverageInterval`, `SourceConfiguration`, `DiaryConfiguration`, `ReelsBlockConfiguration`, `ProtectionConfiguration`, `LiveOverlayConfiguration`, `StoreMetadata`, `FactOrigin`.
- **Retention:** history is kept until the person erases it (ADR-005). Erase removes all history and consent records in one transaction.

## Database evolution

- Schemas 1–9 are exported to `android/app/schemas/`. Migrations are automatic Room migrations tested with populated fixtures from every earlier schema.
- 1.1.0 (schema 9) is the production baseline (ADR-108). Every later release must migrate it, including skipped versions, preserving IDs, origins, choices and keys.
- Opening a database from a newer, unknown schema fails without deleting anything. A failed migration rolls back and can be retried.
- Never use destructive migration fallbacks. Add a new schema version with a migration and a populated-fixture test; record material changes as an ADR.

## Backup and restore

| | Manual export | Android-managed backup |
|---|---|---|
| Trigger | Person chooses a destination in the file picker | Android, roughly nightly when eligible |
| Default | On demand | On unless a saved choice says off (ADR-079) |
| Contents | Logical archive of history, choices and per-fact origins | One file: `files/system-backup/history.attentionbackup` |
| Excluded | Live database, keys, preferences, grants, Protection Lock | Same; device-to-device transfer excluded entirely |
| Requirements | — | Android 9+, secure screen lock, client-side-encrypted transport |

- **Format:** logical version 2 / schema 9, Tink streaming AES-GCM-HKDF framing (profile 3). The wrapping credential is SHA-256 of the public package name, so anyone holding a file can read or modify it (ADR-073). Privacy relies on where the file is stored.
- **Limits:** 20 MiB plaintext and 100,000 records; larger exports are refused rather than truncated.
- **Restore:** the file is authenticated before parsing, validated into a separately keyed encrypted staging database, previewed, then committed as a full replacement in one transaction. Failure before commit leaves live data untouched. After restore, counting, playback measurement and protection are off until re-enabled. Restore is replacement, not merge.
- Backup rules: `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`; `tools/check_backup_policy.py` guards them.

## Dependencies

- pnpm only; each direct dependency is pinned to an exact version resolved at its latest stable release, with the frozen lockfile used for builds (ADR-010). `pnpm-workspace.yaml` keeps strict peers and disabled install scripts.
- `tools/mobile-scaffold.json` records the reviewed direct packages, patch hash and native pins; `python tools/check_mobile.py` verifies the manifest and lockfile against it. Update it in the same change as any dependency update.
- Native libraries are pinned in `android/gradle/libs.versions.toml` (Room 3, SQLCipher, Tink, AndroidX Biometric, coroutines, desugaring). Gradle dependency lockfiles are committed; refresh them only with `tools/android_build.py --write-locks` in a reviewed change.
- `tools/kotlin-toolchain.json` pins the standalone Kotlin compiler and R8 (URL, size, SHA-256) used by the host test runners.
- No analytics, crash-reporting, advertising or networking SDKs. Record license changes in `licenses/` and NOTICE.
- If the latest version of something cannot be used, record the blocker in the change and in [DECISIONS](DECISIONS.md) if it is lasting.
