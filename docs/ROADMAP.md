# Roadmap

Planned work after 1.1.0, most valuable first within each group. Nothing here is built or promised by a date; each item ships only with fixtures, phone checks and, where it changes what the app reads or does, an updated privacy policy, Data safety answers and accessibility declaration. Decisions that bind these plans are in [DECISIONS](DECISIONS.md); the current state is in [STATUS](STATUS.md).

## Next release candidates

| Idea | Why it matters | Notes |
|---|---|---|
| **Pause before opening** | A short, calm pause when a chosen short-video app opens ("Open anyway" after a few breaths, or pick something real). Interrupting the automatic open is the single strongest habit lever and works before the first video, not after the tenth | Uses the existing foreground detection and cover; changes accessibility behavior, so it needs an updated declaration and reviewer video |
| **Daily budget and streaks** | One short-video budget across all apps, with a quiet streak of days under it and a weekly best. Turns counts into a goal people can win | Local only; builds on Today and Insights |
| **Weekly reflection** | A Sunday recap on device: this week against last, the heaviest hour and app, and one suggestion (a time slot or a break setting) applied with one tap | An optional notification needs `POST_NOTIFICATIONS` and its own consent |
| **One-tap presets** | Ready-made Bedtime, Study and Work time slots and a Quick Settings tile that starts a focus session or hides the counter | Presets are ordinary groups and slots the person can edit |
| **Languages** | Translated app text (Bengali, Hindi, Spanish, Portuguese and Indonesian first) and detection fixtures for those languages | Detection is qualified per language; untested languages keep the current warning |
| **Parent summary** | A Protection Lock-protected page with the week's totals and an on-device note when the accessibility service was turned off | No accounts or remote reporting; stays on the phone |

## Detection and sources

- Re-run the Instagram Reels tab, Facebook auto-scroll and TikTok regional and Lite builds on the current release.
- More sources and entry routes (for example short videos inside Reddit, X, LinkedIn and Pinterest), each with fixtures and live checks.
- Exact per-video watch time, and 3-second, partial and full completion counts (deferred by the maintainer; playback measurement stays hidden for new users, ADR-109).
- Non-English seek-bar readings for YouTube auto-scroll.

## Quality and platform

- R8 minification for release builds.
- Seven-day battery and CPU baseline (ADR-112).
- Reboot, time-zone and daylight-saving qualification for schedules and focus sessions.
- TalkBack and 200% text on the redesigned break and block cards; covers on a physically short phone.
- Tablet and foldable layouts.

## Data and backup

- Selective export scopes and summary-only archives (ADR-026), and a shareable summary for a coach or parent.
- Per-app network usage, notification metadata and other usage families (ADR-085), each behind its own consent.
- App Activity screen (camera, microphone and network context): needs feasibility work, as most of it has no public Android API for other apps.
- Future multi-device merge using the recorded fact origins (ADR-050, ADR-074).

## Not planned

iOS, web, desktop or browser-extension clients (ADR-024), direct Google Drive backup (F-05), cloud accounts, ads or analytics.
