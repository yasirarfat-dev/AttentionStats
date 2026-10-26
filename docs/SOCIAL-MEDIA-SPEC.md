# Social surfaces and optional context

**Status:** Expanded experimental detector design; no app/version/locale qualified. Updated 2026-10-27.
**Responsibility:** Defines transparent app/surface identification, short-video counts, optional metadata and detector qualification.
**Related specifications:** [Tracking](TRACKING-SPEC.md), [Intelligence](BEHAVIOR-INTELLIGENCE.md), [Privacy](PRIVACY.md), [Protection](PROTECTION-SPEC.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-01 and F-08 in [Decisions](DECISIONS.md). No private implementation or universal detector is assumed.

## What can be identified

An Android accessibility event exposes a package name and may expose a source node/window. Nodes can expose view resource IDs, class/role, content descriptions, visibility and collection position. Availability depends on the target app and its UI. Public API references: [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent), [AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo), and [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo).

The package selects an app-specific adapter. It does not identify the surface inside the app. Distinguish a short-video viewer from chat, comments, search, profile, advertisements, stories, live video, and a normal scrolling feed using multiple independent structural signals. A vertical scroll alone is insufficient. Surface names describe an observation context, not what a person watched or why.

Configure service package filters to minimize callbacks, then recheck the package and current consent before accessing a node. Inspect only bounded, allowlisted metadata. Never traverse unrelated apps or collect whole trees. Sensitive, editable and password nodes are excluded before reading text; authentication, payment, private browsing and messaging surfaces are excluded. If a sensitive context cannot be distinguished reliably, disable metadata for that adapter. Product labels used to identify a surface may be matched transiently; content-derived fingerprints require separate content consent.

## Candidate adapter registry

| Adapter key | Intended surface | Must distinguish | Current status |
|---|---|---|---|
| facebook.short_video | Facebook Reels | normal feed, stories, chat, ads, live, comments | Experimental; no selectors or package mapping validated |
| youtube.short_video | YouTube Shorts | long-form player, home previews, search, comments, live | Experimental; no selectors or package mapping validated |
| instagram.short_video | Instagram Reels | feed, stories, direct messages, profiles, comments | Experimental; no selectors or package mapping validated |
| tiktok.short_video | TikTok short-video feed | inbox, profile, search, live, photo carousel, ads | Experimental; regional/build variants unverified |

These target services are observation targets, not reference implementations. Do not invent their current view IDs, assume package variants are equivalent, or hardcode a display name as a package identifier. Resolve package/version from an explicit supported registry during the spike. Lite, regional, web, work-profile and cloned variants need independent qualification. Until then app-level summaries can work while surface status remains unsupported/experimental.

Each adapter record includes package variants, version range, Android range, locale range, structural surface predicates, transient item identity mechanism, unsupported contexts, bounded work budget, calibration thresholds, detector revision and last test date. Ship reviewed detector definitions with application releases; no remote executable selectors or silent support expansion.

## Counting state machine

States: OUTSIDE → CANDIDATE → ACTIVE(item) → TRANSITION → ACTIVE(next), with UNKNOWN and PAUSED reachable at any point. OUTSIDE requires no retained item context. CANDIDATE requires an allowlisted package, confirmed visible surface, interactive device, and stable evidence across observations. The initial candidate dwell threshold is 500 ms; this is a calibration default, not a viewing-completion definition.

1. On confirmed entry with a stable item signal, emit one exposure. Use collection index or a stable non-content accessibility identity if genuinely available. Distinguish exposure count from unique-content count.
2. Repeated content-change, focus and scroll events on the same item do not increment. Debouncing alone is insufficient because redraws can occur seconds apart. Pause/resume and comment overlays must preserve identity when continuity is known.
3. A confirmed change to a settled item emits one new exposure. A rapid fling with skipped items counts only confirmed settled exposures; never guess unseen items from gesture distance. Returning to an earlier item is another exposure if a real transition occurred, not a globally unique video.
4. When no reliable item identity exists, count only independently validated settled page transitions and label the output as an estimate. If neither identity nor transition evidence is reliable, report surface time with count unavailable.
5. App switch, screen off, consent withdrawal or unsupported layout closes or suspends the episode. On uncertain return, avoid a duplicate initial exposure and mark continuity uncertain. After process death do not persist content merely to recover an identity; re-enter with an uncertainty boundary.

Persist exposure ordinal/event ID, app/surface, UTC and monotonic evidence, detector revision, confidence class, coverage and reason codes. Default counts contain no title, creator, URL or global content identifier. Transient identities expire with the episode; hashing content is still processing content and is not a consent bypass. Persist dedup state transactionally with exposure events, and scope identity to package/window/session so events from different apps cannot merge.

The planned short-video count excludes positively identified advertisements, stories, live streams and photo carousels. Interleaved ads must be part of qualification, not guessed from a swipe. Ambiguous eligibility withholds the transition and marks coverage incomplete; it must not produce a fabricated exact count or a false zero. Any future all-feed/ad metric requires an explicit separate definition. On return from an excluded context, prove continuity/dedup before adding a new item. See [live count semantics](LIVE-OVERLAY-SPEC.md).

## Time and optional content

The expanded diary adds separately opted-in feed-post/story/ad exposures, primary activity contexts and interaction evidence. [Analytics](ANALYTICS-SPEC.md) owns disjoint time accounting and intent/outcome semantics; [Metric catalog](METRIC-CATALOG.md) owns all candidate platforms/data points. A safe coarse messaging-context label may be evaluated separately without reading message nodes or retaining recipients/text; otherwise that time remains unclassified. This does not authorize chat/message content capture. Reaction/submit/share control inspection must exclude editable content and public engagement tallies. Current short-video adapters gain no implied support for those independent metrics.

Surface time counts known interactive occupancy, even if playback is paused; it is not verified watch time, completion, eye contact or enjoyment. App totals come from app sessions, and surface intervals are a subset. Missing surface evidence cannot subtract app use or turn unclassified time into short-video time.

Titles, creator labels, domains and URLs are off by default and individually selectable only for adapters with safe allowlisted fields. Do not collect message content, searches as they are typed, passwords, OTPs, comments, clipboard data or URL credentials/query secrets. Default URL mode is domain only. Full supported URLs, if explicitly enabled, remove userinfo, query and fragment and require safe navigation confirmation; do not promise perfect private-mode detection. Unsupported/private/sensitive contexts store no content.

Optional topic analysis is a separate continuous-evolution capability, using supported consented metadata and a bundled local classifier or editable rules. It can say “estimated gaming-related exposures in covered sessions,” show input provenance and unknown fraction, and accept corrections. It cannot know the actual video without reliable metadata, infer internal addiction, profile sensitive traits, or silently enable capture. No screenshot/OCR/audio inference or external content APIs are part of this scope.

## Verification and transparency

The proposed [live awareness overlay](LIVE-OVERLAY-SPEC.md) reads the same committed exposures and SurfaceEpisodes as history. It must not maintain a second callback counter. Current visit is already included in the selected app/surface's local-day total; day boundaries, gaps, stale data and unknown surfaces are explicit. Rendering can coalesce updates while preserving the latest committed count. No overlay grant changes detector accuracy or content consent. C-32/AS-034 remain experimental.

Qualification must measure false positives and false negatives separately using consented manual observation and synthetic public fixtures. Include slow/fast swipes, return swipes, pause/resume, re-entry, comments, ads, mixed media, orientation, multiple locales, split-screen, dark mode, app updates and accessibility hierarchy absence. Set a provisional release target of ≥95% precision and ≥90% recall on the declared fixture set; publish sample size and confidence intervals, never generalize to untested versions. A failing adapter remains disabled.

The user-facing “How tracking works” view shows enabled adapter, supported range, metadata fields inspected, data retained, last verified revision, count meaning, coverage, and a disable/delete action. Developer diagnostics contain reason codes, not content. Tests T-06, T-07, T-10, T-11 and T-15 govern admission. Unsupported surfaces must be visibly unknown, never zero and never silently counted by a generic scroll heuristic.
