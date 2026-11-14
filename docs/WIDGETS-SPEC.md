# Flexible home-screen widgets

**Status:** User-prioritized AS-027 experimental implementation; broader launcher/device acceptance remains open. Updated 2026-11-14.
**Responsibility:** Owns per-widget presentation, responsive sizing, metric availability and configuration.
**Related specifications:** [UX](UX-SPEC.md), [Settings](SETTINGS-SPEC.md), [Analytics](ANALYTICS-SPEC.md), [Tracker](FEATURE-TRACKER.md), [Decisions](DECISIONS.md).
**Unresolved feasibility:** Host sizing varies by launcher; broader devices, large text, restore/reboot and resource qualification remain scope-specific.

## Product behavior (ADR-086/ADR-087/ADR-088)

The current curated set provides five distinct launcher entries, with independent instances and purpose-specific defaults. ADR-090 removes a fixed template ceiling: add further templates only when they meet distinct useful needs, preserving a modest primary experience. Current entries:

| Widget | Purpose and presentation |
|---|---|
| Today Overview | Balanced encounter/session/time/activity tiles; an expanded full-width Screen Time section with a labelled Reels-app share bar |
| One Stat | A large chosen metric with optional secondary values |
| Screen Time | Recorded whole-app time and Reels-app whole-app time, with a share bar when comparable |
| Reels by Apps | Source encounter ranking with labelled relative bars; source names shown by default |
| Hourly Rhythm | Recorded media encounters by hour, with a labelled time axis |

Settings → Add a Home-Screen Widget opens a native gallery with five populated example previews and purpose-specific Add controls. Adding requests the selected launcher provider and requires launcher confirmation; opening the gallery places nothing. Configuration remains available by long-pressing a placed widget.

The original TodayWidget provider identity is preserved for installed widgets; four additional providers use the same native owners, bounded refresh and configuration flow. Five distinct widget products supersede the interpretation of ADR-086 as only generic skins. Tiles/List remain available under More Layouts inside configuration, alongside the five primary layouts. The current launcher set has five entries. Each instance may switch layout in configuration while preserving selected metric keys/order, appearance and name visibility; new instances receive the selected provider’s purpose-specific defaults. Save/Cancel remain explicit. Resizing rearranges/reveals selected metrics, rather than stretching a fixed four-cell layout or changing collection. Android 12+ receives a bounded responsive SizeF/RemoteViews map; older launchers use options-change width/height fallback. Small layouts prioritize the first selected metric, larger ones reveal additional selected metrics; multi-column overviews reveal complete rows rather than leaving an accidental lone Mindful Breaks tile. A dedicated full-width section must earn its space through a meaningful labelled summary/visualization; no infinite pages or background animation.

The selectable statistics are today's short-video encounters, current-session encounters, recorded short-video surface time, whole-device recorded app-time subtotal, Reels-app whole-app time, observed app entries/returns, mindful breaks, photo encounters, ad encounters, confirmed numeric playback, active focus remaining and observed apps. Unknown duration/usage/playback is a dash; recorded zero stays zero. Surface time is deduplicated by source across regional package rows. Encounters, advertising facets, app time, surface time and confirmed playback retain separate meanings. Hourly Rhythm shows saved media encounters, including photos/ads, and does not imply completed watching.

Configuration uses a populated native example preview by default, clearly labelled Example Data, with an optional Preview My Stats switch. Synthetic values stay inside the preview and never update authoritative history or placed widgets. The launcher picker receives purpose-specific compact previewLayout resources with the app icon, a readable representative subset of values and chart/bar composition; these are also labelled Example Data. Older-launcher preview support remains a separate gate.

