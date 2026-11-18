# Detection and metrics

How Attention Stats recognises short videos, what each number means, and where it can be wrong.

## Pipeline

```text
Accessibility event (supported app only)
  -> SnapshotReader        android/app/.../detection/SnapshotReader.kt
       copies the foreground window into a value-only ScreenSnapshot
       (view IDs, classes, bounds, flags, labels); the only code touching live nodes
  -> <App>Adapter          native/reels-core/.../detection/social/<app>/
       returns an AdapterFrame: surface, route (viewer or feed), category,
       in-memory item fingerprint, optional playback progress
  -> EncounterTracker      native/reels-core/.../reels/CounterReducer.kt
       decides when a new item counts and whether it is a revisit
  -> ReelsCoordinator      android/app/.../reels/ReelsCoordinator.kt
       writes encounters and timers to the encrypted store; drives counter,
       breaks and blocks in ReelsAccessibilityService
```

Adapters and the tracker are pure Kotlin with no Android imports (enforced by `tools/check_architecture.py`), so they are unit-tested on the JVM against real screen layouts.

## How an item is recognised

- **Viewer**: `ViewerLocator` uses a known pager view ID when the app exposes one; otherwise it looks for a vertical pager at least 70% of screen height whose page fills it and whose right edge holds a stack of three or more small clickable controls. A page that contains a multi-row list is a feed, not a viewer.
- **Feed**: `FeedLocator` picks the media item crossing the list's middle (or the most visible fully shown item) and fingerprints it with its header row.
- **Identity**: `Fingerprint` normalises on-screen labels (digits, punctuation, navigation words and short toggles such as Follow/Like removed) and hashes them with a device-local salt. Raw text is discarded immediately; the hash cannot be compared across devices.
- **Ads**: `Sponsored` matches ad markers in view IDs and exact labels ("Sponsored", "Ad", and translations in several languages).
- **Typing** (an editable field focused) and incomplete snapshots are excluded; nothing is read from them.

## Counting rules

| Rule | Behavior |
|---|---|
| Dwell | An item must stay focused 300 ms in a viewer or 450 ms in a feed before it counts, filtering fling-throughs |
| First item | The first item after entering a supported screen counts after its dwell; no swipe needed |
| New item | A changed fingerprint counts if the app scrolled, or if the item's metadata is mostly different (auto-advance) |
| Same item | Control changes (like, follow, mute, expanded caption) keep the same item |
| Revisit | Returning to an item already seen in this unlock session is recorded as a revisit, not a new encounter |
| Session | The unlock session runs from unlock to screen-off across all supported apps; app switches do not reset it |
| Loops | A video looping or pausing on screen stays one encounter |

The tracker remembers up to 600 items per session in memory only.

## Supported sources

