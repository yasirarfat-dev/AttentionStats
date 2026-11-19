# Decisions

Architecture decision records (ADRs). IDs are permanent and are cited in code and commits; never renumber or reuse one. Add a new ADR for any material change of behavior, data handling or workflow, and say what it supersedes. Dates are YYYY-MM-DD.

Superseded entries are kept as one line pointing at their replacement.

## Foundation (2026-10-26 to 2026-10-27)

- **ADR-001** 2026-10-26 — Product "Attention Stats": open source (Apache-2.0 until ADR-118, now GPL-3.0-only), Android first, local personal wellbeing (observe, understand, protect).
- **ADR-002** 2026-10-26 — React Native Community CLI, TypeScript, New Architecture; no Expo.
- **ADR-003** 2026-10-26 — Kotlin owns collection and rules, independent of JS; one authoritative Room/SQLite store. MMKV only for non-sensitive UI preferences.
- **ADR-004** 2026-10-26 — Every source is off until explicit in-app consent. An Android grant alone never enables collection.
- **ADR-005** 2026-10-26 — Keep history until the person deletes it; no hidden size-based purging.
- **ADR-006** 2026-10-26 — No backend, account, ads, analytics or automatic uploads. The developer never receives history.
- **ADR-007** 2026-10-26 — Protection is deterministic user-defined rules. Predictions may only recommend; no model-driven actions.
- **ADR-008** 2026-10-26 — Superseded by ADR-073 (portable archive with user-held secret credentials).
- **ADR-009** 2026-10-26 — Direct Google Drive API backup is blocked by Drive API policy (F-05). Android-managed backup is a separate route (ADR-079).
- **ADR-010** 2026-10-26 — pnpm; every new direct package resolves at its latest version, pinned exactly with its license recorded; frozen lockfile for builds.
- **ADR-011** 2026-10-26 — Per-app detectors; counts are estimates of on-screen encounters. Unsupported screens are unknown, never zero.
- **ADR-012** 2026-10-26 — Content-topic patterns, if ever built, are optional and local; no addiction diagnosis or sensitive-trait inference.
- **ADR-013** 2026-10-26 — Privacy message: "No data sent to us. Your history stays on your device. Open source." Never claim "no collection" or hide user-chosen backup transfers.
- **ADR-014** 2026-10-26 — Humans and agents follow the same workflow rules (now [CONTRIBUTING](../CONTRIBUTING.md)).
- **ADR-015** 2026-10-26 — Tools may be added in isolation; never change shared machine state for unrelated work.
- **ADR-016** 2026-10-26 — Canonical repository: public `yasirarfat-dev/AttentionStats`.
- **ADR-017** 2026-10-27 — Superseded by ADR-020 and ADR-029 (documentation-only phase).
- **ADR-018** 2026-10-27 — Android cloud backup and device transfer need explicit include/exclude rules; `allowBackup` alone is not enough (see ADR-073, ADR-079).
- **ADR-019** 2026-10-27 — Design for peace of mind and self-understanding: user autonomy, supportive voice, finite summaries, no guilt, streaks or engagement loops.
- **ADR-020** 2026-10-27 — Completed: a synthetic browser design study preceded native UI. Removed from the tree 2026-11-18.
- **ADR-021** 2026-10-27 — Optional live counter over supported feeds, separate from collection and protection.
- **ADR-022** 2026-10-27 — The live counter defaults to a small top-centre position.
- **ADR-023** 2026-10-27 — Superseded by ADR-034 (ads excluded from counts).
- **ADR-024** 2026-10-27 — Mobile clients only. No website, web app, desktop client or browser extension in current delivery.
- **ADR-025** 2026-10-27 — Long-term goal: a detailed, consented diary of mobile usage; every metric needs its own feasibility evidence.
- **ADR-026** 2026-10-27 — Optional multi-resolution history and summary-only archives are planned; detail is kept by default and any compaction is previewed.
- **ADR-027** 2026-10-27 — Domain-oriented modules: `src/features` public APIs, pure Kotlin domain modules, platform adapters; boundaries enforced by `tools/check_architecture.py`.
- **ADR-028** 2026-10-27 — Commit attribution uses the maintainer's GitHub no-reply identity.
- **ADR-029** 2026-10-27 — Mobile scaffold from the Community CLI template with pnpm; a repository-local `minimumReleaseAge: 0` exception, with strict peers and disabled install scripts kept.
- **ADR-030** 2026-10-27 — GitHub workflows run only on manual `workflow_dispatch`; no push, PR or scheduled triggers.
- **ADR-031** 2026-10-27 — Encrypted store: Room 3 + SQLCipher with a random per-generation key wrapped by an Android Keystore AES-256-GCM key, and non-destructive corruption handling. Its secret-credential archive profile is superseded by ADR-073.
- **ADR-032** 2026-10-27 — Prioritise an installed counter preview inside the main app rather than separate qualification APKs.
- **ADR-033** 2026-10-27 — Calm, clear Material-style interface. Palette details superseded by ADR-071 and ADR-076.

