# Design system

**Status:** Accepted visual direction; preview tokens and browser checks in AS-033; native components unimplemented. Updated 2026-10-27.
**Responsibility:** Defines hierarchy, semantic tokens, primitives, charts, themes and purposeful motion.
**Related specifications:** [Human factors](HUMAN-FACTORS.md), [UX](UX-SPEC.md), [Navigation](NAVIGATION.md), [Settings](SETTINGS-SPEC.md), [Preview](../design/prototype/README.md), [Conventions](CONVENTIONS.md).
**Unresolved feasibility:** F-03 in [Decisions](DECISIONS.md); Android font metrics, frame pacing and TalkBack require native verification. Browser checks do not certify native accessibility.

## Character and hierarchy

**Native preview update, ADR-033:** the user requests Google Digital Wellbeing-style clarity and usefulness. The React Native preview now opens directly to daily Instagram/Facebook counter cards, followed by observation/live-display controls and a clearly reported developer check. Use system sans headings, a compact app bar, 28dp rounded filled containers, 48dp controls, tabular counts and calmer blue/lavender accents. The native light/dark palette lives in `src/ui/theme.ts`; the original browser palette below stays historical reference, not a constraint to retain the serif shell. No fake circular usage data or decorative progress score when counting is unavailable. [Google's wellbeing help](https://support.google.com/android/answer/9346420?hl=en) describes daily/app-level information; [Material color roles](https://m3.material.io/styles/color/the-color-system) and [typography](https://m3.material.io/styles/typography/applying-type) inform this original implementation. No Google assets/fonts are copied or fetched at runtime. Native device, contrast and accessibility qualification remain required.

Warm, composed, precise. Paper-like backgrounds, deep green actions and lavender reflection surfaces give the proposed experience a recognizable character. Use space, type and alignment before adding a card. Colors are aesthetic choices, not claims about treating stress or changing brain chemistry. [Human factors](HUMAN-FACTORS.md) governs chosen engagement and a clear stopping point.

An ordinary screen has a compact identity/privacy row, narrative heading, short orientation, main evidence or action and bounded detail. Today starts with covered app time, offers focus support, then a single descriptive pattern and selected apps. No composite wellness score, moral progress ring or infinite insight feed.

## Semantic palette

The executable palette is [tokens.js](../design/prototype/tokens.js). Keep these values synchronized. Native tokens must preserve their semantic roles. System theme is the future application default; the review studio starts light for reproducible demonstrations and also supports live system/dark modes.

| Token | Light | Dark | Use |
|---|---|---|---|
| canvas | #F6F5EF | #17231F | Warm daylight / quiet evening |
| surface | #FFFEF9 | #202F29 | Cards and dialogs |
| surfaceAlt | #ECEFE6 | #2B3C32 | Grouped controls |
| text | #243C32 | #F2F1E8 | Primary reading |
| muted | #56645B | #BAC8BD | Supporting text |
| accent | #305B48 | #BED6AF | Primary action, selection, observed chart |
| onAccent | #FFFFFF | #203326 | Text/icons on primary fill |
| tint | #E0EADB | #314635 | Quiet emphasis and summary |
| border | #79867B | #8B9D90 | Essential boundaries |
| lavender | #EAE5F0 | #383342 | Reflection background; no good/bad meaning |
| onLavender | #554769 | #D9CAEE | Reflection text/link |
| caution | #80501D | #EDCA91 | Gaps and qualified warnings |
| cautionSurface | #F6EAD5 | #423625 | Warning background |
| danger | #A03431 | #FFBCB2 | Destruction/failure; never moral judgment |
| focus | #7051A2 | #CAB3EB | Keyboard/switch focus |

Normal text needs 4.5:1; meaningful boundaries/focus need 3:1 against adjacent backgrounds. Tests cover text/muted/accent on canvas/surface/surfaceAlt/tint, onAccent on accent, onLavender on lavender, caution on cautionSurface, danger on surface, and border/focus on main backgrounds. New combinations need checks. Never lower contrast for a “calm” effect. Dark mode maps roles deliberately instead of inverting pixels.

## Typography, spacing and layout

System sans serves controls, charts and evidence. A locally available serif can provide warmth for short welcome/reflection headings (preview: Georgia, serif fallback). Native Android must select and verify a platform/bundled serif without downloading fonts or copying unlicensed assets. Serif is optional when legibility requires another choice.

Native roles: narrative 34–40sp regular; section 17–22sp semibold; body 16sp; support 14sp; metadata 12sp. Numeric summary 46sp, tabular where available, with adjacent explicit units. Preview utility/fixture labels may be smaller; they are not a precedent for essential native labels. Never disable font scaling or truncate emergency actions.

Spacing: 4/8/12/16/24/32/48dp. Gutters 20–24dp, adaptable on narrow screens; cards 20–22dp padding, 20dp corners; buttons 14dp corners. Targets are at least 48dp even around small icons. The original code-native sprig/mark is decorative, excluded from spoken semantics, and never a vital-status indicator or a pet requiring care.

Main navigation remains available while content scrolls. Support insets, landscape, small screens, 200% text and RTL; stack or wrap controls instead of shrinking essential labels. No fixed card heights. Native long history uses bounded paging. The separate prototype review studio is not part of the app.

## Component contracts

| Component | Behavior |
|---|---|
| Primary button | Filled accent/onAccent, explicit action; one main step per section; native loading/status and duplicate-submission guard |
| Secondary/refusal | Neutral outline, real 48dp target; permission refusal has equal visual weight to proceeding |
| Text action | Underlined, named, focusable; never disguise destruction |
| Icon control | 48dp target, accessible name, visible boundary/focus; SVG does not duplicate spoken labels |
| Evidence badge | Text identifies observed/estimate/descriptive/forecast; source and coverage available; color supplementary |
| Capability state | Grant, enabled state, health, check age and repair; no vague green “protected” dot |
| Coverage | Specific unknown interval/reason, effect on rules, repair; never fake zero |
| Reflection | One bounded observation, evidence, optional next step and no-change path |
| Rule/review | Target, trigger, action, timezone, overlaps, exceptions, override scope/expiry and emergency together |
| Focus | Quiet remaining time, immediate end and emergency; no pulsing, guilt or failure on early end |
| Dialog/sheet | Title/close, heading focus, contained Tab navigation, Escape/back and focus return |
| Empty/error | Distinguish disabled, no events, observed zero, unsupported, access loss and failure |
| Archive job | Native stage, truthful progress/cancellation/retry; no false-success animation |

Native saves/deletion/activation/export require authoritative success, preserve drafts on recoverable errors and expose revision conflicts. The browser changes in-memory demo state only and labels it. Credentials remain in secure native entry, not a generic form.

## Charts and alternatives

Time bars show units, bin width and zone. Screen time is not attention. Totals include only covered observations; overlapping app totals and device union differ. An incomplete bin can be withheld with explanation. Unknown is a labelled gap, not a short/empty bar. A known zero has an explicit table value.

Observations, patterned estimates and dashed forecasts need text legends. The preview renders observations/gaps only; no validated forecast is demonstrated. Summaries and table/list alternatives carry all essential facts. Evidence access must not require dragging, precise tapping or memorized color. No interpolation across gaps or unexplained mixed-unit score.

## Motion and feedback

Targets: feedback 120ms, sheet reveal 180ms, navigation 220ms. Use short ease-out, very small position changes. Keep text and its background opaque throughout transitions so contrast does not dip during entry. Avoid overshoot, parallax, loops, roulette counters, confetti and repeated chart drawing. Familiar screens should stay stable. These durations are specifications, not measured frame guarantees.

Always honor system reduced motion. In-app reduce can strengthen it, never force more animation. Remove decorative transitions; emergency actions have no animation delay. Haptics/sound start off. Do not announce a timer each second; announce meaningful changes once.

Native work must avoid collectors/queries on the UI thread, bound list/chart data and use UI-thread animations appropriately. Verify frame pacing on declared low/mid-range devices with long history, accessibility and thermal load; do not promise universal 60/120fps. Unistyles, Reanimated, Gesture Handler, FlashList and SVG stay behind [compatibility](DEPENDENCIES.md).

## Native handoff

An optional [live counter](LIVE-OVERLAY-SPEC.md) defaults to a **small top-center text HUD** using the same tokens and a readable visit/day label, with evidence detail on tap. Maintain safe system insets and a 48dp target; leading/trailing presets are alternatives when center placement conflicts with feed controls. Do not pulse/roll digits, lower contrast through transparency, show a decorative mascot or display personal counts over system/private contexts. The original synthetic surface demonstrates the concept without copying any social-app interface.

AS-033 provides an [interactive reference](../design/prototype/README.md), tokens and browser tests. AS-018 must implement native semantics and source/configuration contracts; do not embed a WebView to bypass AS-002/AS-004.

X-02 and X-04–X-07 in [Testing](TESTING.md) cover accessibility, truthful evidence and humane flow. Automated scans, contrast arithmetic, visual inspection, participant review and device accessibility are distinct evidence levels.
