# Privacy specification

**Status:** Product privacy commitments accepted; implementation unverified. Updated 2026-10-27.
**Responsibility:** Owns data inventory, consent, local processing, external transfers and public claims.
**Related specifications:** [Permissions](PERMISSIONS.md), [Security](SECURITY.md), [Storage](STORAGE-AND-RETENTION.md), [Backup](BACKUP-RESTORE.md), [Release](RELEASE.md).
**Unresolved feasibility:** F-01, F-04–F-06 and F-08 in [Decisions](DECISIONS.md); implementation and policy review must prove these commitments before release.

## Public promise

Planned release message: **“No data sent to us. Your history stays on your device. Open source.”** Pair this with a nearby explanation: tracking stores the data you enable locally; optional exports or approved cloud backup move encrypted archives to a destination you choose. Do not advertise “collects no data,” “zero data collection,” or “never leaves your phone” without this distinction. Open source supports inspection; it is not proof that an untested binary has no network traffic.

Today the correct message is “Documentation prepared; application not yet implemented.” No claims about implemented encryption, tracking accuracy or network behavior are justified yet. Store declarations and the public privacy notice must describe actual release behavior, including third-party document providers, independently of how a store defines collection.

No application backend, mandatory product account, advertising SDK, remote analytics, session replay, remote inference, automatic crash uploads or automatic diagnostic uploads. Categorization, predictions and optional topic analysis run on device. The developer receives neither optional encrypted backup data nor provider-visible metadata. A person can independently send a support report, but the app must never send one automatically; support instructions must exclude personal history and secrets.

## Data inventory

AS-043's installed-preview inventory is narrower than the planned catalog: per-app source choices, disclosure decisions, pause/HUD configuration, exposure counts/timestamps, visits and coverage in encrypted local storage. Its passive accessibility adapter reads only bounded selected-package resource IDs, class/role and numeric page/scroll positions; it never reads node text/descriptions, images, messages, input values or content fingerprints. No content/topic control is implemented. An independent affirmative developer-only choice temporarily retains a bounded flat structural diagnostic in private no-backup storage, for up to ten minutes; stopping, expiry, deletion or a fresh process removes it. The developer may read this one file during the local qualification; no automatic upload. User consent and a verified detector are required separately before real counts can be admitted. See [permissions](PERMISSIONS.md) and [current status](STATE.md).

| Data class | Why / default | Storage and recipients | Control |
|---|---|---|---|
| App/package metadata and usage episodes | History; disabled until usage opt-in | Local authoritative DB; no developer recipient | App/source filters, exclusions, pause, retention, selective delete |
| Screen/keyguard activity | Device summary; separate source choices, off | Local; no identity or unlock credential | Independent toggle, gaps and delete |
| Surface/exposure events | Selected short-video observation, off | Local counts/revisions; no video content by default | Per adapter, consent and coverage |
| Coarse primary activities and item/interaction evidence | Optional detailed diary, off per family | Local minimized contexts, exposure/action type/time/evidence; no message/typed content or other-person identity | Per adapter/activity/action opt-in, exclusion, pause, retention/delete |
| Per-app network byte statistics | Optional OS RX/TX history, separately off | Local app/install-epoch/transport/bucket bytes; no SIM ID, endpoints, packets or content | Independent source/app/transport scope, coverage, pause, retention/delete |
| Retained multi-resolution summaries | Only after explicit compaction choice | Local summary facts/coverage/provenance after chosen detail removal; no reconstructed content | Tier/zone/loss preview, selective deletion at retained resolution, summary export scope |
| Future user-selected activity archive imports | Unimplemented optional supported-file research | Native allowlisted parsing only; imported provenance separate, no raw archive in app history | Explicit file/field/date preview, cancel/delete; no account/token scraping |
| Notification metadata | Behavior associations, off | Local package/times/lifecycle; no body/title content | Listener opt-in, app filter, revoke/delete |
| Supported titles/domains/URLs | Optional history, off | Separate local table; sanitized and excluded on sensitive contexts | Separate title/URL consent, detail, shorter retention, delete |
| Categories, insights, topics and predictions | Local interpretation; topics/predictions separately off | Local derived records; no training upload | Correct mapping, disable/reset, source deletion cascades |
| Rules, schedules, overrides and focus sessions | Chosen protection | Local DB; reason text optional, no remote accountability recipient | Per rule behavior, pause, emergency bypass, delete/reset |
| UI preferences | Theme, motion, layout | MMKV; non-sensitive only | Settings/reset; exclude from undocumented OS transfers |
| Keys and optional provider tokens | Encryption / approved backup only | Native secure storage; never exported as plaintext | Key reset, provider disconnect and recovery guidance |
| Archives and provider metadata | User-selected portability/backup, off | Ciphertext at chosen destination; provider sees account, size, name/time, transfer/IP metadata as applicable | Scope, destination, cancellation and external deletion guidance |
| Health and diagnostics | Local troubleshooting | Bounded local reason codes and timings; no raw node text/history/identifiers | Inspect and clear; synthetic reports only in public repository |

## Consent lifecycle and exclusions

Disclose each source in context before opening Android grant settings. Explain what is inspected, what is retained, purpose, limitations, optional transfer and how to revoke. Accessibility gets its own affirmative disclosure, not a checkbox bundled with onboarding or a privacy-policy link. A system grant does not activate tracking. New fields/purposes require a new notice revision and explicit consent; updates cannot expand collection silently.

Titles, URLs and topic processing require separate choices. No credential fields, typed text, private messages, OTPs, payment/authentication surfaces, screenshots, audio, clipboard or whole accessibility trees. An unknown sensitive/private context fails closed for content capture. Consent/filter checks apply before reading optional payloads and again before persistence.

“Maximum detail” under [Analytics](ANALYTICS-SPEC.md) expands the selectable metric catalog, not these exclusions. A source/grant cannot silently enable interaction monitoring, coarse messaging-context detection, network statistics or topics. Each new family explains inspected versus retained fields. Safe structural context duration is different from message capture and is unavailable if the adapter cannot prove the distinction. Open-source distribution never substitutes for consent or validates a hidden collection technique.

The permission dashboard shows enabled sources, fields, applications, detail levels, coverage, last successful observation and active transfers. Revocation stops new collection, discards in-flight payloads and opens a gap; it does not automatically delete old history. Offer deletion separately. A timed pause expires visibly; automatic resume is disclosed when the pause is created.

## Transfers and OS behavior

Portable encrypted exports are independent of cloud backup. A system picker may hand a file to a provider that uploads it; tell the person which destination was chosen and that the provider can see metadata. Encryption does not hide file size, account, time or network origin. The app cannot revoke copies after export. Backups can contain sensitive history even if encrypted; require scope preview and user-held recovery credentials.

Automatic personal Google-account backup is desired, but direct Drive integration is **unverified and blocked pending policy clarification**. It must not be described as working or implemented through a silently substituted transport. No product sign-in is required for local use. Android automatic backup/device transfer of sensitive data is disabled and explicitly excluded as specified in [Backup](BACKUP-RESTORE.md); OEM behavior must be tested.

## Privacy acceptance

S-01–S-06 in [Testing](TESTING.md) require egress inspection across first run, denied/granted permissions, offline use and opt-in transports; dependency review; sensitive-log/temporary-file checks; deletion/derivative checks; and cloud/device-transfer tests. Inspect merged manifests and actual release artifacts, not just source intent. Release notes must describe verified behavior and limitations. Publish truthful privacy language and capability coverage before distributing a binary.