## Counter MVP (2026-10-27 to 2026-10-30)

- **ADR-034** 2026-10-27 — Separate categories: video, photo/carousel, recognised ad, unclassified. Ads have their own tally; one carousel is one encounter.
- **ADR-035** 2026-10-28 — The live counter is one compact icon-and-number button that expands to local statistics.
- **ADR-036** 2026-10-28 — Whole-app time and short-video time are separate; video time is a subset of app time. Auto-scroll is an independent option.
- **ADR-037** 2026-10-28 — Build the full navigation shell first. Tab layout superseded by ADR-083.
- **ADR-038** 2026-10-28 — The counter stays visible through known viewer transitions on a translucent background. Motion detail superseded by ADR-040.
- **ADR-039** 2026-10-28 — Totals count encounters; returning to an item is tracked separately. Revisit rule refined by ADR-066.
- **ADR-040** 2026-10-28 — Counter numbers change with a 240 ms vertical page animation.
- **ADR-041** 2026-10-28 — Social support is additive: one isolated adapter per app over shared domains.
- **ADR-042** 2026-10-28 — The maintainer authorises operating the debug phone over adb for testing; this never changes product consent flows.
- **ADR-043** 2026-10-28 — Permanent Play application ID `attention.stats`; release archives live outside the repository.
- **ADR-044** 2026-10-28 — Plain compact numbers; Today defaults to all observed apps.
- **ADR-045** 2026-10-28 — Superseded by ADR-066 (fixed Facebook label matching in diagnostics).
- **ADR-046** 2026-10-28 — Superseded by ADR-066 (version-pinned Facebook viewer).
- **ADR-047** 2026-10-28 — Adopt the long-term wellbeing feature vision; each entry route is qualified independently.
- **ADR-048** 2026-10-28 — Ship a four-app counter (Facebook, Instagram, YouTube Shorts, TikTok) before the broader diary. Snapchat added by ADR-066.
- **ADR-049** 2026-10-29 — Superseded by ADR-066 (TikTok-only caption matching).
- **ADR-050** 2026-10-29 — Keep per-encounter history for the long term, with stable IDs and origin provenance so future multi-device merging cannot double count.
- **ADR-051** 2026-10-29 — Auto-scroll is separately allowed, started and stopped by the person, driven only by numeric playback evidence and one revalidated swipe.
- **ADR-052** 2026-10-29 — "Hide counter" (this visit), "display off" (setting) and "pause counting" are three different controls.
- **ADR-053** 2026-10-29 — Auto-scroll must never interrupt viewing to obtain a signal (no pausing, no revealing provider controls).
- **ADR-054** 2026-10-29 — Count Instagram/Facebook videos opened from Home before expanding automation; auto-scroll is limited to dedicated viewers.
- **ADR-055** 2026-10-29 — MVP adds real app time, per-source video time and opt-in count/time break reminders.
- **ADR-056** 2026-10-29 — App time uses a separate, off-by-default Usage Access consent and records only package, timestamp and event kind, from enablement onward.
- **ADR-057** 2026-10-29 — Developer diagnostics never appear in product screens; counting starts from consent plus the Android grant.
- **ADR-058** 2026-10-29 — Settings shows battery-optimisation status and opens Android's app settings; it never changes a preference itself.
- **ADR-059** 2026-10-29 — The main counter is one unlock-to-lock session across all tracked apps. Optional per-app block of the short-video player.
- **ADR-060** 2026-10-29 — "Count ads as videos" is a reversible display choice; ad tallies are always kept.
- **ADR-061** 2026-10-29 — Compact per-app time rows. Its deferral of schedules is superseded by ADR-068 and ADR-076.
- **ADR-062** 2026-10-29 — "Opens" is an observed foreground-entry estimate, not a process launch count.
- **ADR-063** 2026-10-29 — A larger bounded node-traversal budget for TikTok's dense viewer.
- **ADR-064** 2026-10-30 — Play App Signing with a separate local upload key kept outside Git.
- **ADR-065** 2026-10-30 — Superseded by ADR-066 (Facebook first-viewer geometry).
- **ADR-066** 2026-10-30 — Version-independent detection: per-app adapters in pure `reels-core` prefer stable view IDs and fall back to shared geometry; item identity is a salted in-memory fingerprint of on-screen labels (never stored); a dwell filters fling-throughs; returns are revisits. One disclosure (notice 6) covers all five sources; sanitised real layouts are regression fixtures. Adds mindful breaks and per-app blocks. See [DETECTION](DETECTION.md).

