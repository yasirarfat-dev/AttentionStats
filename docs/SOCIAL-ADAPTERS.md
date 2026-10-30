# Adding and fixing social-app support

**Status:** Version-independent adapters for Instagram, Facebook, YouTube, TikTok (incl. regional/lite) and Snapchat; 26 real-layout fixtures pass. Updated 2026-10-30.
**Responsibility:** How detection is organized, how to add an app, and how to turn a bug report into a regression test.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Social](SOCIAL-MEDIA-SPEC.md), [Decisions](DECISIONS.md#version-independent-detection-interventions-and-mvp-10--adr-066--2026-10-30), [Testing](TESTING.md), [Privacy](PRIVACY.md).
**Unresolved feasibility:** Snapchat has no captured fixtures yet; non-English Facebook feed labels rely on the structural sound-toggle signal.

## Pipeline

1. `android/app/.../detection/SnapshotReader.kt` copies the foreground window of a supported app into a value-only `ScreenSnapshot` (ids, classes, bounds, flags, labels). It is the only code that touches live accessibility nodes.
2. `native/reels-core/.../detection/social/<app>/<App>Adapter.kt` turns a snapshot into an `AdapterFrame`: surface (REELS / EXCLUDED / OUTSIDE), route (VIEWER or HOME feed), category (reel, photo, ad), the focused item's fingerprint and optional playback progress.
3. `native/reels-core/.../reels/CounterReducer.kt` (`EncounterTracker`) decides when a new item counts.
4. `ReelsCoordinator` stores encounters; `ReelsAccessibilityService` renders the counter, breaks and blocks.

Adapters are pure Kotlin with no Android imports (enforced by `tools/check_architecture.py`), so they run on any JVM.

## Shared building blocks (`detection/shared`)

- `ViewerLocator`: finds a full-screen short-video viewer. Known pager ids win; otherwise a pager ≥ 70 % of screen height whose single page fills it and whose right edge carries a contiguous stack of ≥ 3 small clickable controls. Pages containing a multi-row vertical list are feeds, not viewers.
- `FeedLocator`: picks the focused media in a feed (crossing the list's middle, or the most visible fully shown item) and fingerprints it with its header row.
- `Fingerprint`: normalizes labels (digits, punctuation, short toggle words removed) and hashes them with a device-local salt.
- `Sponsored`: ad markers (view-id hints, exact "Sponsored"/"Ad" labels in several languages).

## Adding an app

1. Add an entry to `ReelApp` (all package names) and an adapter in `detection/social/<app>/`; register it in `AdapterRegistry`.
2. Start with `ViewerLocator.locate(snapshot, setOf(<known pager ids>))`; add feed logic only if the app shows inline videos.
3. Add the packages to `res/xml/reels_accessibility_service.xml`, the manifest `<queries>`, and `src/platform/reelsSources.ts`.
4. Capture fixtures (below) for every entry route and at least two negatives (profile, inbox, search grid).

## Turning a report into a fixture

```powershell
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml raw.xml
python tools/sanitize_fixture.py raw.xml native/reels-core/src/test/resources/fixtures/<app>/<case>.xml
```

The sanitizer keeps layout and generic interface labels and replaces every human-written label with a stable synthetic token. Add the expectation to `FixtureTests.kt`, then run `python tools/android_build.py :reels-core:verifyReels`. Never commit unsanitized dumps.
