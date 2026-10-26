# Design system

**Status:** Proposed tokens and component behavior; no implemented component library. Updated 2026-10-27.
**Responsibility:** Defines visual semantics, accessible primitives, charts, theming and motion.
**Related specifications:** [UX](UX-SPEC.md), [Navigation](NAVIGATION.md), [Settings](SETTINGS-SPEC.md), [Conventions](CONVENTIONS.md).
**Unresolved feasibility:** F-03 in [Decisions](DECISIONS.md); contrast and animation behavior require rendered-device verification.

## Tone and semantic tokens

Calm, precise and nonjudgmental. Use readable system typography and simple native-feeling controls. Avoid reward streak pressure, alarming brain imagery, shame copy or fictitious “dopamine” scores. Marketing and onboarding must use the privacy wording governed by [Privacy](PRIVACY.md).

| Token | Light proposal | Dark proposal | Meaning |
|---|---|---|---|
| canvas | #F7F9FC | #10151E | Screen background |
| surface | #FFFFFF | #192231 | Cards and sheets |
| textPrimary | #172133 | #F2F5FA | Primary labels |
| textSecondary | #465469 | #B9C5D6 | Supporting copy |
| accent | #2455B8 | #A9C4FF | Primary action/selected state |
| positive | #17613A | #8AD8AA | Confirmed healthy status, not moral judgment |
| caution | #795000 | #F2CC80 | Limited confidence or missing coverage |
| danger | #A52828 | #FFB4AB | Destructive action / failed operation |
| outline | #69778A | #8B9CB3 | Interactive boundaries and focus |

Tokens are proposals, not a certified contrast palette. Validate text/background and icon/boundary combinations before use: target WCAG AA text contrast (4.5:1 normal, 3:1 large) and 3:1 meaningful non-text boundaries. Never put small white text on an accent without checking. Category colors are editable but labels/patterns preserve meaning independently.

## Type, spacing and layout

System font, scalable text, tabular numerals where available. Suggested roles: title 28sp, section 22sp, body 16sp, label 14sp, caption 12sp; never disable font scaling. Use 4/8/12/16/24/32 dp spacing, screen gutters 16dp, cards 16dp padding and 12dp radius. Minimum interactive target 48×48dp. Layout reflows at 200% text rather than truncating essential actions. Charts and cards support narrow screens, landscape and RTL without fixed content heights.

## Component contracts

Buttons expose label, loading/disabled reason, accessible role and visible focus. Destructive actions use an explicit verb and scoped preview. Toggle rows explain local consent versus OS grant; changing one never implies the other. CapabilityStatusBadge includes text, icon, check time and a details action. EvidenceLabel distinguishes observed/estimate/classification/prediction. CoverageBanner links to gaps and repair steps. EmptyState distinguishes disabled, no covered observations, unsupported and failure.

Rule cards show enabled state, effective targets, next boundary and enforcement health. Conflict previews show combined reasons and chosen precedence. Intervention surfaces keep emergency access reachable and do not imitate Android system permission UI. Archive job cards show native stage, progress if known, cancellation availability, last-confirmed status and a retry path. Secure credential fields are native and excluded from ordinary state persistence.

## Charts

SVG charts use explicit axes, units and selected timezone; long history uses bounded bins, not one view per event. Solid observations, patterned estimates and dashed forecasts include text legends. Unknown ranges have gap hatching and explanation; no interpolation through disabled periods. Use accessible summary text and a table/list alternative. A touch/keyboard selection has a stable focus target and detail sheet. Never encode confidence only by opacity or compare multiple units on an unexplained axis.

## Motion and themes

System theme is default; explicit light/dark choices apply through semantic tokens. Proposed durations: 120ms small feedback, 180ms panel transition, up to 240ms large navigation. Respect system reduced-motion settings and an in-app stronger override. Remove looping/decorative motion, spring overshoot and chart draw animations under reduced motion. Motion must not delay emergency bypass or hide state changes. Avoid haptic/sound defaults for interventions; these are opt-in and respect platform accessibility/audio settings.

Use Unistyles, Reanimated, Gesture Handler, FlashList and SVG only after [dependency checks](DEPENDENCIES.md). Prefer accessible standard controls over custom gesture-only components. Verification X-02 covers TalkBack, switch/keyboard access where available, type scaling, dark/light contrast, RTL, reduced motion and chart alternatives. Record actual device evidence before marking design implemented.