## Beta and product completion (2026-11-03 to 2026-11-15)

- **ADR-067** 2026-11-03 — `main` is the release line; work continues on `develop`.
- **ADR-068** 2026-11-03 — Merging to `main` needs explicit maintainer agreement to the concrete candidate; passing checks alone never authorise it. Adds editable app groups and whole-app schedules.
- **ADR-069** 2026-11-03 — Encounters are settled on-screen items, not "watches". Media kind (video/photo/unknown) is separate from ad classification. Optional numeric playback metrics (3-second watches, completions) come only from advancing position/duration; missing signals stay unknown. Presentation changed by ADR-109.
- **ADR-070** 2026-11-03 — Superseded by ADR-072 (illustrated permission guide). The accessibility service label "Attention Stats: Digital Wellbeing & BrainRot Shield" remains.
- **ADR-071** 2026-11-03 — Selectable Light/Dark/System appearance and accents, stored as device-local preferences. Dark surfaces superseded by ADR-076.
- **ADR-072** 2026-11-03 — Permission buttons open Android Settings directly and recheck the grant on return.
- **ADR-073** 2026-11-04 — Backups need no remembered password: archives use a public key derived from the package name, so anyone holding a file can read it. The live database stays SQLCipher/Keystore protected. Its off-by-default part is superseded by ADR-079.
- **ADR-074** 2026-11-04 — Schema 9 records per-fact origin (`FactOrigin`) and archives preserve IDs and origins, ahead of any future synchronisation. Android backup is disaster recovery, not sync.
- **ADR-075** 2026-11-04 — The counter stays steady across supported feeds; one auto-scroll toggle on the counter.
- **ADR-076** 2026-11-05 — Per-app or group time slots (whole app or short videos only, custom or all-day), near-black dark theme, reliable break dismissal.
- **ADR-077** 2026-11-05 — Before a break screen, tap a visible Pause control once and confirm Play is shown; never press Play. If pausing is not possible, show a smaller reminder instead.
- **ADR-078** 2026-11-05 — Schedule editing moves into the shared React Native Protect screens; native code keeps evaluation and storage.
- **ADR-079** 2026-11-07 — Android-managed backup starts on unless a saved choice says off; requires Android 9+, a secure screen lock and client-side-encrypted transport; device transfer is excluded.
- **ADR-080** 2026-11-07 — Auto-scroll completion: a near-end-to-start loop or two terminal numeric samples. Explicitly classified photos may advance after five seconds; never a timer for videos.
- **ADR-081** 2026-11-12 — Compact active counter layout; source reliability limits are stated, not hidden.
- **ADR-082** 2026-11-13 — Voluntary focus sessions of 5–180 minutes on a frozen set of apps, with immediate early End.
- **ADR-083** 2026-11-13 — Four primary tabs: Today, Insights, Protect, Settings; growth goes into destinations within them.
- **ADR-084** 2026-11-13 — Quieter Today hero, a Quick Actions menu, Week/Month/All Time ranges, group-based focus and Allow Only mode.
- **ADR-085** 2026-11-13 — Long-term ambition: every useful, technically observable mobile usage family, each behind its own consent and feasibility gate.
- **ADR-086** 2026-11-13 — Superseded by ADR-087 (six generic widget layouts).
- **ADR-087** 2026-11-13 — Five distinct widgets: Today Overview, One Stat, Screen Time, Reels by Apps, Hourly Rhythm; configured per instance.
- **ADR-088** 2026-11-13 — Suggested focus shortcuts in Quick Actions; per-widget transparency.
- **ADR-089** 2026-11-13 — Quiet separators between parallel statistics; switches last in Settings rows.
- **ADR-090** 2026-11-13 — A curated widget set with progressive configuration; add templates only for distinct needs.
- **ADR-091** 2026-11-13 — In-memory prewarming of Insights ranges; DD/MM/YYYY dates; direct focus chooser; editable starter groups; break defaults 30 videos / 15 minutes.
- **ADR-092** 2026-11-13 — Compact Focus Now entry. Proposed supervised protection, implemented by ADR-111 and ADR-113.
- **ADR-093** 2026-11-13 — Optional, separately allowed skipping of recognised standalone Facebook Reels ads during auto-scroll. Provider "Skip Ad" buttons are never pressed.
- **ADR-094** 2026-11-14 — Full-spectrum colour picker for text, background and accent; named app widgets.
- **ADR-095** 2026-11-14 — All Time uses a calendar picker to explore a day.
- **ADR-096** 2026-11-14 — Cards show headings, data and controls; routine explanations sit behind an information button.
- **ADR-097** 2026-11-14 — "Explore a Day" is visibly grouped and repeats the selected date.
- **ADR-098** 2026-11-14 — An app icon appears beside every app name.
- **ADR-099** 2026-11-14 — An isolated emulator may be used for layout checks; physical-phone evidence is still required for detection. Deferred until product completion.
- **ADR-100** 2026-11-14 — Appearance has two groups: Templates and Customization.
- **ADR-101** 2026-11-14 — Our auto-scroll is independent of any built-in app auto-scroll; Settings only explains how to find the built-in option.
- **ADR-102** 2026-11-15 — The welcome tour resets scroll position per step and offers Back.
- **ADR-103** 2026-11-15 — A uniform 8 dp inset around the Appearance frame.
- **ADR-104** 2026-11-15 — Choosing a theme mode clears custom text/background overrides; accent choices stay.
- **ADR-105** 2026-11-15 — Research into further local playback signals is allowed; no new production capability without disclosure and qualification.
- **ADR-106** 2026-11-15 — Database durability and migration correctness are core requirements; keep enabled raw facts with provenance; expand collection only through its own consent gates.

