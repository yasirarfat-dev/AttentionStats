# Current state

**Status:** MVP 1.0.0 (versionCode 10) built with version-independent detection, interventions, redesigned UI and widget; verified live on the maintainer's Samsung phone (Android 16). Updated 2026-10-30.
**Responsibility:** Records what exists now, how it was verified, and what is next. Older detail lives in git history and the [implementation log](IMPLEMENTATION_LOG/README.md).
**Related specifications:** [Index](INDEX.md), [Tracker](FEATURE-TRACKER.md), [Decisions](DECISIONS.md), [Adapters](SOCIAL-ADAPTERS.md), [Release](RELEASE.md).
**Unresolved feasibility:** Fidelity on other phones, languages and future app versions is estimated, not proven; Snapchat has no fixtures yet; F-05 (automatic Google-account backup) remains blocked.

## Product

- **Attention Stats**, Android package `attention.stats` (development build `com.attentionstats.debug`), Apache-2.0, public repo `yasirarfat-dev/AttentionStats`. Mobile only; iOS later via contributors.
- React Native (Community CLI, TypeScript, New Architecture) UI; Kotlin accessibility service, overlays and SQLCipher/Room storage; pure Kotlin `reels-core` (counting + detection) and `analytics-core`.

## Implemented (1.0.0)

| Area | What exists |
|---|---|
| Detection (ADR-066) | Adapters for Instagram, Facebook, YouTube, TikTok (+ trill, lite) and Snapchat; ID-first, geometry fallback; inline feed videos on Instagram/Facebook; ads/photos classified separately; first item counted on entry; revisits excluded from totals. |
| Counting store | Room 6: exposures with surface, dwell, revisit, app version, method, device-local item key, unlock session/ordinal; reels timers; app-usage events; intervention events; per-app block limits. |
| Live counter | Draggable see-through HUD (this unlock / today) with a ring toward the next break; tap for Videos/Photos/Ads breakdown, open app, pause, hide for this visit, auto-scroll controls. |
| Interventions | Mindful breaks (N videos / M minutes, countdown, audio-focus pause, "I'm done" → Home); per-app blocks (off / daily limit / always) that leave the player with Back and never trap. |
| App UI | Trust-first onboarding with prominent disclosure and guided setup; Today (hero count, per-app with logos, hourly rhythm, screen time); Insights (7/30 days, equivalents, peak hour, breaks); Protect; Settings (sources, permissions, extras, report a problem, privacy, delete). |
| Widget | Configurable home-screen widget: Today at a glance (videos, watching, screen time, session in four tiles) or a single stat (videos, session, watching, screen time, breaks); reconfigurable by long-press; "Add widget" button in Settings. |

## Verification (2026-10-30)

- `:reels-core:verifyReels`: 29 encounter checks + 79 checks over 26 sanitized real layouts (all five route families and negatives).
- `pnpm typecheck`, `pnpm lint` (oxlint + architecture boundaries), `pnpm test` (66), `python tools/check_docs.py`: pass.
- Live phone, 5 swipes each: Instagram Reels +5, Facebook Reels tab +5, YouTube Shorts +5, TikTok +5 (one run +4 where the feed itself did not advance); Instagram Home feed videos counted; session continues across apps until screen-off; Facebook block leaves the player and lands on Facebook Home; break after 5 videos with countdown; "I'm done" goes Home.
- Also verified live: auto-scroll advances when a video ends and moves past photos/image ads after 5 s, stopping for a break; Instagram daily limit (30) shows "Daily limit reached" and "Okay" lands on Instagram Home; HUD pause shows a "Paused · tap to resume" pill and resuming continues counting; expanded HUD shows aligned Session/Today columns; three-step history erase deletes everything (counts 0, counting off) and onboarding re-enables counting; widgets render and reconfigure on the Samsung launcher.
- Not yet verified on a device: Snapchat (not installed on the test phone), the 5-minute break-run reset (implemented, not yet observed live), and other phones/languages.

## Next

1. Upload the signed AAB to the Play closed test; complete the Accessibility permission declaration with a short screen recording (see [Play Console notes](../release/PLAY-CONSOLE.md)).
2. Gather tester reports (in-app "Report a problem") and turn each into a fixture.
3. Capture Snapchat and non-English fixtures; then encrypted export/import (AS-014/AS-020).
