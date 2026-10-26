# User experience

**Status:** Native experience planned; AS-033 implements a separate synthetic browser design study. Updated 2026-10-27.
**Responsibility:** Defines journeys, screen states, permissions, chart semantics and accessible interactions.
**Related specifications:** [Human factors](HUMAN-FACTORS.md), [Navigation](NAVIGATION.md), [Design](DESIGN-SYSTEM.md), [Settings](SETTINGS-SPEC.md), [Product](PRODUCT.md), [Privacy](PRIVACY.md).
**Unresolved feasibility:** F-01, F-02 and F-06 in [Decisions](DECISIONS.md); native grant routes, detectors and provider flows require device validation.

## Screen inventory

The experience should provide peace of mind, understandable evidence and user-owned boundaries. [Human factors](HUMAN-FACTORS.md) governs the supportive voice and evaluation. The [interactive study](../design/prototype/README.md) makes that direction reviewable with synthetic states; it does not replace native implementation.

Today opens with covered app time, a coverage explanation, one optional focus action, one finite descriptive pattern and selected app totals. Detail is available on demand. Show an explicit stopping point, not an infinite feed. Avoid repeated permission prompting, streak pressure, composite wellness scores or emotional dependency. Recreation is not automatically a problem. Every reflection can end with no change.

| Screen | Primary content/action | Required states |
|---|---|---|
| Welcome and privacy | Local-first explanation, open-source link, choose observation/protection goals | Fresh install, resumed onboarding, skip |
| Source selection | Each source, retained fields, app selection, exclusions and detail | All off, selected, unsupported, sensitive-detail opt-in |
| Permission disclosure and return | Separate explanation, grant navigation, actual recheck | Declined, settings cancelled, partial grant, granted-but-disabled |
| Home | Covered device/app summary, active focus, source health, chosen dashboard cards | No consent, no events, partial coverage, stale, error |
| History | Calendar/range, sessions, opens, device activity, filters and provenance | Loading, empty-known, unknown gaps, paginated, storage failure |
| App detail | Duration/opens, sessions, surface summary, category, rule shortcut | Untracked/excluded, removed app, unsupported surface |
| Social summary / surface detail | App totals versus confirmed surface time/exposures; count explanation | Experimental disabled, supported range, unknown item count |
| Insights / insight detail | Descriptive patterns, explanation, data window and correction | Insufficient data, low confidence, deleted evidence, prediction off |
| Protect overview | Rules, active sessions, enforcement health and emergency bypass | Observation only, enabled, conflict, unavailable enforcement |
| Rule editor / preview | Targets, trigger, action, schedule/zone, exceptions and overrides | Invalid, changed category, permissions missing, conflict preview |
| Focus session | Remaining configured session, reasons, pause/stop/override | Active, expired, reboot recovery, clock anomaly |
| Intervention | Rule reason, chosen action, scoped override, emergency exit | Reminder, cooldown, block, renderer unavailable |
| Settings hub | Searchable grouped preferences and source/privacy shortcuts | Defaults, changed preferences, pending native save |
| Permissions and service health | OS grants versus enabled sources, last success, gaps, fix steps | Denied, paused, degraded, unsupported, stale |
| How tracking works | Adapter/source fields, version, support evidence, data destinations | No adapter, experimental, validated, withdrawn |
| Optional live awareness counter / detail | Current-visit plus today exposures/time for the same qualified app/surface; source, coverage, hide/position and optional pause | Off, qualified, partial, stale, unknown/removed, hidden; [spec](LIVE-OVERLAY-SPEC.md) |
| Storage and deletion | Measured bytes/rows, retention, scope preview and delete | Calculating, stale preview, low space, job failure |
| Backup and recovery | Manual export/import, recovery key, status; automatic backup blocked | No archive, job running, provider failure, incompatible file |
| Restore preview | Archive scope/version/date, replacement impact, current-data export | Auth failure, corrupt, unsupported, ready, committed |
| About and licenses | Version, source/commit, Apache license, third-party notices | Documentation-only before a release |

## Onboarding and permission journeys

