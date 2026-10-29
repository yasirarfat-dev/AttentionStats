# Adding social-app support

**Status:** Adapter boundary implemented for the experimental Instagram viewer; additional apps and mixed feeds remain unqualified. Updated 2026-10-29.
**Responsibility:** Explains additive platform support and the preview's remaining catalog/contract work.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Social](SOCIAL-MEDIA-SPEC.md), [Structure](FILE-STRUCTURE.md), [Contracts](NATIVE-CONTRACTS.md), [Testing](TESTING.md).
**Unresolved feasibility:** F-01/F-08/F-09; source organization does not establish fidelity or permission to observe.


Current source increment (ADR-046): the selected Facebook viewer candidate now uses the same seven exact interface-label matches during observation under revised source notice 3. Matching transiently inspects accessibility descriptions and immediately discards nonmatches; no raw description is retained. Instagram still uses layout identifiers only. A fresh affirmative notice is required after upgrading. The temporary diagnostic remains separately selected and may save only bounded fixed tokens and structural metadata. Facebook category separation and Home-feed counts remain unqualified.

The revised temporary development check additionally recognizes exact Like, Comment and Share controls and fixed Home/Reels/Video tab-position labels. It retains only their fixed tokens and up to three ancestor layouts, within the existing 16-match and 32-KiB limits (interface revision 2). These extra labels are diagnostic-only and never become observation signals without separate qualification. A fresh diagnostic notice describes the fields before each trial.

## Ownership

The product scope includes relevant social apps, including Instagram, Facebook, YouTube, TikTok and Reddit. Ship supported subsets incrementally; do not equate the roadmap with universal tracking. A platform can expose several independently qualified surfaces and metrics. Watching video, encountering a feed post and attempting an interaction have different semantics.

| Concern | Current owner | Rule for an additional app |
|---|---|---|
| Android node acquisition | `detectors/accessibility/StructuralSnapshotReader.kt`, `DiagnosticScrollTrace.kt` | Shared bounded, passive, metadata-only reader and temporary event bracket; no app-specific predicates |
| Versioned surface/category recognition | `detectors/social/instagram/InstagramDetector.kt` | Own `detectors/social/<service>/` package with fixtures/evidence; no sibling implementation imports |
| Adapter contract and composition | `detectors/SurfaceDetector.kt`, `detectors/DetectorRegistry.kt` | Consume structural values, return normalized observations; registry selects an exact qualified app/version |
| Platform lifecycle and display | `reels/ReelsAccessibilityService.kt` | Shared consent/window/keyguard checks and rendering; no target selectors |
| Exposure rules | `native/reels-core/` | Reuse stable-dwell, transition deduplication and proposals where semantics match |
| Consent and writes | `reels/ReelsCoordinator.kt`, `storage/` | One serialized coordinator and encrypted Room store; adapters never call DAOs |
| Mobile UI | `src/features/` public `index.ts` APIs | Typed read models; screens do not inspect another app or own history |

Paths beginning with `detectors/`, `reels/` and `storage/` are relative to `android/app/src/main/java/com/attentionstats/`. These are packages in the existing app module, not separate Gradle modules. The pure reducer is a separate module. `tools/check_architecture.py` enforces adapter isolation, acquisition placement, passive/no-content guards and TypeScript feature boundaries. Compilation and phone tests remain necessary.

## Current limits

AS-048 adds Instagram, Facebook, YouTube Shorts (`com.google.android.youtube`) and TikTok (`com.zhiliaoapp.musically`) to the enum, narrow package visibility, native initialization, contract 5, strict decoder and mobile controls. New rows are disabled without consent, preserving existing history and grants. Exact-version YouTube and TikTok detector candidates now exist, with the bounded trials described below. These entries and narrow trials do not establish full route coverage. Regional variants and Reddit remain future independent admissions.

One registry entrypoint per admitted app/version may compose that app's surface classifiers as they become qualified. No Home-feed classifier is admitted now. New surfaces need their own metric/episode scope and explicit disclosure; do not relabel mixed posts as Reels or merge totals silently. A numeric pager position is transient location evidence, not a durable content identity.

## Admission steps

1. Define the app variant, surface and metric. Establish safe public signals, exclusions and transient item/transition identity; review current policy/APIs.
2. Obtain independently chosen source and temporary diagnostic consent for live qualification. Use bounded static/numeric evidence; no target content or raw trees in public fixtures.
3. Implement the app-owned adapter against `SurfaceEvidence`; return unknown/outside when incomplete. Add synthetic positive, negative, version, mixed-media and dedup fixtures.
4. Update source catalog/package visibility, contracts/decoder, consent and read models together when expanding beyond the two preview candidates. Reuse shared storage/counting/overlay; never duplicate a database or callback counter.
5. Build and qualify on the authorized physical phone: positive/excluded surfaces, ads, carousel motion, return/refresh, switch, lock and revocation. Record version/locale, precision/recall, coverage and unsupported variants before enabling the supported subset.

## Home-feed diagnostic increment

ADR-045 additionally permits a revised temporary Facebook interface diagnostic after explicit user authorization and fresh affirmative notice. Interface revision 1 emits at most 16 exact allowlisted tokens (reels/video/home/close/comments/search/back), with class, bounds and selected/clickable booleans. Only `InterfaceLabelMatcher` may inspect descriptions, behind diagnostic, selected-package, visibility and editable/password gates; nonmatches never leave that helper. Node text stays excluded. The temporary check alone does not establish production acceptance or durable content identity. Android's [description API](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getContentDescription()) returns arbitrary `CharSequence` data, so claims distinguish transient inspection from retention. ADR-046 subsequently adds the reviewed notice-3 source candidate described above.

