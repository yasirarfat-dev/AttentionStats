# Storage and retention

**Status:** Normative design; storage implementation pending. Updated 2026-10-27.
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

## User-configurable summary tiers

[Analytics](ANALYTICS-SPEC.md) extends preservation with optional per-source/app/detail policies. Select custom age thresholds and day/week/month/year resolution, named fixed zone, week start and retained statistics. Example only: detailed basic history for 90 days, daily facts through one year, monthly facts thereafter, titles removed after 30 days; the default remains preserve enabled history until deletion. Weekly/monthly partitions cannot both be authoritative for the same period. Validate tier order/overlap, supported dimensions and source retention before accepting a policy.

Preview detail/rows/bytes, estimated resulting rows/bytes, exact scope and irreversible losses: chronology, individual exposures/actions/content, exact visit timing, arbitrary-range drill-down, unsupported unique/percentile calculations, category/topic changes and regrouping into another timezone. Savings are estimated until measured. Deletion finer than retained dimensions requires deleting the whole affected bucket or cancelling, never proportional deletion. Offer current-data export without making it a hidden prerequisite.

Store additive sufficient statistics and coverage per metric/semantic revision/evidence kind, selected dimensions and UTC partition. Means use sums/counts; ratios retain numerator/denominator; histograms retain bounds. Do not average averages, infer distinct content, reconstruct events or merge incompatible definitions. Coverage/unknown partitions survive coarsening. Disable requested statistics that cannot be preserved safely and explain before pruning. Separately consented topic summaries cannot smuggle purged content into labels.

Create a revision-bound plan → compute bounded staged facts → validate conservation/partition coverage/provenance → recheck deletion/consent fences → transactionally install RetainedSummaryFacts and remove detailed authority → invalidate caches/jobs/archives. Before commit, cancellation/interruption preserves the current representation. After commit, report the new revision; never rerun stale pruning. Cleanup is idempotent, encrypted/no-backup, bounded and crash-safe. Room remains the sole store. AggregateBucket is rebuildable; retained summary facts are not disposable after chosen detail loss.

Queries choose the finest non-overlapping representation for each metric partition. Coarse partial-boundary buckets return actual bounds or are withheld, never interpolated. Freeze summary zone/week/taxonomy/semantic metadata. A new display zone can regroup surviving detail but cannot recreate absent detail. Preview any compatible further coarsening; a year summary cannot expand back to months.

Local compaction and summary-only encrypted export are independent. Export may leave local details intact. Restore declares summary resolution, invents no detail and reactivates no sources. Older external archives keep their detail until deleted by the user. D-04–D-06 cover atomicity, conservation, granularity, fences and archived resolution. AS-035 implements only a pure additive preview; no compaction job, pruning or durable summary storage exists.

## Growth and query performance

Illustrative sizing only: 5000 minimized rows/day at 400 bytes/row is about 2 MB/day, 730 MB/year before indexes, WAL and encryption overhead. Content fields may dominate growth. Show measured actual database/WAL/cache/staging/archives bytes, row counts by source/detail, free space and estimation basis. Do not label these assumptions as benchmarks.

Index event time/ID, app/time, source dedup key, coverage time and rule state. Use keyset paging and bounded queries. Aggregate daily/hourly bins incrementally with source/mapping revision; rebuilding affected bins must give the same result as a clean rebuild. Original intervals cross midnight intact; bins split by selected zone. Keep cache size bounded independent of preserved history.

When storage is low, show a warning and offer export, explicit pruning and detail reduction. If a write fails, preserve committed data, stop advancing cursors, report degraded coverage and attempt bounded retry; do not delete data automatically or silently drop facts. Temporary export/import files have quotas, no-backup placement, encryption and crash cleanup. Long-history scenarios include a synthetic million-event history, one-year summaries, cancelled queries, low-space writes, migration failure and restore interruption under [Testing](TESTING.md).
