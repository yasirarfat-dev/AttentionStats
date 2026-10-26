# Release and distribution

**Status:** Documentation publication authorized; no application release exists. Updated 2026-10-27.
**Responsibility:** Defines public repository publishing, Android distribution, signing and release gates.
**Related specifications:** [Testing](TESTING.md), [Security](SECURITY.md), [Dependencies](DEPENDENCIES.md), [Contributing](CONTRIBUTING.md), [State](STATE.md).
**Unresolved feasibility:** F-02–F-05 and F-08 in [Decisions](DECISIONS.md); signing identity, application ID, store enrollment and declarations are future maintainer decisions.

## Documentation publication

The designated public repository is [yasirarfat-dev/AttentionStats](https://github.com/yasirarfat-dev/AttentionStats). The user explicitly authorized public publication of this foundation. Review all files, run documentation checks and whitespace validation, inspect the staged tree for secrets/private material, commit and push without application scaffolding. Verify repository visibility and the remote commit; record actual evidence in the dated log. Do not claim a binary release or publish invented badges/download links.

GitHub documentation CI uses read-only repository permission, Python and no release credentials. Public issue/PR templates require synthetic reports. Private vulnerability reporting should be enabled when repository administration permits. Documentation “implemented” means the documents/checks exist; it does not change any application capability's status.

## Android release ownership

Release maintainers own permanent application ID/namespace, package signing, Play Console credentials, store declarations and rollout decisions. Contributors may build debug artifacts using local debug signing, but must never commit keystores/passwords or release signatures. Generate release credentials outside the repository with encrypted maintainer-controlled backup and access recovery. A signing key is not the archive recovery key or device DB key.

Select and document Play App Signing and upload-key responsibilities before publishing to a store. Record certificate fingerprints only if needed as public distribution metadata; no private key paths/secrets in logs. Every artifact has versionName/versionCode, source commit, exact locked dependency/toolchain manifest, license notices and checksum. Never reuse or decrement Android version codes.

## Binary release gates

1. Select candidate from passing code/review and accurate tracker/specs. Re-resolve latest only in a reviewed update task; release builds consume the reviewed lockfile.
2. Build signed release AAB/APK reproducibly from clean state using frozen-lockfile and pinned native toolchain. Record any reproducibility limitation, including signing timestamps. Inspect merged manifest, exported components, permissions, backup exclusions and dependency notices.
3. Pass relevant [acceptance scenarios](TESTING.md), including real-device lifecycle, enforcement escape routes, crypto/restore failure, egress/log checks and OS cloud/device transfer exclusions. Unsupported detectors remain disabled with declared ranges.
4. Recheck current Play rules for target API, accessibility, package visibility, foreground services, permission disclosures, privacy and Data safety. Rules change; use current primary documentation rather than hardcoded target deadlines. The product must not claim accessibility-tool eligibility or autonomous actions.
5. Publish an accurate public privacy notice and release notes describing data stored locally, optional transfers, coverage, known gaps, experimental detectors and rollback/recovery. Marketing follows [Privacy](PRIVACY.md), not “collects no data.” Direct Drive backup remains unavailable while F-05 is blocked.
6. Maintainer reviews the concrete candidate and store forms, signs off, and starts internal/closed testing before staged public rollout. Monitor voluntary synthetic feedback; no remote telemetry pipeline. Do not infer policy approval from another application's listing.

## Recovery and updates

Schema changes need tested migration paths and a pre-update encrypted-export option where appropriate. Never force a destructive reset to recover from migration failure. Rollback may be limited by schema/signing/version constraints; document a forward-fix strategy and archival compatibility. If a detector breaks after a target app update, mark its support unavailable and ship a reviewed fix; no remote silent data-collection expansion.

Security incidents follow [reporting policy](../SECURITY.md). Withdraw unsafe release links, disable an affected feature in the next reviewed release when necessary, preserve user history where possible and state affected versions. No over-the-air executable code, hidden policy bypass or automatic diagnostic upload is part of release operations.
