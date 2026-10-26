# Data models

**Status:** Logical schema accepted; SQL/Room implementation and migrations pending. Updated 2026-10-26.
**Responsibility:** Defines entities, invariants, time, provenance and schema evolution.
**Related specifications:** [Storage](STORAGE-AND-RETENTION.md), [Contracts](NATIVE-CONTRACTS.md), [Tracking](TRACKING-SPEC.md), [Backup](BACKUP-RESTORE.md).
**Unresolved feasibility:** F-03 and F-04 in [Decisions](DECISIONS.md); encrypted Room driver and serialization remain unselected.

## Entity catalog

All IDs are locally generated opaque strings. No hardware/advertising identifier, contact, product account or remote user key is needed. Stable observation identity is distinct from any transient item identity used by a detector.

| Entity | Required fields and relationships | Invariants / indexes |
|---|---|---|
| StoreMetadata | schemaVersion, generation, installationEpoch, lastRevision, createdAtMs | Singleton; generation changes on replacement; installation epoch is local only |
| AppDescriptor | appId, platformPackage, profileScope, label, version, firstObservedAtMs | Unique package/profile within supported observation scope; label from OS not trusted executable text |
| SourceConfiguration | sourceId, enabled, consentRevision, effectiveAtMs, filters, detail, pauseUntilMs, retentionPolicyId | Default off; versioned atomic config; no old grant implies consent |
| ConsentRecord | id, source/detail, noticeRevision, decision, atMs, configRevision | Minimal affirmative/withdrawal evidence; contains no screen content |
| SourceEvent | id, sourceId, appId?, kind, occurredAtMs, receivedAtMs, elapsedMs?, bootEpoch?, zoneId, offsetSeconds, dedupKey, occurrenceOrdinal, collectorRevision, quality | Unique source/dedup identity; index `(occurredAtMs,id)` and `(appId,occurredAtMs,id)` |
| Session | id, appId, startMs, endMs?, countedMs, sourceStartId?, sourceEndId?, quality, boundaryReasons, reducerRevision | end ≥ start; countedMs excludes known locked/off gaps; null end is censored, never infinite |
| SurfaceEpisode | id, sessionId?, appId, surfaceId, startMs, endMs?, detectorRevision, quality | Known interval subset of corresponding app usage where link is available; no extra app duration |
| Exposure | id, surfaceEpisodeId, occurredAtMs, ordinal, detectorRevision, countKind, reasonCodes | Unique episode/ordinal; optional transition evidence contains no content by default |
| ContentRecord | id, exposureId?/episodeId?, title?, safeDomain?, sanitizedUrl?, sourceField, consentRevision, redactionRevision | Separate table; absent by default; no raw URL secrets, editable input or sensitive surfaces |
| NotificationEpisode | id, appId, localLifecycleKey, firstPostMs, lastUpdateMs, removedAtMs?, reasonClass | Metadata only; transient OS key normalized; update ≠ new notification |
| CoverageInterval | id, sourceId, startMs, endMs?, state, reasonCode, configRevision, detectorRevision? | Non-overlapping state intervals per source/adapter; explicit unknown and pause |
| Category / AppCategoryMapping | id, name, colorToken / appId, categoryId, validFromMs, mappingRevision | One primary category per effective time; user override wins |
| Insight / TopicAssignment | id, kind, window, algorithmRevision, params, evidenceRefs, confidenceClass, explanationCodes / contentRef, topic, correction | Rebuildable; invalidated when evidence removed; no diagnosis |
| ProtectionRule | id, revision, targetDefinition, resolvedTargets, trigger, action, schedule, zonePolicy, overrideMode, exceptions, enabled | Explicit activation and emergency bypass invariants; config authoritative in Room |
| FocusSession / Override | id, rules, targets, startMs, deadlineMs, elapsedDeadline?, bootEpoch, state | Overrides scoped and expiring; no permanent hidden allow token |
| Intervention | id, ruleIds, triggerAtMs, renderer, outcome, reasonCodes, configRevision | Triggered, displayed, dismissed and failed are distinct states |
| AggregateBucket | id, metric, startMs, endMs, groupingZoneId, app/category/surface, mappingRevision, sourceRevision, values, coverage | Unique dimensions/revision; regenerated after mutation; never source truth |
| IngestionCursor / DeletionFence | sourceId, highWaterMs, overlapMs, revision / scope, timeRange, generation | Batch/cursor/fence committed atomically; no deleted-event resurrection |
| BackupJob | id, formatVersion, snapshotRevision, state, stage, transport, byteCount, sanitizedFailure, completedAtMs? | No key/passphrase; transport handles private; status does not assert provider durability |
| CommandRecord | commandId, operation, payloadDigest, resultRevision, outcome, expiresAtMs | Idempotency record atomically persisted with mutation; no credential payload |

## Time, evidence and precision

UTC epoch milliseconds are the transport/storage instant. Store event-time zone ID and offset for provenance, and use explicit selected view zone for calendar grouping. Elapsed realtime is valid only within the same boot epoch. Durations are nonnegative milliseconds, counts nonnegative integers; SQL 64-bit values crossing JS must be safely representable or encoded as decimal strings under a versioned contract.

Every measure identifies source observation IDs or a bounded query provenance descriptor, reducer/model version, configuration revision, coverage interval, and kind (observed/estimate/classification/prediction). A confidence class is not a measured probability. Unknown metrics use null and reasons. Synthetic example: `com.example.social` has a covered episode from 2026-10-20T10:00:00Z to 10:10:00Z, 600000 ms; an unknown period afterward has no inferred duration.

## Relationships and deletion

Source events may produce sessions, surface episodes and derived insights. Keep referential deletion rules explicit: deleting app/time/source history removes or clips intersecting episodes, content, notifications, aggregates and model state. Where an event supports a boundary outside the deletion range, rebuild affected sessions and remove evidence references that no longer exist. Do not leave titles or topic labels in cached explanations after deleting their source.

Rules are configuration, not history: selective history deletion retains rules unless the preview explicitly includes them. All-data reset removes configuration, consent, keys, jobs, tokens and UI preferences as well as history. Consent audit retained after a selective deletion contains only minimal consent metadata, never deleted behavioral payloads; full reset removes it.

## Migrations and schema versioning

Use monotonically increasing Room schema versions, committed exported schemas, explicit transactional migrations, and fixtures for every supported upgrade path. No destructive fallback migration for user history. Migration failure leaves the original store recoverable and collection paused. Test database/WAL handling, storage exhaustion and interruption before changing a release's schema.

Backup format version and database schema version are independent. Portable archives contain a documented logical representation and manifest, not an uncontrolled copy of an open SQLite database. Import validates schema and bounds, applies supported staged migrations, and rejects unsupported future versions without touching live data. Rebuildable aggregates may be omitted; provenance, consent scope and coverage cannot.