The temporary check discloses geometry revision 1: viewport plus at most 16 allowlisted media/control anchors and their three nearest ancestor layouts. Records contain static resource ID, integer bounds, collection row/column and scan-local numeric slot. Slots associate layouts within one snapshot; they are not node/content identities and never enter exposure history. This is a bounded partial projection, not a raw tree. Geometry anchors are acquired only during the separately selected ten-minute diagnostic.

The host accepts historical flat diagnostics or the exact geometry-1 extension. It rejects extra/content fields, malformed rectangles, invalid slot ancestry and oversized lists/files, and prints aggregates only. The 192-node/32-KiB limits, package/editable/password boundaries, source/epoch fences and stop/expiry/process cleanup remain. The notice describes new fields before a fresh affirmative choice. Android's [bounds API](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getBoundsInScreen(android.graphics.Rect)) supplies screen coordinates, including magnification transforms; coordinates alone do not establish identity, playback or attention.

Association revision 1 extends geometry revision 1 with a separately disclosed scroll-event bracket. It replaces the unrelated rolling `scrolls` list with at most 16 pre-scan events received within two seconds, source bounds (or null for an unavailable/unsafe source), sequence, relative age and delivery delay. Scan start/end sequences, duration and an explicit stability flag distinguish intervening delivered scroll events. Window IDs and consent epochs stay internal: changing app/window/epoch or stopping resets the buffer and invalidates outstanding scan tickets, including a later return to the same scope. Structure is published only after the selected window and consent fence are rechecked. No live Android nodes are retained in the buffer.

`stable` means no scroll callback was recorded during this traversal; it does **not** mean Android supplied an atomic tree, that all events were delivered, or that source bounds identify a post. Mid-scan callbacks make the bracket unstable and are not paired backwards with the earlier geometry. A slow/regressing scan is discarded, stale/future event times add no event record, and source package/visibility/password/editable checks suppress unsafe source metadata. The host validates all sequence/time relationships and exact nested fields, preserving historical diagnostic compatibility. Android documents the [event time](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent#getEventTime()), [nullable source](https://developer.android.com/reference/android/view/accessibility/AccessibilityRecord#getSource()) and [source window](https://developer.android.com/reference/android/view/accessibility/AccessibilityRecord#getWindowId()); these API values do not qualify content continuity. A fresh affirmative temporary check is required after the revised notice; source notice 2, bridge/schema 2 and Home-count unavailability remain unchanged.

AS-048 YouTube candidate: installed version 21.40.165 exposes `reel_recycler`, `reel_player_page_container` and `reel_time_bar`, with centered collection rows changing 0→1→0 during the bounded forward/return trial. The exact-version predicate requires all three IDs, rejects editable/truncated trees, withholds pages during the loading spinner, and leaves categories unclassified. This is encounter evidence only; comments, ordinary watch/Home, ads, broader routes and lifecycle still require independent negative/live tests before acceptance.

## Transient TikTok matching under ADR-049

The user expressly authorizes the developer to choose effective on-device mechanisms after the content-matching tradeoff is explained. TikTok 47.5.3 source notice 4 separately discloses reading only visible, noneditable/nonpassword `title`/`desc` fields as text, with description fallback. Processing is local collection even though no raw field is retained. All other sources keep notice 3. Existing TikTok notice-3 enablement is insufficient; a fresh affirmative notice precedes acquisition.

The acquisition-owned helper streams at most 4096 UTF-16 code units per field into a freshly keyed HMAC-SHA256 comparison. Fixed field framing/order avoids order/concatenation ambiguity. Only keyed digests, a random in-memory key and the previous comparison state survive the scan; no raw-value conversion, log, database, diagnostic or bridge token is permitted. Duplicate, blank, oversized, editable, invisible, foreign-package, missing-caption and incomplete scans withhold identity. Keys/comparison state reset on app/window/consent epoch changes, outside-app/keyguard and service teardown; an invalidated collector cannot commit. Only a monotonically increasing encounter ordinal enters the existing numeric page/count path. Tokens are not durable content identities. Repeated/missing captions can undercount; caption changes can misidentify transitions. Playback, completion, distinct counts and full target accuracy remain unavailable until independently qualified.

## Route and post-type acceptance matrix

The user explicitly prioritizes Home-opened and inline Home videos, every supported path into a viewer, and independently recognized post types in the expanded counter. [Current route matrix](evidence/counter-route-matrix.json) enumerates four-app routes and honest evidence states. External research cannot qualify our detector. Explore dedicated tabs, Home suggestions, inline media, profile/page grids, search/Explore and shared/deep links. Validate first item, real transition/return, idle/redraw, comments, ads and leaving each route. Record exact app version/locale, acquisition scope and binary before claiming coverage.

Recognized video, photo, carousel, text/post, ad and unknown are separate observations or facets according to the domain model; carousel pages must not silently become new posts, and an ad can overlap a media type. Inline Home episodes must retain their own surface provenance rather than being relabeled as dedicated Reels. Unknown sponsorship/media stays unknown. Existing viewer categories do not establish Home post support. Expand the native contract, preservation migration, notice and projections together only after the respective signal qualifies. The expanded counter/dashboard must show truthful unavailable rows until then. AS-041 owns post/surface/action semantics; AS-048 owns the initial four-source integration.