Package names: Instagram `com.instagram.android`; Facebook `com.facebook.katana`; YouTube `com.google.android.youtube`; TikTok `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, `com.zhiliaoapp.musically.go`; Snapchat `com.snapchat.android`.

| Source | Counted screens | Classification | Not counted | Auto-scroll |
|---|---|---|---|---|
| Instagram | Reels viewer from any entry (tab, Home, Explore, profiles, shares); videos and photos in Home/profile feeds | Video, photo/carousel, ad | Stories, Direct, Explore grid | Experimental; earlier passes, intermittent; badge-qualified ad skip |
| Facebook | Reels tab and Reels opened from Home; feed videos, photos and albums | Video, photo, ad, standalone Reels ad | Marketplace, ordinary text posts | Usually unavailable (no playback progress, ADR-110); optional standalone-ad skip |
| YouTube | Shorts player from any entry (tab, Home shelf, search, channel, links) | Video, image post (photo), text post, ad | Long-form watch page, Home feed | Experimental; seek-bar progress (ADR-114); badge-qualified ad skip |
| TikTok | The vertical feed from any entry (For You, Following, search, profiles, shares) | Video, photo post, ad | Inbox, profile grid | Experimental; verified bounded runs on 47.7.3; badge-qualified ad skip |
| Snapchat | Spotlight (geometry only; resource names are obfuscated) | Video, ad | Stories, chat, map | Unavailable (no progress exposed) |

Support depends on app version, language and layout. Fixtures, not claims, define what is supported. A screen an adapter cannot recognise is unknown: nothing is counted and no zero is invented.

## Metrics

| Metric (UI label) | Definition | Source |
|---|---|---|
| Video encounters | Settled short videos newly seen this unlock session; recognised ads are included only if "Count ads as videos" is on | Accessibility |
| Photos encountered | Recognised photos and carousels; one carousel post is one encounter. Separate switch | Accessibility |
| Ads encountered | Recognised ads, always tallied separately (may overlap video/photo) | Accessibility |
| Revisits | Returns to an item already seen in the session; excluded from totals | Accessibility |
| This session | Encounters since the last unlock, across all supported apps | Accessibility (memory) |
| On screen / video surface time | Time a recognised short-video surface was on screen. Not watch time: paused or ignored videos still count as on screen | Accessibility sampling |
| Recorded app time | Foreground time per app from Android usage events | Usage Access |
| App opens | Observed entries into an app (returns after another app or a lock count); not process launches | Usage Access |
| Breaks / reminders recorded | Break screens shown and the choice made (continue or leave) | App |
| Hourly encounters | Encounters per local hour | Accessibility |

Video time is a subset of app time and is never added to it. Because the two come from different Android sources with different delays, video time can briefly exceed app time; Today then marks app time "(incomplete)".

### Playback metrics (hidden for new users)

Three-second watches, observed completions, confirmed playback and measured visits exist in the schema but are not offered for new activation in 1.1.0 (ADR-109). If they were enabled on an earlier build, Settings keeps a disable-only switch and recorded values stay visible. `PlaybackEvidence` only accepts advancing numeric position/duration from the player: a three-second watch needs at least 3,000 ms of observed advance in one visit, and a completion needs a continuous run from near the start to near the end. Pauses, seeks, buffering and missing samples never fill time.

### Not measured

Text posts, likes, reactions, comments, what a video is about, who posted it, and anything you type.

## Auto-scroll signal

Auto-scroll (see [FEATURES](FEATURES.md#auto-scroll-experimental)) reads only numeric player progress, and only after the person allows it: range data, a clock pair ("0:29 / 1:19") or, on a seek bar or progress bar only, a spoken English description ("29 seconds of 1 minute 19 seconds"). A video counts as finished after a near-end-to-start loop, a loop the sampler stepped over (the observed playback rate predicts ≥ 0.97 just before the position returns below 0.10), or two terminal samples (≥ 0.999), each following at least eight small advances over two seconds. Explicitly classified photos advance after five seconds. There is no timer fallback for videos: missing progress means waiting. Before each swipe the app re-checks the app, window, item, consent, lock state and visible counter, and requires arrival on a different item within 2.5 seconds.

## Known limits

- Layout changes in an app update can stop or skew counting until an adapter is fixed.
- Repeated or missing captions can merge or split items; a recycled feed slot can be misread.
- Non-English layouts rely on structural signals; photo detection needs positive evidence.
- Android or the phone maker may stop the accessibility service; the app shows a disconnected state.
- Counts are local estimates, not a record of what was watched.

## Testing detection: fixtures first

Every supported screen and the important negatives (profile, inbox, search grid, stories) have a sanitised layout under `native/reels-core/src/test/resources/fixtures/<app>/`, asserted in `FixtureTests.kt`.

To turn a missed screen into a regression test:

```powershell
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml raw.xml
python tools/sanitize_fixture.py raw.xml native/reels-core/src/test/resources/fixtures/<app>/<case>.xml
python tools/android_build.py :reels-core:verifyReels
```

The sanitiser keeps layout (classes, view IDs, bounds, flags) and generic interface labels, and replaces every human-written label with a stable synthetic token. Never commit an unsanitised dump. Fix the adapter until the fixture passes, then confirm on a real phone.

## Adding an app

1. Add a `ReelApp` entry with all package names and an adapter in `detection/social/<app>/`; register it in `AdapterRegistry`.
2. Start from `ViewerLocator.locate(snapshot, setOf(<known pager ids>))`; add feed logic only if the app shows inline videos.
3. Add the packages to `res/xml/reels_accessibility_service.xml`, the manifest `<queries>` and `src/platform/reelsSources.ts`.
4. Add fixtures for every entry route and at least two negatives.
5. Update the in-app disclosure, [PRIVACY](PRIVACY.md), the public privacy policy and the Play accessibility declaration before release.
