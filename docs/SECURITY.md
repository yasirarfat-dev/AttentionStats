# Security design

**Status:** Threat model/prototype design and isolated store/archive-envelope fixtures exist; product security implementation and audit pending. Updated 2026-10-27.
**Responsibility:** Owns threat boundaries, encryption requirements, credentials, supply chain and incident response.
**Related specifications:** [Privacy](PRIVACY.md), [Backup](BACKUP-RESTORE.md), [Crypto design](CRYPTO-ARCHIVE-DESIGN.md), [Models](DATA-MODELS.md), [Release](RELEASE.md), [Reporting entrypoint](../SECURITY.md).
**Unresolved feasibility:** F-03 and F-04 in [Decisions](DECISIONS.md); reviewed prototype candidates/parameters still need build, interoperability, device-resource and encrypted Room qualification.

## Threat model

| Threat | Required control | Residual limit |
|---|---|---|
| Another ordinary app reading history | Private app storage, non-exported components, least-privilege URI grants, no plaintext shared files | Compromised/rooted OS can defeat the sandbox |
| Lost device or extracted archive | Device lock, app-level encrypted DB target, independently encrypted exports | An unlocked compromised device may use live keys; weak recovery credentials can be guessed offline |
| Broad accessibility capture | Narrow package/field allowlists, source consent and sensitive-context exclusion | Target UI can change; unsupported contexts must fail closed |
| Malicious/corrupt import | Authenticate before parsing trusted content, resource bounds, staged validation and atomic replacement | Resource exhaustion requires explicit quotas and tested limits |
| Dependency or build compromise | Latest metadata/license review, lockfile, approved build scripts, provenance and binary egress checks | Open source and latest releases do not guarantee safety |
| Leaking logs/backups/issue reports | Redaction, no automatic uploads, OS transfer exclusion, synthetic public fixtures | User-controlled external copies cannot be recalled |
| Intervention trapping the user | Permanent emergency routes, renderer escape tests, static rules only | OS/OEM failure can prevent enforcement, never justify anti-uninstall behavior |

Protected assets include usage and optional content history, rule configuration, keys, recovery credentials, provider tokens and signing material. The developer's repository and distribution credentials are separate from runtime device keys. No shared master decryption key or developer recovery service is allowed.

## Encryption and key lifecycle

AS-043 adds an experimental main-app SQLCipher store: a random 32-byte generation key is wrapped with an installation-specific Android Keystore AES-256-GCM key, fresh 12-byte nonce and authenticated package/installation/generation/alias context. A bounded AtomicFile wrapper is verified before persistence. The owned SQLCipher driver retains its key through pool closure, then wipes its owned bytes. Existing database files with a missing, malformed or unauthentic wrapper/key fail closed; no plaintext or destructive fallback. This is implemented preview code, not an audited security claim, hardware-backed-key guarantee or portable recovery mechanism. Rotation, interrupted wrapper/database lifecycle, pool stress, minimum-API and broader attack/failure qualification remain open under F-04.

