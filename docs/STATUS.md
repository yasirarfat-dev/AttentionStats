# Status

Current release state, what has been verified, known issues and what comes next. Updated 19/11/26 (dates in this file are DD/MM/YY).

## Release

| | |
|---|---|
| Version | **1.1.0** (versionCode 11) "Brainrot Shield", package `attention.stats`; phone preview builds show `1.1.0-brainrot-shield` (ADR-123) |
| Channel | Google Play production (first public release; 1.0.0 was a closed test) and a signed APK on GitHub Releases (ADR-123) |
| Branches | `main` is the release line and shows the published app; the maintainer works on `main` until 1.1.0 is published, then `develop` resumes for work (ADR-068, ADR-123) |
| Data baseline | Room schema 9 is the first production schema; later releases must migrate it (ADR-108) |
| Resource baseline | Seven-day battery/CPU comparison waived for this release (ADR-112); still to do |

## Host checks for 1.1.0

All pass on the release source: `pnpm typecheck`, `pnpm lint` (includes architecture, backup-policy and palette guards), `pnpm format:check`, `pnpm test` (Jest), the native verify suites (`:reels-core:verifyReels`, `:protection-core:verifyProtection`, `:analytics-core:verifyAnalytics`), and release lint with 0 errors.

## Verified behavior

"Verified" means checked on a physical Android 16 (API 36) phone on the stated build; it does not mean every phone, app version or language works. Detailed per-run records stay in Git history.

