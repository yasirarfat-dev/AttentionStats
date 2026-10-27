# AS-006 archive primitive and envelope fixture

Standalone, debug-only Android experiment for [ADR-031](../../docs/CRYPTO-ARCHIVE-DESIGN.md). It is excluded from product Gradle settings, dependencies and source. It implements no history schema/parser, provider transport, export/restore screen, staging database, Keystore operation or capability. Version 1 remains a draft; the passphrase/Argon2 profile is rejected as unavailable.

The fixture pins latest stable Tink Android 1.24.0, Kotlin 2.4.21 and AGP 9.4.1 resolved on 2026-10-27. It reuses the committed Gradle 9.8.2 wrapper, JDK 17, API-37 task-local SDK and existing ignored debug signing key. Tink configuration overloads are explicit; no dependency downgrade, metadata suppression or global security-provider registration. The separate generated dependency locks cover this build; root application manifests/catalog/lockfiles are unchanged. Release is disabled.

## Reproduce

From the repository root with the documented local Android tools:

```powershell
python tools/archive_qualification.py build
python -m unittest discover -s tools/tests -p test_archive_qualification.py
node --test tools/tests/archive_reader.test.mjs
node tools/archive_reader.mjs native/archive-qualification/vectors/generated-profile.json
python tools/archive_qualification.py run
```

Only a reviewed dependency introduction/refresh uses `build --write-locks`. Routine builds use existing locks. The phone runner requires exactly one authorized physical ARM64 debug phone, API 24 or newer; API-36 results do not qualify API 24. It refuses a pre-existing fixture package, installs without replacement, checks instrumentation completion and bounded unique aggregates, independently reads generated synthetic vectors with Node, and uninstalls only its own installed package in `finally`. No emulator fallback, product app, grant/setting, shared adb reset, personal history or logcat access. An unavailable phone requires reconnection.

The APK has no permissions, activities or services. It writes only two fixed public synthetic-vector outputs inside its own credential-encrypted no-backup directory. The host reads those exact paths with `run-as`, verifies file-size bounds and independent readback, and retains only ignored `.cache/as-006` output. Uninstall removes the temporary package/data. Host interruption or an install timeout may leave a package; a later run refuses it rather than assuming ownership.

## Scope of checks

- RFC 5869 SHA-256 cases 1–3 through Tink HKDF; the same published cases run separately through Node crypto.
- Canonical 43-character unpadded base64url recovery keys, including rejection of noncanonical trailing bits. Fresh archive keyset, salt, identifier and library-generated randomness; no device key dependency.
- Exact 60-byte prefix, unsigned bounded keyset length, AES-256-GCM RAW keyset wrapping with domain-separated HKDF/AAD, one enabled primary streaming key of the exact suite. Other profiles, key types, segment sizes and multi-key sets fail.
- Primitive payloads of 0/1 bytes, immediately around the first segment boundary, exact/over the second boundary and 3,145,735 bytes spanning four segments. Empty primitive plaintext is not a valid logical archive.
- Single-byte mutations across an entire small envelope, every small-envelope truncation, selected large segment boundaries/tags, append, reorder, duplicate, delete, and a splice from an independently randomized stream using the same envelope/key/AAD.
- Full authentication to discard followed by a second authenticated read of an exclusively owned immutable byte-array snapshot. A failed/cancelled read never calls the accepted-plaintext callback. Mutating the caller's array during a read cannot change the snapshot.
- Four injected read cancellation boundaries (including the second pass), two write cancellation boundaries, ciphertext/plaintext admission limits and smaller explicit read quotas. Library calls themselves are not promised interruptible.

The experiment uses a smaller **4 MiB plaintext / 4 MiB + 16 KiB ciphertext** ceiling, 4 KiB wrapped-keyset limit and 8 KiB copy buffers. It is deliberately in-memory: this does not implement the planned multi-GiB file job, bounded production streaming/staging, memory target or crash-safe immutability protocol. Some owned arrays are cleared; library/managed copies and immutable credential strings cannot be promised erased.

## Independent vectors

[generated-profile.json](vectors/generated-profile.json) contains **public artificial test keys and ciphertext only**, produced by Tink Android. Never reuse them for real data. Three compact cases cover empty/one-byte primitives and a fixture-only manifest/entity/end record byte stream. The artificial record fields are not an application schema; no JSON/history admission is implemented here.

[archive_reader.mjs](../../tools/archive_reader.mjs) independently decodes bounded protobuf fields, unwraps the keyset and verifies each AES-GCM-HKDF segment using Node's built-in crypto, following the [Tink construction](https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming) and [tagged wire definitions](https://github.com/tink-crypto/tink-java/blob/v1.24.0/proto/tink.proto). It imports no Tink/protobuf package. Its manual cryptographic construction exists solely as a qualification oracle and must never be used in product code. It returns plaintext only after all segment tags/physical EOF pass; it uses bounded in-memory output rather than the Android fixture's two passes. Its strict protobuf subset is a vector reader, not a general Tink interchange implementation.

Every phone run also generates three fresh compact vectors and a fresh four-segment vector. The host independently reads all four; only the compact reviewed golden vectors are committed. Original runtime artifacts/large vectors remain ignored. Static tests reject byte mutations, truncation, append, credential/profile/length and quota failures. Golden vectors do not freeze the format or establish another implementation's writer compatibility.

## Remaining gates

[Public results and hashes](../../docs/evidence/archive-qualification.json) record the actual checks. Strict logical JSON/schema/count/reference/overflow validation and fuzzing, fresh-install secure credential UX, encrypted file/staging lifecycle, provider/TOCTOU/disk-full/crash behavior, peak memory/latency, release shrinking, minimum API, independent security review and the passphrase profile remain unqualified. AS-006 stays experimental; B-01–B-05 and S scenarios are not accepted by this isolated fixture.
