# Isolated encrypted-store qualification

AS-006 / ADR-031 experiment only. This standalone Android build is **not included** by `android/settings.gradle` and supplies no production store, history schema, consent, bridge or archive implementation. It has no activity or runtime permissions. Its debug-only APK targets a single authorized physical ARM64 phone; no emulator runs.

Use the repository's existing Gradle wrapper, task-local API-37 SDK and debug signing key:

```powershell
python tools/storage_qualification.py build
python -m unittest discover -s tools/tests -p test_storage_qualification.py
python tools/storage_qualification.py run
```

`build` assembles, lints and reports dependencies using committed Gradle lockfiles. `--write-locks` is for a reviewed dependency introduction/refresh, after resolving latest stable and updating evidence; routine builds leave locks intact. The version catalog pins Room runtime/compiler 3.0.3, SQLCipher Android 4.19.1, SQLite 2.7.1, KSP 2.3.12, coroutines 1.11.0, Kotlin 2.4.21 and AGP 9.4.1. The wrapper remains Gradle 9.8.2. The scaffold's explicit Kotlin/legacy-DSL configuration is retained, with deprecation warnings visible; AGP/Gradle 10 migration is unqualified.

`run` refuses a pre-existing fixture package and ambiguous/no physical targets. It installs without replacement, runs instrumentation, accepts only complete bounded aggregate results plus result code `-1`, and uninstalls the package it installed on success or failure. The product debug app is untouched. Device identifiers and raw output are never printed or persisted. A host-process interruption can leave a package behind; the next run refuses ownership rather than removing it automatically. No adb reset/server restart, grants, settings, reboot, personal history query or logcat read is performed.

The fixture creates an unpredictable directory inside its own credential-encrypted no-backup storage. It uses a new random 32-byte database key in SQLCipher's documented ASCII `x'64-hex-digits'` representation. Driver arrays remain owned until every exposed connection is closed; retirement is refused while a connection remains open. Clearing managed arrays cannot guarantee erasure of native or JVM copies.

Instrumentation checks synthetic Room insert/read/WAL/reopen, direct-driver interoperability and integrity, missing plaintext header, UTF-8/UTF-16LE marker absence in bounded DB/WAL/SHM snapshots, plain-SQLite rejection, wrong-key rejection, rejection of unencoded random bytes, and a first-page ciphertext mutation. Failure cases preserve the main file's SHA-256; the original remains readable after testing the corrupt copy. Both SQLCipher and the plain-SQLite negative control have explicit non-destructive handlers. Success requires removal of the run-owned directory before package uninstall. This does not prove sidecar immutability, absence of all possible plaintext, temp spill/log behavior or crash recovery.

See [qualification evidence](../../docs/evidence/storage-qualification.json), [crypto design](../../docs/CRYPTO-ARCHIVE-DESIGN.md) and [dated log](../../docs/IMPLEMENTATION_LOG/2026-10-27.md). API-24/25, expanded ABI and 16-KiB-page-device execution, production integration, migration, Keystore loss/rotation, low-space/crash/resource tests and archive interoperability remain open. No binary is published or security audit claimed.

SQLCipher's [BSD-style license](../../licenses/sqlcipher-android.txt) and [LibTomCrypt license](../../licenses/libtomcrypt.txt) are retained; LibTomCrypt's public-domain option is selected. Room/SQLite/KSP/coroutines/Kotlin/AGP use Apache-2.0. SQLCipher's Android binding includes Apache-2.0 AOSP code and SQLite core is public domain. A future product dependency/release requires accessible complete notices and a separate distribution review.
