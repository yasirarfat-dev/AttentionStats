# Public contributions

**Status:** Contribution workflow active for documentation; application work follows the tracker. Updated 2026-10-27.
**Responsibility:** Defines issue/PR expectations, review, evidence and community boundaries.
**Related specifications:** [Agent rules](AGENT-RULES.md), [Tracker](FEATURE-TRACKER.md), [Conventions](CONVENTIONS.md), [iOS guide](IOS-CONTRIBUTION-GUIDE.md), [Release](RELEASE.md).
**Unresolved feasibility:** Maintainer review capacity and supported device matrix evolve; see F-01/F-07 in [Decisions](DECISIONS.md).

## Start here

Read [Index](INDEX.md), [State](STATE.md) and the relevant domain specifications. Find an existing AS task or open a proposal with problem, expected behavior, source/access, user controls, limitations, phase and synthetic acceptance scenario. Ideas enter [Queue](QUEUE.md); they do not become promised features automatically. Maintainers own scope and release decisions; contributors need not share personal usage history to participate.

Use respectful, specific feedback. Do not shame users for behavior or medicalize screen use. Do not post harassment, discriminatory content or another person's private information. Maintainers may remove harmful material and restrict participation; no dedicated external conduct-report address is fabricated. Use a minimal contact request when a private channel is needed.

## Most valuable contribution: a missed screen

Apps change their layouts often. If a short video is not counted (or something is counted that should not be), capture that screen with `adb shell uiautomator dump`, sanitize it with `tools/sanitize_fixture.py`, add an expectation in `FixtureTests.kt`, and fix the adapter until `python tools/android_build.py :reels-core:verifyReels` passes. The [adapter guide](SOCIAL-ADAPTERS.md#turning-a-report-into-a-fixture) has the exact steps. Never attach raw dumps or screenshots containing other people's content.

## Issues and pull requests

Bug reports include commit/version, relevant platform/API/OEM class without device identifiers, source/grant states, synthetic reproduction, expected/actual result and redacted reason codes. Detection issues name supported target/version/locale and describe surface confusion using synthetic structures, not account screenshots or raw accessibility dumps. Security vulnerabilities follow [private reporting](../SECURITY.md), not a public bug template.

Work on a focused branch. A PR explains the trigger/problem and resulting behavior, linked AS/capability IDs, changed contracts/schema/permissions, test evidence and known limitations. Include documentation/state/tracker/log updates in the same change. For new dependencies use pnpm and `package@latest`, record licenses/compatibility and commit lockfile; do not silently downgrade or alter global tools.

All fixtures must be synthetic: artificial app IDs, timestamps, node structures and archives with no real titles, URLs, accounts or secrets. No private reference assets, copied product wording, personal phone data, keystores or signing secrets. Public external research citations may support platform constraints; private comparisons and external app implementation claims are not project specifications.

## Review and completion

Maintainers review correctness, consent/privacy, platform legality, accessibility, data loss risk, native lifecycle, dependency provenance and acceptance evidence. Native history/configuration stays authoritative; no duplicate store or UI-only enforcement. A fixture test does not establish support for all versions of a target app. New detectors remain experimental until declared-scope device evidence meets [Social](SOCIAL-MEDIA-SPEC.md).

Before merge run documentation checks and relevant implementation checks; distinguish pass, failure, blocked and not run. Address material review findings and update specs to match final behavior. Never mark planned tests as passed. Maintainers decide merge/release and signing; a contributor PR is not release authorization.

## Licensing and attribution

Contributions are accepted under the repository's [Apache-2.0 license](../LICENSE). Submit only material you have rights to contribute; retain required third-party notices for dependencies/assets. No extra CLA or DCO requirement is imposed by this foundation. If project governance changes, document it before applying it to contributions. Report suspected private data promptly without reposting it; maintainers can remove exposed material and rotate compromised credentials.
