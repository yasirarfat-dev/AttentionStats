# Features

What the app does in 1.1.0 and the rules it follows. For how counting works see [DETECTION](DETECTION.md); for verification status see [STATUS](STATUS.md).

## Design principles

- **Useful by choice.** Every source, reminder and rule is off until the person turns it on. Hiding a display never disables a rule or collection; those are separate, named controls (ADR-052).
- **Calm and honest.** No streaks, scores, guilt text or engagement loops (ADR-019). Unknown is shown as unknown, never as zero. Counts are labelled as encounters, not "watched".
- **Always an exit.** Covers and breaks never block Android Settings, Home, the dialer or emergency calls, and never prevent uninstalling. Protection is best effort, not tamper-proof.
- **Compact screens.** Cards show a heading, data and controls; explanations sit behind an information button (ADR-096). Every app name has its icon (ADR-098).
- **Accessible.** 48 dp touch targets, screen-reader names and states, layouts that reflow at 200% text, and the system reduced-motion setting wins over app animations.

## Navigation

Four tabs (ADR-083): **Today**, **Insights**, **Protect**, **Settings**. New features go inside these hubs. A welcome tour introduces the app and the information-button convention; it can be reopened from Settings.

## Today

- **Hero:** This session (encounters since unlock), On screen (video time), Breaks; then Reels apps, Screen time and App launches for the day.
- **Per source:** encounters, video time, photos and ads per supported app, and app time (marked "(incomplete)" while Usage Access lags behind).
- **Apps:** screen time and opens for every app when Usage Access is on.
- **Quick Actions** (top-right menu): Focus Now, Breaks, Blocks, Schedules.
- A banner shows when the accessibility service is disconnected, with a button to reconnect.

## Perspective

Today and Insights include a **Perspective** card (ADR-115) that turns short-video time into measurable real-life achievements: a named book read in that time (by its length at 250 words a minute) or pages read, kilometres walked (with steps), kilometres cycled, 5 km runs, workouts, language lessons, course hours, meditation and instrument practice, plus thumb travel compared with landmarks (11 cm per counted video). The first line is always reading; the others rotate daily. Assumptions are listed behind the card's information button; these are possibilities, not measurements.

## Insights

- **Week**, **Month** and **All Time** ranges. Week and Month are fixed 7- and 30-day pages counted from the install day, not calendar months.
- **Explore a Day:** one grouped view of a selected date (encounters, video time, app time, opens, breaks, hourly encounters); All Time uses a calendar picker.
- **Explore an App:** searchable per-app history with daily rows and sorting.
- Ranges are prewarmed in memory; nothing is cached to disk. Dates use DD/MM/YYYY.

## Live counter

An optional floating button drawn by the accessibility service over supported short-video screens.

- Shows this-session and today counts (today across all observed apps by default, or the current app only). A ring fills toward the next break.
- Tap to expand local statistics; drag anywhere; hide for this visit. Hiding never pauses counting.
- Numbers change with a short vertical page animation (instant with reduced motion).
- It disappears on unknown, private, typing, locked or foreign screens.

## Mindful breaks

- Trigger after a chosen number of videos or minutes in supported apps (defaults 30 videos / 15 minutes, ADR-091), counted across the unlock session.
- Before the break screen, the app pauses the video with one tap: on a visible Pause button, confirmed by Play appearing (ADR-077), or on TikTok and YouTube on the video itself, only when progress shows it is playing and confirmed only when progress stops (ADR-116). It never taps twice or presses Play. If it cannot confirm the pause, it shows a smaller centred reminder instead.
- The break screen is vivid and interactive (ADR-115): a centred card with a slowly drifting background in the hue of the person's own theme colour (ADR-117), a guided breathing orb (in 4 s, hold 2 s, out 6 s, with the words shown), one real-life comparison (for example thumb travel against a landmark, or the time as pages of a book), one of 36 short invitations, and **Do something real** choices (walk, read, drink water, stretch, call someone, step outside) that go Home with a short encouragement. A lifetime count of those choices is shown; it is not a streak. "Keep watching" unlocks after a configurable delay (default 3 seconds, may be zero); "I'm done" goes Home. Animations follow the system reduced-motion setting.
- Leaving supported apps for five minutes resets the break timer.

## Blocks, limits and schedules

| Tool | Behavior |
|---|---|
| Per-app short-video limit | Off, Daily limit (videos per day) or Always. When reached, the app presses Back once to leave the short-video player; the rest of the app works. Audio may continue |
| App groups | Editable named sets of installed apps. Starter groups (Reels apps, Social apps, Distracting apps, Games, Productive apps, Essentials) are suggestions and fully editable |
| Time slots | Recurring weekday periods or all day, for a group or one app, covering the whole app or only its short videos. Overnight slots belong to their start day. New slots save off until activated |
| Allow Only | During a slot or focus session, every other launchable app is covered |
| Focus sessions | 5–180 minutes, Block Apps or Allow Only, on a frozen set of apps; End works immediately |
| Covers | A full-screen cover explains which rule applies and offers a five-minute allowance and "Open Attention Stats". Android Settings, Home, keyboards, the dialer and system UI are never covered |

