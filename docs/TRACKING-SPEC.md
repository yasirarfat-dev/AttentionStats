# Tracking specification

**Status:** Normative design; no collectors implemented. Updated 2026-10-27.
**Responsibility:** Defines event meaning, collection lifecycle, session accounting, and coverage.
**Related specifications:** [Capabilities](CAPABILITY-MATRIX.md), [Social](SOCIAL-MEDIA-SPEC.md), [Models](DATA-MODELS.md), [Permissions](PERMISSIONS.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-02 and F-09 in [Decisions](DECISIONS.md); polling cadence, public event retention, and OEM coverage need measurement.

## Sources and consent

Detailed per-app activity composition, short-video analytics, qualified interaction evidence and network-byte accounting follow [Analytics](ANALYTICS-SPEC.md) and the [Metric catalog](METRIC-CATALOG.md). They extend the diary without changing source consent, unknown/gap semantics or the prohibition on input/message capture. Network-statistics consent is independent of existing Usage Access enablement; activity-specific fields require individually qualified adapters.

Use Android public UsageStatsManager queries for app/activity history and available screen/keyguard events. UsageEvents are retained by the system for a limited interval; the collector must persist allowed observations while available rather than promise arbitrary retrospective recovery. On Android R and later queries can return null before user unlock. See [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager). These constraints require a native collection spike, not an always-running JavaScript timer.

[AS-003 research and qualification](COLLECTION-FEASIBILITY.md) separates historical reconciliation from live signals. First-unlock readiness is distinct from an ordinary keyguard lock: credential-encrypted storage remains available after relocking until reboot ([Direct Boot](https://developer.android.com/privacy-and-security/direct-boot)). A successful empty query does not establish complete coverage. Record query completion/freshness separately from measured usage and live-source availability.

Each source has an enabled flag, consent revision and effective time, package/category inclusion, exclusions, detail level, pause interval, retention policy, and coverage state. Default disabled. A grant alone cannot enable collection. Do not persist or backfill events before enablement, inside pauses, or after revocation. Recheck the current revision before commit so in-flight batches cannot defeat a withdrawal. Updating categories resolves the effective package filter in native code; exclusion wins. Source pausing and protection pausing are separate controls with explicit explanations.

Accessibility is reserved for individually opted-in surface detectors and optional context. Notification collection is a separate listener and consent. Neither may dump event text or whole node trees to disk. No screenshots, microphone, camera, keyboard monitoring, VPN inspection, root, or private app databases.

## Event and session semantics

Normalize activity resume/pause/stop into foreground episodes. Multiple activities in the same package do not automatically mean a new app open. Model session boundaries with package, window/display context where available, interactive state, and source evidence. A screen-off or locked boundary ends counted interactive usage. Audio/background playback is a different concept and excluded from foreground totals. An unpaired transition produces an uncertain/censored interval, not a duration extending indefinitely.

For app opens, count a confirmed entry from another package or from an inactive/locked episode. The initial short-gap grouping threshold is two seconds and is versioned/configurable; classify the result as a derived estimate. Do not equate resumes, notification taps, recents navigation, or unlocked screens with launcher taps.

Device screen time is the union of known interactive intervals, not the sum of app durations. Split-screen app intervals may overlap; per-app totals can exceed device time and must be labelled. Picture-in-picture, secondary displays, work profiles, cloned apps and uncertain active windows require explicit capability coverage, not invented precedence. Never observe another profile beyond public access and consent.

Observed unlock means a supported keyguard dismissal event, optionally corroborated by runtime state. Screen-on alone is not an unlock. Unlock method, biometric data, credential text, and identity are never captured. [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event) defines distinct screen and keyguard signals.

Historical screen/keyguard events require API 28; on API 24–27 those event families are unavailable. Any alternative live source needs independent qualification and cannot retrospectively fill missed boundaries. Do not infer complete interactive time or unlock totals from activity transitions alone.

## Ingestion, time and reconciliation

Use an overlapping query window with a persisted high-water mark; insert normalized source events idempotently. Dedup keys include source, installation epoch, boot identity where available, event type, timestamp, package, activity/window and an occurrence discriminator for otherwise identical legitimate events. Never merge distinct events solely because they share a millisecond. Persist batch, cursor, coverage and generation in one transaction. Apply a source precedence table: UsageEvents owns app-level timing; accessibility enriches surface intervals and never adds app duration a second time.

AS-003's [synthetic reconciliation experiment](COLLECTION-FEASIBILITY.md#observation-provenance) tests maximum observed signature multiplicity and local ordinals under overlap. Indistinguishable replacements remain unresolved; this is neither exact dedup identity nor a qualified count lower bound. Truncation commits no partial slice, and a successful empty query only updates query health/position. The in-memory fake does not implement the persisted high-water mark, coverage or consent coordinator above.

The synthetic planner preserves consent/fence exclusions as explicit intervals; its pagination position is never processed history. Density retries split left first under one attempt/deadline budget, stop on unsplittable groups, and retain independently committed children. Only caller-qualified source failures may back off; other stop reasons do not loop. The existing fake refuses cursor jumps across excluded time. Durable per-range progress, exclusion receipts and restart behavior remain unimplemented; no planning or retry outcome establishes metric coverage.

Store UTC instants and independently available event-time zone/offset, elapsed realtime and boot evidence with explicit provenance; absent evidence stays absent. Prefer monotonic differences only for directly observed same-boot endpoints. Historical UsageEvents expose wall timestamps, so retrospective durations require wall-time uncertainty/anomaly handling; query-time clock samples cannot invent old monotonic endpoints. See [provenance constraints](COLLECTION-FEASIBILITY.md#observation-provenance). Intervals are half-open [start, end). Reboot terminates monotonic continuity; no duration spans an unknown shutdown gap. Preserve original instants and view-zone grouping separately. Midnight splits affect aggregates, not source sessions. Day boundaries use a named timezone, including daylight-saving transitions; do not assume a day always has 24 hours.

Coverage intervals distinguish disabled, paused, permission_denied, unavailable, unsupported, collecting, stale, and unknown. Empty queries do not prove no use. Reconciliation after permitted process restart uses bounded overlap, marks unrecoverable gaps, and rebuilds only affected aggregates. User deletion and source disablement set ingestion fences so OS query overlap cannot resurrect removed history.

## Notification and browsing details

For notifications retain package, event time, lifecycle key, post/update/removal kind and coarse grouping metadata only. Treat repeated updates to the same live key as updates; a later repost may be a new episode. Removal is not a read receipt, and historical notifications missed by the listener are unknown. [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) is the source interface. A notification preceding an open supports a temporal association, never proof of causation.

Browsing domain/title/URL support is expansion work under [Social and context rules](SOCIAL-MEDIA-SPEC.md). Do not read typed address-field input. Only a confirmed supported navigation result can qualify; unknown private-mode status fails closed. All core tracking operates offline. Health UI shows source status, last success, known gap ranges and recovery steps without promising permanent background operation.

Acceptance uses T-01–T-11, T-16 and lifecycle cases L-01–L-08 in [Testing](TESTING.md). Every measured output must expose provenance, coverage and whether it is observed or reconstructed.
