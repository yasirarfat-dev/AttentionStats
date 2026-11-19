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
| Covers | A full-screen cover explains which rule applies and offers "Open Attention Stats" and one "− Allow 5 min +" control: minus, plus or a sideways swipe change the minutes (5, 10 or 15) and tapping allows them (ADR-122). Covers fit any screen height without scrolling. Android Settings, Home, keyboards, the dialer and system UI are never covered |

Whole-app rules check only which app is in the foreground, never its screen content. Short-video rules apply only on recognised players; an unknown screen releases them rather than blocking the whole app. Overlapping rules show one cover with all reasons.

## Protection Lock

Optional parental lock (ADR-111, ADR-113, ADR-117, ADR-120, ADR-121), under **Protect → Parent approval**.

- Two exclusive ways to approve, shown as two tabs like the schedule views: **Phone fingerprint** and **App PIN**. A status card says whether the lock is on and which way approves; a dot marks the tab in use, and the other tab offers to switch.
- **Phone fingerprint:** "Turn on with fingerprint" opens the system prompt at once, and one touch turns the lock on. A backup phrase is recommended afterwards but optional on Android 11 and later (Android 8–10 asks for one). Any fingerprint saved on the phone approves, so this suits phones where only parents' fingerprints are saved; Android cannot tell an app which finger was used and does not let apps register their own. Adding a fingerprint invalidates the lock's key and pauses approval until a parent resets it: with a backup phrase saved, only the phrase resets it; with none, the phone's screen lock does. Face and the phone's code can be added as extras next to fingerprint (off by default); they never authorize changes.
- **App PIN:** a 4–12 digit PIN or a 3×3 dot pattern (minimum 4 dots) that works only in this app, set up step by step like a phone's own lock: choose a PIN or pattern, confirm it on its own page, then add one backup phrase (3+ characters; up to two more optional). A final page confirms the lock is on. Changing the PIN or pattern asks for the current one first; "Forgot PIN?" resets it with a backup phrase.
- Switching is one flow from the other tab: to fingerprint, enter the current PIN or pattern, then touch the sensor (the key is deleted, phrases stay); to an app PIN, choose and confirm it, then touch the sensor (fingerprint stops approving). Locks from earlier builds that hold a key plus phone shortcuts keep working and offer "Stop phone unlock approving".
- Approval: in fingerprint mode the system prompt opens by itself; in app PIN mode the page asks for the PIN or pattern.
- The parent chooses which changes are protected: allowances, ending focus, editing or removing rules and groups, turning sources or protection off, erasing history and restoring backups.
- Credentials are stored only as PBKDF2-SHA256 verifiers (10,000 rounds, so checks are instant; ADR-122) on the phone, excluded from backups and exports. Turning the lock off deletes them. Set it up again after reinstalling.
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
