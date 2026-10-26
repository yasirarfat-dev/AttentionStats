# Backup and restore

**Status:** Portable encrypted archives planned; direct automatic Drive integration blocked. Updated 2026-10-27.
**Responsibility:** Defines backup experience, format requirements, transports, recovery and failure atomicity.
**Related specifications:** [Security](SECURITY.md), [Privacy](PRIVACY.md), [Storage](STORAGE-AND-RETENTION.md), [Contracts](NATIVE-CONTRACTS.md), [Testing](TESTING.md).
**Unresolved feasibility:** F-04–F-06 in [Decisions](DECISIONS.md); crypto format, provider durability and automatic-backup policy must be resolved independently.

## Portable encrypted archive: MVP

Export/import is available without a product account or cloud integration once implemented. User chooses included apps/sources/time ranges, basic history versus separately consented content, and whether protection configuration is included. Preview size, scope and known omissions. Generate a consistent snapshot at a known revision; do not copy an actively changing database/WAL as if it were a complete archive.

Use a documented versioned logical archive with authenticated encryption. Proposed manifest fields: format version, minimum reader version, schema/model revisions, creation UTC, snapshot generation/revision, data scope, entity counts, coverage, timezone representation and bounded crypto parameters. Sensitive manifest details live inside the encrypted payload; public header contains only necessary version/crypto framing. No provider token, device key, account email or raw recovery credential is included. [Security](SECURITY.md) owns algorithm review; no unreviewed cipher/KDF parameters become a shipping format.

A generated recovery key or reviewed passphrase flow is user-held. Show recovery consequences and verify a round trip before advertising backup as ready. Device Keystore protection can secure a local remembered credential for scheduled use, but restoration on another device must not require the old Keystore. Lost credentials cannot be reset by the developer. Credential changes apply to future archives unless older archives are explicitly re-encrypted.

Export stages: preflight capacity → snapshot → bounded serialization → encryption → native staging → selected transport write → completion confirmation. Staging is private/no-backup and encrypted, cleaned on success/cancel and after crash. A provider accepting bytes does not necessarily mean remote durability; show the strongest confirmed state and uncertainty. Never overwrite the only known-good external archive before the new file is complete; provider-specific atomicity needs a capability check.

## Optional summary-only scope

Under [Retention](STORAGE-AND-RETENTION.md#user-configurable-summary-tiers), a person may choose detail or selected day/week/month/year facts independently from local pruning. A summary-only archive manifest declares metric/semantic/taxonomy revisions, units, dimensions, fixed zone/week start, covered/eligible/unknown partitions, retained sufficient statistics and omitted detail/resolution. It contains no fabricated chronology, purged content or implied source enablement. A summary export does not compact the local store.

Restore validates partition authority/non-overlap and sufficient-statistic bounds before replacing live history. Do not double count included detail and its summaries or expand yearly facts into monthly detail. Preview lost drill-down and coarse-deletion limits. Format versions explicitly support or reject these entities; no fallback can silently discard them. D-06 applies alongside all B-01–B-07 failures. This extension remains planned after the reviewed base encrypted archive implementation; AS-035's pure rollup preview is not backup/crypto code.

## Automatic Google-account experience

The desired future experience is automatic encrypted backup into the person's selected personal Google account: opt in, choose account, choose scope, save/test recovery credentials, choose schedule (initial proposal daily, configurable), Wi-Fi/metered and charging constraints, and inspect last attempted/completed backup, byte size, revision and next eligible attempt. Android scheduling is best effort, never an exact nightly promise. Switching accounts confirms future destination and explains that earlier copies remain in the old account; disconnect cancels jobs, revokes local authorization and retains local history.

**Direct Drive integration is unverified and blocked pending policy clarification.** The current [Workspace API user data policy](https://developers.google.com/workspace/workspace-api-user-data-developer-policy) lists backup of app/user content from a developer's app among disallowed Drive-scope uses. Our intended use therefore conflicts with the published policy. Do not implement OAuth/upload or promise an appDataFolder exception without documented clarification applicable to this use case. No existing service's mechanism is assumed publicly reusable. F-05 requires dated authoritative clarification and review before choosing scopes, libraries or implementation.

Optional cloud backup transfers ciphertext and provider-visible metadata such as account, file size/name/timestamps and network request information. The developer receives neither. Local-only use stays complete without provider authorization. The provider's own storage/account policies and availability still apply.

## System-picker transport

Android SAF can expose local or cloud-backed document providers and user-selected file access. See [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider). Evaluate manual `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT` for encrypted export/import without broad storage access. Treat provider behavior, URI-grant persistence, interrupted writes, quota and deletion support as per-provider capabilities.

Picker-backed export is not equivalent to automatic Drive backup. Selecting a cloud provider can cause that provider to upload the encrypted file and expose metadata to it; disclose this. A persisted URI grant does not resolve a provider policy question or guarantee background scheduling, account selection, atomic replace or upload completion. MVP offers manual transport only after verification; scheduled picker-backed destinations are an independent future evaluation.

## Restore state machine

1. Select archive and enter recovery credentials in a secure native flow. Preflight file/expanded-size limits and free space without changing live data.
2. Validate framing and bounded KDF parameters, authenticate/decrypt into private staging, validate manifest/entity counts/references and supported versions. No rule or imported field can execute code.
3. Migrate the staging store through supported migrations. Reject future/incompatible formats. Produce a preview of replacement scope, age, omitted data and estimated size.
4. MVP restore is explicit **replacement**, not merge. Offer a current-data encrypted export before replacement. Require user confirmation of the concrete preview. This is an application UX requirement, not a request for repository-work approval.
5. Acquire native write barrier, pause ingestion/interventions, transactionally commit validated state or an interruption-safe generation swap, rebind database handles and increment generation. Retain a rollback-safe old generation until commit durability is confirmed.
6. Rebuild aggregates, invalidate cursors, preserve current device grants separately, and leave imported source consent and protection disabled until the person reviews/reactivates them. Imported data never grants current permissions or silently enables tracking. Resume only explicitly chosen current configuration.

Wrong credentials, corrupted tags/payloads, incomplete archives, cancellation, interrupted writes, insufficient storage, invalid references and unsupported versions must leave existing live data unchanged before the commit point. After a durable commit, report committed state even if UI delivery failed; replaying a job cannot import twice. Never delete the current store to “make room” for an import.

## Android automatic backup and transfer

Sensitive history, configurations, MMKV preferences, staging, keys and tokens must not leave through undocumented Android defaults. Set `allowBackup=false`, explicit legacy backup exclusions and modern data-extraction exclusions for cloud and device-to-device transfer across all used storage domains; use no-backup paths for staging/key wrappers where appropriate. Govern cross-platform transfer rules too where the target supports them. Avoid debug-only overrides leaking into release manifests.

Android documentation warns that some manufacturers may still allow device transfer when only allowBackup is false; explicit rules and real-device tests are necessary. See [Android Auto Backup](https://developer.android.com/identity/data/autobackup). The release gate tests merged manifest, database/WAL, shared preferences, files, MMKV locations, no-backup files, cloud restore and device transfer. Unexpected transfer blocks release. OS backup is not a fallback for the blocked product feature.

Acceptance B-01–B-07 and S-01 in [Testing](TESTING.md) includes fresh-device recovery, wrong credentials, corruption, interruption at each stage, format compatibility, permission reactivation and preservation of existing history. Cloud UI must clearly show blocked/unavailable today.
