# Attention Stats

**See where your attention goes. Counted on your phone, never collected.**

Attention Stats is a free, open-source digital wellbeing app for Android. It counts the short videos you watch (Instagram Reels, Facebook Reels, YouTube Shorts, TikTok and Snapchat Spotlight), shows how long you spend in every app, and offers gentle tools you choose: mindful breaks, daily limits and per-app blocks.

No account. No servers. No ads. No analytics. The app does not even request the Internet permission. Everything stays encrypted on your phone, and every line is here for you to read.

## What it does

| | |
|---|---|
| **Counts every short video** | Reels, Shorts, TikToks and Spotlight, however you reach them: the dedicated tab, a video tapped in Home, Explore, a profile, a shared link, plus inline videos in Instagram and Facebook feeds. Each video counts once; going back to one is a revisit, not a new count. Ads and photos are tallied separately. |
| **Live counter** | A small, see-through count on top of short videos: this unlock and today. A ring fills toward your next break. Drag it anywhere; tap for details. |
| **Screen time** | Time and opens per app (with Usage Access), time spent in short videos, an hour-by-hour rhythm and 7/30-day trends. |
| **Mindful breaks** | After the number of videos or minutes you choose, a calm full-screen pause with a breathing animation. "Keep watching" unlocks after a few seconds; "I'm done" takes you home. |
| **Blocks you choose** | Per app: off, a daily limit, or always. A block leaves the short-video player and pauses playback; the rest of the app keeps working. |
| **Home-screen widget** | Today's count and where it came from, at a glance. |

## How detection works (and why it keeps working after app updates)

Social apps change constantly, so Attention Stats does not depend on exact app versions. Each app has a small adapter (`native/reels-core/src/main/kotlin/org/attentionstats/detection/social/`) that:

1. prefers stable view IDs where an app exposes them (for example Instagram's clips pager or YouTube's Shorts recycler), and
2. falls back to geometry every short-video viewer shares: a full-screen vertical pager with a contiguous action rail on the right.

The focused item's on-screen metadata is turned into an in-memory fingerprint to tell one video from the next; raw text is never stored. Detection is tested on the JVM against **real screen layouts** captured from phones and sanitized of all personal content (`native/reels-core/src/test/resources/fixtures`). If an app update breaks something, one `uiautomator dump` plus `tools/sanitize_fixture.py` turns it into a regression test. See the [adapter guide](docs/SOCIAL-ADAPTERS.md).

## Privacy by construction

- Accessibility reads only the five supported apps, only while they are on screen, and only in memory.
- Stored: counts, times, categories and your choices, in an SQLCipher database keyed by the Android Keystore, excluded from cloud backup.
- No Internet permission, no third-party SDKs that receive data, no crash uploads.
- Read the plain-language [privacy policy](policies/PRIVACY.md).

## Build it

Requirements: Node 26 + pnpm, JDK 17, Android SDK (see [development setup](docs/DEVELOPMENT.md)).

```powershell
pnpm install
pnpm typecheck; pnpm lint; pnpm test                     # TypeScript, architecture boundaries, unit tests
python tools/android_build.py :reels-core:verifyReels      # encounter rules + real-layout detection fixtures
python tools/android_build.py :app:assemblePreview         # installable preview APK
```

Project layout: `src/` (React Native UI, one folder per feature), `android/app` (Kotlin accessibility service, overlays, encrypted storage), `native/reels-core` (pure Kotlin counting and detection, unit-tested), `native/analytics-core` (pure rollups).

## Contribute

Bug reports from real phones are the most valuable contribution: if counting misses something, use **Settings → Report a problem** in the app (it attaches your phone model and app versions, never your history). Code, translations and new app adapters are welcome; see the [contributor guide](CONTRIBUTING.md). An iPhone port is open for contributors ([guide](docs/IOS-CONTRIBUTION-GUIDE.md)).

Security reports: [SECURITY.md](SECURITY.md). Product vision: [VISION](docs/VISION.md) and [feature vision](docs/FEATURE-VISION.md). Current status: [STATE](docs/STATE.md).

Licensed under [Apache-2.0](LICENSE). Copyright 2026 Attention Stats contributors.