## First production release (2026-11-16 onward)

- **ADR-107** 2026-11-16 — History writes use SQLite `synchronous=FULL` on the primary writer (stronger commit durability); foreign keys are enabled on every pooled connection.
- **ADR-108** 2026-11-16 — Earlier builds were test-only. The first public release (1.1.0) is the production data baseline; every later release must migrate it, including skipped versions. Debug builds use the separate ID `com.attentionstats.debug`.
- **ADR-109** 2026-11-17 — Hide the four playback tiles when a scope has no recorded playback facts and withdraw new activation; existing users keep a disable-only switch. Live-source playback is deferred.
- **ADR-110** 2026-11-17 — Facebook auto-scroll (missing playback progress) is a known limitation, not a release blocker.
- **ADR-111** 2026-11-17 — Optional parental PIN with recovery phrases and optional strong biometrics in the next release; it never blocks uninstall, Android Settings or emergency access. Credential details superseded by ADR-113.
- **ADR-112** 2026-11-18 — Same-day publication of 1.1.0. The maintainer waives the seven-day battery/CPU resource baseline for this release: a recorded waiver, not a measured pass; it remains follow-up work. Remaining checks prioritise current-build reliability of source counting.
- **ADR-113** 2026-11-18 — Protection Lock (one-secret rule and setup defaults refined by ADR-117): PIN 4–12 digits, app-own pattern, parent-chosen unlock methods. The parent sets a 4–12 digit PIN or a 3×3 dot pattern (minimum 4 dots), plus recovery phrases (3+ characters each). All are stored only as local PBKDF2-SHA256 verifiers and never backed up. The parent chooses unlock methods: strong biometrics on by default and key-bound; face/weak biometrics off by default (less secure); the phone screen lock off by default (a child who knows it could bypass). Changing these needs the app PIN or pattern. Turning the lock off deletes its verifiers.
- **ADR-114** 2026-11-18 — Auto-scroll broadened for 1.1.0 (the maintainer reversed the earlier deferral of broader auto-scroll). YouTube Shorts joins auto-scroll through its player seek bar, read from range data, a clock pair or its spoken description ("29 seconds of 1 minute 19 seconds"); still numeric progress only, never a timer. A loop the 400 ms sampler steps over is recognised when the observed playback rate predicts the end (≥ 0.97) just before the position returns near the start. YouTube image posts in the Shorts pager are photos (text-only posts are posts), so they advance after five seconds like other photos. "Skip Ads" (renamed from Skip Standalone Ads) also swipes past TikTok, Instagram Reels and YouTube Shorts pages that carry their own small exact "Sponsored"/"Ad" badge; paid partnerships, captions and view-ID hints never authorise a skip. Snapchat Spotlight exposes no progress and stays unsupported.
- **ADR-115** 2026-11-18 — Motivating interventions and Perspective (refines ADR-019's tone at the maintainer's request). Break and block screens become vivid and interactive: an animated background, the session count counting up, one real-life comparison, and "Do something real" choices (walk, read, water, stretch, call a friend, step outside) that lead Home with a short encouragement. A lifetime count of those choices may be shown; it is not a streak and nothing is lost by missing a day. Today and Insights gain a Perspective card that translates short-video time into measurable achievements (a named book by length, pages, km walked or cycled, runs, workouts, lessons) and swipes into thumb travel against landmarks, using fixed, stated assumptions. Passive pastimes (films, naps) are not offered as comparisons. Copy motivates without shaming: no "wasted", guilt, scores or streaks. Every exit, the countdown on "Keep watching" and reduced-motion behaviour stay as before.
- **ADR-116** 2026-11-18 — Tap-to-pause before breaks (extends ADR-077 at the maintainer's request: a break over a playing video is odd). TikTok and YouTube expose no Pause control but pause on a tap of the video. If playback progress is still, the video is treated as already paused and nothing is tapped; if progress is moving, the service taps the middle of the video once and counts the pause as confirmed only when progress then stops. Never a second tap, never Play; an unconfirmed pause still shows the smaller reminder. Breaks become a centred guided-breathing card (in 4 s, hold 2 s, out 6 s) without emojis.
- **ADR-117** 2026-11-18 — One parent key, plain-language Protection Lock, theme-coloured interventions, sentence case (refines ADR-113 and ADR-115 at the maintainer's request). Protection Lock holds exactly one app secret, a PIN **or** an app pattern, because two are easy to forget; saving one replaces the other, and records from earlier builds holding both keep working until either is changed. The screens call it the "parent key" and say it works only in this app and is not the phone's own unlock. Setup asks whether the child can unlock the phone with their own fingerprint or face: "Yes, or not sure" (the default) leaves fingerprint, face and phone-code shortcuts off; "No, only mine" turns fingerprint on. Strong biometrics are therefore off by default at setup (was on). Recovery phrases are presented as "backup phrases". Break and block screens take a deep gradient in the hue of the person's own accent or custom colour (a night blue for neutral accents) instead of fixed vivid presets. All UI text is sentence case (no forced Title Case), and sub-pages use a compact back link above the title.
- **ADR-118** 2026-11-18 — License: GPL-3.0-only (replaces Apache-2.0 from ADR-001 at the maintainer's request). The maintainer wants the code to stay open: anyone may use, change and redistribute it, but every copy and derivative must carry the same license with complete corresponding source, and nobody may relicense it under other terms. GPL-3.0-only (not "or later") keeps the exact license text fixed. The name and icon are not licensed (GPL-3.0 section 7(e)), so forks ship under their own name. Apache-2.0 and the other permissive third-party licenses in NOTICE are compatible with GPL-3.0. All prior commits are the maintainer's own, so no contributor consent was needed. The Play release remains the maintainer's own distribution of their copyright.
- **ADR-119** 2026-11-18 — Appearance templates apply at once (refines ADR-104 at the maintainer's request). Tapping Light, Dark, System or an accent preset saves and applies immediately, because every template is a readable, tested combination and a separate Save step for a one-tap choice felt broken. Free-form custom colours and layout still preview in a draft until Save appearance, so dragging the colour canvas never repaints the whole app with an unreadable midpoint. Quick successive taps queue, and the latest choice wins. The default theme stays System (follows the phone's dark setting).
- **ADR-120** 2026-11-19 — Fingerprint-first Protection Lock, step-by-step setup, one required backup phrase, 5/10/15 minute allowances (refines ADR-113 and ADR-117 at the maintainer's request). The phone's fingerprint is more convenient than any app secret, so a parent can choose fingerprint-only approval with no app PIN or pattern. This is offered only when the parent says the child cannot unlock the phone with their own fingerprint. The lock's strong key is invalidated by any new enrollment, which pauses approval until a backup phrase resets the lock to a parent key; face and the phone code never authorize changes. With a parent key, fingerprint (when allowed) is asked first and the key is one tap away. Apps cannot register their own fingerprints or tell which enrolled finger was used, so an app-specific finger is not possible. Setup is one decision per page and ends on a confirmation page. Backup phrases are 1 to 3 (was exactly 3), and earlier records keep their three. Covers offer 5, 10 or 15 more minutes (was 5).
- **ADR-121** 2026-11-19 — Protection Lock as two exclusive modes: phone fingerprint or app PIN (refines ADR-120 at the maintainer's request). The lock page shows two tabs, like the schedule views: **Phone fingerprint** and **App PIN**, so it is obvious which way approves and what the phone's own security has to do with it. A record is in exactly one mode; switching is one flow that proves the current mode first, and saving an app PIN or pattern from fingerprint mode turns the phone methods off in the same write. Fingerprint mode turns on with one touch: the parent taps once, the system prompt opens and the lock is on. A backup phrase is recommended afterwards but optional on Android 11 and later, because the phone's screen lock can re-arm fingerprint approval when no phrase is saved (anyone able to add a fingerprint already passed that screen lock); with a phrase saved, only the phrase re-arms it. Android 8 to 10 still asks for a phrase, as the screen lock cannot be asked alone there. Re-arming verifies the proof before a new fingerprint key exists. App PIN mode is set up step by step like a phone's own lock: choose a PIN or pattern, confirm it on its own page, then a backup phrase (still required). Records from earlier builds with a key plus phone shortcuts keep working and can turn the shortcuts off. Native pages and React Native screens lift the focused field above the keyboard (edge-to-edge windows are not resized by Android), and break and block covers fit the window on any height by leaving out optional lines first and then scaling every size, with touch targets kept at 44 dp or more.
- **ADR-122** 2026-11-19 — Instant PIN checks, a one-step allowance and pinned welcome actions (at the maintainer's request). App PIN, pattern and backup phrase verifiers use PBKDF2-SHA256 with 10,000 rounds (was 600,000, which made every check take seconds). The verifier record is already sealed by a Keystore key that never leaves the phone and attempts are throttled before each check, so the extra rounds only slowed an attacker who had both; a check now takes milliseconds. Each verifier stores its rounds; one saved by an earlier build is checked at 600,000 rounds once and replaced with a 10,000-round verifier for the same secret after the next correct entry, with no change to the lock's revision. Covers that grant time show one control, "− Allow 5 min +": minus, plus or a sideways swipe on the button change the minutes in steps of 5 (5 to 15), and tapping the button allows them, so extra time is one step instead of two. When a cover does not fit, it now also leaves out one and then both rows of real-life choices before scaling further; the two actions always stay on screen. The welcome pages keep their actions pinned above the navigation bar while the content scrolls.
- **ADR-123** 2026-11-19 — 1.1.0 is released from `main` as "Brainrot Shield", and GitHub Releases carry a signed APK (at the maintainer's request). Store builds keep a plain version name (1.1.0); Brainrot Shield is the release's name in notes and on GitHub, and the phone preview build's suffix is `-brainrot-shield` (was `-reels-preview`). Until 1.1.0 is published, the maintainer works on `main`, so the default branch shows the published app; `develop` resumes as the working branch afterwards (ADR-068). Each release is tagged `v<version>`, and its GitHub release attaches the arm64 APK signed with the upload key, with SHA-256 sums and the upload certificate's fingerprint, for people without Google Play. Play re-signs its installs with the Play app-signing key, so a GitHub install and a Play install have different signatures and Android will not update one with the other; switching means exporting a backup, uninstalling and restoring. Every GitHub APK uses the same upload key, so GitHub installs update from later GitHub releases.
- **ADR-124** 2026-11-19 — Welcome pages fit the window, and the first public release is 1.1.0 (11) with them (at the maintainer's request; replaces ADR-122's pinned welcome actions, which let content slide under them). Each welcome page measures itself against the window and steps down until it fits: spacing first, then text a little (to 80% at most), then optional parts: the help card on page 2; on page 3, later setup steps show only their title until they are next, step 1 keeps only its button, the Settings footnote goes, and Back shares a row with the finish action. The actions end the page and never cover content. If the smallest step still overflows (very large system text), the page scrolls with the actions at the end. The accessibility disclosure on page 3 is never shortened. An earlier 1.1.0 (11) build without this change was briefly on GitHub; Play never received it, so this build keeps 1.1.0 (11) and replaces it.

## Feasibility gates

Open questions that block or bound features, as of 18/11/26.

| Gate | Question | Status |
|---|---|---|
| F-01 | Per-app detection fidelity across versions, languages and routes | Open; fixtures plus live checks each release |
| F-02 | Android lifecycle and enforcement reliability (OEM restrictions, reboot, force-stop) | Open; best effort |
| F-03 | Latest-stack compatibility (React Native, Kotlin, Gradle) | Resolved for the 1.1.0 build |
| F-04 | Encrypted storage and key-loss recovery | Implemented; key loss is recoverable only from a backup |
| F-05 | Direct Google Drive API backup | Blocked by policy |
| F-06 | Document-picker provider durability | Open |
| F-07 | iOS capabilities | Not pursued (Android only) |
| F-08 | Accessibility API use under Play policy | Declared; reviewed each release |
| F-09 | Metric quality (opens, multi-window, gaps) | Open |
| F-10 | Per-app network accounting | Not implemented |
