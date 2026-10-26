# Security design

**Status:** Threat model and release gates accepted; no security implementation or audit completed. Updated 2026-10-26.
**Responsibility:** Owns threat boundaries, encryption requirements, credentials, supply chain and incident response.
**Related specifications:** [Privacy](PRIVACY.md), [Backup](BACKUP-RESTORE.md), [Models](DATA-MODELS.md), [Release](RELEASE.md), [Reporting entrypoint](../SECURITY.md).
**Unresolved feasibility:** F-03 and F-04 in [Decisions](DECISIONS.md); crypto dependencies, KDF parameters and encrypted Room integration need review.

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

At-rest target: an encrypted Room-compatible SQLite solution using a randomly generated database key wrapped by Android Keystore. Room alone is not encryption. Until AS-006 validates a maintained driver, migrations, WAL/temp handling and recovery behavior, database encryption remains unimplemented and cannot be claimed. Android Keystore can protect non-exportable key material and constrain its use; hardware-backed guarantees depend on device capabilities. See [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

Export encryption is independent of device Keystore so a new device can restore using user-held recovery credentials. Proposed baseline: authenticated encryption with an audited library, 256-bit random archive data key, fresh salt/nonces, authenticated bounded header and manifest, and a password-derived wrapping key using a reviewed memory-hard KDF. Algorithm suite, format framing, KDF cost, streaming/chunk sequencing and nonce construction must be finalized in a versioned format ADR under AS-006 before implementation ships. Do not invent cryptography or treat an unauthenticated checksum as integrity protection.

Offer a generated high-entropy recovery key and optionally a passphrase route after KDF evaluation. Display recovery credentials only in a secure explicit flow, require acknowledgement/test, and never send them to the developer or provider. Loss of credentials can make archives unrecoverable; present that before enabling a backup. Biometric unlock may gate local access later but cannot be the only portable recovery mechanism. Do not place secrets on clipboard by default or in normal screenshots, logs, change events or generic JS stores.

If a device key is invalidated or unavailable, stop sensitive reads/writes safely, explain recovery choices, and preserve encrypted files; never silently reset history. Key rotation must be interruption-safe and tested with both current and prior archive formats. Deleting all data removes key material and tokens. Local logical deletion cannot promise forensic flash erasure.

## Components, transport and builds

Set native components non-exported unless Android integration requires export with an appropriate binding permission. Validate every intent and URI; no implicit trust in deep links or picker paths. Prevent path traversal and decompression bombs in imports. Use native handles for credentials/files across the UI boundary, bounded background queues, and no plaintext staging on shared storage.

For any future approved network transport use platform TLS verification, least-scope authorization, cancellable bounded retries, secure token storage and explicit account selection. No permissive trust manager, embedded secret, or account token in an archive. Do not add certificate pinning without an operational rotation plan and concrete need.

Use pnpm latest direct dependencies with committed lockfile after scaffolding. Review dependency lifecycle/build scripts and explicitly approve required native builds per the resolved pnpm version; do not globally permit arbitrary install scripts. Record licenses, transitives with networking, vulnerabilities and exceptions. Keep CI permissions minimal; no release signing secrets in documentation-check jobs. Release signing credentials remain maintainer-controlled and outside source control.

## Verification and response

Before binary release: S-01–S-06, malicious-archive fuzz/bounds tests, migration/low-space recovery, secure component inspection and emergency bypass checks. Audit release logs and network behavior on a real device. Do not claim third-party security audit until a report exists. Report vulnerabilities through [the root security entrypoint](../SECURITY.md). Maintainers triage privately, prepare a fix and advisory, rotate exposed secrets, and record affected versions; no response-time guarantee is fabricated.
