# Live awareness overlay

**Status:** Experimental Android feature; synthetic interaction preview only; no native overlay or live detector implemented. Updated 2026-10-27.
**Responsibility:** Defines optional in-context visit/day counters, permission boundaries, snapshot semantics, safety and humane behavior.
**Related specifications:** [Social detection](SOCIAL-MEDIA-SPEC.md), [Tracking](TRACKING-SPEC.md), [Human factors](HUMAN-FACTORS.md), [Permissions](PERMISSIONS.md), [Contracts](NATIVE-CONTRACTS.md), [Models](DATA-MODELS.md), [Protection](PROTECTION-SPEC.md), [Settings](SETTINGS-SPEC.md), [Testing](TESTING.md).
**Unresolved feasibility:** F-01, F-02, F-08 and F-09 in [Decisions](DECISIONS.md); target-version detection, native renderer choice, touch behavior, OEM lifecycle, policy review, battery cost and accessibility need evidence in AS-034.

## Purpose and default

Help someone notice continued scrolling while they are still in a supported feed. A glance should answer “how long has this visit lasted, how much have I already seen today, and do I want to continue?” It is an awareness tool, not a competition, diagnosis or mandatory barrier.

Off by default. Opt in per supported app/surface after a preview. The person chooses count/time fields, size, position and visibility. App-level tracking, surface counting, overlay display and protection are separate choices. Declining the overlay keeps other supported features available. Hiding it does not pause tracking or turn off a protection rule; explain and expose those separate actions.

## Minimal display and expanded detail

Default placement: **top center**, below the status bar/cutout and outside the target app's critical controls. Use a small text HUD, **“This visit ~18 · Today ~64”**, with a legible “estimated exposures” label or equivalent accessible name. No mascot, decorative animation or large floating dashboard. Keep at least a 48dp hit target around the compact visual. Offer leading/trailing presets when the center would obscure controls or when the person prefers another position. Use minutes instead if count is unavailable and covered surface time is supported; do not substitute swipes. Expand on deliberate tap to show:

- Current app and confirmed surface; default day total is **this same app/surface**, not an unexplained cross-app sum.
- Current visit exposures and known active surface minutes; today's exposures and known surface minutes, calendar date and selected timezone.
- Evidence kind, coverage, last update and what a count means. Repeated items can be new exposures; completion and attention are unknown.
- An optional chosen budget/reminder, with remaining amount only when coverage supports it. Unknown use cannot become an exact remaining allowance.
- “Take a pause” opens the configured protection choice; it does not autonomously click Back, close an app or activate a new rule.
- Collapse, hide for this visit, hide until tomorrow, disable overlay, pause observation and adjust rule as clearly separate controls. Emergency access remains immediate wherever an actual intervention appears.

Keep ordinary chrome small and positioned away from feed controls, subtitles, gesture edges and system insets. Never shrink the interactive hit target below 48dp. Offer preset positions and reset, not drag-only placement. Movement is deliberate, bounded and never an engagement game. Reflow at large text and support readers/switch input. Do not steal focus or announce every increment. Optional threshold announcements follow explicit reminder consent and caps.

No pulsing, rolling digits, “personal best,” competitive score, guilt text or default red escalation. A constantly visible counter may increase preoccupation for some people; an easily hidden or time-only mode must be equally valid. Whether the overlay helps is a user-review question, not an assumed psychological result.

Prefer a small set of useful options over a dense floating dashboard: (1) glanceable visit/day awareness, (2) optional chosen count/time threshold reminder, (3) explicit short pause/focus choice, (4) ordinary continuation and emergency access, (5) quiet later review with covered evidence. An estimate-based threshold must disclose approximation; never silently hard-block based on an uncertain count. No reminder after each item, automatic model-driven escalation or sound/haptic barrage. A user can choose lower frequency or no prompts. Selective protection must preserve messaging and other unrelated surfaces where qualified; unknown surfaces release it rather than becoming whole-app blocks.

## What the metrics mean

**This visit** references a native SurfaceEpisode with confirmed continuity. It begins only after the detector qualifies the surface/item, not simply on an app launch. Brief overlays/comments may preserve the episode and item identity when continuity is proven; excluded or non-surface time adds no surface duration. An app switch, lock, unsupported surface or uncertain continuity suspends/closes according to [Social](SOCIAL-MEDIA-SPEC.md). A new confirmed episode starts a new visit, without resetting today's history.

**Today** uses Exposure timestamps in a half-open local civil day for an explicit zone. It includes the current visit's qualifying exposures exactly once, using authoritative IDs. A visit crossing midnight continues as a visit; today's total includes only the new day. Thus the current-visit count can exceed today's count. Zone/DST changes recompute date bounds, retain source instants and label the change. Never add an entire session count again when a visit ends.

The tilde means an estimate, not a probability. An observed-transition count still needs detector limitations. Item identity or validated settled transitions drive increments; every accessibility callback, scroll event or redraw is **not** another exposure. Dedupe commits with the exposure record before a reliable displayed projection advances. Failures or stale snapshots cannot reset either value to zero.

Count eligibility must explicitly distinguish short videos from positively identified advertisements, stories, live streams and photo carousels. The proposed short-video metric excludes those other contexts; it does not claim to count every finger swipe. A separately requested all-feed/ad-exposure metric would need its own label, model and acceptance before implementation. If eligibility is ambiguous, withhold that transition and explain incomplete coverage. Returning from an excluded context must preserve or re-establish item identity without duplicate counting. Qualification must include interleaved ads; a short manual run does not establish general detector accuracy.

