# Encryption and archive design review

**Status:** AS-006 prototype design selected; no crypto/store implementation, interoperability result or security audit. Reviewed 2026-10-27.
**Responsibility:** Owns the proposed archive suite/framing, local key lifecycle, prototype limits and evidence required before format freeze.
**Related specifications:** [Security](SECURITY.md), [Backup](BACKUP-RESTORE.md), [Models](DATA-MODELS.md), [Storage](STORAGE-AND-RETENTION.md), [Dependencies](DEPENDENCIES.md), [Decision ADR-031](DECISIONS.md).
**Unresolved feasibility:** F-03/F-04 remain open. These are decisions for an isolated prototype, not a shipping format or encryption claim; AS-009/AS-020 acceptance remains separate.

## Scope and current evidence

ADR-031 selects Room 3 with SQLCipher's SQLiteDriver integration for qualification, and a provider-independent logical archive using Tink Streaming AEAD. Neither Room nor crypto is installed in the application. Generated recovery credentials are the first archive path to prototype. A passphrase profile is specified for evaluation and stays unavailable until its separate gates pass. No Drive, scheduled transport, new source consent or device operation is authorized by this design.

[Registry and artifact evidence](evidence/crypto-storage-review.json) records eight current stable candidates, POM/metadata hashes, four downloaded artifact hashes checked against repository digests, and source findings. Downloads remain in ignored repository-local `.cache/as-006`. Hash matches establish download consistency, not independent provenance or a vulnerability audit. No dependency graph, compiler, native build, benchmark or phone test ran for these candidates.

