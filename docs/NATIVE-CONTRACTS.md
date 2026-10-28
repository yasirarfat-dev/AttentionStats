# Native contracts

**Status:** Normative domain interface draft; read-only scaffold bootstrap spec/adapter exists, full domain transport pending. Updated 2026-10-29.
**Responsibility:** Defines typed operations, status, revisioning, events, errors and platform behavior.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Models](DATA-MODELS.md), [Tracking](TRACKING-SPEC.md), [Backup](BACKUP-RESTORE.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-02–F-04 and F-07 in [Decisions](DECISIONS.md); logical unions must be lowered into supported Codegen primitives without changing meaning.

## Experimental scaffold handshake

AS-004/AS-008 introduces only `NativeBootstrap.getBootstrapStatus(): Promise<{contractVersion: number; sourceState: string}>` for Codegen/build/runtime qualification. Version 1 accepts exactly `{contractVersion: 1, sourceState: 'not_implemented'}`. Android returns this fixed source-unavailable declaration without reading grants/history, scheduling work, changing settings or persisting data. TypeScript strictly validates responses, distinguishes disabled from unsupported/invalid/native-failure, and omits raw native error details. Missing iOS implementation returns unsupported. The shell checks once on mount, ignores results after unmount and retries only by explicit action.

This is a temporary scaffold handshake, not `getCapabilities`, consent/configuration, collection health, history or a claim that Android's public sources are unsupported. The operation catalog remains unimplemented. Nine adapter tests cover version/type/state/extra-field corruption, absent module and sanitized native failure. Codegen/native round-trip and X-10 results are recorded separately; JS tests alone do not accept the bridge or screens.

AS-003's separate JVM reconciliation prototype has injected source/clock/commit ports and serial planning/retry decisions for synthetic experiments only. These are not native bridge operations or production consent/storage interfaces. Planner pagination never means processed history; unresolved intervals and fixed stop reasons preserve failure distinctions. Only caller-qualified source failures can request bounded backoff, with no permission retry loop.

Its pure per-range receipt experiment adds bounded recovery and scope/request/revision-pinned proposals. `Queried` and `Excluded` receipts cannot substitute for each other. Acceptance validates a permitted prefix or exact exclusion, retaining unresolved siblings; the candidate snapshot must be assigned with facts/health in the same coordinator transaction. Only tests simulate that transaction. Same-scope stale proposals require fresh tickets; changed scopes stop recovery without migrating receipts or authorizing backfill. These types add no public operation, capability registry or transport shape. Durable progress/exclusion, scope migration, production correction and retry recovery contracts remain future work.

Pure `beginCorrection` proposes a new bounded overlap pass after same-scope completion, carrying no old receipts and incrementing the revision. It performs no query or installation; the coordinator must compare previous scope/revision before assigning the candidate while retaining facts/health. Repeated scope/window values never reset revision. The caller pins the target and chooses invocation; a capped end leaves the rest of that target unprocessed, not acknowledged. Scope-change fixtures require independently rebuilt effective consent and a fresh revision, with no receipt migration or backfill permission. These are JVM experiment values only, with no bridge, durable job or scheduling contract.

## Common types

The following TypeScript describes public semantics. It is a contract, not executable application code. Generated TurboModule declarations may use flattened tagged records and JSON-free typed structures where Codegen requires them. Validate every decoded value in Kotlin and at the TypeScript boundary; Zod alone cannot protect native callers.

```ts
type EpochMs = number; // finite safe integer, UTC milliseconds
type Revision = string; // opaque monotonic generation/revision, never JS bigint
type CapabilityId = string; // registry ID, not a platform permission name
type State = 'unsupported' | 'disabled' | 'paused' | 'needs_permission'
  | 'collecting' | 'degraded' | 'unavailable';
type EvidenceKind = 'observed' | 'estimate' | 'classification' | 'prediction';
interface TimeRange { startMs: EpochMs; endMs: EpochMs } // [start,end)
interface Mutation { commandId: string; expectedRevision: Revision }
interface CapabilityStatus {
  id: CapabilityId; state: State; reasonCodes: string[];
  grantState: 'granted' | 'denied' | 'not_applicable' | 'unknown';
  configuredEnabled: boolean; lastSuccessMs: EpochMs | null;
  verifiedRange: string | null; revision: Revision;
}
interface Coverage { knownMs: number; eligibleMs: number; gaps: TimeRange[] }
interface PageRequest {
  range: TimeRange; appIds: string[]; kinds: string[];
  limit: number; cursor: string | null; zoneId: string;
}
interface Page<T> { items: T[]; nextCursor: string | null; revision: Revision }
interface NativeError {
  code: string; retryable: boolean; capabilityId: string | null;
  correlationId: string; messageKey: string; // no captured content
}
```

ID and revision strings are opaque; local-only random installation epoch is not an advertising identifier. Results use a discriminated success/error envelope. Numeric durations are milliseconds; counts are nonnegative safe integers; unavailable measures are null with reason codes, not zero. Validate range length, package filters and limits; cap pages at 500, default 100. Long operations return job IDs with cancellation and status queries.

Capability results must distinguish query freshness, metric coverage and live-signal availability under [Collection feasibility](COLLECTION-FEASIBILITY.md). `lastSuccessMs` means the operation succeeded, not that every event/metric is covered; an empty query does not justify a measured zero. History provenance preserves absent event-time zone/boot/elapsed fields and the [model's time basis](DATA-MODELS.md#time-evidence-and-precision). These are requirements for the pending full contract; the version-1 bootstrap remains unchanged.

## Operation catalog

### AS-043 narrow preview contract

ADR-044 advances the earlier snapshot to **contractVersion 4**, retaining the contract-3 diagnostic flag and adding required strict `counterAppearance: {showIcons: boolean, allAppsToday: boolean}`. `setCounterAppearance(showIcons, allAppsToday, expectedRevision)` atomically saves only native display preferences and returns a fresh snapshot; stale revisions fail without overwriting newer choices. It does not enable sources/overlay, reset an active visit or alter consent. Native HUD projection and expanded category day totals honor the scope even when JS is stopped. Defaults are icons on and all observed apps for Today; session stays current-app. Source notice remains 2; Room schema becomes 3 for the two display fields. Historical contract-2/3 descriptions follow.

ADR-043 advances the narrow snapshot to **contractVersion 3**, adding required boolean `diagnosticsAvailable`, derived only from native `BuildConfig.DEBUG`. The JS screen offers the temporary structure check only when true; `calibrationEnabled=true` with availability false is rejected. Bundled development previews have JS development mode disabled, so `__DEV__` is not the authority. Release native code also rejects enabling diagnostics. This changes no observation fields/source notice (still 2), store schema (still 2), counts or bootstrap contract. Contract-2 references below describe the earlier category increment, not the current transport version.

The snapshot distinguishes `accessibilitySelected` (this component remains in Android's persisted choice) from `accessibilityEnabled`/`serviceConnected` (operational availability). A selected but disconnected/crashed service is not reported as usable or treated as a fresh grant request. Temporary-check failure/prerequisites and active status appear beside that control. Developer inspection of the flat file may include at most 32 static-ID clickable-control bounds; these are numeric layout metadata, never labels/content, and the host rejects extra fields or out-of-bounds structures.

ADR-041 extends only the independently selected temporary diagnostic with `geometryRevision: 1`, integer `viewport` bounds and up to 16 allowlisted `anchors`. Each anchor has static `id`, scan-local `slot`, integer bounds/collection row/column and at most three nearest `ancestors` with the same fields, without recursive children. Slot associations expire with the snapshot; they never enter history or become content identity. The host accepts the exact extension or historical flat format and rejects partial revisions/extra fields/invalid bounds or ancestry. The updated diagnostic notice names the fields before consent. The 192-node/32-KiB/ten-minute limits and source/epoch/stop/process fences remain; bridge contract 2 is unchanged. See [adapter guide](SOCIAL-ADAPTERS.md).

The experimental `NativeReels` TurboModule is separate from the unchanged bootstrap handshake and the full operation catalog below. `getSnapshot` returns bounded JSON (8 KiB maximum): generation, configuration `revision`, independent `historyRevision`, storage/service/grant health, day/zone, overlay/temporary-diagnostics state and exactly four source states: Instagram, Facebook, YouTube Shorts and TikTok. Counts are canonical nonnegative signed-int64 **decimal strings**, validated without conversion to JavaScript numbers; unsupported zero observations display unavailable. Both revisions belong to the returned generation. Polling occurs only while the screen is focused and the app is active.

`configure(app, enabled, noticeRevision=2, expectedRevision)`, `setPaused`, `setOverlay` and `deleteHistory` commit native state before returning a fresh snapshot. Snapshot contract 2 adds per-source `consentCurrent` and strict decimal-string category counts (`reel`, `photo`, `ad`, `unclassified`; unsupported normal-feed `post` is null). `today`/`visit` count all recorded viewer pages, explicitly including ads. Old notice grants remain recorded but do not authorize new category collection; the screen requires a fresh affirmative source choice. Configuration revisions prevent stale settings from overwriting newer choices; exposure writes advance only history revision. This narrow bridge has no command-ID replay/event/paging/archive contract and does not implement those full-product operations. Errors are sanitized stable codes. `openAccessibilitySettings` and `openTarget` return launch success, never permission or source enablement. `setCalibration` controls an independently consented, process-local ten-minute structure check. Native code rechecks source choice/current notice and actual grant before observation. See [AS-043 scope](STATE.md#active-priority-installed-reels-counter).

ADR-035's native-only overlay projection adds committed visit/day category counts from two grouped authoritative-store queries. It is separate from the JSON bridge contract and contains no item content. The compact icon/count header expands in place; its pause and hide controls use the same revision-checked native operations as the dashboard. Hide changes display only; pause fences observation. Unknown foreground, lock, source/privacy changes or an invalid snapshot remove the entire panel.

| Operation | Request | Result and side effects |
|---|---|---|
| getCapabilities | none | Registry with status per capability, detector versions, service health, current revision and check time |
| openPermissionSettings | capabilityId | launched/unsupported/unavailable; only opens a user-visible system route; never claims grant |
| getTrackingConfiguration | none | Consent revision, sources, filters, detail, pause ranges, retention and effective times |
| setTrackingConfiguration | Mutation + complete proposed config + disclosure revisions | Validated committed config/revision; opt-ins must have explicit affirmative consent; in-flight collectors observe revision fence |
| queryHistory | PageRequest | Page of normalized events/sessions with evidence kind, provenance IDs, uncertainty and source coverage |
| queryAggregates | range, granularity, zone, app/category/surface filters | Bounded bins with counts/durations/nulls, coverage, grouping revision and taxonomy version |
| getInsights | range, insight kinds | Explanations, parameters, evidence references, coverage, confidence class, or insufficient_data reasons |
| setCategoryMapping | Mutation + app + category + historical scope | Mapping version; invalidates only affected classified views |
| getProtectionRules | none | Native rules/revisions, resolved targets and latest enforcement health |
| previewProtectionRule | proposed rule + evaluation instant | Conflicts, permission needs, exclusions, next schedule boundary; no activation |
| upsertProtectionRule / deleteProtectionRule | Mutation + rule or ID | Atomic config revision, rule validation and schedule reevaluation |
| startFocus / stopFocus | Mutation + target snapshot and deadline / session ID | Focus session and effective rule set; JS need not stay alive |
| grantOverride | Mutation + explicit rule IDs, targets, expiry, optional local reason | Scoped token, duration and affected rules; never resets usage |
| emergencyBypass | commandId + active scope | Immediate persisted bypass and renderer dismissal; no delay, optional reason or biometric prerequisite |
| getStorageSummary | none | Bytes and row counts by source/detail; archive freshness, pending cleanup and free-space estimate |
| deleteData | Mutation + preview token + app/source/time/detail scope | Job ID; fences ingestion, deletes derivatives, invalidates models/backup freshness |
| prepareExport | scope + recovery-credential handle + format | Job ID; consistent snapshot and encrypted staging; no plaintext through JS |
| chooseExportDestination | jobId | System picker result; URI handle stays native and is capability-scoped |
| inspectImport / commitRestore | picker handle + recovery-credential handle / validated preview token + Mutation | Job progress and sanitized manifest preview / atomic replacement result with new generation |
| getBackupConfiguration / setBackupConfiguration | none / Mutation + selected approved transport configuration | Manual mode available when built; automatic Drive returns POLICY_BLOCKED until gate clears |
| getJob / cancelJob | jobId | queued/running/waiting_user/succeeded/failed/cancelled, stage and progress; cancellation only before commit point |
| getChangeState | lastSeenRevision | Current generation and invalidated domains; permits recovery from dropped events |
| getLiveOverlayConfiguration / setLiveOverlayConfiguration | none / Mutation + selected targets, fields, renderer, placement and hideUntil | Explicit off-by-default config; no implied source/protection enablement or new grants |
| getLiveSurfaceSnapshot | selected app/surface and explicit day zone | Committed LiveSurfaceSnapshot or unsupported/unavailable; renderer does not need JS |
| getMetricCatalog | optional app/platform filters | Metric definitions, units/revisions, inspected/retained fields, source requirements and per-target availability; no source enablement |
| queryActivityDiary | bounded PageRequest + primary surfaces/facets/action families | Covered episodes and separately labelled UI outcomes/attempts, provenance and retained resolution; no private input/message fields |
| queryMetricSeries | metric IDs, bounded range, granularity/zone, dimensions | Nullable values or decimal strings, evidence/revision/freshness/coverage; compatible base numerator/denominator; split incompatible definitions |
| queryNetworkUsage | app/transport/range and supported bucket mode | OS RX/TX bytes, actual bucket scope, freshness and attribution/gap reasons; shared/unknown UID app mapping unavailable |
| previewRetentionPolicy / commitRetentionPolicy | proposed scoped tiers/statistics/zone / Mutation + validated preview | Lost detail/dimensions, estimated bytes, current revision / config and staged compaction job; no pruning at preview |
| queryRetainedResolution | metric/dimension/range | Detailed/summary partitions, fixed zones/versions and deletion/regrouping limits; no fabricated drill-down |
| inspectActivityArchive / importActivityArchive | native picker handle + allowed scope / Mutation + validated preview | Future bounded parser/schema/source preview / staged import; distinct from app restore, no blind merging with observed data |

Recovery credentials use a dedicated secure native entry flow or one-use handle; no plaintext credentials in generic command bodies, logs, JS state persistence, crash reports or change events. Permission navigation is distinct from tracking enablement. No method silently grants access or reads beyond declared scope.

Under [ADR-031 prototype design](CRYPTO-ARCHIVE-DESIGN.md), generated-key credentials are the initial path and the optional passphrase profile stays unavailable until qualified. Native handles are job-scoped and invalidated after completion/cancel; re-entry is required for a new attempt. Job progress distinguishes ciphertext authentication, staged validation, preview and durable activation. A resource limit returns RESOURCE_LIMIT with a sanitized reason, never successful partial export/import. There is no crypto operation in the current bootstrap bridge.

Current AS-048 catalog admission uses **contractVersion 5**, requiring four unique known source keys (`instagram`, `facebook`, `youtube`, `tiktok`). Missing, duplicate, unknown or historical two-source payloads are rejected. Room schema stays 3; missing source rows are inserted disabled with no consent in the existing transaction, preserving old history, consent, generation and display preferences. A catalog addition on an existing store advances configuration revision once. Source notice 3 applies independently to each source; unavailable detectors never become supported through enablement.

## Live awareness read model

Detailed metric responses follow [Analytics](ANALYTICS-SPEC.md) and [Metric catalog](METRIC-CATALOG.md). Signed net-interaction deltas are explicitly derived values, not nonnegative base counts. Rates carry source numerator/denominator and undefined reasons. Retained summaries expose available resolution/zone, never pretend raw paging is possible. New contract operations are specifications only; AS-035 implements pure calculations, not this bridge.

Planned LiveSurfaceSnapshot includes generation, revision, capturedAtMs, lastSourceEventAtMs, appId, surfaceId, episodeId?, dayStartMs, dayEndMs, zoneId, countKind, visitExposureCount?, todayExposureCount?, visitKnownSurfaceMs?, todayKnownSurfaceMs?, coverage and reasonCodes. Counts/time are nullable nonnegative values; absence differs from zero. Coverage distinguishes complete/partial/unknown; any displayed lower-bound claim needs explicit evidence that the value is a lower bound, not merely a noisy estimate. Freshness uses the source check/event age, not the renderer's last paint.

A native-only snapshot subscription can update the renderer from committed history; a `liveSurfaceChanged` invalidation hint can refresh an open JS settings/detail view. Coalesce display updates independently of observation ingestion. Recover dropped updates by requerying the latest revision/generation; never add deltas twice. Removal of a qualified surface invalidates display eligibility immediately when observed. No content payload or credentials in snapshots/events. See [overlay semantics](LIVE-OVERLAY-SPEC.md); no method is implemented by the browser prototype.

## Paging and revisions

History ordering is `(occurredAtMs, stableId)`. Cursors are opaque, scoped to query/filter/zone, snapshot revision and database generation, with a bounded lifetime. Mutation after a read either retains snapshot semantics or returns STALE_CURSOR; choose and document the implementation consistently. Foundation choice: invalidate cursors on relevant history mutation and return STALE_CURSOR, requiring a fresh first page. Unrelated theme changes do not invalidate them. Restore always changes generation. Never use OFFSET paging over million-row histories as the primary strategy.

Idempotent mutations record commandId and result within the transaction. Retrying the same ID with identical payload returns the prior outcome; differing payload returns IDEMPOTENCY_CONFLICT. A stale expectedRevision returns REVISION_CONFLICT and current revision without overwriting newer configuration. Persisted command records have a bounded retention; expired command IDs return status unknown rather than replaying a destructive operation silently.

## Events, errors and cancellation

Events: capabilitiesChanged, configurationChanged, historyChanged, aggregatesInvalidated, protectionChanged, storageChanged, backupJobChanged. Payload: contract version, generation, revision, changed domain IDs, optional job ID and timestamp. Coalesce noisy events; they are invalidation hints and not durable history transport. A reconnect must query getChangeState; JS must handle dropped/reordered events and dispose subscriptions on unmount.

Stable errors: UNSUPPORTED, PERMISSION_REQUIRED, CONSENT_REQUIRED, SOURCE_PAUSED, SERVICE_UNAVAILABLE, INVALID_ARGUMENT, REVISION_CONFLICT, IDEMPOTENCY_CONFLICT, STALE_CURSOR, STORAGE_FULL, RESOURCE_LIMIT, KEY_UNAVAILABLE, POLICY_BLOCKED, BACKUP_AUTH_FAILED, ARCHIVE_CORRUPT, FORMAT_UNSUPPORTED, CANCELLED, BUSY, INTERNAL. Wrong credentials and authentication corruption may share a generic user message to avoid misleading certainty. Only explicitly retryable failures retry with bounded backoff; no retry loop can repeatedly prompt for a permission or credential.

Queries accept cancellation; native work must check it between bounded batches. Cancellation before restore commit preserves live data; after commit returns the committed generation and does not fake rollback. No raw SQLite, stack trace, node text, path containing account details or provider token appears in public errors. Unsupported platforms return typed unavailable states, never successful empty history. Tests cover contracts, event loss, stale revisions/cursors, duplicate commands, and restore generations.

The test-only [self-package cancellation protocol](COLLECTION-FEASIBILITY.md#self-package-timing-and-cancellation-protocol) injects requests before query dispatch, after a synchronous response and during iteration. It adds no public job operation. Cooperative checks can discard staged results after cancellation; they cannot interrupt the public UsageStatsManager call or promise a bounded cancellation response time while it blocks. Production cancellation still needs the serialized consent/commit boundary and independent runtime qualification.

All three injected boundaries and validated aggregate measurements now pass on the physical API-36 phone, with instrumentation `-1` and test package cleanup. This is test-harness evidence; the bootstrap response and public cancellation contract remain unchanged and production behavior remains unqualified.

The AS-043 internal overlay projection also carries a transient backward-position animation hint (ADR-040). It is derived from settled numeric pager positions, is not persisted or exposed as content identity, and changes neither bridge contract 2 nor committed totals. The renderer scrolls retained old/new values in a clipped viewport; unchanged snapshots do not restart motion.

ADR-049 keeps snapshot contract 5 and Room schema 3. Configure validates the source-specific notice: TikTok 4, other sources 3. Old TikTok consent cannot authorize content comparison. Comparison tokens never cross the bridge; temporary TikTok page values are used only by the reducer and are not stored; saved exposure ordinals enumerate committed visit encounters, not content identity.
