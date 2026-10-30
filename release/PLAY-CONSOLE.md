# Attention Stats — Google Play Console information

Everything to paste into Play Console for **attention.stats** 1.0.0 (versionCode 10). Updated 30 October 2026. A copy lives at `C:\home\biobot\AppReleases\AttentionStats\PLAY-CONSOLE.md` next to the signed bundle.

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
How many Reels did you really watch today?

Attention Stats counts the short videos you scroll through on Instagram, Facebook, YouTube Shorts, TikTok and Snapchat Spotlight, and shows how you spend time on your phone. Then it gives you gentle tools to take back your attention. Free, open source, no ads, and nothing ever leaves your phone.

COUNTS EVERY SHORT VIDEO
• Reels, Shorts, TikToks and Spotlight, however you reach them: the Reels tab, a video tapped in your feed, Explore, profiles or shared links
• Videos in your Instagram and Facebook feeds count too
• Each video counts once: scrolling back is a revisit, not a new count
• Ads and photos are counted separately, and you choose whether ads count

A LIVE COUNTER THAT STAYS OUT OF THE WAY
• A small, see-through counter shows this unlock and today
• A ring fills as you get closer to your break
• Drag it anywhere, tap it for details, hide it for a visit

SEE YOUR DAY CLEARLY
• Short videos and watching time per app, with each app's logo
• Screen time and opens for every app
• Your daily rhythm by hour, and 7- and 30-day trends
• What that time could have been: pages read, kilometres walked

GENTLE TOOLS YOU CHOOSE
• Mindful breaks: a calm, full-screen breather after the number of videos or minutes you pick
• Daily limits: stop short videos in an app after your chosen number
• Blocks: turn off Reels or Shorts in one app while everything else keeps working
• Home-screen widget with today's count

PRIVATE BY DESIGN
• No account, no servers, no ads, no analytics
• The app does not request Internet access, so your data cannot leave your phone
• History is encrypted on your device; delete it any time
• 100% open source, so anyone can check: github.com/yasirarfat-dev/AttentionStats

HOW IT WORKS
Attention Stats uses Android's Accessibility service to recognise short-video screens in the five supported apps and tell one video from the next. It processes the screen in memory and never records, stores or sends what you watch, type or message. Usage Access (optional) adds screen time per app.

Attention Stats is not a medical app and does not diagnose anything. It simply helps you see your habits and choose your own limits.
```

**Graphics** (in `AppReleases\AttentionStats\store\en-US\graphics`): `icon-512.png`, `feature-graphic-1024x500.png`; phone screenshots in `store\en-US\screenshots`.

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

- **Does your app collect or share any of the required user data types?** No. (All processing happens on the device and nothing is transmitted; Google's definition of "collection" covers data transmitted off the device.)
- **Is all data encrypted in transit?** Not applicable; no data is transmitted.
- **Do you provide a way for users to request that their data is deleted?** Yes: in-app **Settings → Delete all history**, and uninstalling removes everything.

### Sensitive permissions and APIs

**Accessibility API (AccessibilityService, isAccessibilityTool = false)**. Use the permission declaration form:

- Core functionality: *Counting short videos (Reels, Shorts, TikTok, Spotlight) the user watches and showing a live counter, user-chosen break reminders and user-chosen blocks of short-video players.*
- Description to paste:

```plain
Attention Stats is a digital wellbeing app. With the user's consent, its AccessibilityService reads the screen structure and on-screen labels of five apps only (Instagram, Facebook, YouTube, TikTok, Snapchat), only while they are in the foreground, to detect when a short-video player or a feed video is visible and to distinguish one video from the next. This powers the core feature: counting short videos and time spent watching them. The service also draws an overlay counter, shows break reminders the user configures, and, only when the user enables blocking for an app, performs a Back action to leave that app's short-video player. All processing is on-device; no screen content is stored or transmitted (the app has no Internet permission). A prominent in-app disclosure explains this and requires explicit agreement before any data is accessed, and the user must separately enable the service in Android settings.
```

- Video: record a 30–60 s screen capture showing: onboarding disclosure → "I agree" → Accessibility settings → enabling the service → counter on Instagram Reels → a break screen → Settings → Delete all history. Upload it unlisted (e.g. YouTube) and paste the link.

**Usage access (PACKAGE_USAGE_STATS)**: optional screen-time feature; granted by the user in system settings after an in-app explanation.

## Release

| Field | Value |
|---|---|
| Track | Closed testing (then production after the 14-day / 12-tester requirement for new personal accounts) |
| App signing | Use Google Play App Signing (Google generates the app key); upload with the local upload key in `AppReleases\AttentionStats\private\signing` |
| Bundle | `AppReleases\AttentionStats\releases\1.0.0+10\android\attention-stats-1.0.0-10.aab` |
| Release name | 1.0.0 (10) |

Release notes (≤ 500):

```text
First release of Attention Stats: count Reels, Shorts, TikToks and Spotlight from any screen, see screen time per app, and use gentle mindful breaks, daily limits and per-app blocks. Private by design: no account, no ads, no Internet access, open source. Found a screen that isn't counted? Use Settings → Report a problem.
```

## Testers

New personal developer accounts need at least 12 testers opted in for 14 continuous days before applying for production. Share the closed-test opt-in link from Console with friends who use the supported apps, and ask them to use **Settings → Report a problem** for anything missed. Never fabricate engagement.
