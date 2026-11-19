# Release

How a release is built, checked and published, plus the Google Play Console text. The four `text` blocks below are read by `tools/release_candidate.py` (title, short description, full description, release notes, in that order) and checked against Play's length limits; keep exactly four.

## Release rules

- Maintainers own the application ID (`attention.stats`), signing keys, Play Console and rollout decisions. Contributors never commit keystores, passwords or signed artifacts.
- Never reuse or decrease a `versionCode`. Check Play Console for the highest used code before choosing the next one.
- A merge to `main` needs the maintainer's explicit agreement to the concrete candidate (ADR-068).
- From 1.1.0 every release must migrate earlier production data (ADR-108).
- Listing, Data safety, accessibility declaration and privacy policy must describe the exact uploaded build.
- If a detector breaks after an app update, ship a reviewed fix; never change collection remotely.

## Signing

Play App Signing holds the app-signing key; a separate upload key signs the bundle (ADR-064).

1. Once per maintainer: `python tools/release_signing.py provision` creates the upload key and a protected configuration outside the repository (default under the user's `AppReleases/AttentionStats/private/signing`, or `ATTENTION_RELEASE_ROOT`). It refuses to overwrite an existing key. Keep an independent encrypted backup of the key.
2. The build reads credentials through `--signing-config <path-to-upload-signing.json>`, which `tools/android_build.py` accepts only with release tasks. Without it, release builds are unsigned inspection builds.

## Build and audit

From a clean tree on the release commit, with `versionCode`/`versionName` set in `android/app/build.gradle`:

```powershell
pnpm install --frozen-lockfile --ignore-scripts
python tools/check_mobile.py --installed
pnpm typecheck; pnpm lint; pnpm format:check; pnpm test
python tools/android_build.py :reels-core:verifyReels :protection-core:verifyProtection :analytics-core:verifyAnalytics
python -m unittest discover -s tools/tests

# Unsigned inspection build and static audit
python tools/android_build.py :app:assembleRelease :app:bundleRelease :app:lintRelease --arch arm64-v8a --workers 2
python tools/audit_current_release.py --apk android/app/build/outputs/apk/release/app-release-unsigned.apk `
  --aab android/app/build/outputs/bundle/release/app-release.aab --version-name 1.1.0 --version-code 11

# Signed build and signed audit
python tools/android_build.py :app:bundleRelease :app:assembleRelease --signing-config <upload-signing.json>
python tools/verify_signed_release.py --profile current --certificate <upload-cert> `
  --version-name 1.1.0 --version-code 11 --archive <new-folder-outside-repo>
```

The audits check package and version, merged manifest permissions and exported components, compiled backup rules, matching native libraries and 16 KB page alignment. They are static checks; they do not replace testing the build on a phone.

## GitHub release

Each release is also published on GitHub for people without Google Play (ADR-123). Tag the release commit `v<version>` and attach the upload-signed APK from the signed build, renamed `attention-stats-<version>-<code>-arm64.apk`, with a `SHA256SUMS.txt`. The notes give the release name, what changed, the upload certificate's SHA-256 (`6c7606a45a21074193c0e4d5d40daa13eb7d3f12a59ac555aef3efde8c0d1168`) and this warning: Play installs are signed by Google's app-signing key, so a GitHub install and a Play install cannot update each other; to switch, export a backup, uninstall and restore.

```powershell
git tag -a v1.1.0 -m "Attention Stats 1.1.0"; git push origin v1.1.0
gh release create v1.1.0 <apk> <SHA256SUMS.txt> --title "Attention Stats 1.1.0 · Brainrot Shield" --notes-file <notes.md>
```

`python tools/release_candidate.py prepare` copies this file, the policies and the store text into the external release folder; `archive` stores an unsigned candidate there. Release folders are never inside the repository.

## Before upload

- [ ] Host checks above pass; release lint has no errors.
- [ ] Install the release build over the previous production version: history, choices and keys survive.
- [ ] Spot-check counting on each supported app, a break, a block cover and its escape routes, and Protection Lock.
- [ ] Listing, Data safety answers, accessibility declaration and reviewer video match the build.
- [ ] Release notes updated; [STATUS](STATUS.md) updated.
- [ ] Maintainer agreement to merge `develop` into `main`; tag the release commit.
- [ ] Publish the GitHub release (below).

## Google Play Console

Package **attention.stats**, release 1.1.0 (11). The closed test was 1.0.0 (10); production access has been granted.

### App details

| Field | Value |
|---|---|
| App name | Attention Stats |
| Package | attention.stats |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free (no in-app purchases, no ads) |
| Category | Productivity (alternative: Health & Fitness) |
| Tags | Digital wellbeing, Productivity, Self-improvement |
| Email | consciousbiobot@gmail.com |
| Developer account | Convenience Labs |
| Website | https://yasirarfat.com (creator's site; the source link is in the description) |
| Privacy policy | https://github.com/yasirarfat-dev/AttentionStats/blob/main/policies/PRIVACY.md |

### Store listing

Title (≤ 30):

```text
Attention Stats
```

Short description (≤ 80):

```text
Count Reels, Shorts & TikToks. Breaks and blocks you choose. Private, no ads.
```

Full description (≤ 4000):

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

SEE YOUR HISTORY CLEARLY
• Today: short-video encounters and time on short-video screens per app, with each app's logo
• Screen time and observed app opens when you allow Usage Access
• Week, Month or All Time insights, a calendar for any day, per-app history and hourly rhythm
• Perspective: what your scrolling time could have been, from pages of a book to kilometres of walking, and how far your thumb has scrolled
• Home-screen widgets in several layouts, each configurable

GENTLE TOOLS YOU CHOOSE
• Mindful breaks: a lively full-screen break with real-life choices (walk, read, call a friend) after the number of videos or minutes you pick; the video is paused first where the app allows it
• Per-app limits: a daily short-video limit, or always leave Reels or Shorts in a chosen app
• App groups and time slots: cover chosen apps during recurring periods, or allow only the apps you pick
• Focus sessions from 5 minutes to 3 hours
• Covers keep Android Settings, Home and emergency calls available and offer a 5, 10 or 15 minute allowance
• Optional Protection Lock: approve with the phone's fingerprint (one touch, if your child's is not saved on the phone) or an app PIN or pattern, for the changes you choose to protect, allowances and erasing history