At-rest target: encrypted Room 3 using SQLCipherDriver, with a random per-generation database key wrapped by Android Keystore AES-256-GCM. ADR-031 and [crypto design](CRYPTO-ARCHIVE-DESIGN.md#encrypted-room-and-device-keys) select the qualification path, key ownership and non-destructive corruption handler. Room alone is not encryption. AS-043 implements this narrow preview path; full AS-006/AS-009 admission still requires migrations, WAL/temp handling and recovery qualification. Android Keystore can protect non-exportable key material and constrain its use; hardware-backed guarantees depend on device capabilities. See [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

Export encryption is independent of device Keystore so a new device can restore using user-held recovery credentials. ADR-031 selects Tink AES256_GCM_HKDF_1MB, a fresh archive keyset, AES-256-GCM keyset wrapping and bounded authenticated framing for the prototype. A generated 256-bit recovery key is the initial path; the optional Argon2id profile stays unavailable pending qualification. [Crypto design](CRYPTO-ARCHIVE-DESIGN.md#archive-credentials-and-suite) owns exact profiles, domain separation, limits and format-freeze gates. These decisions are not an independent security audit or shipping format. Do not invent cryptography or treat an unauthenticated checksum as integrity protection.

Offer a generated high-entropy recovery key and optionally a passphrase route after KDF evaluation. Display recovery credentials only in a secure explicit flow, require acknowledgement/test, and never send them to the developer or provider. Loss of credentials can make archives unrecoverable; present that before enabling a backup. Biometric unlock may gate local access later but cannot be the only portable recovery mechanism. Do not place secrets on clipboard by default or in normal screenshots, logs, change events or generic JS stores.

If a device key is invalidated or unavailable, stop sensitive reads/writes safely, explain recovery choices, and preserve encrypted files; never silently reset history. [Rotation design](CRYPTO-ARCHIVE-DESIGN.md#loss-rotation-and-crash-recovery) distinguishes wrapping-key rewrap, database-generation replacement and portable credential changes. Each needs interruption tests; existing archive credentials remain necessary for old copies. Deleting all data removes key material and tokens. Local logical deletion cannot promise forensic flash erasure.

The [isolated storage fixture](CRYPTO-ARCHIVE-DESIGN.md#isolated-encrypted-store-result) passes 55 synthetic assertions on an API-36 ARM64 phone, including encrypted DB/WAL marker scans, raw-key representation, close/reopen, wrong-key and corrupt-copy main-file preservation. It is excluded from the product build and uses neither production history nor Keystore. The SQLCipher handler was not invoked in these cases; forced callback behavior, temp/log leakage, native pool stress, minimum-API, key loss/rotation and crash/resource qualification remain open. Framework plain-SQLite negative controls also need explicit preserving handlers: read-only mode did not prevent default recovery from removing the test DB path during an earlier harness run. No product encryption or security-audit claim follows.

The [isolated archive-envelope fixture](CRYPTO-ARCHIVE-DESIGN.md#isolated-archive-envelope-result) passes 1,759 synthetic API-36 ARM64 assertions, including 869 rejected inputs, exact suite/wire-keyset admission, segment boundaries and six cancellation checks. Tink and an independent Node test reader read public artificial vectors without a source Keystore; both HKDF implementations pass RFC-5869 SHA-256 cases 1–3. No production manual cryptography is introduced: the Node construction is only a bounded test oracle. This is in-memory primitive/framing evidence, not strict history parsing, secure recovery-key UX, file/staging lifecycle, resource measurement, provider recovery or an independent security audit. Passphrase support remains unavailable and the format remains unfrozen.

## Components, transport and builds

Set native components non-exported unless Android integration requires export with an appropriate binding permission. Validate every intent and URI; no implicit trust in deep links or picker paths. Prevent path traversal and decompression bombs in imports. Use native handles for credentials/files across the UI boundary, bounded background queues, and no plaintext staging on shared storage.

For any future approved network transport use platform TLS verification, least-scope authorization, cancellable bounded retries, secure token storage and explicit account selection. No permissive trust manager, embedded secret, or account token in an archive. Do not add certificate pinning without an operational rotation plan and concrete need.

Use pnpm latest direct dependencies with committed lockfile after scaffolding. Review dependency lifecycle/build scripts and explicitly approve required native builds per the resolved pnpm version; do not globally permit arbitrary install scripts. Record licenses, transitives with networking, vulnerabilities and exceptions. Keep CI permissions minimal; no release signing secrets in documentation-check jobs. Release signing credentials remain maintainer-controlled and outside source control.

## Verification and response

Before binary release: S-01–S-06, malicious-archive fuzz/bounds tests, migration/low-space recovery, secure component inspection and emergency bypass checks. Audit release logs and network behavior on a real device. Do not claim third-party security audit until a report exists. Report vulnerabilities through [the root security entrypoint](../SECURITY.md). Maintainers triage privately, prepare a fix and advisory, rotate exposed secrets, and record affected versions; no response-time guarantee is fabricated.
