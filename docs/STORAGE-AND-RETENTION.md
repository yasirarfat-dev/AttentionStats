# Storage and retention

**Status:** Normative design; storage implementation pending. Updated 2026-10-26.
**Responsibility:** Owns durability, retention, deletion, deduplication, growth and recovery.
**Related specifications:** [Models](DATA-MODELS.md), [Privacy](PRIVACY.md), [Backup](BACKUP-RESTORE.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-02 and F-04 in [Decisions](DECISIONS.md); encrypted storage performance and filesystem behavior require tests.

## Authority and durability

Room-backed SQLite is the sole authority for history, protection configuration, consent and native job state. MMKV holds only small UI preferences; JS query caches are disposable. A write coordinator serializes collection, deletion, migration and restore. Commit a normalized batch, high-water mark, dedup state and revision together. Crash replay must be idempotent and cannot advance the cursor before history is durable.

Raw here means minimized normalized observations, not raw accessibility payloads. Whole trees, screenshots, credential fields and notification bodies never enter storage. Bounded overlapping queries recover permitted source history only while Android retains it. After a missed source interval, mark an unknown gap instead of synthesizing uninterrupted sessions.

## Retention controls

Default: preserve history until the person deletes it. Optional policies independently target observations, optional content detail and derived summaries, with explicit scopes and a preview of lost detail. Suggested choices are preserve, 30 days, 90 days, one year, or a custom duration. No hidden automatic pruning when space runs low. Retaining summaries after pruning raw history requires explicit choice and a “summary only” coverage label; otherwise delete derived summaries too.

Changing to a shorter policy previews affected rows/bytes and effective date before committing. Disabling a source stops future capture and asks separately whether prior data should be deleted; it does not silently erase. Pauses are gaps, not evidence of zero behavior. Optional title/URL retention has its own control and can be shorter than basic usage history.

## Deletion transaction

Preview by source, app, category resolution, date range, content detail or all data. Explain whether rules/configuration are included. After confirmation install an ingestion fence, drain or cancel conflicting batches, delete source and dependent rows, invalidate aggregates/cursors/models, clear caches, and mark existing archives stale. Category-scoped deletion resolves package/time membership at preview revision; a changed mapping invalidates the preview.

Do not query deleted periods again from Android during overlap reconciliation. Future collection after the selected range remains possible. A full reset rotates installation/store generation, removes local keys, provider authorizations, scheduled jobs, staging files and UI preferences, and returns all sources to disabled. External user-owned archives are not silently removed: provide location/deletion guidance and an explicit provider delete operation only where supported and authorized.

Logical deletion is testable; guaranteed forensic erasure from flash, OS snapshots or already exported copies is not. Document WAL/checkpoint/vacuum cleanup and encrypted key destruction without promising physical overwrite. Keep this explanation in data controls, not as an obstacle to deletion.

## Growth, indexing and aggregation

Illustrative sizing only: 5000 minimized rows/day at 400 bytes/row is about 2 MB/day, 730 MB/year before indexes, WAL and encryption overhead. Content fields may dominate growth. Show measured actual database/WAL/cache/staging/archives bytes, row counts by source/detail, free space and estimation basis. Do not label these assumptions as benchmarks.

Index event time/ID, app/time, source dedup key, coverage time and rule state. Use keyset paging and bounded queries. Aggregate daily/hourly bins incrementally with source/mapping revision; rebuilding affected bins must give the same result as a clean rebuild. Original intervals cross midnight intact; bins split by selected zone. Keep cache size bounded independent of preserved history.

When storage is low, show a warning and offer export, explicit pruning and detail reduction. If a write fails, preserve committed data, stop advancing cursors, report degraded coverage and attempt bounded retry; do not delete data automatically or silently drop facts. Temporary export/import files have quotas, no-backup placement, encryption and crash cleanup. Long-history scenarios include a synthetic million-event history, one-year summaries, cancelled queries, low-space writes, migration failure and restore interruption under [Testing](TESTING.md).