Whole-app rules check only which app is in the foreground, never its screen content. Short-video rules apply only on recognised players; an unknown screen releases them rather than blocking the whole app. Overlapping rules show one cover with all reasons.

## Protection Lock

Optional parental lock (ADR-111, ADR-113, ADR-117), under **Protect → Parent approval**.

- Setup is three plain-language steps. Step 1: one **parent key**, either a 4–12 digit PIN or a 3×3 dot pattern (minimum 4 dots), that works only in this app and is not the phone's unlock. Saving a PIN replaces a pattern and the other way round. Step 2: phone unlock shortcuts, guided by one question. Step 3: three backup phrases (3+ characters each) that reset a forgotten key.
- The parent chooses which changes are protected: allowances, ending focus, editing or removing rules and groups, turning sources or protection off, erasing history and restoring backups.
- Shortcuts are chosen by the parent. Setup asks whether the child can unlock the phone with their own fingerprint or face; "Yes, or not sure" (default) keeps every shortcut off, "No, only mine" turns fingerprint on. Strong biometrics (fingerprint) are bound to a Keystore key; face and other weak biometrics off by default (less secure); the phone screen lock off by default (a child who knows it could bypass). Changing them needs the parent key. Android cannot tell an app which enrolled finger was used, so enable biometrics only if the enrolled fingerprints belong to the parent.
- Credentials are stored only as PBKDF2-SHA256 verifiers on the phone, excluded from backups and exports. Turning the lock off deletes them. Set it up again after reinstalling.
- It is not tamper-proof: Android Settings, clearing storage and uninstalling stay available.

## Auto-scroll (experimental)

- Off by default. Turn it on in Settings (with its own disclosure), then press Start on the live counter in a supported viewer. The same control stops it. The permission lasts only for the current service session.
- Swipes up once when a video finishes (numeric progress only: TikTok and Instagram player progress, YouTube Shorts seek bar) or after five seconds on an explicitly recognised photo or image post. Missing progress means waiting, never guessing (ADR-114).
- Stops on Stop, a tap or manual scroll, leaving the viewer, lock, hiding the counter, revoked access or any unknown screen.
- Optional **Skip Ads** (separately allowed) swipes past recognised ads at once: standalone Facebook Reels ads, and TikTok, Instagram Reels and YouTube Shorts pages carrying their own "Sponsored"/"Ad" badge. Paid partnerships are not skipped. Provider "Skip Ad" buttons and ad links are never pressed (ADR-093, ADR-114).
- Never likes, comments, follows, shares or opens anything. Works independently of built-in Facebook/Instagram auto-scroll, which Settings only explains how to find (ADR-101).
- Source support: see the table in [DETECTION](DETECTION.md#supported-sources).

## Settings

| Group | Contents |
|---|---|
| Counting | Per-app sources (each asks for consent), Pause all counting, Count ads as videos |
| Permissions | Accessibility (with connection state), Usage Access, battery guidance (opens Android's app settings; never changes them) |
| Display & experience | Live counter content and icons, photo encounters, legacy playback switch (disable only), auto-scroll, built-in auto-scroll guidance, Skip Ads, home-screen widgets |
| Data & privacy | Backup & restore, Erase history (three confirmations; turns automatic backup off), privacy policy, source code, open-source licenses (offline: every bundled component, its license text and NOTICE) |
| Appearance | Templates (Light/Dark/System; Graphite, Blue, Teal, Violet accents) and Customization (full-spectrum text, background and accent colours with hex/RGB; card corners, spacing, statistic size); gentle animations |
| Help & feedback | Report a problem (email or GitHub issue draft with phone model and app versions only), Suggest an idea (the app's listing on VibeCodedProjects (vibedprojects.com), a GitHub issue or email; nothing attached), share, rate, welcome tour |

Templates (theme mode and accent presets) apply as soon as they are tapped; custom colours and layout are previewed and saved explicitly. Choosing a theme mode clears custom text/background colours; accent choices stay (ADR-104, ADR-119).

## Home-screen widgets

Five widgets (ADR-087): **Today Overview**, **One Stat**, **Screen Time**, **Reels by Apps** and **Hourly Rhythm**. Each placed widget is configured separately (layout, statistics and order, colours, transparency) by long-pressing it. Widgets read the same local history and refresh when it changes.

## Backup, restore and erase

- **Android backup** starts on unless you saved "off" (ADR-079). Only one logical history file is offered, only over client-side-encrypted transport (Android 9+, secure screen lock); device-to-device transfer is excluded.
- **Manual export/restore** uses Android's file picker. Files use a public app key: anyone holding a file can read it (ADR-073).
- **Restore** is staged and previewed, then replaces local history in one transaction. Counting and protection stay off afterwards until re-enabled.
- **Erase history** deletes all local counts, timers, events and consent records, turns counting and automatic backup off, and keeps Protection Lock. Copies already in backups or exported files remain.

See [ARCHITECTURE](ARCHITECTURE.md#backup-and-restore) for the format and [PRIVACY](PRIVACY.md) for data handling.
