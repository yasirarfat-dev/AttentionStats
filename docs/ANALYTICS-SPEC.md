# Detailed personal activity diary

**Status:** Expanded mobile specification; AS-035 pure Kotlin calculation slice implemented, collectors and screens unimplemented. Updated 2026-10-28.
**Responsibility:** Defines detailed app/activity analytics, evidence accounting, diary exploration, comparisons and summary semantics.
**Related specifications:** [Metric catalog](METRIC-CATALOG.md), [Tracking](TRACKING-SPEC.md), [Social](SOCIAL-MEDIA-SPEC.md), [Models](DATA-MODELS.md), [Storage](STORAGE-AND-RETENTION.md), [UX](UX-SPEC.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-01/F-02/F-08/F-09 and F-10; no social-platform detector or network collector has device qualification. A calculation fixture is not source verification.

## Product intent

Build a rich, user-owned diary of the digital activity Android exposes, rather than a dashboard limited to overall screen time. Reels/Shorts analytics are first-class mobile content. The user chooses applications, sources, metric families, detail levels and retention. “Maximum detail” is an optional disclosed selection of supported metrics, never an implicit permission to record every screen or input. Trust comes from inspectable collection, truthful limits, local ownership and effective controls; open source alone proves neither safety nor accuracy. Do not claim to outperform every existing product without comparative evidence.

Supported output can grow to include per-app sessions/opens, primary activities, short-video exposures, regular-feed/post/story exposures, qualified interaction evidence, notification behavior, supported browsing metadata, per-app network usage, categories, recurring patterns and optional local content topics. The [catalog](METRIC-CATALOG.md) owns individual metric definitions and admission. No adapter is assumed to expose all of them. A new platform or metric starts unavailable until its exact support scope passes qualification.

No website, web app, extension or WebView is added. The completed browser study is not expanded for this request. Android-native collectors and React Native mobile screens remain the delivery architecture.

## Meaning and dimensions

ADR-036 explicitly separates **whole-app time** from **Reels-viewer time** and preserves per-session, calendar-day, week and month counts/time in the app. The floating control shows only the current session and today. Viewer occupancy is a subset of app occupancy, never an extra additive duration or proof of completed viewing. Periods carry zone/coverage and use half-open bounds; unavailable collectors do not become zero time. The AS-043 preview currently supplies viewer-page counts only; whole-app/Reels duration and weekly/monthly runtime views await AS-011/AS-013/AS-014/AS-037. Optional auto-scroll is an independent bonus (AS-044), not a prerequisite or implicit statistics consent.

Every result carries metric ID/semantic revision, unit, evidence kind, app/profile scope, surface/facet when relevant, UTC half-open range, selected grouping zone, source and detector revisions, configuration revision, store generation/revision, known/eligible coverage, source freshness and reason codes. Counts/bytes use 64-bit native arithmetic with overflow rejection; values crossing JavaScript use safe numbers or decimal strings. Missing access, unqualified detection, unavailable history and unknown values are distinct from covered zero.

Primary dimensions: application; primary activity; selected date/time; local day/week/month/year/custom range; source; category; transport where applicable. Optional labels/topics add independent dimensions only with consent. Never group by another person's identity, message recipient or private account handle. App version/locale are support provenance rather than user-facing rankings.

Use neutral primary activities: short_video, feed, stories, long_video, live, search_navigation, profile_navigation, creation_context, messaging_context, settings and unclassified. Their meaning is visible occupancy, not inferred intention. Generic package usage cannot identify any of them. A separate facet such as comments_panel can overlap a primary activity; its time is marked “within” and is not stacked as extra app time. Messaging-context duration requires separate coarse-context consent and a proven structural detector that never reads messages or editable nodes; otherwise leave it unclassified. No message content/count or typed search/comment text is implied.

### Duration conservation

For one app and bounded query: clip intervals to the query, union overlapping app intervals, then intersect primary surfaces with that union. Merge duplicate intervals for the same surface. A time slice with exactly one known primary surface belongs to it; absent or conflicting primary evidence belongs to unclassified, with a conflict reason when appropriate.

`app covered duration = sum(disjoint primary-surface durations) + unclassified app duration`.

Device-interactive union is a separate metric; overlapping apps in multi-window may each have visible time, so app totals need not sum to device union. Unknown surface time stays inside observed app time. Surface occupancy is not verified playback, completion or attention. Background audio needs a distinct future public source and must not inflate interactive time.

### Item and interaction counts

Exposure means a qualified, settled visible item occurrence, including revisits. A swipe, content callback, public like counter, comment count shown on a post or server view count is not the user's activity count. Only declared surface/item predicates can create normalized transition ordinals. Repeated callbacks and ingestion replay retain the same identity; a new confirmed transition gets a new ordinal. Count semantics distinguish short-video, feed-post, story-frame, ad and photo exposure; media exclusions in the short-video metric do not erase separately enabled ad/feed statistics. Ambiguous media is unknown.

ADR-039 preserves exposures as the default and treats distinct items/repeat exposures as independently qualified breakdowns under [Social](SOCIAL-MEDIA-SPEC.md#repeat-visits-and-distinct-items). Automatic same-page loops do not increment. Unknown identities remain explicit; session position is not a cross-session content ID. Distinct counts are non-additive across periods and cannot be recovered from today's stored ordinal-only preview history.

Keep interaction evidence in two lanes: attempted control activation and observed UI outcome. A qualified unselected→selected reaction control can support “observed like-state changes”; a button click alone supports “like attempts.” Count unlike/unsave/unfollow separately; net changes may be derived within matching covered scope. Never silently collapse toggles into unique liked posts. A comment composer opening is not a submitted comment. A submit action plus a qualified non-content acknowledgement may support “observed comment submissions”; it still does not prove server acceptance or retention. A share sheet opening is distinct from a handoff and from delivery. Do not inspect text to resolve outcomes. Transient interaction correlation must be bounded and expire on uncertain context changes.

### Network usage

Evaluate Android [NetworkStatsManager](https://developer.android.com/reference/android/app/usage/NetworkStatsManager) with separate network-statistics consent and user-granted Usage Access. Use aggregate mobile/Wi-Fi queries with null subscriber identifiers where supported; do not collect SIM identifiers. Collect RX/TX byte counts for a qualified UID/install/profile mapping, retaining transport, source bucket bounds and query revision. See [bucket dimensions](https://developer.android.com/reference/android/app/usage/NetworkStats.Bucket).

OS history is coarse/delayed and may be absent. Query off the main thread; close returned resources. Do not interpolate partial buckets, poll for a per-second HUD, or allocate bytes proportionally to Reels time. These are OS-accounted transfer bytes, including caching/prefetch/background activity, not content size, billing, cellular quota, cost or energy. Show returned temporal scope and freshness. A shared/reused/unknown UID cannot be attributed to one app; preserve a labelled UID group or mark app attribution unavailable. VPN, tethering, profiles, reinstall and OEM behavior need F-10 tests. No packet inspection, TLS interception, VPN permission or notification access is required for this planned source.

Persist one canonical untagged bucket partition per selected source query. Deduplicate exact identity and replace the validated queried span atomically when OS counters revise it; do not add repeated snapshots. Never sum an ALL-state/metered/roaming/default bucket with its finer partitions. Conflicting or overlapping snapshot dimensions fail closed. A platform summary over a requested range is a different provenance/metric mode from full detailed buckets; do not mix them in one total.

## Native mobile experience

Home offers a finite overview and a visible “Reels & short videos” entry. A pinned compact card can show today per supported platform: known short-video duration, estimated exposures, coverage and current visit. Unsupported rows say why. No zeros appear to imply unsupported tracking. A richer chosen dashboard can pin additional metrics without filling the default screen with every possible chart.

Dedicated Social activity screen: platform tabs/list, shared date filter, per-app total, activity composition, short-video minutes and counts, visit lengths/distribution, selected interaction counts, and network tab. Compare platforms only on compatible metric revisions and coverage, showing missing fractions. App detail expands to a full diary: time by primary activity; a chronological episode list with gaps; item/activity counts; qualified interaction outcomes versus attempts; notification associations; daily/hourly patterns; supported content history/topics if enabled; and data-usage totals. Every displayed value links to “How measured,” scope, uncertainty and source controls.

Synthetic target example, not a promise: “Social app: 5 h; short-video surface: 4 h; estimated short-video exposures: 1,000; feed-post exposures: unavailable; observed like-state changes: unavailable.” Once separate detectors qualify, supported post/interaction rows replace those unavailable values. Do not seed plausible counts in real empty history. The Kotlin example demonstrates the arithmetic only.

An advanced metric explorer lets the user choose available metrics, dimensions, range, chart/table, ordering and export scope. Availability and coverage remain visible. Large diaries use bounded queries/keyset pagination and summary tiers; a thousand exposure rows should not render on the first screen. Tables, TalkBack, text scaling, motion/insets and calm information hierarchy remain required. The small top-center [HUD](LIVE-OVERLAY-SPEC.md) remains minimal; deep analytics live inside the mobile app.

## Long-term summaries and reflection

Support custom retention per source/detail/app and staged day/week/month/year rollups, custom week start and named zone. Default keeps details until deletion. People may keep raw basic history indefinitely, drop content sooner, retain daily detail for a chosen period and monthly/yearly summaries longer, or export a summary-only encrypted archive. Local compaction and archive scope are independent actions; neither silently deletes the other. [Storage](STORAGE-AND-RETENTION.md) owns preview, transaction and loss rules.

Totals and ratios must be recomputed from additive sufficient statistics: store sums/counts and coverage, not “average of averages.” Exact medians, unique-content counts, percentiles, sequence/reopening reconstruction and arbitrary timezone regrouping cannot be recovered from coarse totals. Persist only explicitly selected statistics and declare available resolution. Do not interpolate events across a summary boundary or invent a chronology from monthly totals. Separate measurement revisions/evidence kinds; combining incompatible definitions needs an explicit migration or a split series. Topic/category summaries preserve taxonomy/mapping revision and cannot reveal purged content.

Optional time equivalents use a user-chosen activity and session length. Four covered hours can be shown as eight 30-minute sessions or a share of a personally chosen time budget. It does not imply eight workouts occurred, a language was learned, money was lost, sleep was recovered or that leisure was wasted. Hidden by default, adjustable, dismissible and neutral. Predictions/recommendations remain separate local opt-ins with insufficient-data behavior.

## Implemented slice and remaining gates

[Kotlin core](../native/analytics-core/README.md) implements pure, bounded projections over synthetic normalized inputs: interval conservation; civil-day/calendar boundaries; exposure identity deduplication; interaction evidence separation; canonical byte-bucket aggregation; additive rollup previews; and time-equivalent arithmetic. It reads no Android API, performs no collection/persistence/pruning/encryption and provides no mobile UI. Callers remain responsible for source admission, consent fences, complete eligible coverage partitions, provenance and transactional store integration. F-01/F-02/F-03/F-04/F-08/F-09/F-10 remain open.

T-17–T-23 and D-04–D-06 in [Testing](TESTING.md) specify the expanded scenarios. Fixture passing establishes only the named calculation subset. AS-035 does not clear any app/detector/store/backup release gate. AS-036–AS-040 track remaining native collection, metric screens, durable compaction, supported archive import and optional time reflection.
