# Metric catalog and platform research

**Status:** Authoritative expanded inventory; all collection/display rows planned or experimental, no device-qualified support. Updated 2026-10-27.
**Responsibility:** Enumerates measurable data points, source/permission/control/accuracy/phase/scenario and per-platform admission.
**Related specifications:** [Analytics](ANALYTICS-SPEC.md), [Capabilities](CAPABILITY-MATRIX.md), [Social](SOCIAL-MEDIA-SPEC.md), [Privacy](PRIVACY.md), [Tests](TESTING.md).
**Unresolved feasibility:** F-01/F-02/F-08/F-09/F-10; source existence does not establish that a particular target exposes it. This is a research inventory, not advertised live support.

## How to read this catalog

Stable M identifiers name individual metrics, while C identifiers in [Capabilities](CAPABILITY-MATRIX.md) name product capability families. Every row below has **device verification: none**. AS-035 verifies only calculation subsets of T-17/T-18/T-19/T-20/T-23, not collectors or complete acceptance scenarios. “planned” means specified; “experimental” means admission evidence required. No current Android or iOS collector is implemented.

Source/access codes: U = UsageEvents and native interactive/keyguard state with user-granted Usage Access; A = narrowly allowlisted accessibility structural metadata with separate disclosure, consent and user-enabled service; N = metadata-only Notification Listener grant; W = NetworkStatsManager with user-granted Usage Access plus separate network-statistics consent; L = local derived calculation, requiring the underlying sources; H = separately consented supported title/domain/URL fields through A; I = a future user-selected supported archive via system picker URI grant. No input recording or packet capture source exists. Source grants never enable another metric family.

All collection controls start off. Control codes: B = source/app/category include/exclude, pause, retention and selective delete; S = B plus per-adapter and activity/metric opt-in; J = S plus separate interaction-type selection, no content text; C = B plus separate field/topic opt-ins and shorter optional detail retention; D = display/configuration controls and inherited source exclusions; R = explicit retention/summary preview and chosen loss; I = explicit file/scope/date selection, preview and cancellation. References inherit these minimums; none means “enable everything.”

Phase: M = MVP; E = expansion; C = continuous evolution. For A/H rows, F-01 and F-08 always apply; for U/N rows F-02/F-09 apply; W requires F-10; L inherits all its sources' gates. Per-row limits below add to these shared boundaries. API/source evidence is listed after the inventory.

## Device and app history

| Metric | Meaning / source and access | Controls | Accuracy / restriction | Phase / status | Scenario |
|---|---|---|---|---|---|
| M-001 App interactive duration | Union of covered app intervals / U | B | Reconstructed visibility, not gaze; overlap/gaps explicit | M / planned | T-01, T-17 |
| M-002 App foreground entries | Confirmed package-entry episodes / U | B | Activity resume is not always a new open | M / planned | T-02 |
| M-003 Session count | Normalized foreground sessions / U | B | Gap-grouping revision changes definition | M / planned | T-01 |
| M-004 Session length distribution | Count/sum/min/max and selected histogram / L(U) | D | Censored sessions separate; no exact median after lossy rollup | M / planned | T-17, D-04 |
| M-005 Longest covered session | Max known contiguous session / L(U) | D | Gap never bridged | M / planned | L-01, T-17 |
| M-006 Hour/day usage distribution | Zone-aware covered bins / L(U) | D | Compare actual civil bounds and coverage | M / planned | L-02 |
| M-007 Reopening episodes | Configured repeat-entry threshold / L(U) | D | Descriptive repetition, not compulsion diagnosis | M / planned | T-13 |
| M-008 App switches / fragmentation | Qualified cross-app transitions / L(U) | D | Unknown intervening history breaks sequence | M / planned | T-13 |
| M-009 First / last observed use | Covered event extrema in range / L(U) | D | Not proof of waking/sleeping or full-day extrema | M / planned | T-17 |
| M-010 Device interactive duration | Display/keyguard-aware union / U | B | Distinct from summed app visibility | M / planned | T-03, L-08 |
| M-011 Screen-on episodes | Observed interactive transitions / U | B | Source-dependent, not unlocks | M / planned | T-03, T-04 |
| M-012 Unlock episodes | Keyguard-dismissal proxy / U | B | No method, credential or person identity | M / planned | T-04 |
| M-013 Late-window usage | Duration inside chosen clock window / L(U) | D | User-defined, no sleep/health claim | M / planned | T-13, L-02 |
| M-014 Category duration / share | Versioned user categories and app union / L(U) | D | Classification; compatible denominator required | M / planned | T-05, T-12 |

## Primary activities and visible media

