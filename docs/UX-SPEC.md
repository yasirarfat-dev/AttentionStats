# User experience

> **Current behavior (1.0.0):** [ADR-066](DECISIONS.md#version-independent-detection-interventions-and-mvp-10--adr-066--2026-10-30) supersedes version-pinned detectors, per-app notices 3–5, structure-check diagnostics and the five-tab preview described below where they conflict. See [State](STATE.md) and the public [privacy policy](../policies/PRIVACY.md).

**Status:** AS-045 implements an experimental native five-tab shell; most domain features remain unavailable. Updated 2026-10-28.
**Responsibility:** Defines journeys, screen states, permissions, chart semantics and accessible interactions.
**Related specifications:** [Human factors](HUMAN-FACTORS.md), [Navigation](NAVIGATION.md), [Design](DESIGN-SYSTEM.md), [Settings](SETTINGS-SPEC.md), [Product](PRODUCT.md), [Privacy](PRIVACY.md).
**Unresolved feasibility:** F-01, F-02 and F-06 in [Decisions](DECISIONS.md); native grant routes, detectors and provider flows require device validation.

## Screen inventory

The experience should provide peace of mind, understandable evidence and user-owned boundaries. [Human factors](HUMAN-FACTORS.md) governs the supportive voice and evaluation. The [interactive study](../design/prototype/README.md) makes that direction reviewable with synthetic states; it does not replace native implementation.

ADR-055/AS-050 makes Today lead with concise per-app statistics: Reels encounters, Reels time and separate whole-app duration. Source health belongs in Settings; detailed explanations are available on demand. History shows saved counts and timer history; Patterns explicitly says it is being built until actual analysis exists. Protect supplies disabled-by-default configurable count/time reminders. These integrations remain planned at this checkpoint. Detail is available on demand. Show an explicit stopping point, not an infinite feed. Avoid repeated permission prompting, streak pressure, composite wellness scores or emotional dependency. Recreation is not automatically a problem. Every reflection can end with no change.

ADR-062 uses the exact summary labels App Time, Reels Time, Reels Encounters and Apps Timed. Allow Reels Encounters to wrap cleanly inside a growing card; keep 14sp summary labels and exact-value accessibility/dialogs. Whole-app rows show observed opens below the name, separately from used time and the disabled post-MVP timer column. Opens includes observed returns from other apps and suppresses internal activity navigation; it is not a cold-process launch count. Unknown opens remain an em dash. These refinements are experimental until the updated own-window and real app-entry trials pass.

| Screen | Primary content/action | Required states |
|---|---|---|
| Welcome and privacy | Local-first explanation, open-source link, choose observation/protection goals | Fresh install, resumed onboarding, skip |
| Source selection | Each source, retained fields, app selection, exclusions and detail | All off, selected, unsupported, sensitive-detail opt-in |
| Permission disclosure and return | Separate explanation, grant navigation, actual recheck | Declined, settings cancelled, partial grant, granted-but-disabled |
| Home | Per-app Reels counts/time and separate whole-app duration; concise statistical cards | No consent, no events, partial coverage, stale, error |
| History | Calendar/range, sessions, opens, device activity, filters and provenance | Loading, empty-known, unknown gaps, paginated, storage failure |
| App detail | Duration/opens, sessions, surface summary, category, rule shortcut | Untracked/excluded, removed app, unsupported surface |
| Social summary / surface detail | App totals versus confirmed surface time/exposures; count explanation | Experimental disabled, supported range, unknown item count |
| Reels & short videos | Prominent Home entry; per-platform time/exposures/visit/day, shared date range and evidence | No enabled source, supported subset, unsupported target, partial/stale; never generic app time labelled Reels |
| Detailed activity diary / metric explorer | Activity composition, chronology, qualified interactions/attempts, data-usage tab, chosen metric comparisons | Sparse/summary-only, incompatible revisions, unknown metric, bounded paging, source controls |
| Summary policy / preview | Custom per-source/app/detail tiers; day/week/month/year, zone, lost detail and storage estimates | Preserve default, invalid overlap, stale preview, interrupted transaction, unsupported regrouping |
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

[Analytics](ANALYTICS-SPEC.md) defines the deeper mobile screen structure and [Metric catalog](METRIC-CATALOG.md) defines available data points. The dashboard must make per-platform short-video time/counts discoverable, with progressive disclosure for advanced detail. Do not expand the completed browser study. Root `src/features` owns mobile screens by domain; every implemented route/state/behavior requires live debug-phone or emulator verification under X-10.

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

AS-050 replaces descriptive Today/History placeholders with native statistical cards: foreground app duration, independently observed Reels-surface duration and saved encounters by source. Unknown time remains an em dash with a short coverage explanation. History offers daily and rolling seven/31-day views with recorded-day selection; no synthetic usage or insight is displayed. Patterns explicitly says Being built. Settings groups actual controls and source health, including source-grant guidance. Protect shows the disabled-by-default reminder’s actual thresholds/status and links to its editor. AS-049’s first-run single-screen introduction emphasizes open source, inspectable source/privacy links, encrypted local history, no developer backend/ads and the accurate statement No data sent to us. It does not promise guaranteed trust or no local recording. TalkBack/scale/RTL/performance and complete lifecycle acceptance remain separate from initial screen traversal.

ADR-057 adds shared seconds/minutes/hours/days formatting and exact count aggregation. Today and History use a uniform two-by-two summary: App time, Reels time, Encounters and Apps timed. The last metric counts distinct packages with recorded app intervals, with an em dash for unavailable coverage. Summary counts abbreviate from ten thousand; tapping reveals exact counts or full time units, also available through accessible labels. Card height expands for wrapping. Dense per-app counts abbreviate only visually and retain exact accessible values. Test-only labelled display fixtures exercise Today and History at 320dp, including 24-hour/month-long values, signed-64-bit maximum counts, exact-value dialogs and large-to-small updates. They do not write synthetic history. Normal screens contain no developer verification control. Onboarding completion resets the destination stack and selects Today; choosing Reels sources preserves an actual root screen/back destination instead of trapping the Today tab on setup.

ADR-058 adds optional Battery & background guidance in Settings, actual package status and a package-scoped Android app-settings action. It explains optional device-specific steps and possible battery cost, without requiring a preference change to use the app or promising uninterrupted collection.

ADR-059 labels the main counter This unlock session across all observed apps, preserving totals across app/route changes with per-app contributions in details. Blocking is a separately activated Settings choice, has a calm opaque native cover and immediate per-app-visit Allow/Open Attention Stats actions. It neither hides recovery nor promises to stop audio. ADR-060 exposes the recognized-ad inclusion choice without losing independent ad counts, and fixes Today/History to two explicit two-column rows with 14sp summary labels and 13sp secondary labels. Own-window screenshot review and actual card geometry checks are required, beyond text-fit assertions.

ADR-061 aligns Time by app into identity/used/timer columns and separates each app/Reels group with a softly contrasted rounded surface and spacing. Timers, app locking/overrides and per-app weekly drill-down are explicitly post-MVP. The timer affordance is disabled and named unavailable; time remains real data with a full accessible value. A new own-window row/layout review is pending beyond the earlier diary artifact.


## Selected-day history in Insights (2026-11-12)

AS-014/AS-018/AS-037 adds a bounded native daily explorer within the existing Insights tab. A seven/31-day period summary leads into a daily chart and named date buttons, newest first. The selected date controls video encounters/surface duration, per-source rows, hourly encounters and an optional exact hourly list, whole-app duration/observed opens, media/playback detail and recorded mindful-break choices. Previous/Next buttons provide a non-dragging alternative; boundary buttons disable. A date still in the new range remains selected; otherwise selection resolves to the newest available date. Native snapshots remain authoritative, and no new tracking or consent is enabled.

Calendar labels use the snapshot's civil date without UTC offset conversion. The grouping zone and today's incomplete scope remain visible. Missing duration and opens use an em dash/unavailable label; partial known duration is labelled recorded time, with the native usage-status explanation. No saved events does not establish full coverage. Hourly bins include photos/ads and are labelled encounters, not watched videos. Source-level media facts are counted once per source/day even when regional package rows repeat them; whole-app time remains package-level. Video surface and whole-app time overlap and must not be added.

Loading and initial failure withhold statistics; failure after a successful read labels retained observations and offers retry. Automatic book/walking/scroll-distance equivalents are removed: the specified personal reflection remains a future opt-in with user-owned assumptions under AS-040. No forecast, wellness score or coverage comparison is introduced. Statistic labels wrap, segmented controls have 48dp minimum height and zero-share meters render no fill. Full TalkBack/large-type/RTL and every failure state still need separate acceptance.

## App-history explorer (2026-11-12)

AS-014/AS-018/AS-037 adds By day / By app choices below the shared period summary. By app searches saved app names and package identifiers, with time, opens or alphabetical ordering. Time/opens order uses exact integer subtotals, with missing values after recorded zero. Ten app rows render per page with named Previous/Next controls; search/sort resets paging. Search is transient view state, not a stored personal field or collection filter.

Selecting an app shows its seven/30-day recorded time and observed-open subtotals, the number of dates with each value, measurement explanation and newest-first daily rows. Each daily row retains independent unavailable time/opens; a missing row does not imply zero. Time bars are relative to the largest recorded daily duration in the period, with no fill for zero and no bar for unavailable time. The list initially shows seven days and can expand to the full bounded period. Selecting a daily row opens that date's By day overview. Back to app list and Android Back return from app detail; search/sort/page are retained during that return. View transitions/page navigation return to the top, and selecting an app dismisses the keyboard.

App editions remain separate by package. Source-level video facts must not be relabelled as package-specific history. The explanation distinguishes usage-event time from attention and observed entries from cold launches. Range refresh uses current snapshot data; loading withholds old-range content, and removed app selections resolve to the current list. No session chronology, new source access, category inference, forecast or protection activation is introduced. Full accessibility/theme/performance and injected error-state breadth remain separate acceptance work.

**Period refinement after maintainer feedback:** Insights now offers Last 7 days and Last 30 days, including today in the snapshot zone; month is a rolling 30-day window, not a calendar month or 31 days. All time means all retained history with actual date bounds and known/unknown coverage, not an arbitrary last-X-day alias. The separate native aggregate/page operation described below now supplies this experimental control. A future custom Last X days is a separate range. Earlier seven/31-day descriptions above record the initial slice, superseded for current Insights by this refinement.

## All-time Insights (2026-11-12)

The Insights range selector offers Last 7 days, Last 30 days and All time. All time shows retained source video encounters and recorded surface-time subtotals, Since [first date] and the actual inclusive calendar span through today. Empty retained history says No retained observations yet. Calendar days include gaps; they are not covered days. Optional Show/Hide all-time sources reveals source-level counts/time and explains missing intervals; it does not infer viewing or attention. Last retained observation and the grouping zone are disclosed. App detail/list returns and app-day drill-down scroll to the explorer header beneath the retained-history summary; date paging returns to the summary. This refines the earlier scroll-to-page-top behavior. App daily labels include the year, and historical app detail does not imply that its page includes today.

Below that lifetime summary, Browse retained history shows exact page date bounds, page-only video/app-time totals and named Older dates/Newer dates buttons. Each page contains at most 30 days; the oldest can be shorter. Boundary controls disable; Return to latest dates remains available for a historical page, including a failed read. Existing By day and By app explorers consume only this bounded page. Their subtotals/search apply to its dates, not all retained packages. Daily headers include a year so older years remain identifiable. No all-time app-duration total is invented from a partial page. Paging resets the selected day and scroll position; range changes return to the newest page. Loading withholds mismatched page statistics, and same-page refresh failures label retained values. Lifetime queries refresh on entry, paging, foreground return or retry. Custom Last X days, whole-history app ranking, durable rollups and broad accessibility/performance acceptance remain open.
