# Security

## Reporting a vulnerability

Use [GitHub private vulnerability reporting](https://github.com/yasirarfat-dev/AttentionStats/security/advisories/new). Do not post exploit details, personal data or secrets in a public issue. If private reporting is unavailable, open a minimal issue asking for a private channel, without details.

Include the affected version or commit, the impact and a synthetic reproduction. The project is maintained on a best-effort basis; there is no guaranteed response time and no independent security audit has been done. Fixes are released as a new version with an advisory naming the affected versions.

Supported version: the latest release on Google Play (currently 1.1.0).

## Security model

**What is protected:** local usage history, rules and choices, Protection Lock credentials, and the maintainer's signing keys. The app has no server, account or network access, so there is no remote attack surface of its own.

| Threat | Control | Limit |
|---|---|---|
| Another app reads history | Private app storage; non-exported components; scoped document-picker grants; no shared plaintext files | A rooted or compromised OS can bypass the sandbox |
| Lost or stolen phone | SQLCipher database with a random key wrapped by an Android Keystore AES-256-GCM key; no plaintext fallback | An unlocked, compromised phone can use live keys |
| Backup or exported file obtained by someone else | Android backup requires client-side-encrypted transport and a secure screen lock; device transfer excluded | Archive files use a public key derived from the package name: anyone holding one can read or modify it (ADR-073). Keep them private |
| Malicious or corrupt restore file | Bounded size; authenticate before parsing; strict validation into an encrypted staging database; one-transaction replacement | Very large inputs are rejected, not partially imported |
| Over-broad accessibility access | Package allowlist; editable and password fields skipped; labels processed in memory and discarded | Target app layouts change; unknown screens fail closed |
| Protection Lock bypass | Credentials stored as PBKDF2-SHA256 verifiers in no-backup storage; strong biometrics bound to a Keystore key; one-time authorisation per action; lock screens are secure windows | Not tamper-proof: Android Settings, clear storage and uninstall stay available by design |
| Being trapped by a cover | Settings, Home, keyboards, dialer and system UI are never covered; five-minute allowance; static user rules only | Android or the phone maker may stop the service, so blocking is best effort |
| Dependency or build compromise | Exact pins, frozen lockfile, disabled install scripts, reviewed patches, static release audits | Open source and latest versions do not guarantee safety |
| Leaks through logs or reports | No automatic diagnostics; "Report a problem" includes only phone model and app versions; synthetic public fixtures | Copies the person sends themselves cannot be recalled |

Release signing uses Play App Signing with a separate upload key kept outside the repository. CI workflows have read-only permissions, no secrets and run only on manual dispatch.

Architecture details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#storage-and-encryption). Data handling: [docs/PRIVACY.md](docs/PRIVACY.md).