| Metric | Meaning / source and access | Controls | Accuracy / restriction | Phase / status | Scenario |
|---|---|---|---|---|---|
| M-015 Short-video surface duration | Qualified Reels/Shorts/feed occupancy / A + U attribution | S | Not verified playback/watch completion; subset of app time | M / experimental | T-06, T-17 |
| M-016 Short-video exposures | Settled qualifying video occurrences / A | S | Estimate; return transitions included, same-page loops/redraws deduped, ads excluded. Distinct/repeat breakdowns require independently qualified scoped identity (ADR-039); unavailable in current preview | M / experimental | T-07, T-18 |
| M-017 Short-video visits | Qualified surface episodes / A | S | Unknown continuity splits/censors visits | M / experimental | T-06, X-09 |
| M-018 Current-visit exposures / time | Committed current episode projection / L(A) | D; optional HUD | Visit already included in today | M / experimental | X-08, X-09 |
| M-019 Exposures per covered surface minute | M-016 divided by M-015 / L(A,U) | D | Matching scope; undefined at zero/unknown duration | M / experimental | T-18, T-23 |
| M-020 Exposure dwell distribution | Time between qualified boundaries / A | S | Censored/unknown pauses; not actual view completion | E / experimental | T-18 |
| M-021 Regular-feed duration | Qualified primary feed intervals / A | S | Mixed media must not be relabelled all Reels | E / experimental | T-17 |
| M-022 Feed-post exposures | Settled visible post occurrences / A | S | Viewport/dwell criteria versioned; not posts read/unique posts | E / experimental | T-18 |
| M-023 Stories duration | Qualified stories surface occupancy / A | S | Distinct from short video and ads | E / experimental | T-17 |
| M-024 Story-frame exposures | Qualified settled story frames / A | S | Auto-advance requires independent evidence; not swipe count | E / experimental | T-18 |
| M-025 Long-form video context time | Qualified long-form surface / A | S | No playback duration unless separately exposed/proven | E / experimental | T-17 |
| M-026 Live-stream context time | Qualified live surface / A | S | Not inferred from animated content | E / experimental | T-17 |
| M-027 Search/navigation context time | Structural navigation surface / A | S | No query or typed text retained | E / experimental | T-21, T-11 |
| M-028 Profile/navigation time | Qualified non-sensitive profile context / A | S | No account handles/identities retained | E / experimental | T-21 |
| M-029 Creation-context time | Qualified editor/camera context / A | S | No media, keystrokes or draft content; does not count posted uploads | E / experimental | T-21, T-11 |
| M-030 Messaging-context time | Coarse safe structural context only / A | S; separate enablement | No message nodes/text/recipient/count; unavailable if unsafe | E / experimental | T-21, T-11 |
| M-031 Comments-panel time / opens | Secondary facet within primary activity / A | S | Overlaps labelled “within”; never added to app total | E / experimental | T-17, T-19 |
| M-032 Identified ad exposures / time | Qualified ad context separately / A | S; separate metric | Advertising classification uncertain; not all skipped items are ads | E / experimental | T-18 |
| M-033 Unclassified app duration | App union less unambiguous primary coverage / L(U,A) | D | Visible missing/contradictory classification, not another inferred task | M / planned | T-17 |

## Interaction evidence

| Metric | Meaning / source and access | Controls | Accuracy / restriction | Phase / status | Scenario |
|---|---|---|---|---|---|
| M-034 Observed like-state additions | Qualified unselected→selected control / A | J | UI outcome only; no server success/unique-post claim | E / experimental | T-19 |
| M-035 Observed unlike-state changes | Qualified selected→unselected control / A | J | Separate from additions; public like tally is not this metric | E / experimental | T-19 |
| M-036 Reaction attempts | Qualified activation without outcome / A | J | Separate attempted interactions; no promotion to M-034 | E / experimental | T-19 |
| M-037 Comment composer opens | Qualified panel/composer navigation / A | J | Does not read draft or imply submission | E / experimental | T-19, T-11 |
| M-038 Observed comment submissions | Submit plus safe non-content acknowledgement / A | J | Unsupported without acknowledgement; server acceptance unknown | E / experimental | T-19 |
| M-039 Comment-submit attempts | Safe qualified submit action / A | J | No comment body or keystroke inspection | E / experimental | T-19, T-11 |
| M-040 Share-sheet opens | Qualified share UI navigation / A | J | Not delivery, recipient, message, repost or external engagement | E / experimental | T-19 |
| M-041 Observed share handoffs | Qualified safe UI handoff / A | J | Separate from open; no success/delivery claim | E / experimental | T-19 |
| M-042 Save / unsave changes | Qualified safe toggle outcomes, separate counters / A | J | No saved-content identity needed; server outcome unknown | E / experimental | T-19 |
| M-043 Follow / unfollow changes | Qualified safe toggle outcomes, separate counters / A | J | No followed account identity; unsupported if unsafe | E / experimental | T-19 |
| M-044 Net observed interaction changes | Additions less removals for matching scope / L(A) | D | Can be negative; not total account inventory or unique items | E / experimental | T-19, T-23 |

