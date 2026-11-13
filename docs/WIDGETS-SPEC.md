# Flexible home-screen widgets

**Status:** User-prioritized AS-027 experimental implementation; broader launcher/device acceptance remains open. Updated 2026-11-13.
**Responsibility:** Owns per-widget presentation, responsive sizing, metric availability and configuration.
**Related specifications:** [UX](UX-SPEC.md), [Settings](SETTINGS-SPEC.md), [Analytics](ANALYTICS-SPEC.md), [Tracker](FEATURE-TRACKER.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** Host sizing varies by launcher; broader devices, large text, restore/reboot and resource qualification remain scope-specific.

## Product behavior (ADR-086)

Provide six selectable layouts: Adaptive, Spotlight, Tiles, List, Reels by Apps and Hourly Rhythm. One provider supports independently configured instances. Resizing rearranges/reveals selected metrics, rather than stretching a fixed four-cell layout or changing collection. Android 12+ receives a bounded responsive SizeF/RemoteViews map; older launchers use options-change width/height fallback. Small layouts prioritize the first selected metric, larger ones reveal additional selected metrics; no infinite pages or background animation.

The selectable statistics are today's short-video encounters, current-session encounters, recorded short-video surface time, whole-device recorded app-time subtotal, Reels-app whole-app time, observed app entries/returns, mindful breaks, photo encounters, ad encounters, confirmed numeric playback, active focus remaining and observed apps. Unknown duration/usage/playback is a dash; recorded zero stays zero. Surface time is deduplicated by source across regional package rows. Encounters, advertising facets, app time, surface time and confirmed playback retain separate meanings. Hourly Rhythm shows saved media encounters, including photos/ads, and does not imply completed watching.

Configuration uses a live native preview, explicit Save/Cancel, style, one-to-nine ordered stats, Match App/Light/Dark appearance and app/selected accent. Drafts survive ordinary Activity recreation. Changing a style/stat/size never starts collection or focus. Per-instance settings contain only display keys, not history. Preserve legacy single-stat/overview selections; deletion removes only the deleted widget's config. Restored IDs copy available local display preferences; fresh-device configuration recovery remains unqualified. Actual statistics remain in encrypted authoritative storage and bounded in-memory projections, without a new plaintext history cache.

Optional app-name details default hidden for new instances. Widgets are home-screen only, not a lock-screen data feature. Display an actual update timestamp and dated/stale/unavailable state; do not imply real-time refresh after a source stops. Refresh uses the existing coordinator's bounded/throttled path and platform periodic updates; resize/configuration requests a refresh but enables no source, new grant, service or network transfer. Widget failures cannot stop counting. Root/metric taps open existing app screens; no tap silently starts/stops focus or changes consent.

## Acceptance

Use physical-device attached AppWidgetHostView tests for each layout and relevant compact/wide/tall sizes, unknown versus zero, long counts, source deduplication, instance isolation, legacy mapping and configuration cancellation. Check actual launcher add/reconfigure/resize where available, preserving existing widgets and launcher arrangement. Synthetic values belong only in an isolated test host, never all placed widgets or real history. Record artifact/launcher/API and exact cases; API-36 results do not accept minimum API or every launcher. Broader TalkBack, large text, RTL, orientation, refresh/process/reboot/restore and battery/parcel-size tests remain separate gates.

Implementation follows [Android flexible widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts), including responsive layouts and onAppWidgetOptionsChanged fallback. An SDK capability is not proof that a particular launcher reports every size correctly.
