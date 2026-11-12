# Attention Stats — Google Play Console information

Reviewed preparation draft for **attention.stats**, updated 12 November 2026. The reported closed beta is 1.0.0 (versionCode 10); the Console artifact/source mapping is not independently verified. A historical copy lives in the maintainer's private release folder (outside the repository). This repository copy does not update Console or that private archive.

**Production hold:** resolve the [release gates](../docs/RELEASE.md#readiness-assessment-2026-11-07) on a concrete candidate before submission. The development branch adds Android-managed backup, schedules and optional auto-scroll; its listing, Data safety answers, accessibility declaration and reviewer video must describe the actual uploaded binary. Do not copy the historical no-transmission answers or claim every route is qualified. Source still declares 1.0.0 / 10; choose the next unused version code only after inspecting Console.

## App details

| Field | Value |
|---|---|
| App name | Attention Stats |
| Package | attention.stats |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free (no in-app purchases, no ads) |
| Category | Productivity (alternative: Health & Fitness) |
| Tags | Digital wellbeing, Productivity, Self-improvement |
| Email | consciousbiobot@gmail.com (replace with a dedicated Convenience Labs support inbox when created) |
| Developer / account | Convenience Labs (https://play.google.com/store/apps/developer?id=Convenience+Labs) |
| Website | https://yasirarfat.com (creator's site; the source link is in the description) |
| Privacy policy | https://github.com/yasirarfat-dev/AttentionStats/blob/main/policies/PRIVACY.md |

## Store listing

**Title** (≤ 30):

```text
Attention Stats
```

**Short description** (≤ 80):

```text
Count Reels, Shorts & TikToks. Breaks and blocks you choose. Private, no ads.
```

**Full description** (≤ 4000):

```text
How many short videos did you scroll through today?

Attention Stats estimates short-video encounters on supported screens in Instagram, Facebook, YouTube Shorts, TikTok and Snapchat Spotlight, and shows local activity summaries. Choose your own breaks and limits. Free, open source, no ads, and no developer account or telemetry.

COUNTS ON SUPPORTED SCREENS
• Recognises supported short-video players and some Instagram and Facebook feed layouts
• Support varies with app version, language and screen; missed or misclassified items are possible
• Returning to a recognised item within an unlock session is treated as a revisit
• An encounter counts a visible item; it does not prove that you watched it
• Ads and photos are counted separately, and you choose whether ads count

A LIVE COUNTER THAT STAYS OUT OF THE WAY
• A small, see-through counter shows this unlock and today
• A ring fills as you get closer to your break
• Drag it anywhere, tap it for details, hide it for a visit

SEE YOUR DAY CLEARLY
• Short videos and watching time per app, with each app's logo
• Screen time and observed app opens when you allow Usage Access
• Your daily rhythm by hour, and 7- and 30-day trends
• What that time could have been: pages read, kilometres walked

GENTLE TOOLS YOU CHOOSE
• Mindful breaks: a calm, full-screen breather after the number of videos or minutes you pick
• Daily limits: stop short videos in an app after your chosen number
• Blocks: turn off Reels or Shorts in one app while everything else keeps working
• Home-screen widget with today's count

PRIVATE BY DESIGN
• No developer account, servers, ads or telemetry
• Local history is encrypted on your device; delete it any time
• The app has no Internet permission. Android backup and your chosen file provider can still transfer data
• Android-managed backup starts on unless you saved an off choice; turn it off in Settings
• Backup files use a public app key, which provides no secrecy to anyone holding the file. Protect external copies
• 100% open source, so anyone can check: github.com/yasirarfat-dev/AttentionStats

HOW IT WORKS
With your consent, Attention Stats uses Android's Accessibility service to recognise supported short-video screens and distinguish items. Labels are processed in memory and discarded; local records keep encounter facts, not captions, names, messages or typed input. User-chosen protection can cover selected apps or leave a blocked player with Back. Optional session auto-scroll swipes only when its supported playback or photo signal qualifies; it may remain unavailable. Usage Access (optional) adds screen time per app.

Attention Stats is not a medical app and does not diagnose anything. It simply helps you see your habits and choose your own limits.
```

**Graphics** (in `<release folder>/store\en-US\graphics`): `icon-512.png`, `feature-graphic-1024x500.png`; phone screenshots in `store\en-US\screenshots`.

## App content

| Section | Answer |
|---|---|
| App access | All functionality is available without special access (no login). |
| Ads | No, the app does not contain ads. |
| Content rating | Questionnaire category "Utility, Productivity, Communication or Other"; no violence, sexual content, gambling, user-generated content sharing or location sharing → expected rating Everyone / PEGI 3. |
| Target audience | 13–15, 16–17 and 18+ (not designed for children under 13). |
| News app | No. |
| Government app | No. |
| Financial features | None. |
| Health | Not a health/medical app (wellbeing habits only). |

### Data safety

- **Collection/sharing and transit encryption:** pending candidate-specific review. The old no-transmission rationale cannot be reused for the development build: Android-managed backup and picker providers can transfer history without an Internet permission in this app. Check applicable exceptions and provider handling against [Google's Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), then record the exact answers for the selected artifact. No final answers or Console changes are made here.
- **Deletion:** in-app **Settings → Delete all history** removes local history and disables future app backup participation. Uninstalling removes local data; neither action guarantees deletion of external files or provider-held backup copies. See the [deletion policy](../policies/DATA-DELETION.md).

### Sensitive permissions and APIs

**Accessibility API (AccessibilityService, isAccessibilityTool = false)**. Use the permission declaration form:

- Core functionality: *Counting short videos (Reels, Shorts, TikTok, Spotlight) the user watches and showing a live counter, user-chosen break reminders and user-chosen blocks of short-video players.*
- Description to paste:

```plain
Attention Stats is a digital wellbeing app. With affirmative source consent, its AccessibilityService reads foreground screen structure and labels in selected Instagram, Facebook, YouTube, TikTok and Snapchat screens to estimate encounters and visible time. Labels are processed in memory and discarded; encounter facts are stored locally. It draws the optional counter and user-configured break reminders. User-chosen blocks can leave a short-video player with Back. Selected app/group schedules inspect foreground package ownership only and show a cover with an immediate allowance. Separately disclosed session auto-scroll uses a visible Start/Stop control and deterministic qualifying playback/photo signals for a single upward swipe; unsupported signals suspend actions. It never likes, comments, follows or messages. The app has no Internet permission or developer telemetry. Android-managed backup and user-selected file providers may transfer logical history under the disclosed backup choices. In-app accessibility disclosure and Android service enablement are separate from source and convenience choices.
```

- Reviewer video: demonstrate the actual candidate's prominent disclosure, Android enablement, supported counter, break and safe escape, schedule allowance and separately disclosed auto-scroll Start/Stop if shipped. Use synthetic history and no private source content. Keep recording/upload outside this preparation task. Changed API uses require an accurate updated declaration and video; see [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

**Usage access (PACKAGE_USAGE_STATS)**: optional screen-time feature; granted by the user in system settings after an in-app explanation.

## Release

| Field | Value |
|---|---|
| Track | Closed testing (then production after the 14-day / 12-tester requirement for new personal accounts) |
| App signing | Use Google Play App Signing (Google generates the app key); upload with the local upload key in `<release folder>/private\signing` |
| Bundle | `<release folder>/releases\1.0.0+10\android\attention-stats-1.0.0-10.aab` |
| Release name | 1.0.0 (10) |

Release notes (≤ 500):

```text
Attention Stats estimates short-video encounters on supported screens, shows local activity summaries and offers breaks and blocks you choose. No developer account, ads or telemetry. Support varies by app version and layout. Review Settings for backup choices and data handling. Report detection issues through Settings → Report a problem.
```

## Testers

New personal developer accounts need at least 12 testers opted in for 14 continuous days before applying for production. Share the closed-test opt-in link from Console with friends who use the supported apps, and ask them to use **Settings → Report a problem** for anything missed. Never fabricate engagement.

## Production-access application preparation — 12 November 2026

The maintainer confirms the uploaded beta is 1.0.0 / versionCode 10 and expects the test period to finish on 13 November; this is a reported date, not verified Console eligibility. [Current Google guidance](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en) requires eligible new personal accounts to have at least 12 continuously opted-in testers for the preceding 14 days at application time. The application asks about testing, engagement, feedback, changes and readiness; review usually takes seven days or less but can take longer. Access approval and a production rollout are separate steps.

Prepare these facts privately before applying:

| Item | Evidence still needed |
|---|---|
| Eligibility | Console's eligible action, continuous tester count/dates and account-specific requirements |
| Tested build | Actual track/version code, artifact SHA-256 and source mapping; do not substitute local preview results |
| Engagement and feedback | Real tester participation, tested journeys, feedback channels and recurring problems; exclude tester identities from Git |
| Changes made | Link fixes to feedback and identify which build testers actually received; develop-only fixes are not beta-tested fixes |
| Readiness | Accurate supported scope, remaining limitations and applicable candidate checks; current assessment remains on hold |
| Declarations | Candidate-matched privacy/Data safety, accessibility form/video and outstanding Console warnings |

No application answers about tester engagement or readiness are invented here. The historical release table above is not a new upload instruction.