## Networks, notifications and optional content

| Metric | Meaning / source and access | Controls | Accuracy / restriction | Phase / status | Scenario |
|---|---|---|---|---|---|
| M-045 Wi-Fi received bytes | Canonical OS UID buckets / W | B; separate network consent | Coarse/delayed, shared UID/reinstall limits; not Reels bytes | E / experimental | T-20 |
| M-046 Wi-Fi sent bytes | Canonical OS UID buckets / W | B; separate network consent | Includes background/prefetch; not content or billing | E / experimental | T-20 |
| M-047 Mobile received bytes | All supported mobile networks, no SIM ID / W | B; separate network consent | No per-SIM assertion; bucket completeness visible | E / experimental | T-20 |
| M-048 Mobile sent bytes | Canonical OS UID buckets / W | B; separate network consent | Same scope/limits as M-047 | E / experimental | T-20 |
| M-049 Foreground/background network state | OS state partitions if exposed/proven / W | B | State labels need qualification; not activity-surface allocation | E / experimental | T-20 |
| M-050 Network daily/monthly trend | Compatible byte buckets plus coverage / L(W) | D | OS window/freshness; no proportional split across missing buckets | E / experimental | T-20, T-23 |
| M-051 Notification lifecycle counts | Metadata posts/updates/removals / N | B | Updates/group summaries deduped; removed is not read | E / planned | T-08 |
| M-052 Notification/open associations | Covered temporal joins / L(N,U) | D | No causality or inferred clicked notification | E / experimental | T-09 |
| M-053 Supported browsing-domain time | Qualified domain context intervals / H | C | Private/uncertain excluded; not arbitrary browser history | E / experimental | T-10, T-21 |
| M-054 Supported title / URL diary | Consented sanitized navigation metadata / H | C | Optional local content records; never editable fields/secrets | E / experimental | T-11 |
| M-055 Optional topic share / transitions | Consent-matched local title classification / L(H) | C | Unknown fraction, correction, taxonomy revision; no addiction label | C / experimental | T-15, T-23 |
| M-056 Coverage / source-health history | Native grant/config/source status / L | D | Checked-at/last-success, not proof of complete data | M / planned | T-16, L-06 |

## Long-term diary and reflection

| Metric | Meaning / source and access | Controls | Accuracy / restriction | Phase / status | Scenario |
|---|---|---|---|---|---|
| M-057 Day/week/month/year summaries | Selected additive facts and coverage / L | R | Lossy, zone/revision fixed; no reconstructed detail | E / planned | D-04, D-05 |
| M-058 Lifetime covered totals | Compatible retained facts across tiers / L | D, R | No sum of overlapping detailed and summary representations | E / planned | D-04, T-23 |
| M-059 User-chosen time equivalents | Covered duration / chosen session length / L | D; off by default | Arithmetic only; not a promised life outcome | E / planned | T-23 |
| M-060 Storage / detail inventory | Actual rows/bytes/tier/loss preview / L | R | Measured bytes separate from estimates; no hidden purge | M / planned | D-01, D-04 |
| M-061 User-imported activity records | Future allowlisted supported archive / I | I, B, C where content | Delayed/partial, schema-specific, source lane kept distinct | E / experimental | T-22 |
| M-062 Local descriptive trends | Comparable retained periods / L | D | Withhold incompatible metrics/coverage/taxonomy | M / planned | T-13, T-23 |
| M-063 Explainable local forecasts | Opted-in sufficient history / L | D; separate opt-in | Calibrated/insufficient-data, recommendations only | C / experimental | T-14 |
| M-064 Selected activity targets | Compare covered time to a chosen personal budget / L | D; opt-in | No default judgment of leisure or aggregate wellness score | E / planned | T-23 |

## Platform-by-platform admission

The following are **candidate activity families to investigate**, not claims that fields currently exist. Each cell requires a package/version/locale/build-specific evidence record. “Candidate” always means experimental, unavailable in the product until qualified. Android app totals and network research use public OS sources across selected apps; intra-app semantics cannot be generalized to every app. No current service gets a verified badge.

