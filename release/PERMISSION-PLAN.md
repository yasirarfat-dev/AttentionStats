# Attention Stats permission and declaration plan

Reviewed 29 October 2026. Package: **attention.stats**. This covers the known roadmap so no access requirement is overlooked. It is not a request to grant unimplemented features access now.

Google permits sensitive permissions/APIs only for implemented current features promoted in the listing, with incremental in-context requests and consent. Future features can add declarations in later updates; they do not need speculative preapproval in the first closed test. [Google Play policy](https://support.google.com/googleplay/android-developer/answer/9888170), [Android permission workflow](https://developer.android.com/training/permissions/requesting).

| Access / mechanism | Current counter | Later feature and publication gate |
|---|---|---|
| AccessibilityService protected by BIND_ACCESSIBILITY_SERVICE | Present; bounded selected-source observation with separate disclosures/source choices and Android settings. Optional session-only auto-scroll additionally requires Allow then explicit viewer Start. canPerformGestures=true, isAccessibilityTool=false; the binding permission protects the service, not a runtime grant | ADR-051 supersedes the passive-only service. Numeric progress and one revalidated upward swipe are separate from counting; visible Stop and context/lock/revocation fences are required. Source-specific action qualification and the updated Play declaration/video remain pending |
| Package visibility: four counter targets and launcher apps | Present; four explicit packages plus exact MAIN/LAUNCHER query for app-time labels | No QUERY_ALL_PACKAGES. New discovery requires a concrete implemented purpose and audit |
| Accessibility overlay | Optional counter, reminders and dedicated-viewer blocking using the enabled service; separate controls and immediate dismissal/bypass | No SYSTEM_ALERT_WINDOW needed. Blocking can leave audio playing; unsupported/Home/editable surfaces remain open |
| PACKAGE_USAGE_STATS / Usage Access special settings | Present for the experimental app-time collector, independently off by default; separate notice plus actual AppOps grant required | Bounded event-kind/package/timestamp processing and revocation fences. App time is an interval estimate, not app-open or Reels counting. Broader lifecycle qualification remains open |
| NotificationListenerService / BIND_NOTIFICATION_LISTENER_SERVICE | Absent | Optional notification metadata lifecycle only; separate disclosure, user notification-access settings, no bodies/titles, source/revocation tests |
| POST_NOTIFICATIONS runtime permission where required | Absent | Working optional reminders or eligible visible-service notifications, requested when enabled; denial handled without inventing service eligibility |
| Foreground-service declaration/type/permissions | Absent | Only a demonstrated eligible task/start path after platform/policy review. No generic always-on monitoring type or unimplemented specialUse declaration |
| RECEIVE_BOOT_COMPLETED | Absent | Permitted implemented schedule/recovery with first-unlock, reboot and OEM testing; no promise of survival after force-stop |
| ACCESS_NOTIFICATION_POLICY / DND special access | Absent | Optional actual bedtime/focus DND rules, reversible and explicitly enabled; keep other actions usable after denial |
| SAF document URI grants | No archive picker in current candidate | Working export/import with bounded authentication/parsing/recovery. Use the system picker and scoped grants, not broad filesystem permissions |
| INTERNET | Absent | Actual approved optional transport only; update privacy/Data safety and SDK/egress review. Direct Drive remains blocked. Browsers open public links without this app having Internet permission |
| Provider OAuth scopes | Absent | Only a permitted implemented integration, least scope and explicit destination choice; no speculative token capture |
| Battery optimization settings / direct exemption | Optional own-package status/guidance and app-settings navigation; no exemption permission/request | User chooses Android battery settings. No automatic preference changes or promise of continuous service survival |
| Exact alarms / wake locks | Absent | No justified current feature. Add only for an actual reviewed need, with applicable declarations and denial behavior |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | Build-generated signature permission scoped to attention.stats | Internal AndroidX receiver protection, not a sensitive user grant to request |

Contacts, location, camera, microphone, SMS, call logs, broad photos/video access, all-files access, device admin, root, VPN capture, screen recording and notification/message-body capture are outside the current design. Do not request them speculatively. Adding a new requirement is a separate design/policy decision.

ADR-054 separately authorizes a test-only memory-resident progress-bar-pixel investigation for missing playback signals. Its instrumentation capture saves/uploads no frame and changes no provider playback controls. It adds no production screenshot capability. Admitting a production path requires measured cost/fidelity, source/context fences and updated in-app/privacy/Play disclosures before it runs.

Before each publishing candidate: compare this plan with the exact merged AAB manifest, service XML, active-track behavior, in-app disclosure, policy, Data safety and Console declarations. Test denial, cancellation, grant, revoke, pause and delete on the actual package. A grant on the development package is not a grant on attention.stats. ADB operator authorization is for testing; end users choose each supported source/access purpose themselves.

The maintainer requires verified **Facebook, Instagram, YouTube Shorts and TikTok** counting before the publishing candidate. Permissions alone cannot create a detector or qualify Facebook's UI signals.
