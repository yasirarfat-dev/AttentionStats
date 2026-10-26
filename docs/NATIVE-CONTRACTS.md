# Native contracts

**Status:** Normative domain interface draft; Codegen syntax and transport implementation pending. Updated 2026-10-26.
**Responsibility:** Defines typed operations, status, revisioning, events, errors and platform behavior.
**Related specifications:** [Architecture](ARCHITECTURE.md), [Models](DATA-MODELS.md), [Tracking](TRACKING-SPEC.md), [Backup](BACKUP-RESTORE.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-02–F-04 and F-07 in [Decisions](DECISIONS.md); logical unions must be lowered into supported Codegen primitives without changing meaning.

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

## Operation catalog

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

Recovery credentials use a dedicated secure native entry flow or one-use handle; no plaintext credentials in generic command bodies, logs, JS state persistence, crash reports or change events. Permission navigation is distinct from tracking enablement. No method silently grants access or reads beyond declared scope.

## Live awareness read model

Planned LiveSurfaceSnapshot includes generation, revision, capturedAtMs, lastSourceEventAtMs, appId, surfaceId, episodeId?, dayStartMs, dayEndMs, zoneId, countKind, visitExposureCount?, todayExposureCount?, visitKnownSurfaceMs?, todayKnownSurfaceMs?, coverage and reasonCodes. Counts/time are nullable nonnegative values; absence differs from zero. Coverage distinguishes complete/partial/unknown; any displayed lower-bound claim needs explicit evidence that the value is a lower bound, not merely a noisy estimate. Freshness uses the source check/event age, not the renderer's last paint.

A native-only snapshot subscription can update the renderer from committed history; a `liveSurfaceChanged` invalidation hint can refresh an open JS settings/detail view. Coalesce display updates independently of observation ingestion. Recover dropped updates by requerying the latest revision/generation; never add deltas twice. Removal of a qualified surface invalidates display eligibility immediately when observed. No content payload or credentials in snapshots/events. See [overlay semantics](LIVE-OVERLAY-SPEC.md); no method is implemented by the browser prototype.

## Paging and revisions

History ordering is `(occurredAtMs, stableId)`. Cursors are opaque, scoped to query/filter/zone, snapshot revision and database generation, with a bounded lifetime. Mutation after a read either retains snapshot semantics or returns STALE_CURSOR; choose and document the implementation consistently. Foundation choice: invalidate cursors on relevant history mutation and return STALE_CURSOR, requiring a fresh first page. Unrelated theme changes do not invalidate them. Restore always changes generation. Never use OFFSET paging over million-row histories as the primary strategy.

Idempotent mutations record commandId and result within the transaction. Retrying the same ID with identical payload returns the prior outcome; differing payload returns IDEMPOTENCY_CONFLICT. A stale expectedRevision returns REVISION_CONFLICT and current revision without overwriting newer configuration. Persisted command records have a bounded retention; expired command IDs return status unknown rather than replaying a destructive operation silently.

## Events, errors and cancellation

Events: capabilitiesChanged, configurationChanged, historyChanged, aggregatesInvalidated, protectionChanged, storageChanged, backupJobChanged. Payload: contract version, generation, revision, changed domain IDs, optional job ID and timestamp. Coalesce noisy events; they are invalidation hints and not durable history transport. A reconnect must query getChangeState; JS must handle dropped/reordered events and dispose subscriptions on unmount.

Stable errors: UNSUPPORTED, PERMISSION_REQUIRED, CONSENT_REQUIRED, SOURCE_PAUSED, SERVICE_UNAVAILABLE, INVALID_ARGUMENT, REVISION_CONFLICT, IDEMPOTENCY_CONFLICT, STALE_CURSOR, STORAGE_FULL, KEY_UNAVAILABLE, POLICY_BLOCKED, BACKUP_AUTH_FAILED, ARCHIVE_CORRUPT, FORMAT_UNSUPPORTED, CANCELLED, BUSY, INTERNAL. Wrong credentials and authentication corruption may share a generic user message to avoid misleading certainty. Only explicitly retryable failures retry with bounded backoff; no retry loop can repeatedly prompt for a permission or credential.

Queries accept cancellation; native work must check it between bounded batches. Cancellation before restore commit preserves live data; after commit returns the committed generation and does not fake rollback. No raw SQLite, stack trace, node text, path containing account details or provider token appears in public errors. Unsupported platforms return typed unavailable states, never successful empty history. Tests cover contracts, event loss, stale revisions/cursors, duplicate commands, and restore generations.
