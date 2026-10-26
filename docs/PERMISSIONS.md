# Permissions and capability access

**Status:** Access model specified; no manifest or permission flow implemented. Updated 2026-10-26.
**Responsibility:** Defines Android declarations, user grants, disclosures, revocation and safe degradation.
**Related specifications:** [Capabilities](CAPABILITY-MATRIX.md), [UX](UX-SPEC.md), [Privacy](PRIVACY.md), [Architecture](ARCHITECTURE.md).
**Unresolved feasibility:** F-02, F-08 and F-09 in [Decisions](DECISIONS.md); final manifest depends on target SDK and tested service strategy.

## Grant catalog

| Code / access | Android mechanism | Purpose and in-app disclosure | Denial/revocation behavior |
|---|---|---|---|
| U Usage Access | `PACKAGE_USAGE_STATS` declaration plus user special-access settings/AppOps | Enabled app/device usage sources; not runtime permission dialog | Stop relevant queries, close coverage, keep prior history visible |
| A Accessibility | Service protected by `BIND_ACCESSIBILITY_SERVICE`, metadata and user-enabled service | Selected surface detection and explicit deterministic protection; separate affirmative disclosure | Surface counts unavailable; release selective blocking and discard in-flight payloads |
| N Notification Listener | Service binding permission `BIND_NOTIFICATION_LISTENER_SERVICE`, user notification-access settings | Metadata lifecycle only, no message body | Notification gaps; no fabricated historical catch-up |
| P Post notifications | `POST_NOTIFICATIONS` runtime grant on applicable Android versions | User-selected reminders and eligible visible service status | Show unavailable reminders; never infer foreground-service legality solely from grant |
| O Overlay if needed | `SYSTEM_ALERT_WINDOW` special access for application overlays | Selected renderer/counter only; separate from accessibility overlays | Block renderer unavailable or explicitly chosen fallback; no silent whole-app block |
| D DND (expansion) | `ACCESS_NOTIFICATION_POLICY` special access and supported rule APIs | Optional bedtime/focus DND only | Keep other routine actions; don't claim DND applied |
| S Document picker | SAF create/open document URI grants | Export/import to chosen provider/file | Cancel cleanly; do not request broad storage access |
| Boot handling | `RECEIVE_BOOT_COMPLETED` normal permission where selected | Restore permitted schedule evaluation after unlock | Delayed/restricted boot work becomes health gap |
| Foreground service if justified | Appropriate FGS declaration/type/permissions and allowed start path | Only a demonstrated eligible user-visible task | Catch start/type restriction; report unavailable; never misuse a service type |
| Future provider/network | Least-scope approved authorization, `INTERNET` only if actual build requires it | Explicit optional encrypted backup | No auth/upload for blocked Drive path; disconnect clears tokens/jobs |

Binding permissions protect service access; they are not runtime permissions to request from users. Accessibility window retrieval and view-ID reporting must be declared/configured only when required by an enabled adapter. The service package filter minimizes scope, and code rechecks consent and package before inspecting nodes. Do not set `isAccessibilityTool=true` for this general wellbeing product.

No contacts, camera, microphone, location, SMS, call log, notification body capture, broad filesystem access, device-admin role or root requirement. Package visibility uses the narrowest supported discovery and explicit queries; `QUERY_ALL_PACKAGES` is not assumed. Installed-app selection itself can be sensitive and must not be uploaded. Do not require battery-optimization exemption during onboarding; provide optional, device-specific troubleshooting only when health evidence warrants it, with policy review for any direct exemption request.

The optional [live counter](LIVE-OVERLAY-SPEC.md) separates **detection** (qualified accessibility signals), **display** (one selected renderer), **storage** (Room) and **lifecycle** (permitted service strategy). Usage access alone does not count reels. Overlay access alone neither reads a feed nor keeps collection alive. An accessibility overlay and `SYSTEM_ALERT_WINDOW` application overlay are alternatives to assess, not grants to bundle automatically. No platform-independent “run in background” permission guarantees service permanence; OEM settings vary.

For battery troubleshooting, distinguish opening general optimization settings from a direct exemption request. The latter requires an eligible use case; [Android guidance](https://developer.android.com/training/monitoring-device-state/doze-standby) notes policy restrictions when the core function is not adversely affected. Eligibility here remains unverified. Do not add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, wake locks, exact alarms or an unrelated FGS type merely because another app requests them. Declining optional reliability changes retains available features and visible gaps. Recheck actual state on return; never promise survival after force-stop.

## User flow

Permission states are separate from consent/configuration: OS-granted but product-disabled is valid. Onboarding offers “Continue without tracking.” Each capability explanation identifies fields, purpose, local storage, optional transfer, limitations and the Android setting about to open. An affirmative action records consent, then opens the appropriate settings route. On return recheck actual OS state; never mark successful merely because an intent launched.

If Android/OEM has no deep link, show a manual path and remain available. Do not loop prompts or block access to prior history. New observation detail needs separate consent even if the OS grant already covers it. Permission dashboard shows granted/needed/unsupported alongside source enablement, pause and last-success time. Revocation keeps prior data and offers deletion separately.

## Policy and lifecycle evidence

Accessibility disclosure must be in the normal in-app flow, describe access/use/sharing, require affirmative action, and support the store declaration/review requirements. Do not infer that another released application proves this one's eligibility. Recheck [Google Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) before any release.

System-only permissions and APIs for usage observers or package suspension are not a distribution strategy for a normal third-party app. AOSP defines restricted usage observer access and suspension permissions; public API designs must not depend on them. See [usage observer source](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/usage/UsageStatsManager.java) and [package-management source](https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/content/pm/PackageManager.java). This is a platform boundary, not a claim about another application's private implementation.

Tests cover denial, partial grant, settings cancellation, listener disconnect, revocation during writes, process death, reboot, force-stop and unsupported settings intents. Source status and enforcement outcome must remain independently accurate. OS backup/device-transfer restrictions are a release permission/privacy gate under [Backup](BACKUP-RESTORE.md).