PRIVATE BY DESIGN
• No developer account, servers, ads or telemetry
• Local history is encrypted on your device; erase it any time
• The app has no Internet permission. Android backup and your chosen file provider can still transfer data
• Android-managed backup starts on unless you saved an off choice; turn it off in Settings → Backup & restore
• App PINs, patterns, backup phrases and biometric keys stay on this device and are never backed up
• Backup files use a public app key, which provides no secrecy to anyone holding the file. Protect external copies
• 100% open source under GPL-3.0, so anyone can check: github.com/yasirarfat-dev/AttentionStats

HOW IT WORKS
With your consent, Attention Stats uses Android's Accessibility service to recognise supported short-video screens and distinguish items. Labels are processed in memory and discarded; local records keep encounter facts, not captions, names, messages or typed input. User-chosen protection can cover selected apps or leave a blocked player with Back. Optional session auto-scroll swipes only when its supported playback or photo signal qualifies; it may remain unavailable. Usage Access (optional) adds screen time per app.

Attention Stats is not a medical app and does not diagnose anything. It simply helps you see your habits and choose your own limits.
```

Graphics (kept in the external release folder under `store/en-US/`): `icon-512.png`, `feature-graphic-1024x500.png` and phone screenshots. Generate icons with `tools/make_brand_assets.py`.

### App content

| Section | Answer |
|---|---|
| App access | All functionality is available without special access (no login) |
| Ads | No ads |
| Content rating | Category "Utility, Productivity, Communication or Other"; no violence, sexual content, gambling, user-generated content sharing or location sharing; expected Everyone / PEGI 3 |
| Target audience | 13–15, 16–17 and 18+ (not designed for children under 13) |
| News / government / financial | No / No / None |
| Health | Not a health or medical app (wellbeing habits only) |

### Data safety

- **Collection, sharing and encryption in transit:** answer for the exact uploaded build. Android-managed backup and file-picker providers can transfer history even though the app has no Internet permission; check the applicable exceptions in [Google's Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469) and record the final answers in the Console.
- **Deletion:** in-app **Settings → Erase history** removes local history and turns off future app backups. Uninstalling removes local data. Neither deletes exported files or provider-held backup copies. See the [deletion policy](../policies/DATA-DELETION.md).

### Sensitive permissions and APIs

**Accessibility API** (AccessibilityService, `isAccessibilityTool = false`), permission declaration form:

- Core functionality: *Counting short videos (Reels, Shorts, TikTok, Spotlight) the user watches and showing a live counter, user-chosen break reminders and user-chosen blocks of short-video players.*
- Description:

```plain
Attention Stats is a digital wellbeing app. With affirmative in-app consent, its AccessibilityService reads foreground screen structure and labels in Instagram, Facebook, YouTube, TikTok and Snapchat to estimate short-video encounters and time on those screens. Labels are processed in memory and discarded; only encounter facts are stored locally. It draws the optional counter and user-configured break reminders; just before a break screen the user enabled, it may pause the video with one tap, on the source app's visible Pause control or, in TikTok and YouTube, on the video itself after confirming from playback progress that it is playing; the pause counts only when progress stops (never a second tap, never Play), otherwise it only shows the reminder. User-chosen blocks can press Back to leave a short-video player. For app/group time slots and focus sessions the person sets up (including Allow Only), it checks foreground package ownership only and shows a cover; system navigation, Settings, launcher, keyboard and emergency/dialer stay available and a 5, 10 or 15 minute allowance is offered (an optional Protection Lock approval can be required). Separately allowed auto-scroll uses a visible Start/Stop control and a deterministic qualifying playback/photo signal for one upward swipe; an additional separate choice swipes past pages that carry the source app's own Sponsored/Ad label, without opening ads; unavailable signals suspend actions. It never likes, comments, follows or messages. The app has no Internet permission or developer telemetry; Android-managed backup and user-selected file providers may transfer logical history under disclosed backup choices.
```

- Reviewer video: show the in-app disclosure, enabling the service in Android Settings, the counter on a supported app, a break and its exit, a schedule cover with its allowance, and auto-scroll Start/Stop. Use synthetic history and no private content. Any change in accessibility behavior needs an updated declaration and video ([policy](https://support.google.com/googleplay/android-developer/answer/10964491)).

**Usage access** (`PACKAGE_USAGE_STATS`): optional screen time, granted by the user in system settings after an in-app explanation.

**`USE_BIOMETRIC` / `USE_FINGERPRINT`** (normal permissions from AndroidX Biometric): optional Protection Lock unlock; the app receives only an authentication result, never biometric data.

### Release

| Field | Value |
|---|---|
| Track | Production |
| App signing | Play App Signing; upload with the local upload key |
| Bundle | `attention-stats-1.1.0-11.aab` from the external release folder |
| Release name | 1.1.0 (11) Brainrot Shield |

Release notes (≤ 500):

```text
First public release. Counts short videos on Instagram, Facebook, YouTube Shorts, TikTok and Snapchat. New: lively break screens with real-life choices, Perspective (what your scrolling time could have been), Week/Month/All Time insights, app groups, time slots, focus sessions, Allow Only, Protection Lock with fingerprint or app PIN, backup and restore, widgets and themes. Auto-scroll now supports YouTube Shorts and can skip ads. No ads, account or telemetry.
```