| Candidate target | Primary activities to investigate | Item/interaction metrics to investigate | Initial support claim |
|---|---|---|---|
| Facebook | Reels, regular feed, stories, live, safe navigation; messaging separate if structurally safe | Short-video/post/story/ad exposures; reactions, comment outcome/attempt, shares, saves where qualified | None; app/surface/media/action gates independent |
| Instagram | Reels, feed, stories, profiles, safe creation; messaging separate | Short-video/post/story/ad exposures; reaction/save/comment/share outcomes or attempts | None; comments/ads/re-entry must not inflate Reels |
| YouTube | Shorts, long video, live, search/navigation | Short-video exposures, qualified reactions/comment/share/subscription UI evidence | None; player surface occupancy is not completed watch time |
| TikTok | Short video, photo carousel, live, search/profile; inbox separate | Qualified video/photo/ad exposures and reaction/comment/share/save UI evidence | None; photo/live/ad eligibility separate; regional variants separate |
| Reddit / X / LinkedIn | Regular feed, video, thread/comments, search/profile | Post/media exposures; supported reactions and safe interaction evidence | None; later adapters, no generic feed/swipe detector |
| Snapchat / Pinterest | Safe stories/feed/media/navigation | Qualified settled exposures and safe controls only | None; ephemeral/private contexts require stronger exclusion evidence |
| Twitch | Live/video/navigation | Qualified context time and safe UI actions | None; no chat content or verified viewing claim |
| WhatsApp / Telegram / Discord | Coarse messaging/navigation context only if safely distinguishable | No message-content, message-count, recipient or keystroke metric | None; app totals work independently of context feasibility |
| Supported Android browsers | Domain/navigation metadata where separately consented | Domain context/time and sanitized URL/title history only | None; private/unknown modes fail closed |
| Other installed apps | App/device history and separately consented network-statistics research | In-app activities only after a dedicated safe adapter qualifies | None for intra-app behavior; never infer work/game/intent from package alone |

When an upstream layout changes, disable only affected metric/support ranges with visible gaps; app totals may continue. Publish adapter revision, inspected fields, retained fields, fixture evidence/precision/recall, last check date and limits in How tracking works. Metric status is separate from app installation and grant status.

## Source research and alternative approaches

Primary sources reviewed **2026-10-27**:

- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) and [UsageEvents](https://developer.android.com/reference/android/app/usage/UsageEvents.Event): public app/device evidence, not universal in-app actions.
- [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent) and [AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo): structural/control events can support candidate adapters. A click event means a view was clicked; app-specific outcomes require additional evidence. Respect password/editable/sensitive nodes and unavailable hierarchy, including API-34 sensitive-data indicators.
- [NetworkStatsManager](https://developer.android.com/reference/android/app/usage/NetworkStatsManager) and [network buckets](https://developer.android.com/reference/android/app/usage/NetworkStats.Bucket): UID/transport byte statistics, coarse temporal granularity, permissions and dimension semantics. Network metadata cannot reveal encrypted social content or individual actions.
- [Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en): separate prominent disclosure/consent, declaration/review and limits on automation/privacy bypass. Openness and sideloading do not create new public APIs or justify concealed capture. No accessibility-tool designation is assumed.
- [YouTube activities API](https://developers.google.com/youtube/data/reference/rest/v3/activities/list): documents channel activity events, not a universal live viewing/action diary. Do not advertise it as a replacement for Android observation or add an account/API key to this app for speculative coverage.
- [TikTok Data Portability prerequisites](https://developers.tiktok.com/docs/en/data-portability-api-get-started): approval, registered application and Login Kit consent are required for that API path. It is not selected as a live collector. A future offline user-export parser is a different feature and needs actual schema/terms review; no eligibility or fields are assumed here.

No general third-party API grants complete activity across every social platform. Provider-specific public APIs, user exports and a dedicated adapter can be evaluated independently when they provide a permitted missing metric. Meta help contents could not be reviewed without a login during this research; no Meta export schema/API coverage is asserted. Do not sign in, scrape private endpoints, harvest tokens, root a device, intercept credentials/traffic, record screens/audio or inspect private messages to fill gaps. These are product boundaries, not temporary workarounds. Personal archives and experiments stay outside Git; synthetic fixtures and public schema references support review.

## Admission and acceptance

For every admitted metric record: target/package/profile/version/locale/OS range; definition/unit; inspected/retained fields; necessary access; consent/exclusion/detail controls; source freshness; false-positive/negative evidence where relevant; dedup identity; coverage/null behavior; phase; fixture/device evidence and remaining restrictions. Interaction precision and coverage are assessed per action, not inherited from surface detection. Out-of-range support is unavailable by default.

T-17–T-23 and D-04–D-06 extend the [test inventory](TESTING.md). Documentation/calculation checks do not qualify the named social platforms or establish network accounting accuracy. No row becomes implemented merely because a synthetic number can be displayed.
