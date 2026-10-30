# Protection specification

**Status:** Rule engine planned; enforcement mechanisms require Android and policy validation. Updated 2026-10-26.
**Responsibility:** Defines deterministic rules, conflict resolution, interventions, exceptions and safe failure.
**Related specifications:** [Native contracts](NATIVE-CONTRACTS.md), [Permissions](PERMISSIONS.md), [Settings](SETTINGS-SPEC.md), [Testing](TESTING.md).
**Unresolved feasibility:** F-01, F-02 and F-08 in [Decisions](DECISIONS.md); third-party apps cannot assume privileged system suspension.

## Rules and choices

Each rule includes ID/revision, enabled state, target apps/category snapshot or supported surface, trigger, applicable zone policy, action, enforcement behavior, exceptions, override behavior, emergency bypass and last evaluation. Triggers include daily budget, weekday/time range, manual focus deadline, repeated reopen threshold and optional surface exposure budget. Save disabled until the person previews and activates it. Category targets show current resolved membership; a membership change previews new targets and versions the rule.

Actions include reminder, reflection prompt, cooldown and best-effort block. Per rule the person chooses reminder only, immediate override, or a voluntarily stricter ordinary override. Observation-only use remains available. Interventions explain the triggering evidence and any missing coverage. Unknown usage must not appear as an exact remaining balance; a user may explicitly choose conservative blocking on unknown usage, with a visible reason, rather than the app inventing consumed minutes.

## Deterministic evaluation order

Evaluate in Kotlin against one transactional configuration revision and a timestamped source snapshot. Precedence:

1. Emergency bypass and invariant access exclusions (emergency dialer, Android settings/permission recovery, essential system UI, and the app's own recovery screen).
2. A user-issued protection pause or valid scoped override. All-active overrides must explicitly enumerate/snapshot affected rules; later rules are not silently included.
3. Per-rule allow exceptions for target/time; an exception to one rule does not waive another rule.
4. Active applicable rules. Select the strongest user-authorized action (block > cooldown > prompt > reminder); combine reasons without duplicating usage or interventions. Tie-break by explicit priority, then stable rule ID. More restrictive override behavior must be previewed before activating overlapping rules.
5. Available renderer and grants. If unavailable, report unavailable or reminder fallback only if the user chose that fallback. Never report “blocked” when the action failed.

Daily category budgets use the union of applicable session slices per target definition, avoiding double count across overlapping rules. Time already consumed is independent of intervention. A three-minute override does not erase a daily budget. Store rule evaluation and intervention outcome separately with reason codes and configuration revision.

## Schedules, cooldowns and commitment

Daily limits reset at the next calendar boundary in the selected zone; DST and travel do not create negative or duplicated counters. The editor defaults to follow-device zone, offers fixed zone explicitly, and previews the next boundary. Overnight windows carry an explicit start weekday. A focus session uses a monotonic deadline within a boot and a persisted UTC fallback after reboot; clock anomalies are visible. On changes/reboot/restart, reevaluate current conditions before restoring an intervention.

Cooldown triggers are idempotent and keyed by rule and target episode. Repeated callbacks cannot extend a cooldown unless the rule explicitly defines a new trigger. Prompts have a frequency ceiling; a failed renderer is not counted as a displayed prompt. Bedtime routines may combine reminders, schedules and optional DND in expansion. DND changes require ownership-aware restoration so ending a routine cannot undo a newer manual setting.

Commitment modes may add a reflection step, bounded wait, or a chosen ordinary override duration. They are voluntary, previewed, and reversible through a clear route. Never hide an uninstall path, block permission settings, impersonate system dialogs, enable device administration, or delay emergency access. No external accountability recipient or PIN guardian is included without a separate product/privacy specification.

## Enforcement boundary

The [awareness overlay](LIVE-OVERLAY-SPEC.md) is optional information, not enforcement. Hiding it does not disable rules or pause observation; those actions are separate and named. Tapping “Take a pause” opens an explicit choice and cannot silently activate a block or navigate the underlying feed. Count/exposure thresholds can trigger only the rule the person previewed and enabled, under the same conflict/emergency/frequency invariants.

Candidate renderers include in-app prompts, permitted notifications, and tested accessibility/overlay mechanisms. Overlay access and accessibility overlays are different mechanisms: select one explicitly after feasibility tests, disclose required grants, and do not request both reflexively. A deterministic user-selected navigation action such as Back may be evaluated only for a validated narrow rule with safe exclusions and distribution review. Never automate clicks, sign-in, content engagement or arbitrary navigation.

Android's public policy permits static, user-defined accessibility automation while prohibiting autonomous planning/execution for general apps; separate in-app disclosure, consent and store declarations are required. This project is not designated an accessibility tool. See [Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en). Recommendations do not execute actions.

## Failure and acceptance

Revoked permission, stopped service, unknown surface, unavailable overlay or missed schedule must change health and rule outcome promptly when observed. On uncertain surface identification, release selective blocking and explain unavailable enforcement. Never block an entire app as a silent fallback for a surface rule. Native logic must survive UI/JS death, but Android force-stop and OEM restrictions prevent universal guarantees. Recovery is bounded and user-visible.

Acceptance P-01–P-08 covers overlapping budgets, schedule/reboot, bedtime ownership, selective surfaces, duplicate prompts, scoped overrides, emergency access, unavailable grants and voluntary friction. Tests must prove exclusions and emergency routes on real devices before any blocking release.

ADR-055/AS-050 now supplies an experimental basic visit-reminder candidate, disabled by default, with durable configuration and separate affirmative activation. Defaults are 15 saved viewer encounters or 15 minutes of detected surface occupancy, whichever threshold comes first. It uses committed encounters and measured intervals, not swipes or wall time in an unknown context. Known dedicated viewer pages include the existing category distinctions; Home requires admitted video detection. The same rule applies to enabled sources. Thresholds restart on leaving recognized Reels, app/window/source/service changes, rule edits and dismissal. The accessibility overlay has immediate Continue and Open Attention Stats actions; it does not navigate or close the provider app, runs independently of HUD display, and stops active auto-scroll. Unknown/locked/outside surfaces release it. Protect shows status; Settings exposes the editor. This does not implement schedules, focus, blocking, emergency-friction or the full rule engine. Physical renderer/trigger and broader safety evidence remain separate gates.

ADR-059/AS-051 adds an experimental free, independently opt-in per-source dedicated-viewer blocker. Recognized, supported, noneditable viewers only; normal Home feeds and uncertain surfaces release it. An opaque native accessibility cover intercepts provider-area touch, pauses observation/time while covered, leaves system navigation/recovery available and offers immediate per-app-visit Allow and Open Attention Stats. It never closes an app, clicks provider controls or promises audio pause. A bypass survives internal navigation but ends on leaving that app, lock/rule/source/service changes. Rule status distinguishes saved-on from missing supported observation/access. Basic reminders now accumulate eligible counts/time across the observed unlock session; ineligible surfaces pause timing rather than resetting totals. ADR-060 excludes recognized ads from count thresholds only when chosen; measured time is unchanged. Full rule engine/lifecycle/Play acceptance remains open.

ADR-061 explicitly defers whole-app time budgets/locking and typed/timed continuation overrides until after MVP. A disabled timer icon in app-time lists is only a future affordance; the current dedicated-Reels cover and basic reminders do not implement these rules.
