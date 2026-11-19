# Attention Stats

**See where your attention goes. Counted on your phone, never sent to us.**

Attention Stats is a free, open-source digital wellbeing app for Android. It counts the short videos you scroll through (Instagram Reels, Facebook Reels, YouTube Shorts, TikTok and Snapchat Spotlight), shows how long you spend in every app, and offers gentle tools you choose: mindful breaks, limits, app schedules, focus sessions and an optional parent lock.

No account. No servers. No ads. No analytics. The app does not even have the Internet permission.

[Get it on Google Play](https://play.google.com/store/apps/details?id=attention.stats) · [Download the APK](https://github.com/yasirarfat-dev/AttentionStats/releases/latest) · Current version 1.1.1 · [Privacy policy](policies/PRIVACY.md) · [Support](policies/SUPPORT.md)

## Features

| | |
|---|---|
| **Counts short videos** | Reels, Shorts, TikToks and Spotlight: the dedicated players from any entry, plus videos and photos in Instagram and Facebook feeds. Each video counts once per unlock; going back to one is a revisit. Ads and photos are tallied separately. |
| **Live counter** | A small, see-through count on top of short videos: this unlock and today. A ring fills toward your next break. Drag it anywhere; tap for details. |
| **Screen time** | Time and opens per app (with Usage Access), time on short-video screens, an hourly rhythm, and Week / Month / All Time history with a day calendar and per-app history. |
| **Mindful breaks** | After the number of videos or minutes you choose, a calm full-screen pause. The video is paused first where the app allows it, with a guided breathing exercise. |
| **Blocks you choose** | Per app: off, a daily limit, or always. App groups, recurring time slots, focus sessions (5–180 minutes) and Allow Only. Android Settings, Home and emergency calls always stay available. |
| **Protection Lock** | Optional parent approval with the phone's fingerprint or an app PIN or pattern, for the changes you choose to protect. Stays on this phone; never backed up. Not tamper-proof. |
| **Backup & restore** | Android-managed backup (on unless you turn it off) and manual export/restore through Android's file picker. |
| **Widgets & themes** | Five home-screen widgets and light, dark and custom colour themes. |
| **Auto-scroll (experimental)** | Optional: moves to the next video when the current one finishes, where the app exposes playback progress. |

## Supported apps and limits

| App | What is counted |
|---|---|
| Instagram | Reels from any entry; videos and photos in Home and profile feeds |
| Facebook | Reels tab and Reels opened from Home; feed videos and photos |
| YouTube | Shorts from any entry |
| TikTok | The video feed from any entry, including photo posts (main, regional and Lite apps) |
| Snapchat | Spotlight |

Counts are estimates of what appeared on screen, not proof you watched it. Support varies with app version and language; an app update can break recognition until we ship a fix. Text posts, likes and comments are not measured. Stories, messages and other screens are ignored. See [known issues](docs/STATUS.md#known-issues).

## Privacy in plain words

- The accessibility service looks at the screen only in the five supported apps, only while they are open, and only in memory. Captions and names are turned into a one-way fingerprint and thrown away. Nothing you watch, type or say is saved or sent.
- Schedules and focus sessions check only which app is open, never its content.
- Your history (counts, times, choices) is stored in an encrypted database on your phone.
- It leaves your phone only through Android backup (which you can turn off) or a file you export yourself. Backup files can be read by anyone who has them, so keep them private.
- Every line of code is here to read. Full details: [privacy policy](policies/PRIVACY.md) and [technical privacy notes](docs/PRIVACY.md).

## Build from source

Requirements: Node 26 with pnpm 12 (pinned in `package.json`), JDK 17, Python 3.11+, and an Android SDK with platform 37, Build Tools 37.0.0, NDK 30.0.16248370 and CMake 4.1.2.

```powershell
pnpm install --frozen-lockfile --ignore-scripts
pnpm typecheck; pnpm lint; pnpm test
python tools/android_build.py :reels-core:verifyReels      # detection tests on real screen layouts
python tools/android_build.py :app:assemblePreview         # installable preview APK (com.attentionstats.debug)
```

The preview APK is written to `android/app/build/outputs/apk/preview/`. See [CONTRIBUTING](CONTRIBUTING.md) for setup details, checks and how to add a test from a missed screen.

## Documentation

| Document | Contents |
|---|---|
| [Features](docs/FEATURES.md) | What every screen and tool does |
| [Detection](docs/DETECTION.md) | How counting works per app, metric definitions, limits |
| [Architecture](docs/ARCHITECTURE.md) | Modules, data flow, storage, encryption, backup |
| [Privacy (technical)](docs/PRIVACY.md) | Data inventory, permissions, retention |
| [Status](docs/STATUS.md) | Current release, what is verified, known issues |
| [Roadmap](docs/ROADMAP.md) | Planned features and what is not planned |
| [Decisions](docs/DECISIONS.md) | Decision log (ADRs) |
| [Release](docs/RELEASE.md) | Release process and Play Console text |
| [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) | How to help; how to report a vulnerability |

## Contributing

The most valuable contribution is a report from a real phone: if counting misses something, use **Settings → Report a problem** in the app (it includes your phone model and app versions, never your history). Code, translations and adapter fixes are welcome; see [CONTRIBUTING](CONTRIBUTING.md).

Created by **[Yasir Arfat](https://yasirarfat.com)** and published on Google Play by Convenience Labs. Licensed under [GPL-3.0-only](LICENSE): anyone may use, study, change and share the code, and every copy or derivative must stay under GPL-3.0-only with its full source available. The license grants no rights to the Attention Stats name or icon; forks need their own. Third-party notices in [NOTICE](NOTICE). Copyright 2026 Attention Stats contributors.
