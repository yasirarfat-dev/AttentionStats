# Adding social-app support

**Status:** Adapter boundary implemented for the experimental Instagram viewer; additional apps and mixed feeds remain unqualified. Updated 2026-10-28.
**Responsibility:** Explains additive platform support and the preview's remaining catalog/contract work.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Social](SOCIAL-MEDIA-SPEC.md), [Structure](FILE-STRUCTURE.md), [Contracts](NATIVE-CONTRACTS.md), [Testing](TESTING.md).
**Unresolved feasibility:** F-01/F-08/F-09; source organization does not establish fidelity or permission to observe.

## Ownership

The product scope includes relevant social apps, including Instagram, Facebook, YouTube, TikTok and Reddit. Ship supported subsets incrementally; do not equate the roadmap with universal tracking. A platform can expose several independently qualified surfaces and metrics. Watching video, encountering a feed post and attempting an interaction have different semantics.

| Concern | Current owner | Rule for an additional app |
|---|---|---|
| Android node acquisition | `detectors/accessibility/StructuralSnapshotReader.kt` | Shared bounded, passive, metadata-only reader; no app-specific predicates |
| Versioned surface/category recognition | `detectors/social/instagram/InstagramDetector.kt` | Own `detectors/social/<service>/` package with fixtures/evidence; no sibling implementation imports |
| Adapter contract and composition | `detectors/SurfaceDetector.kt`, `detectors/DetectorRegistry.kt` | Consume structural values, return normalized observations; registry selects an exact qualified app/version |
| Platform lifecycle and display | `reels/ReelsAccessibilityService.kt` | Shared consent/window/keyguard checks and rendering; no target selectors |
| Exposure rules | `native/reels-core/` | Reuse stable-dwell, transition deduplication and proposals where semantics match |
| Consent and writes | `reels/ReelsCoordinator.kt`, `storage/` | One serialized coordinator and encrypted Room store; adapters never call DAOs |
| Mobile UI | `src/features/` public `index.ts` APIs | Typed read models; screens do not inspect another app or own history |

Paths beginning with `detectors/`, `reels/` and `storage/` are relative to `android/app/src/main/java/com/attentionstats/`. These are packages in the existing app module, not separate Gradle modules. The pure reducer is a separate module. `tools/check_architecture.py` enforces adapter isolation, acquisition placement, passive/no-content guards and TypeScript feature boundaries. Compilation and phone tests remain necessary.

## Current limits

The preview is still scoped around Reels. `ReelApp`, source initialization, package queries, bridge contract 2 and its strict TypeScript decoder enumerate Instagram/Facebook only. They are not yet a general capability catalog. Registering an adapter alone cannot add an app to settings or authorize observation. YouTube/TikTok/Reddit require reviewed additive catalog/contract/UI changes and, if needed, a migration preserving old source meanings. Do not pre-create empty platform folders or claim these adapters exist.

One registry entrypoint per admitted app/version may compose that app's surface classifiers as they become qualified. No Home-feed classifier is admitted now. New surfaces need their own metric/episode scope and explicit disclosure; do not relabel mixed posts as Reels or merge totals silently. A numeric pager position is transient location evidence, not a durable content identity.

## Admission steps

1. Define the app variant, surface and metric. Establish safe public signals, exclusions and transient item/transition identity; review current policy/APIs.
2. Obtain independently chosen source and temporary diagnostic consent for live qualification. Use bounded static/numeric evidence; no target content or raw trees in public fixtures.
3. Implement the app-owned adapter against `SurfaceEvidence`; return unknown/outside when incomplete. Add synthetic positive, negative, version, mixed-media and dedup fixtures.
4. Update source catalog/package visibility, contracts/decoder, consent and read models together when expanding beyond the two preview candidates. Reuse shared storage/counting/overlay; never duplicate a database or callback counter.
5. Build and qualify on the authorized physical phone: positive/excluded surfaces, ads, carousel motion, return/refresh, switch, lock and revocation. Record version/locale, precision/recall, coverage and unsupported variants before enabling the supported subset.

## Home-feed diagnostic increment

The temporary check discloses geometry revision 1: viewport plus at most 16 allowlisted media/control anchors and their three nearest ancestor layouts. Records contain static resource ID, integer bounds, collection row/column and scan-local numeric slot. Slots associate layouts within one snapshot; they are not node/content identities and never enter exposure history. This is a bounded partial projection, not a raw tree. Geometry anchors are acquired only during the separately selected ten-minute diagnostic.

The host accepts historical flat diagnostics or the exact geometry-1 extension. It rejects extra/content fields, malformed rectangles, invalid slot ancestry and oversized lists/files, and prints aggregates only. The 192-node/32-KiB limits, package/editable/password boundaries, source/epoch fences and stop/expiry/process cleanup remain. The notice describes new fields before a fresh affirmative choice. Android's [bounds API](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getBoundsInScreen(android.graphics.Rect)) supplies screen coordinates, including magnification transforms; coordinates alone do not establish identity, playback or attention.
