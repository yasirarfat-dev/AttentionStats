# Navigation

**Status:** Native navigation planned; separate browser study implements demonstration routes only. Updated 2026-10-27.
**Responsibility:** Defines route ownership, hierarchy, parameters, back behavior and external entrypoints.
**Related specifications:** [UX](UX-SPEC.md), [Design](DESIGN-SYSTEM.md), [Contracts](NATIVE-CONTRACTS.md), [Settings](SETTINGS-SPEC.md).
**Unresolved feasibility:** F-03 in [Decisions](DECISIONS.md); latest navigator/native-screen compatibility needs build verification.

## Hierarchy

```text
Root
  OnboardingStack: Welcome → Sources → Disclosure → PermissionReturn
  MainTabs
    HomeStack: Home → MetricDetail → AppDetail / Health
    HistoryStack: History → SessionDetail → EvidenceDetail
    InsightsStack: Insights → InsightDetail → CategoryEditor
    ProtectStack: Protect → RuleEditor → RulePreview / FocusSession
    SettingsStack: Settings → Sources / Health / Storage / Backup / Appearance / About
  Shared destinations: SocialSummary → SurfaceDetail → HowTrackingWorks
  Modal flows: AppPicker, DateRangePicker, DeletePreview, ExportScope, RestorePreview
  Native flows: Android settings, document picker, secure credential entry, intervention
  Native awareness: optional live counter → detail / explicit protection choice / hide
```

Five primary tabs reflect distinct user jobs. Social summaries are reachable from Home and History app detail; their backing data is shared. Settings is reachable even when onboarding is skipped or permissions are missing. Native intervention is not dependent on React Navigation or the JS runtime staying alive.

Visible tab labels are **Today**, **History**, **Patterns**, **Protect**, **Settings**. Today maps to HomeStack and Patterns to InsightsStack; internal route names need not imitate the visible labels. Keep tabs available while the screen content scrolls. The browser [design study](../design/prototype/README.md) demonstrates labels, heading focus, modal dismissal and focus return; it is not a React Navigation implementation or a native back-stack test.

Live awareness setup is reached from Settings and supported surface detail; native display is outside React Navigation and independent of JS. Its detail routes to evidence, display controls or an explicit protection choice; it never silently executes an underlying app action. The study's “Preview live counter” is a review route using an original static synthetic surface, not a third-party app or a WebView implementation strategy.

## Typed route parameters

Routes carry opaque IDs and bounded filter keys: appId, sessionId, insightId, ruleId, sourceId, surfaceId, jobId and validated date range/zone. Do not pass history arrays, URL/title content, credentials, provider URIs or serialized rule objects as navigation state. Load current records through the platform adapter. Deleted/stale IDs show a neutral unavailable state with a back route.

History date/filter state stays in the current screen/view preferences; authoritative native filters and consent are separate. A deep link to a rule editor opens a draft with current validation, never activates it. All external entrypoints validate route and IDs and require appropriate in-app confirmation for mutation. No public link grants a permission, overrides a rule or starts a restore.

## Back and restore behavior

Tab changes preserve bounded view state; no hidden infinite data retention. Back from a draft asks to discard only if there are unsaved meaningful edits. Permission-return back goes to the originating explanation, with a fresh grant check. Picker cancellation returns to the backup screen without a failed-history state. Long jobs are owned natively; navigating away does not accidentally cancel or duplicate them. Return by job ID and query status.

On process restart show the current native focus/enforcement state before offering actions; do not restore a stale “blocking succeeded” screen. Persist only non-sensitive navigation preferences. After restore, invalidate history/insight detail routes from the old generation. Startup defaults to Home with restored-source review, not resumed source activation.

Screen readers receive route titles and focus on the heading after navigation, return focus to originating controls after dialogs, and announce important status changes once. Modals and overlays have deterministic focus/escape handling and never trap access to emergency controls. Tests cover back navigation without grants, stale/deleted routes, restored generations, secure credential flow and accessibility focus.