Opening the app records no behavioral history. Explain the planned privacy promise in plain language; optional provider transfers are described separately when selected. Offer “Continue without tracking” with equal prominence. Users can inspect product guidance and prior restored history without granting new access.

Select source → select apps/exclusions → explain retained fields → separate consent → open relevant Android settings → recheck on return → explicitly enabled source begins coverage. Do not bundle title/URL/topic opt-ins with basic usage. Accessibility disclosure is a dedicated step; refusal preserves other features. Show the difference between permission available, product enabled and collector healthy. Repeated denials do not trigger automatic prompts.

Source pausing offers a duration and visible resume time. Protection pause is a separate action. Turning off a source offers optional prior-data deletion; retaining history is a valid choice. Content capture remains off when uncertain context safety prevents reliable exclusion.

## Charts and interpretation

Duration axes use explicit units and zone-labelled dates. Distinguish device interactive union from app totals that can overlap. Never combine exposure counts and minutes into an unexplained score. Unknown intervals are patterned gaps; an empty chart with disabled access says “Not tracked,” while a fully covered zero uses “No observed activity.” Provide table/list alternatives and TalkBack summaries. Do not rely on color alone.

A chart tap opens a detail sheet with source, evidence kind, coverage, detector/reducer revision, assumptions and relevant controls. Predictions use clearly labelled forecast styling and uncertainty. Content topics show unknown fraction and correction. Neutral examples: “Three reopenings in ten minutes” or “Estimated short-video exposures”; avoid “addicted,” “dopamine depleted,” or medical claims.

## Rule creation and intervention

Rule editor stages target → trigger → action → per-rule behavior → schedule/timezone → exceptions/override → preview → explicit activation. Preview shows overlaps and the effective action. A surface rule cannot silently turn into an entire-app block. Missing grants display unavailable enforcement, with an explicitly chosen reminder fallback if any.

Interventions show why, which rules apply and what scope an override covers. Emergency access is visible and immediately available without a wait, PIN, explanation or biometric requirement. Commitment-mode friction affects only ordinary exits. Returning from an override shows expiry and retained usage, not a fake budget reset.

## Customization, safety and recovery

Dashboard cards, chart range, chart type, category labels/colors, theme, motion, number/time formatting and start-of-week are configurable. Data-changing settings require native success before the UI reports saved; preserve edits on transient failure and resolve revision conflicts explicitly. Destructive previews include scope and derived-data impact, without shaming or repeated coercive confirmations.

Support 200% text, screen-reader navigation, large touch targets, RTL-ready layouts, reduced motion and predictable back behavior. Recovery tasks cannot depend solely on charts, swipes or color. Keep grants and account identifiers off lock-screen widgets by default. Backup credentials are entered through a secure flow with no accidental screenshot/clipboard exposure. Restore errors preserve the current history and offer a retry or cancel path, not a reset button as the only escape.

Acceptance X-02 and UX review in [Testing](TESTING.md) cover skipped onboarding, partial grants, chart gaps, long text, errors, emergency bypass and recovery. A static design review does not count as real-device accessibility verification.

## Calm, useful engagement and handoff

Primary labels: Today (Home route), History, Patterns (Insights route), Protect and Settings. Preserve predictable navigation while the content scrolls. Use the [palette/type/motion contracts](DESIGN-SYSTEM.md) for a coherent experience across charts, buttons, dialogs and settings. Privacy is directly reachable from each main screen.

Protection prompts state the chosen rule and threshold before offering return, scoped continuation and unconditional emergency access. An ordinary override states which rule it waives, when it expires and which other rules still apply. A completed or ended focus session has a quiet neutral outcome, never a failure badge. Consent refusals have equal-weight controls and remain usable paths.

The browser study covers main views, app detail, evidence, source explanation, rule draft/review, focus timer and interruption. Native permission navigation, saved preferences, background enforcement, secure recovery and complete settings remain AS-018 and related task scope. Never carry prototype in-memory success into a native authoritative-save claim.

X-04–X-07 define humane-copy, evidence, interruption, theme/motion and layout acceptance. Browser automation establishes only its tested subset. Participant review, actual Android typography/insets, TalkBack, switch access, battery and frame pacing remain unverified.
