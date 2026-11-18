# Privacy and permissions (technical)

The engineering view of what the app reads, stores and transfers. The plain-language policy for users is [policies/PRIVACY.md](../policies/PRIVACY.md); both must stay consistent, and any change to data handling needs an ADR, an updated in-app disclosure and updated Play declarations.

## Invariants

- No server, account, telemetry, advertising, analytics or crash upload. Release builds have no `INTERNET` permission.
- Every source is off until the person agrees in the app (ADR-004). An Android grant alone enables nothing.
- Screen labels are processed in memory and discarded. No captions, names, messages, typed text, screenshots, audio or video are stored or sent.
- Data leaves the phone only through Android-managed backup (on by default, can be turned off) or a file the person exports.
- Never describe the app as "collecting no data": it records local history (ADR-013).

## Data inventory

| Data | Where | Backed up / exported | Retention |
|---|---|---|---|
| Encounters: time, app, route (viewer/feed), category, media kind, dwell, revisit flag, app version, detection method, device-local item key, unlock-session ordinal | Encrypted database | Yes | Until erased |
| Surface episodes and video-surface timers | Encrypted database | Yes | Until erased |
| Usage events: package, timestamp, event kind (from consent time onward) | Encrypted database | Yes | Until erased |
| Break and block events with the choice made | Encrypted database | Yes | Until erased |
| Consent records, source/diary/block/protection configuration, coverage intervals | Encrypted database | Yes | Until erased (protection configuration survives erase) |
| Per-fact origin IDs | Encrypted database | Yes | Until erased |
| Optional playback facts (legacy; hidden for new users) | Encrypted database | Yes | Until erased |
| Appearance, widget, backup-choice and detection-salt preferences | Private SharedPreferences | No | Until uninstall or clear storage |
| Database key wrapper and Keystore keys | No-backup storage / Android Keystore | No | Until uninstall |
| Protection Lock PIN/pattern/recovery verifiers, retry state, biometric key | No-backup storage / Android Keystore | No | Until the lock is turned off |
| Live counter, unlock-session counts, item fingerprints, auto-scroll state | Memory only | No | Until screen-off or service stop |

The device-local item key lets a later view of the same item be recognised as a revisit. It is derived with a device-local secret and cannot be compared across devices or reversed into content.

## Permissions and Android access

| Access | Why | Notes |
|---|---|---|
| AccessibilityService (`BIND_ACCESSIBILITY_SERVICE`) | Recognise supported short-video screens; draw the counter, breaks and covers; Back for blocks; one Pause tap before a break; optional auto-scroll swipes | Static package filter: the seven supported packages. `canRetrieveWindowContent`, `flagReportViewIds`, `flagIncludeNotImportantViews`, `canPerformGestures=true`, `isAccessibilityTool=false`. At runtime the filter widens to apps in the person's schedules/focus and adds interactive-window retrieval, reading only the foreground package for those apps |
| `PACKAGE_USAGE_STATS` (Usage Access) | Optional app time and opens | Special access granted in Android Settings after a separate in-app consent |
| Package visibility (`<queries>`) | Name/icon of supported apps; launcher, Home and dialer for app lists and cover exclusions | Seven explicit packages plus MAIN/LAUNCHER, MAIN/HOME and DIAL intents. No `QUERY_ALL_PACKAGES` |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Optional Protection Lock unlock | Normal permissions merged from AndroidX Biometric; the app receives only the authentication result |
| Storage Access Framework | Manual export/restore | Per-document grants from the system picker; no broad storage permission |
| Overlays | Counter, breaks, covers | Accessibility overlay windows; no `SYSTEM_ALERT_WINDOW` |
| Android backup | Disaster recovery | Custom agent; one logical file; client-side-encrypted transport required; device transfer excluded |
| Battery optimisation | Guidance only | Shows status and opens Android's app settings; no exemption request |

Not requested: Internet, notifications, notification listener, foreground service, boot receiver, exact alarms, location, camera, microphone, contacts, SMS, call logs, photos/media, all-files access, device admin, VPN, screen capture. Debug builds add `INTERNET` for the Metro dev server only.

Adding any new permission or data family is a design decision: ADR, in-app disclosure, both privacy documents, Data safety answers and (for accessibility) the Play declaration and reviewer video.

## Consent and revocation

- Each source has its own consent with a notice revision (currently 6). Raising the revision requires fresh consent.
- Usage Access, auto-scroll and Skip Ads each have their own disclosure. Auto-scroll consent lasts only for the current service session.
- Revoking an Android grant stops new data immediately; existing history stays until erased. The app rechecks real grant state on return from Settings and never marks a permission granted because an intent opened.
- After a restore, counting and protection are off until re-enabled; imported consent never grants current permissions.

## Deletion

- **Settings → Erase history** removes all counts, timers, usage events, intervention events and consent records, turns counting and automatic backup off and removes pending restores. Protection Lock is kept and managed separately.
- Clearing storage or uninstalling removes everything local, including keys.
- Exported files and backup-provider copies are outside the app's control. See [policies/DATA-DELETION.md](../policies/DATA-DELETION.md).

## Release checks

Before each release compare the merged release manifest, accessibility service XML and backup rules with this page, the public policy, the Data safety form and the accessibility declaration (`tools/audit_current_release.py` checks the manifest and compiled backup rules).
