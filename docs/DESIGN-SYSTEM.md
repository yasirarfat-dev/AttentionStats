# Design system

**Status:** Accepted visual direction; experimental native shell, shared panels/icons and counter exist; full accessibility/performance qualification remains open. Updated 2026-10-28.
**Responsibility:** Defines hierarchy, semantic tokens, primitives, charts, themes and purposeful motion.
**Related specifications:** [Human factors](HUMAN-FACTORS.md), [UX](UX-SPEC.md), [Navigation](NAVIGATION.md), [Settings](SETTINGS-SPEC.md), [Preview](../design/prototype/README.md), [Conventions](CONVENTIONS.md).
**Unresolved feasibility:** F-03 in [Decisions](DECISIONS.md); Android font metrics, frame pacing and TalkBack require native verification. Browser checks do not certify native accessibility.

## Current native appearance (ADR-071)

The beta continuation replaces the forest-green palette with soft-white/cool-neutral light surfaces and deep navy dark surfaces, plus blue, teal or violet accents chosen by the person. Light, Dark and System selections and optional motion persist as device-local native preferences and apply to React Native navigation and native schedule/archive screens. Reduced motion takes priority; the rejected hand guide is removed under ADR-072. This is an aesthetic/readability choice, not proof of trust or relaxation. Native light-mode/accent and restart persistence have scoped phone evidence in the dated log; broad accessibility/performance qualification remains open. Older study palettes below are historical references.

## Character and hierarchy

**Native preview update, ADR-033:** the user requests Google Digital Wellbeing-style clarity and usefulness. The React Native preview now opens to Today within five persistent tabs. Actual daily Instagram/Facebook viewer-page estimates link to observation/live-display controls and a clearly reported developer check. Unimplemented metrics use unavailable states. Shared native panels/destinations and original SVG tab symbols provide consistent routes without adding dependencies. Use system sans headings, a compact app bar, 28dp rounded filled containers, 48dp controls, tabular counts and calmer blue/lavender accents. The native light/dark palette lives in `src/ui/theme.ts`; the original browser palette below stays historical reference, not a constraint to retain the serif shell. No fake circular usage data or decorative progress score when counting is unavailable. [Google's wellbeing help](https://support.google.com/android/answer/9346420?hl=en) describes daily/app-level information; [Material color roles](https://m3.material.io/styles/color/the-color-system) and [typography](https://m3.material.io/styles/typography/applying-type) inform this original implementation. No Google assets/fonts are copied or fetched at runtime. Native device, contrast and accessibility qualification remain required.

Warm, composed, precise. Paper-like backgrounds, deep green actions and lavender reflection surfaces give the proposed experience a recognizable character. Use space, type and alignment before adding a card. Colors are aesthetic choices, not claims about treating stress or changing brain chemistry. [Human factors](HUMAN-FACTORS.md) governs chosen engagement and a clear stopping point.

An ordinary screen has a compact identity/privacy row, narrative heading, short orientation, main evidence or action and bounded detail. Today starts with covered app time, offers focus support, then a single descriptive pattern and selected apps. No composite wellness score, moral progress ring or infinite insight feed.

ADR-038 adds a compact icon/count accessibility overlay with translucent charcoal background and opaque digits/icons. It keeps committed counts in the same window during known viewer transitions and removes on unsafe/unknown contexts. ADR-040 clarifies full vertical pagination of the old/new count within its clipped viewport, reversing for a confirmed backward transition and respecting disabled animations. [Live overlay](LIVE-OVERLAY-SPEC.md) owns exact motion/opacity/contrast/placement semantics. It does not sample underlying target pixels. Presets and advanced customization are proposed settings, not implemented profiles.

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


2026-11-04 contrast review: light faint text was 4.37:1 on the alternate surface, below the normal-text target. Darkened that token to `#5C6C86`; other light/dark body/muted/faint pairs on canvas/surface/alternate/raised surfaces are calculated independently of screenshots. Full native dialog, accent/state, large-type and TalkBack acceptance remain separate.


ADR-076 supersedes the native dark navy backgrounds with near-black neutral roles: canvas #08090B, surface #141518, alternate #1C1D21, raised #25262B, text #F1F2F4, muted #B9BCC5 and border/track #3B3D45. Blue/teal/violet remain explicit accents; dark hero backgrounds use restrained charcoal tints. Native editor/dialog backgrounds and intervention covers use the same blackish direction. Light appearance stays supported. Contrast calculations and physical-phone appearance review are recorded separately; a palette change alone does not accept broad accessibility.


ADR-078 makes [paletteTokens.json](../src/ui/paletteTokens.json) the current shared semantic source. [generate_palette.py](../tools/generate_palette.py) generates Kotlin NativePalette roles and native style resources; `--check` in lint rejects drift. Graphite adds readable neutral accent text (#D5D7DD dark / #30333B light), distinct charcoal action fill (#3A3D45 dark / #30333B light) and neutral selected surfaces. The shared React Native Kit uses accentFill for filled controls; accent remains readable for text/icons. New installs default to Graphite, and existing explicit choices remain. Native backup/widget setup and break overlays use these generated roles. The former stock schedule Activity is removed; schedule selection, time fields, groups and reviews reuse Screen/Kit. Android Settings/document pickers remain OS surfaces.


The November 12 Insights continuation uses the shared hero palette for period totals and ordinary surfaces for the selected-day explorer. Date buttons have a minimum 48dp target, visible selected border/fill and spoken selection; named Previous/Next controls provide an alternate path. Shared statistic labels wrap instead of clipping; segmented controls use 48dp minimum height and a zero-share meter has zero fill. Hourly charts offer an exact-value list. Broad text-scale/RTL/TalkBack acceptance remains separate from the scoped phone review.

The app explorer uses the same shared cards, app icons, 48dp segmented controls and named back/paging actions. A bordered 52dp search field filters a finite ten-row app page. Detail pairs period statistics with value-availability text and daily exact values, with optional relative time bars. Missing durations have no bar; zero has zero fill. Inline measurement details are optional; daily rows provide a labelled non-dragging route to the day overview. No color-only or invented wellbeing comparison is added.

All-time Insights keeps lifetime video totals in the shared hero and progressively reveals per-source totals. A separate Browse retained history card labels the page's exact date span and its own statistics before By day/By app detail. Named older/newer/latest controls use the existing 48dp Kit buttons, wrap with the shared layout, and disable at boundaries. Calendar span is text rather than a coverage meter; no missing-time or comparison score is invented.
