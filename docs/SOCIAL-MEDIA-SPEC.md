# Social surfaces and optional context

**Status:** Expanded experimental detector design; no app/version/locale qualified. Updated 2026-10-28.
**Responsibility:** Defines transparent app/surface identification, short-video counts, optional metadata and detector qualification.
**Related specifications:** [Tracking](TRACKING-SPEC.md), [Intelligence](BEHAVIOR-INTELLIGENCE.md), [Privacy](PRIVACY.md), [Protection](PROTECTION-SPEC.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-01 and F-08 in [Decisions](DECISIONS.md). No private implementation or universal detector is assumed.

## What can be identified

An Android accessibility event exposes a package name and may expose a source node/window. Nodes can expose view resource IDs, class/role, content descriptions, visibility and collection position. Availability depends on the target app and its UI. Public API references: [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent), [AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo), and [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo).

The package selects an app-specific adapter. It does not identify the surface inside the app. Distinguish a short-video viewer from chat, comments, search, profile, advertisements, stories, live video, and a normal scrolling feed using multiple independent structural signals. A vertical scroll alone is insufficient. Surface names describe an observation context, not what a person watched or why.

Configure service package filters to minimize callbacks, then recheck the package and current consent before accessing a node. Inspect only bounded, allowlisted metadata. Never traverse unrelated apps or collect whole trees. Sensitive, editable and password nodes are excluded before reading text; authentication, payment, private browsing and messaging surfaces are excluded. If a sensitive context cannot be distinguished reliably, disable metadata for that adapter. Product labels used to identify a surface may be matched transiently; content-derived fingerprints require separate content consent.

## Candidate adapter registry

ADR-034 adds separate preview categories: `reel` (recognized single-video layout), `photo` (recognized photo/carousel page), `post` (normal-feed post, unavailable initially), `ad` (positively recognized ad layout) and `unclassified`. Recognized ads never add to video/photo categories. One carousel page is one exposure, not its hidden image count. Layout labels do not establish universal ad exclusion; show that limitation and unclassified counts. Total viewer pages includes all categories and must say so. This is separate from the stricter M-016 short-video metric; do not present these provisional layout counts as fully qualified M-016 or normal-feed history.

The experimental Instagram definition is pinned to the consented installed version and requires its viewer/media/action-container IDs plus a centered pager row. Feed/profile/comment trials and 110 controlled swipe attempts establish candidate structure; no content was read. Recognized image ads and photo-carousel layouts have distinct markers. Video-ad fidelity and broader lifecycle/accuracy remain open. Facebook exposes useful settled indices in the manually confirmed viewer, but generic classes also occur elsewhere; no Facebook definition is admitted yet. Disclosure revision 2 is required before new recording.

AS-043 maps the canonical installed candidates `com.instagram.android` and `com.facebook.katana` to independent preview sources. Mapping package identity does not qualify a Reels surface. The bounded passive adapter and 500 ms pure reducer never infer Reels from a generic scroll event; synthetic reducer checks are not per-app detector evidence. Optional ten-minute local structural qualification reads no node text/descriptions or target screenshots and requires separate consent. The current category implementation and phone acceptance are recorded in State; neither application has full detector/metric acceptance.

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

### Repeat visits and distinct items

ADR-039 keeps total **exposures** as the default: leaving A for B and settling on A again contributes three exposures. Remaining on A while it loops, redraws or pauses contributes one. This measures observed encounters, not playback starts, completions or attention. The preview already follows this transition rule; existing totals are not rewritten as unique-item history.

Distinct items and repeat exposures are separate future breakdowns, admitted only for an adapter's qualified identity scope. With complete stable identity, A → B → A means three exposures, two distinct items and one repeat exposure. A repeat is an exposure after the first observed encounter with the same identity **within the selected scope**, not evidence the person has never seen it before. Items with uncertain identity stay in an explicit unknown-identity group; never infer exact repeats by subtracting an incomplete distinct count from the total. Distinct counts cannot be added across days/weeks or sessions; requery the identity union for a supported range. A selected-range repeat and an earlier-lifetime encounter are different questions.

Pager positions identify locations in a current viewer, not durable Reel identities. Reload/reordering/recycled nodes, app switch, process death and feed changes invalidate unproven continuity. Session-only revisits may be offered as such after independent qualification; cross-session/day identity requires a separate reviewed, disclosed metadata/storage design. No title/creator/image fingerprinting, hidden permalink capture or persistent content ID is added to obtain it. The current preview shows distinct/repeat breakdowns as unavailable rather than zero. Android's [node unique-ID API](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getUniqueId()) is nullable and identifies a node; its existence alone does not establish target-content identity or stability.

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