For incomplete coverage use “≥64 observed · gaps” when the available value is a defensible lower bound, or “Today unknown” otherwise. Estimates with known false-positive risk are not automatically lower bounds. Separate a frozen last-known value from a current metric with a visible stale label. If the target surface becomes unknown, remove the awareness window from that context; explain the gap in the app. On return to a qualified surface show count unavailable/partial until supported. Never leave an old number floating over chat or another app.

## Native source and snapshot

Kotlin consumes committed exposures, SurfaceEpisodes, coverage and aggregates from the authoritative Room store. A lightweight renderer receives immutable, revisioned snapshots. It does not traverse more nodes to draw a counter and does not depend on JS. Collection can process every qualified transition; rendering may coalesce visual updates (proposed ceiling once per second), preserving the latest committed total. Do not animate every increment or poll full history in a tight loop.

[Native contracts](NATIVE-CONTRACTS.md) owns the planned LiveSurfaceSnapshot shape. It carries app/surface/episode, generation, revision, evidence kind, nullable counts/time, day bounds/zone, coverage, freshness and reason codes. No titles, URLs, creator names or private content in the window or change events. No separate overlay history store. Configuration is native; presentation presets do not become a second source of behavioral truth.

After process death, requery the store. Unknown continuity starts an uncertain boundary, not a fabricated initial exposure. Deletion/reset/restore invalidates snapshots by revision/generation before redraw. Pausing observation stops new writes and live display; a privacy pause must not leave visible personal counts. Hiding display alone retains tracking consent. If storage is unavailable, show unavailable or remove the window rather than showing uncommitted events as durable history.

## Permissions and lifecycle are different responsibilities

| Access | Role | Boundary |
|---|---|---|
| Usage access | Coarse app sessions/day history where supported | Cannot by itself identify/count individual reels or shorts |
| Accessibility | Candidate package/surface/item metadata from a qualified adapter | Separate disclosure/consent; never assumed universal; content/topic capture still separate |
| Draw over other apps | Application-overlay renderer, if selected | Presentation only; does not improve detection or grant background permanence |
| Accessibility overlay | Alternative renderer owned by an enabled accessibility service | Different mechanism; evaluate least privilege/policy and touch behavior, not both grants reflexively |
| Notifications | Optional chosen reminders and eligible service visibility | Not permission to read notification contents or bypass background limits |
| Battery settings / OEM background controls | Optional troubleshooting after demonstrated reliability problems | No universal “run forever” grant and no guaranteed force-stop survival |

Android application overlays use `TYPE_APPLICATION_OVERLAY` and require `SYSTEM_ALERT_WINDOW`; system restrictions can move/hide them. Accessibility overlays are a distinct window type. These are renderer candidates, not proof of Play approval or a working detector. [Window types](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY), [overlay settings](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION).

Battery optimization exemption is not a first-run prerequisite. Generic settings guidance and a direct exemption request differ; Android documents restrictions on direct requests, including an adversely affected core function. Eligibility here is unverified. OEM “autostart/background” settings vary. Do not request an unrelated FGS type, wake lock or exact alarm merely to keep a counter alive; measure and justify the actual lifecycle strategy. Exemption cannot guarantee recovery from force-stop. [Doze guidance](https://developer.android.com/training/monitoring-device-state/doze-standby), [FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

Accessibility use still requires [distribution review](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en), separate disclosure and user-defined static rules for automated actions. Pure display is not permission for arbitrary navigation or autonomous action. Do not infer implementation or eligibility from another product.

## Safety and failure

Show only for the selected, qualified foreground surface/window on an interactive unlocked device. Remove on chat/private/sensitive/unsupported context, lock, permission/system dialogs, keyboard/sensitive input, app switch or lost renderer/source access. System, payment, authentication, emergency and permission-recovery controls must never be obscured. If safe placement/context cannot be established, hide. Multi-window and display ambiguity disable display until qualified.

The native spike must prove window flags, noninteractive-region touch behavior, close/tap/drag accessibility, no transparent full-screen interception, no tapjacking, and no overlay obstruction of permission prompts. Test API-level obscured-touch restrictions; do not work around them with unsafe alpha tricks. Exclude the app's own windows/events from detection to avoid feedback loops. Removing a counter never implies enforcement stopped; renderer/collector/rule health are independent.

## Delivery and acceptance

AS-043 now contains an experimental top-center `TYPE_ACCESSIBILITY_OVERLAY` renderer in the main Android app, with a 48dp minimum target, committed visit/day estimates and tap-to-open controls. Display consent is independent of source choice; privacy pause/disable invalidates queued observations/display and unknown/outside/locked states remove the window. No draw-over-apps grant is used. It is dormant while detector definitions remain unqualified. Safe insets/target-control overlap, multi-window, actual qualified-source updates, large-type/TalkBack, lifecycle and policy admission remain live-device gates; implementation is not acceptance of C-32. Leading/trailing placement, duration options, reminders and full configuration are not implemented in this narrow preview.

AS-033 demonstrates this interaction in a synthetic feed with explicit next-item and duplicate-callback controls. AS-034 implements native live display only after AS-013/AS-007 qualification and native store/consent prerequisites. C-32 remains experimental; there is no enabled production renderer or supported version list.

X-08 covers live updates, dedup, visit/day separation, gaps, pause/hide and large-type interaction. X-09 covers native midnight/timezone/reboot/generation/secure-context/touch/policy/battery behavior. Native tests remain not run. Optional content-topic interpretation is later, separately consented C-15; it should not clutter this minimal awareness display.
