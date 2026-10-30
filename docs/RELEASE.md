# Release and distribution

**Status:** Closed-test preparation authorized; no Play rollout exists. Updated 2026-10-29.
**Responsibility:** Defines public repository publishing, Android distribution, signing and release gates.
**Related specifications:** [Testing](TESTING.md), [Security](SECURITY.md), [Dependencies](DEPENDENCIES.md), [Contributing](CONTRIBUTING.md), [State](STATE.md).
**Unresolved feasibility:** F-01–F-04 and F-08 in [Decisions](DECISIONS.md); `attention.stats` is selected, but signing, store enrollment/declarations and release qualification remain open.

## Current closed-test preparation

ADR-043 sets the production package exactly **attention.stats**, versionName 0.1.0 / initial versionCode 1, subject to Console uniqueness. Keep the `com.attentionstats` source namespace and existing `com.attentionstats.debug` development identity/history; neither changes the Play package. Release snapshot contract 3 hides development-only structure controls and native code rejects those sessions.

ADR-064 reserves versionCode 2 for the resumed signed 0.1.0 candidate; code 1's unsigned investigation archive remains immutable. Check actual Console use before uploading any version code.

[Play Console information](../release/PLAY-CONSOLE.md) supplies listing text, app-content declarations, reviewer steps, assets, signing/tester/rollout fields and current blockers. Public user-facing [privacy](../policies/PRIVACY.md), [support](../policies/SUPPORT.md), [local deletion](../policies/DATA-DELETION.md) and [testing](../policies/CLOSED-TEST.md) documents are separate from internal specifications. The app links the policy. Publishing these GitHub documents is within the user's instruction; no GitHub Pages or automatic pipeline is needed. Signed-out URL accessibility must be verified after publication.

Code/builds remain in this repository. The separate local archive is the maintainer's private release folder (outside the repository), with `PLAY-CONSOLE.md` at its root and versioned `releases/<versionName>+<versionCode>/{android,metadata,notes}`, plus store, policies, testing and private/signing folders. Keep credentials and personal tester/feedback material outside Git. Never overwrite an archived release or represent an unsigned investigation bundle as uploadable.

Closed testing can begin before the broad roadmap is complete only with a working, accurately described scoped candidate and applicable source/privacy/security/policy checks. Facebook/Home remain unavailable until independently qualified. Current [Google guidance](https://support.google.com/googleplay/android-developer/answer/14151465) requires applicable newer personal accounts to retain at least 12 continuously opted-in testers for 14 days before applying for production access. Documents, sideloads and internal tests do not start that period; real Console opt-ins and rollout establish eligibility. No production-approval date is promised.

## Documentation publication

The designated public repository is [yasirarfat-dev/AttentionStats](https://github.com/yasirarfat-dev/AttentionStats). The user explicitly authorized public publication of this foundation. Review all files, run documentation checks and whitespace validation, inspect the staged tree for secrets/private material, commit and push without application scaffolding. Verify repository visibility and the remote commit; record actual evidence in the dated log. Do not claim a binary release or publish invented badges/download links.

GitHub documentation CI uses read-only repository permission, Python and no release credentials. Under ADR-030 all CI/CD workflows are manual only (`workflow_dispatch`); publishing a commit or opening/updating a pull request starts no pipeline. Run appropriate local checks before publication; a remote run requires an explicit user request. Public issue/PR templates require synthetic reports. Private vulnerability reporting should be enabled when repository administration permits. Documentation “implemented” means the documents/checks exist; it does not change any application capability's status.

## Android release ownership

The maintainer authorized conventional Play App Signing on 2026-10-30: Google should generate/hold the delivery app-signing key, with a separate local upload key used for the AAB. `python tools/release_signing.py provision` refuses existing key material and keeps it outside the repository; its Windows current-user DPAPI password file is not a portable backup. Key readback and protection/rejection safeguards pass. Independent maintainer-controlled encrypted recovery backup, Console enrollment and exact signed-artifact/runtime checks remain distinct gates. To build with the external configuration, pass `--signing-config <external-upload-signing.json>` to `tools/android_build.py` with release tasks only. Without explicit credentials, release inspection builds remain unsigned. Secrets never appear as command-line password values or enter Git.

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
