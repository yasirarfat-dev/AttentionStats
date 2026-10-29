# Attention Stats closed-test guide

The closed test is being prepared. There is no live Play opt-in link or launch date yet. App package: **attention.stats**. Only install the maintainer's reviewed Play closed-test release when available; development APKs do not start Play's testing period.

## What the candidate does

The experimental Instagram adapter estimates settled page exposures in the dedicated Reels viewer on an explicitly supported installed version. A photo/carousel, recognized image ad or unclassified page can be counted separately. A return to a page adds another exposure; a redraw or playback loop on the same settled page should not. These are not unique-video or completed-view statistics. An optional compact display can show counts over a qualified viewer.

The development preview also has experimental Facebook, YouTube Shorts and TikTok candidates. Narrow trials do not establish all-route support or a Play-ready four-app counter. Inline Home-feed counting, time tracking, distinct/repeat breakdowns, broader history, protection, backup and restore remain unavailable. Unsupported app versions may report **Needs verification**. Do not enable an unsupported source expecting it to count. Final closed-test instructions must match the exact qualified release.

## Testing steps

1. Read the [privacy policy](PRIVACY.md). Open the app's Reels screen, read the separate observation disclosure and choose whether to enable a supported source. Access is optional.
2. If you consent, enable Attention Stats through Android accessibility settings. It is a general wellbeing counter, not an accessibility tool for a disability. Observation never starts scrolling. The separate experimental Convenience option requires its own session Allow and explicit viewer Start before it can perform a bounded upward swipe after an observed playback loop. Stop remains visible; unsupported end signals remain unavailable.
3. Choose whether to show the live counter. Open the dedicated Instagram Reels viewer. Compare a few deliberate forward/return transitions with the change in saved counts. Stop if the app reports unsupported or unavailable coverage.
4. Check that the display disappears outside the viewer and on lock, pause stops observations, and history remains after reopening the app. HUD Hide keeps counting and lasts until you leave that app; persistent display off is separate. If separately testing auto-scroll, check Stop and context/lock cancellation without assuming continuous or universal support. Hiding also stops future automatic swipes. Use [deletion instructions](DATA-DELETION.md) only if you intend to erase your history.
5. Send voluntary feedback through [support](SUPPORT.md), with the app/Android versions and a synthetic description. Never send other people's content, personal totals or credentials. There is no automatic feedback upload.

## Testing period

Where Google's newer-personal-account requirement applies, at least 12 testers must remain opted in continuously for 14 days before the maintainer can apply for production access. Production approval is separate. Use the actual Play opt-in date and Console eligibility; do not start a countdown from this document or a sideload. Test actual functionality and report defects throughout the period. Updates can continue during testing.

[Google testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) · [Source code](https://github.com/yasirarfat-dev/AttentionStats)