| Area | Verified scope | Open |
|---|---|---|
| Counting on 1.1.0 (18/11/26) | TikTok 47.7.3, Instagram Home feed 455.0, Facebook Reels 587.0, YouTube Shorts 21.40, Snapchat Spotlight 14.32: each video seen counted once, ads and photos classified, the live counter matched the saved history. Found and fixed: TikTok photo posts counted as videos, a Facebook Reel counted again after swiping back to it, a recount when the accessibility service reconnected, and Instagram app time cut short by in-app navigation. Each fix has a regression test | Instagram Reels tab not re-run on 1.1.0 (earlier qualification only); TikTok regional/Lite builds; other versions and languages |
| Revisits and unlock sessions | One count per item per unlock session; returns are revisits | Long-session edge cases |
| App time and opens (Usage Access) | Entry/time checks on the phone | Opens are estimates (F-09) |
| Live counter | Show/hide, drag, pause/resume, recovery after returning to the app | — |
| Mindful breaks | Count and time triggers over real minutes, dismissal delay, five-minute absence reset. Tap-to-pause before a break (ADR-116) on preview builds 18/11/26: TikTok 47.7.3 paused with one tap, confirmed by progress that stopped moving; YouTube Shorts 21.40 paused once in three tries (it needed its spoken seek-bar time because the bar's numeric range stays at 0, fixed), and the other tries safely showed the compact break that asks the person to pause | Instagram, Facebook and Snapchat show the break without pausing (no tap toggle) |
| Per-app limits and blocks | Daily limit and Always; leaving a blocked player | More sources and lifecycle cases |
| App groups, time slots, Allow Only | Editor create/edit/remove; real one-minute slots activate and release on time; overlapping rules; five-minute allowance expiry; Settings, Home and the app stay reachable | Reboot, time-zone/DST changes |
| Focus sessions | Start, countdown, early End | Real expiry, reboot |
| Protection Lock | Earlier native PIN, recovery-phrase and fingerprint authorisation checks; protected actions cannot be bypassed via individual source toggles; framework departures | Two-tab model (ADR-121), preview 19/11/26 on a separate review install with fresh data (not the maintainer's lock): off state and both tabs; "Turn on with fingerprint" opened the system prompt at once and cancel returned cleanly; app pattern set up step by step (a wrong confirmation was rejected, the right one accepted), backup phrase, finish page and on-state overview; "Switch to fingerprint" rejected a wrong pattern and, with the right one, opened the prompt, and cancelling kept the pattern. The PIN path (mismatch then match) also worked. Not yet verified: completing fingerprint setup, switching, reset and approval with a real touch (needs the parent's finger), the screen-lock reset, earlier-build records with shortcuts, and the 5/10/15 minute cover choices |
| Today, Insights, history explorer | Week/Month/All Time, day and app explorers, calendar, paging, error/retry, large text (200%). Hourly charts fixed 18/11/26: the hourly query grouped by the encounter's own `category` column instead of the hour, putting nearly everything at 12 AM; verified on the phone after the fix | — |
| Break and block screens, Perspective (ADR-115, ADR-117) | Block cover and the full break card (theme-coloured scene, breathing guide, thumb-travel line, Do something real icons) render on the phone. Found and fixed 18/11/26: full-screen covers started under the status bar so their bottom slid behind the navigation bar; a parent approval opened from a cover could sit hidden under it; covers now re-check the screen every 1.5 s so none can linger until the phone is locked | TalkBack and 200% text on the redesigned cards; the stale-cover report from earlier testing was not reproduced, the watchdog is the safeguard |
| Keyboard and short screens (ADR-121) | Preview 19/11/26: native lock pages and the React Native time-slot editor lift the focused field above the keyboard (fields that were covered before). Limit, schedule and break covers rendered at 520 dp, 600 dp and full-height windows (the real view in a review harness) show everything, both buttons included, without scrolling | Other editors and the erase-history dialog not re-checked on the phone; covers on a physically short phone |
| Instant checks and one-step allowance (ADR-122) | Preview 19/11/26 on a separate review install (not the maintainer's lock): a PIN and backup phrase saved by a 600,000-round build were accepted after updating; the first check took the old time once (about 3.3 s) and later checks were too fast to measure over adb; a wrong PIN was still rejected. The "− Allow 5 min +" control rendered in full-height and 520 dp covers without clipping; plus and sideways swipes changed 5 → 10 → 15 and back, and plus greys out at 15. The welcome pages keep their actions above the navigation bar | Granting time from a live cover with the new control not exercised on the phone (qualification scripts updated, not run); the maintainer's own lock upgrades on its next correct PIN |
| Appearance and widgets | Theme templates, custom colours, five widgets render and refresh. Templates now apply on tap (ADR-119), verified Light ↔ Dark on the phone | Widget resize/launcher lifecycle |
| Storage | Migrations from every earlier schema to 9; write durability; rejection of unknown future schemas; rollback on failed migration; process-death recovery | Filesystem-full on a real device |
| Backup and restore | Manual export/restore; Android backup hand-off and cleanup; restore staging with hostile-entry rejection | Fresh-install restore through the Android framework |
| Erase history | Three-step erase and cancellation | — |
| Auto-scroll | 1.1.0 preview (18/11/26): YouTube Shorts 21.40 advanced at video end four times in three minutes via its seek bar, left an image post after five seconds and skipped a Shorts image ad; TikTok 47.7.3 advanced twice in 150 s. Facebook standalone-ad skip once (14/11/26) | Instagram Reels and Facebook not re-run on 1.1.0; TikTok/Instagram ad skip seen only in fixtures |

## Known issues

- **Video time can briefly exceed app time.** Video time comes from the accessibility service and app time from Usage Access, which reports later. Today marks the app time "(incomplete)" when this happens.
- **Month range** starts on the install day and extends 30 days, by design. It is not a calendar month.
- **YouTube text posts** inside Shorts are recorded as posts, not videos or photos; only image posts are photos.
- **Auto-scroll** is experimental. Facebook often exposes no playback progress, so it waits (ADR-110). Snapchat Spotlight exposes no progress and is unsupported. YouTube's spoken seek-bar reading is English-only; other languages wait.
- Detection can miss or misclassify items after app updates or in untested languages. Report with **Settings → Report a problem**.

## Should fix next

- R8 minification is off for release builds.
- Seven-day battery/CPU baseline (ADR-112).

## Next

Planned work, deferred maintainer decisions and what is not planned are in the [Roadmap](ROADMAP.md).
