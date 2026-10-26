# Behavior intelligence

**Status:** Descriptive insights planned; predictions and content topics experimental. Updated 2026-10-26.
**Responsibility:** Defines categorization, local indicators, confidence, forecasts and explanations.
**Related specifications:** [Models](DATA-MODELS.md), [Tracking](TRACKING-SPEC.md), [Social](SOCIAL-MEDIA-SPEC.md), [UX](UX-SPEC.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-04 and F-09 in [Decisions](DECISIONS.md); calibration and useful local prediction need evaluation.

## Meaning and categories

Every result declares kind: observed, estimate, classification, or prediction. A reconstructed usage session is an estimate supported by observed events. An app category is a classification, even when the user supplied it. A forecast never overwrites either. Keep source IDs, algorithm revision, parameters, data window, coverage, generated time and explanation reasons.

Starter categories are communication, work/study, entertainment, social, utilities and uncategorized. They are editable, neutral defaults. A package has one primary category for mutually exclusive rollups; optional tags are separate, non-additive facets. Manual mapping wins over bundled heuristics. Changing a category prompts whether it affects future views only or recategorizes past views; event history remains unchanged. A category is not a judgment about intent: work can occur in a social app.

## Descriptive indicators

| Indicator | Definition / provisional default | Conditions and explanation |
|---|---|---|
| Repeated reopening | At least three confirmed entries to the same package in a ten-minute window | Use app-open grouping from tracking; show actual times and threshold; no claim of compulsion |
| Fragmentation | Package switches per covered interactive hour, plus median session length | Exclude unknown gaps; report covered denominator; low denominator suppresses rate |
| Late-night use | Covered duration overlapping the user's chosen bedtime window | Default proposal 23:00–07:00, inactive until selected; use named zone and split DST correctly |
| Long episode | Covered continuous session exceeding user's threshold, initially 20 minutes | Display threshold and uncertainty at session boundaries |
| Trend | Difference in like-for-like duration/rate over matched periods | Require comparable weekday mix and coverage; no percentage if baseline is zero |
| Notification association | App open within a selected window after an observed post | Default window 60 seconds; temporal association only; exclude listener gaps |

MVP insights use deterministic local queries. Suggested minimum for a weekly comparison: four comparable days in each period with ≥80% known coverage within the enabled measurement window. Coverage means source availability, not fraction of a life or proof that a missing phone was unused. Show numerator/denominator and exclusions. Below threshold return insufficient_data with reasons and no trend arrow. Thresholds are versioned defaults subject to validation, not scientific diagnostic cutoffs.

## Predictions and recommendations

Opt-in local forecasting may estimate daily budget exceedance or a likely high-use time window. Begin with a transparent baseline using comparable prior days, not an opaque model. Proposed admission floor: 14 comparable days in 28 days and ≥80% source coverage on each included day. Train/evaluate chronologically to avoid future leakage, compare to a simple baseline, report calibration and uncertainty, and reset when the user deletes relevant history. Do not display invented numerical confidence from a heuristic score; use explicit evidence classes until calibrated probabilities exist.

A forecast explanation includes “based on 16 covered weekdays,” recent use, typical range, excluded gaps, model revision, and how to disable/reset. An insufficient-data result gives missing prerequisites and can offer descriptive totals instead. Historical outliers, new schedules, timezone changes and drift lower confidence or withhold output. No remote training, behavioral upload, automatic model download, or externally hosted inference.

Predictions offer recommendations only. They must not create, intensify, or execute accessibility actions. A suggested rule becomes active only after the user reviews a static trigger/action and explicitly saves it. The native protection engine consumes user configuration, not model policy.

## Optional topic patterns

Topics such as sports, gaming, learning and entertainment can be user-assigned or estimated from consented supported titles/metadata. Unknown stays unknown. Show the classified fraction, individual evidence if retained, and correction controls. Do not claim that prolonged viewing proves addiction, that a title reliably describes video content, or that the application measures dopamine. Avoid sensitive-trait inference. Topic classification is separate from exposure counting and cannot enable content capture implicitly.

Acceptance: T-12–T-15 in [Testing](TESTING.md). Validate boundaries, zero baselines, low coverage, corrected categories, deleted history, time-zone changes and deterministic replay. Keep evaluation datasets synthetic unless separately consented research is explicitly scoped outside this repository.