| Candidate / role | Stable on review date | Evidence and remaining compatibility work |
|---|---|---|
| Room 3 runtime/compiler | 3.0.3; Apache-2.0 | [Official release](https://developer.android.com/jetpack/androidx/releases/room3); inspected Android runtime minSdk 23/minCompileSdk 34. New `androidx.room3` namespace; Kotlin uses KSP. Latest 3.1 alpha is excluded by stable policy |
| SQLCipher Android Community | 4.19.1; BSD-style with required notices | Inspected AAR contains `net.zetetic.database.sqlcipher.driver.SQLCipherDriver`, minSdk 23/minCompileSdk 36 and four Android ABIs. [Tagged integration example](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/README.md) supports Room 3 `.setDriver`; its example versions lag registry stable. ABI presence does not qualify alignment or execution |
| AndroidX SQLite | 2.7.1; Apache-2.0 | Room 3 POM requests 2.7.1, SQLCipher POM requests 2.7.0; inspect actual Gradle resolution/API behavior before admission |
| KSP | 2.3.12; Apache-2.0 | [Release](https://github.com/google/ksp/releases/tag/2.3.12) raises minimum AGP to 8.12.0. Upstream SQLCipher tests use earlier Room/KSP; latest reviewed app stack still needs actual compilation |
| Tink Android | 1.24.0; Apache-2.0 | [Setup](https://developers.google.com/tink/setup/java) specifies Java 11 and full Android support from API 24, matching the scaffold's API floor. Streaming/keyset classes exist in inspected JAR; packaging, release shrinking and device behavior untested |
| Bouncy Castle lightweight Argon2 | bcprov-jdk18on 1.86; MIT | [Published release](https://www.bouncycastle.org/download/bouncy-castle-java/) and inspected JAR contain Argon2BytesGenerator. Optional evaluation only; Android D8/R8, memory overhead, cleanup and provider coexistence untested. Do not register/replace a global security provider |

Room 2.8.5 remains a comparison candidate, not a silent fallback from Room 3. SQLCipher 5 prereleases are also outside stable selection. Re-resolve latest stable and review licenses/transitives before adding any dependency. Preserve SQLCipher Community and bundled-library notices in user-accessible licensing materials; [upstream terms](https://www.zetetic.net/sqlcipher/license/) differ by edition. This repository's Apache-2.0 license does not replace dependency terms.

## Encrypted Room and device keys

Use the maintained `net.zetetic:sqlcipher-android` artifact, not the deprecated `android-database-sqlcipher` binding. Load its native library explicitly; configure Room 3 with SQLCipherDriver at the native composition root. Do not choose a bundled/plain framework SQLite driver when key access fails. Room, its presence in a dependency graph and device filesystem encryption do not by themselves establish encrypted history.

The prototype uses 32 cryptographically random bytes per database generation as SQLCipher key material. A native adapter must qualify SQLCipher's documented [raw-key representation](https://www.zetetic.net/sqlcipher/sqlcipher-api/) with the driver's byte-array API; passing arbitrary random bytes as a text passphrase is not evidence of raw-key mode. Use current SQLCipher defaults unless a separately reviewed migration requires otherwise; no disabled authentication, plaintext header or reduced password cost workaround. [SQLCipher design](https://www.zetetic.net/sqlcipher/design/) describes encrypted database/WAL pages but temp/spill behavior and actual build settings still need inspection on our binary.

Wrap each generation key using an installation-specific Android Keystore AES-256-GCM key, with a new platform-generated 96-bit nonce and 128-bit tag per wrap. Authenticate wrapper version, local installation epoch, database generation and alias/version using an unambiguous bounded native encoding. Store ciphertext/nonce/tag and nonsecret routing metadata atomically in credential-encrypted no-backup storage; never persist a plaintext key. The wrapper is local metadata, not a portable archive field. Failure to wrap/unwrap stops opening and mutation; it must not create an empty replacement DB.

The first prototype does not require biometric/per-use authentication for this wrapping key, because future native background work must not depend on UI unlock prompts. This is not an app-lock feature. Keep DBs, wrappers and staging in credential-encrypted app storage, unavailable before first unlock after reboot. Ordinary keyguard lock after first unlock is a different state. Hardware/StrongBox availability must be inspected and reported accurately; no universal hardware-backed claim. See [Keystore](https://developer.android.com/privacy-and-security/keystore) and [Direct Boot storage](https://developer.android.com/privacy-and-security/direct-boot).

Two source findings impose explicit adapter requirements:

- [SQLCipherDriver](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/sqlcipher/src/main/java/net/zetetic/database/sqlcipher/driver/SQLCipherDriver.java) retains the supplied key array and exposes a connection pool. Own the bytes until every connection/driver is closed; immediate clearing could break later opens. Minimize copies, then clear owned arrays on close. Managed/native copies cannot be promised erased.
- [DefaultDatabaseErrorHandler](https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/sqlcipher/src/main/java/net/zetetic/database/DefaultDatabaseErrorHandler.java) contains file deletion/path logging and a `hasCodec()` early-return guard. Do not assume its behavior is harmless on this build. Supply a non-destructive error handler for live and staged databases, stop writes and preserve files for recovery. Test wrong keys and corruption separately; never rely on the default guard or destructive Room recovery/migration.

WAL/SHM/journals, sort/temp spill, attached databases, crash dumps and logging need synthetic sentinel inspection. SHM/metadata confidentiality cannot be inferred from encrypted WAL pages. One coordinator owns pool closure, migrations and generation activation. No plaintext export/copy, raw path in public errors, destructive fallback or unreviewed in-place rekey is allowed.

## Archive credentials and suite

Draft format version 1 / suite 1 uses one fresh Tink `AES256_GCM_HKDF_1MB` streaming keyset per archive. It contains exactly one enabled primary RAW key with 32-byte key material, SHA-256 HKDF, 32-byte derived AES key and 1,048,576-byte ciphertext segments. A fresh AES-256-GCM wrapping key encrypts the bounded keyset; payload encryption uses the streaming primitive. Libraries generate AEAD nonces, stream salt/prefix and segment sequencing; the application must not implement segment cryptography or resume encryption into an existing ciphertext. See [Streaming AEAD](https://developers.google.com/tink/streaming-aead) and its [specified construction](https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming).

| Credential profile | Input and derivation | Availability gate |
|---|---|---|
| 1: generated recovery key | 32 random bytes, displayed as canonical unpadded base64url (43 characters); decode to exactly 32 bytes with canonical trailing bits. No human-chosen string in this mode | First prototype; native secure display/re-entry and fresh-install round trip required |
| 2: optional passphrase | Strict UTF-8, preserve spaces/case, no normalization or trimming; reject invalid Unicode. 15–1,024 Unicode scalar values and at most 1,024 UTF-8 bytes. Argon2id v1.3 (`0x13`), m=65,536 KiB, t=3, p=4, 16-byte salt, 32-byte output; no secret/extra associated input | Unavailable until RFC vectors, packaging, minimum-API/device memory/time and cancellation behavior pass; never auto-fallback to a cheaper KDF |

Profile 2 follows the memory-constrained recommendation in [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106.html); its memory setting excludes implementation/runtime overhead. Length rules do not guarantee passphrase entropy. Use Argon2 output as input key material for profile 2; use decoded random key bytes for profile 1. For both derive a 32-byte wrapping key with HKDF-SHA256, the archive's 16-byte salt and exact ASCII info `AttentionStats/archive/v1/wrap` (no terminator), using a library implementation of [RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html). HKDF does not make a weak passphrase safe. The profiles share one salt field because only one profile is used per archive.

Only exact supported profiles/parameters are accepted before KDF invocation; no attacker-selected cost, lane count or iteration loop. Profile 1 encodes zero for all Argon fields. Profile 2 encodes only the tuple above and is rejected as unavailable while gated. A future cost/suite change requires explicit version/profile support, not a permissive arbitrary-parameter reader. No automatic retry on bad credentials. There is no developer recovery or Keystore dependency in either portable profile.

## Draft envelope and authentication

This layout is a prototype contract pending vectors/review; no compatible shipping archive exists. Integers below are unsigned big-endian and checked for overflow before allocation. Strings in authentication domains are literal ASCII; `\0` means one zero byte, not two printable characters.

| Order in fixed prefix P | Size / permitted value |
|---|---|
| Magic | 8 bytes: `41 54 54 4e 41 52 43 00` |
| Envelope version / suite | uint16 = 1 / uint16 = 1 |
| Credential profile / flags / reserved | uint8 = 1 or 2 / uint8 = 0 / uint16 = 0 |
| Argon memory KiB / passes / lanes | Three uint32 values; profile-specific exact tuple |
| Salt / archive identifier | 16 random bytes / 16 independently random bytes per export |

P is exactly **60 bytes**. Follow with uint32 length L, then L bytes W, then one Tink streaming ciphertext C to physical EOF. Accept 1 ≤ L ≤ 4,096; reject missing/unknown versions, flags or values before any expensive work. No original filenames, app IDs, history times, account details or manifest fields belong in P. File size and crypto/version/profile metadata remain visible; this design provides no size padding or external-copy revocation.

W is Tink's binary encrypted-keyset serialization, produced/read through its [keyset-format API](https://github.com/tink-crypto/tink-java/blob/v1.24.0/src/main/java/com/google/crypto/tink/TinkProtoKeysetFormat.java) with AES-256-GCM in RAW/no-output-prefix mode and associated data `AttentionStats/archive/v1/keyset\0 || P`. Do not deserialize secret keyset content until authenticated decryption succeeds. Validate the resulting single-key type/parameters above; an authenticated keyset is not permission to load arbitrary primitive types. Tink's outer EncryptedKeyset metadata is routing information only, never trusted history or algorithm authority; [wire format](https://developers.google.com/tink/wire-format) defines the library encoding.

C uses associated data `AttentionStats/archive/v1/payload\0 || P || uint32(L) || W`, with the exact received bytes, not a parsed/reserialized form. Thus the payload binds the entire envelope, including length and encrypted-keyset metadata. Each new export generates independent key material, salt and identifier. Reordered, duplicated, removed or spliced segments must fail; all stream errors propagate. Require authenticated finalization and physical EOF, including rejection of trailing bytes. Do not accept an authenticated prefix as a complete archive. Changing an old credential requires a complete new archive; no in-place header edit or ciphertext append.

## Logical payload and restore validation

Inside C use a bounded record stream: uint8 kind, uint32 UTF-8 body length, then that many bytes. Exactly one manifest (kind 1) comes first, zero or more entity records (kind 2) follow, and one end record (kind 3) terminates plaintext. Every body is one strict JSON object. Unknown kinds, duplicate object keys, unsupported entity/schema revisions, extra records after end, missing end or trailing plaintext fail. No ZIP, compression, filesystem paths, attachments, executable types or arbitrary polymorphic deserialization in version 1.

The encrypted manifest carries format/minimum-reader/model revisions, creation time, snapshot generation/revision, scope/omissions, supported entity order/counts, source/coverage/time-zone semantics and any supported summary resolution. End repeats exact per-type/total counts and snapshot revision; the reader compares them with observed rows and manifest. These are consistency checks under AEAD, not substitutes for authentication. Schema-specific record definitions and migrations remain AS-009/AS-020 work and must be frozen with vectors before export can ship.

Use schema allowlists and explicit null/enum/integer/string/reference bounds. Store 64-bit integers as canonical decimal strings and require checked signed-64-bit parsing plus domain-specific limits; never round through JS/JSON floating point. Check IDs, duplicates and references using indexed encrypted staging tables rather than an unbounded in-memory graph. Do not include local key wrappers, Keystore aliases, provider credentials, live grants, ingestion cursors, command replay state or rebuildable caches. Keep consent/provenance as historical evidence; imported source enablement and rule activation stay off. Summary-only entities are rejected until explicitly supported; later support must conserve non-overlapping authority under D-06.

Restore copies a bounded provider input once to a private encrypted-archive file and closes the provider. Treat that file as immutable and owned exclusively by the job. First decrypt/authenticate the entire stream to a discard buffer, enforce plaintext size, require final tag/EOF, and parse no history. Only then rewind the same local file and decrypt again into a fresh independently keyed encrypted staging database with bounded parsing. Authenticate the second pass as well. This avoids treating a valid prefix or changed provider response as a validated archive; it costs two reads and still needs TOCTOU/crash tests. Never write plaintext staging to disk.

Validate all rows/references/counts and supported staged migrations before exposing a replacement preview. Revision-bound confirmation then acquires the write barrier and activates the staged generation; no partial import touches live authority. Export reads one consistent revision into encrypted staging; do not copy an open DB/WAL. Bound snapshot duration/WAL growth, abort safely if capacity is exceeded, and release the read transaction before provider interaction. Do not silently omit records to stay within limits.

## Prototype resource limits

These are conservative admission limits/measurement targets, not observed performance guarantees. A smaller runtime quota may reject an operation explicitly; it must never truncate a successful archive. Increasing wire/parser limits requires review and compatibility evidence before format freeze.

| Resource | Prototype limit / behavior |
|---|---|
| Simultaneous sensitive jobs | One export/import/rotation job per process; conflicting mutation returns BUSY |
| Ciphertext / plaintext | At most 8 GiB + 8 MiB / 8 GiB; count actual bytes even when provider size is absent or false |
| Wrapped keyset / record / JSON nesting | 4 KiB / 64 KiB body (including manifest/end) / 16 levels; enforce before allocation/descent |
| Record structure / entities | At most 1,024 object members or array elements per container, 128 entity types, 10,000,000 entity records; enforce global bytes too |
| Local job files | Total ciphertext plus encrypted staging/WAL/journal/rollback job files ≤12 GiB, with ≥256 MiB free-space reserve; never count live history as reclaimable capacity |
| Snapshot WAL growth | Abort export on more than 256 MiB additional WAL growth; choose narrower scope or retry, no history purge |
| Crypto working memory | Measure added peak memory: target ≤32 MiB generated-key path / ≤128 MiB passphrase path, excluding existing app/store baseline; reject unsupported devices without lowering KDF cost |
| Credential work | Exact profiles only; Argon2 target ≤3 seconds on declared minimum device, unmeasured. One attempt at a time; cancellation waits for a non-interruptible library call to finish and discards its result |
| I/O and cancellation | Native worker; bounded buffers/records, check cancellation between reads/batches. Provider calls may block; define/test timeouts separately, never promise immediate interruption |

Use checked arithmetic for byte/row multiplication and cumulative limits. Recheck disk headroom while growing files; free space can change concurrently, so handle ENOSPC even after preflight. On a limit failure preserve live state, return sanitized RESOURCE_LIMIT (STORAGE_FULL for exhausted space), and offer a smaller explicit scope. Do not start a KDF when the declared memory budget cannot be reserved. Clear owned secrets/buffers as practical; no guaranteed JVM/native forensic wiping claim.

## Loss, rotation and crash recovery

Keystore loss/invalidation, absent wrapper, failed tag and corrupt database are distinct native health conditions even when user-facing wording is cautious. Stop collection/mutation and preserve encrypted generations. Offer retry where meaningful, portable restore with user credentials, or separately confirmed full reset. No silent reinitialization, key guessing, default key or recovery upload. Without a usable local key or separately held archive credentials the developer cannot recover history.

Wrapping-key rotation creates a new Keystore alias, wraps the existing DB key with a fresh nonce/AAD, flushes and verifies the new wrapper, then atomically activates its version. Keep the prior wrapper/alias until activation/reopen durability is confirmed. A crash must select a verified committed wrapper; it must not delete a still-needed alias. This changes local protection, not the database key or external archives.

Database-key rotation uses a newly keyed encrypted staging generation and validated logical copy under the coordinator; no first-version in-place rekey. Recheck source revision, close every pool/handle, flush staged files and atomically switch a durable generation pointer. Restore uses the same activation mechanism. Before activation, old generation remains authoritative; afterward replay reports the committed new generation. Keep rollback material until successful reopen and durable activation confirmation, then perform idempotent encrypted cleanup. Directory fsync/rename and interruption behavior need actual Android qualification; a documentation state machine does not prove filesystem atomicity.

Startup resolves interrupted jobs before opening collection. Abandoned staging is removed only when it is outside the committed/rollback generations; use bounded job-owned names and no archive-supplied paths. Full reset destroys all local generation/wrapping keys, job files and authorizations through its explicit workflow. Logical deletion and local key destruction cannot erase flash remnants or revoke user-owned external archives. Credential rotation applies to newly created archives; replace older copies only by explicit re-export/re-encryption with verification.

## Qualification before acceptance

AS-006 remains experimental. The next reviewable slice is an isolated synthetic Room 3/KSP/SQLCipher build and physical-phone fixture using the non-destructive handler, distinct test-only encrypted files and complete cleanup. It must not create a production history schema or read personal history. Preserve F-03 minimum-API and expanded ABI gaps; an API-36 pass alone cannot qualify API 24.

Required subsequent evidence: RFC HKDF/Argon2 vectors; independently readable envelope vectors; empty/exact-segment/large-stream round trips; wrong credentials and every header/segment/end mutation; duplicate/splice/reorder/truncate/append rejection; JSON/depth/count/overflow fuzzing; cancellation, KDF cost and peak-memory measurements; disk-full/corrupt/key-loss cases; DB/WAL/temp/log sentinel scans; migration/pool/16-KiB-page behavior; crash at every wrapper/generation commit boundary; and fresh-install portable recovery without the source Keystore. B-01–B-05, D-02 and S-01/S-03/S-04/S-05 remain not run for this design. Provider durability and OS-transfer qualification remain separate AS-020/AS-021 gates. No independent security audit is claimed.