Per-instance controls use focused pages and progressive disclosure. The primary appearance page shows preview, background opacity, Theme and Accent. More Options reveals corners, number size, timestamp and source names; each choice page stays focused. Controls include explicit Save/Cancel, seven layouts across the current five widget types, one-to-nine ordered stats, Match App/Light/Dark appearance, app/selected accent, 0–100% background opacity, rounded/soft/square corners, small/standard/large stat numbers and update-time visibility. Opacity changes only the panel and tile backgrounds; foreground content remains opaque. With a translucent panel, remove solid tile fills too. Preview opacity changes live; choosing readable foreground appearance against the person's wallpaper remains user-controlled. Configuration document version 3 reads versions 1 and 2; older name flags migrate to visible names. Version-1 input receives opaque/rounded/standard/timestamp defaults; there is no history schema migration. Stat number size applies to statistic tiles, fitting available space; charts retain readable native labels. New Today Overview defaults put This Session before Short Videos; saved metric orders are preserved. Drafts survive ordinary Activity recreation. Changing a style/stat/size never starts collection or focus. Per-instance settings contain only display keys, not history. Preserve legacy single-stat/overview selections; deletion removes only the deleted widget's config. Restored IDs copy available local display preferences; fresh-device configuration recovery remains unqualified. Actual statistics remain in encrypted authoritative storage and bounded in-memory projections, without a new plaintext history cache.

ADR-094 supersedes the earlier hidden-name default: new instances and version-1/2 display configurations show actual source names. Version 3 preserves an explicit Show App Names off choice; names are presentation only. Widgets are home-screen only, not a lock-screen data feature. Offer an actual update timestamp (visible by default) and retain dated/stale/unavailable state even when the timestamp is hidden; do not imply real-time refresh after a source stops. Refresh uses the existing coordinator's bounded/throttled path and platform periodic updates; resize/configuration requests a refresh but enables no source, new grant, service or network transfer. Widget failures cannot stop counting. Root/metric taps open existing app screens; no tap silently starts/stops focus or changes consent.

## Honest time visualization

Screen Time is a recorded app-time subtotal, not a universal total or wellbeing target. The share bar compares the subset of recorded whole-app time in supported Reels apps with recorded whole-app time across apps for the same snapshot/day. Reels Apps includes non-video screens; it is never labelled Reels playback. Draw a share only when both values are known and 0 ≤ subset ≤ total with total > 0. Missing, zero-total or incompatible values retain textual values without an invented ratio. Short-video surface time and confirmed numeric playback remain separate metrics; no residual is labelled non-Reels time. No arbitrary daily maximum or goal is invented to fill a bar.

Actual light/dark attached-host renders using synthetic values are available in the [native preview sheet](evidence/widget-native-previews.png). They are render evidence for this artifact, not real personal usage or broad launcher acceptance.

## Acceptance

Visually inspect actual native renders for hierarchy, proportion, readability, clipping, spacing, usefulness and calm psychological framing in addition to numeric checks. Use physical-device attached AppWidgetHostView tests for each layout and relevant compact/wide/tall sizes, unknown versus zero, long counts, source deduplication, instance isolation, legacy mapping and configuration cancellation. Check actual launcher add/reconfigure/resize where available, preserving existing widgets and launcher arrangement. Synthetic values belong only in an isolated test host, never all placed widgets or real history. Record artifact/launcher/API and exact cases; API-36 results do not accept minimum API or every launcher. Broader TalkBack, large text, RTL, orientation, refresh/process/reboot/restore and battery/parcel-size tests remain separate gates.

Implementation follows [Android flexible widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts), including responsive layouts and onAppWidgetOptionsChanged fallback. An SDK capability is not proof that a particular launcher reports every size correctly.


ADR-094 refresh admission checks all five owned provider classes. A Reels By Apps, One Stat, Screen Time or Hourly Rhythm instance must load native projections even when no original Today Overview widget is placed. Zero placed widgets may still skip projection work. This keeps the existing bounded refresh throttle and actual-history projection authority.


### Compact configuration help (ADR-096)

Native gallery, stat/order/opacity/display sections and style/theme choices expose routine descriptions through independent inline information controls. Example previews stay visibly marked as examples. Statistics, draft values, selected state, Save/Cancel and host confirmation remain visible. Opening choice help must not select that choice or save the widget. No provider, data projection, instance ownership, resize or migration semantics change. Actual configuration/attached-host and phone scope must be recorded separately from source presence.
