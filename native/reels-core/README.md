# Reels counting domain

Pure Kotlin state transitions used by the main Android counter under AS-043. Infrastructure must supply a qualified surface and non-content page identity; this module never treats arbitrary scroll events as Reels. It waits for a 500 ms stable candidate, deduplicates repeated observations, counts one confirmed settled page after a skipped range, preserves known exclusion continuity, and suppresses the first page after uncertain re-entry. A proposal is acknowledged only after its exposure commits.

Proposals carry a transient backward-position hint for the counter's vertical page animation. It changes presentation direction only: returning to an earlier settled page still adds an exposure. It is not durable content identity, a unique-item counter or evidence of video completion.

Run `python tools/android_build.py :reels-core:verifyReels --arch arm64-v8a`. The same synthetic test program is included in the Android test APK's `reels-storage` qualification. Host results are not detector/device accuracy evidence. The Android source/consent/store/HUD live in the main app; unsupported target versions remain unavailable.
