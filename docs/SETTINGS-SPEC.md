# Settings specification

**Status:** Planned catalog with working narrow Reels controls and experimental settings routes; broader preferences remain unimplemented. Updated 2026-10-28.
**Responsibility:** Defines user controls, defaults, persistence owner and change effects.
**Related specifications:** [UX](UX-SPEC.md), [Privacy](PRIVACY.md), [Protection](PROTECTION-SPEC.md), [Backup](BACKUP-RESTORE.md), [Models](DATA-MODELS.md).
**Unresolved feasibility:** F-01–F-08 in [Decisions](DECISIONS.md); unavailable settings must state the gate rather than appear functional.

## Persistence and change rules

AS-043 exposes a narrow working implementation of these planned controls in the React Native Reels route: independently disclosed Instagram/Facebook source switches (default off), persisted pause/resume of enabled sources, separately persisted live HUD switch (default off), actual accessibility health/settings launch and confirmed all-Reels-history deletion. Deletion also disables both sources/HUD. Unsupported versions stay count-unavailable even after consent. A separate, volatile ten-minute structural diagnostic requires its own affirmative disclosure and source/grant checks; stop removes the local file. Export, restore, per-app deletion, retention tiers and broader settings remain planned. Native configuration revisions protect saves; a failed/stale save is visibly unconfirmed and requires refresh.

N = native authoritative Room configuration, U = non-sensitive UI preference in MMKV, K = secure native credential/token storage. Defaults apply to new installation, never as a silent reset on update. Source/detail expansions need affirmative consent. Data mutations return native revision/success before the UI reports saved; conflict/failure keeps a visible unsaved draft. Display current defaults, effect scope and relevant capability health.

AS-045 adds native Settings destinations for sources/health, local storage, backup/recovery, app appearance, floating counter, convenience and about. Read-only unavailable rows describe unimplemented controls; they do not persist fake preferences. System theme remains automatic. The compact counter is draggable for the service session and has accessible move/reset actions; full appearance profiles are not implemented. Optional completion-triggered auto-scroll is a separate disabled/unavailable convenience setting under AS-044, not a statistics/source switch.

## Catalog

| Group / settings | Default and available choices | Owner / effect |
|---|---|---|
| Tracking master pause | No sources active initially; timed pause or indefinite until resumed | N; stops configured source writes; separate from protection pause |
| Per-source enablement | App use, screen, unlock, surface, notification, browser independently off | N; effective consent time; no pre-consent import |
| Apps/categories to track | Empty until selected; per-source include lists and category resolution | N; native filter before reading optional payloads/persistence |
| Exclusions | Sensitive contexts always excluded; user app/category/time exclusions configurable | N; exclusions win; preview scope change |
| Source detail | Basic metadata only for enabled basic source; titles/URLs/topics off | N; independent affirmative detail consent |
| Supported surface adapters | Off individually; registry shows unverified/experimental/supported | N; no generic silent fallback |
| Detailed activity families | Off individually; primary contexts/feed/story/ad exposures where qualified | N; per-app/adapter/metric selection; separate coarse messaging-context choice, no private message data |
| Interaction evidence | Off; choose reaction/comment/share/save/follow families per qualified adapter | N; attempts versus UI outcomes separate; no typed text or server-success claim |
| Network byte statistics | Off; selected apps/transports and visible source bucket/freshness | N; separate consent despite existing Usage Access; no VPN, SIM ID or bytes-per-Reel inference |
| Advanced diary / metric explorer | Per-platform short-video entry visible; chosen available metrics/cards/dimensions/range/table | U; cannot hide missing coverage or turn unavailable into zero |
| Short-video count display | Exposures with available confidence; never “videos completed” | U for presentation; N for versioned algorithm configuration |
| App-open grouping | Proposed two-second short-gap grouping; adjustable with explanation | N; revisioned reduction/rebuild, no raw-event rewrite |
| Notification scope | Off; selected packages; metadata-only | N; no notification body capture setting |
| Notification association | Off until sources enabled and selected; proposed 60-second window | N; association not causation |
| Browsing domain/title/URL | All off; domain-only recommended, supported titles separate; sanitized URL optional | N; unsupported/private contexts excluded |
| Content-topic processing | Off; local rules/model, manual topics/corrections/reset | N; cannot enable metadata capture implicitly |
| Insight display | Descriptive cards available after enabled data; individually hideable | U visibility; N thresholds and taxonomy |
| Prediction | Off; local opt-in, explain/reset/delete | N; insufficient data behavior fixed; no automatic actions |
| Reopen/long-session thresholds | Proposed three entries/ten minutes; 20-minute long episode; adjustable | N; explanation includes chosen values |
| Bedtime measurement window | Proposed 23:00–07:00, inactive until selected | N; no medical interpretation |
| Categories | Editable neutral starter set; primary category plus optional tags | N; choose future-only or recategorized history view |
| Protection mode | Observation only; activate individual rules explicitly | N; no master switch silently activates drafts |
| Per-rule target/trigger/action | Empty draft; app/category/surface, budget/schedule/focus, reminder/prompt/cooldown/block | N; preview requirements/conflicts before activation |
| Per-rule enforcement/override | Reminder or immediate override selected explicitly; optional commitment friction | N; no default hidden restriction; emergency remains immediate |
| Rule exceptions | Explicit target/time/rule scope; invariant recovery exclusions always active | N; exception does not waive unrelated rules |
| Rule fallback on unavailable enforcement | No claimed enforcement; reminder fallback only if selected | N; independent health reason |
| Protection pause | Off; duration or explicit resume | N; does not stop observation unless separately selected |
| Focus presets | No active session; customizable duration/targets and schedule | N; persists independent of JS |
| Schedule zone / start weekday | Follow device zone proposed; fixed named zone optional; overnight start day explicit | N; preview travel/DST behavior |
| Bedtime routine / DND | Off; reminders independent; optional DND access | N; expansion and ownership-safe restore |
| Prompts and cooldowns | Disabled until chosen; frequency cap, text, duration | N; neutral text, no credential/health content |
| Reminder notifications | Off until selected; channel settings link and optional haptics/sound | N for consent/rules; platform for channels |
| Retention per source/detail | Preserve until deleted; 30/90/365 days or custom | N; shortening previews deletion and summary-only choice |
| Multi-resolution summary tiers | Off; preserve detail default; custom per-source/app/detail ages, daily/weekly/monthly/yearly summaries | N; fixed zone/week start/statistics, full loss preview, explicit confirmation before irreversible compaction |
| Summary-only archive | Off; explicit selected source/range/statistics/tier | N job; scope manifest and recovery behavior; never silently compacts local history |
| Optional time equivalents | Off; user-defined label/session length and selected covered duration | U/N as relevant; arithmetic explanation, hide/reset, no guaranteed life outcome |
| Supported activity archive import | Unavailable until parser/schema/terms/security evidence | N; explicit selected file/fields/range/preview; imported/live source lanes separate |
| Storage visibility | Measured DB/WAL/cache/staging bytes and row counts | Native query; no automatic purge setting enabled by default |
| Selective deletion | App/source/date/detail and optional config scope preview | N job; derivative/model invalidation and ingestion fences |
| Full reset | Explicit all-data operation | N/K/U; clears keys, jobs, grants held by app, history/config; external copies explained |
| Portable encrypted export | Manual, selected scope and destination | N job + K credential handle; no product account |
| Restore | Manual, authenticated staged replacement preview | N job; imported collection/rules inactive until review |
| Recovery credentials | No credential initially; generated key or reviewed passphrase flow | K; test, rotate, forget local remembered credential |
| Automatic Google backup | Unavailable; direct Drive blocked pending policy clarification | N/K future; no OAuth/upload action until cleared |
| Future approved backup schedule/account | Desired daily schedule, selected personal account, Wi-Fi/charging preferences | N/K; blocked UI explanation today, no simulated status |
| Existing archive cleanup | Manual user-confirmed destination operation where supported | N/provider; local deletion does not delete external copies |
| Permission/health dashboard | Show all source states, grants, check age and gaps | Native status; Android routes do not change consent |
| Local diagnostics | Bounded redacted reason codes; no automatic upload | N; inspect/clear, public reports synthetic |
| Theme | System / light / dark | U; does not change behavioral data |
| Motion | System preference; reduced-motion override | U; no delayed emergency action |
| Dashboard/chart customization | Default summary cards; hide/order cards, date range and chart/list choice | U; gaps/evidence labels cannot be hidden deceptively |
| Display zone / week start / time format | Device zone/locale, override for history view | U; source instants unchanged; rule schedule zone separate |
| Language / number formatting | System locale, explicit supported locale choice when translations exist | U; no unimplemented translation claim |
| Accessibility | Follow system scaling/reader; chart tables and meaningful labels always available | U for optional stronger preferences; no toggle disabling baseline access |
| Widgets / Quick Settings | Off; lock-screen details hidden | N for source pause actions; U for display; expansion |
| Live awareness overlay | Off; per qualified app/surface, visit/day count/time fields, small top-center HUD default, leading/trailing alternatives, size, hide period and disable | N configuration; separate tracking/protection consent; experimental C-32, renderer/grants/health explicit; [spec](LIVE-OVERLAY-SPEC.md) |
| About/privacy/source/licenses | Repository, release commit, capability support, licenses and no-data-to-developer explanation | Bundled metadata; external links require user navigation |

## Acceptance

Settings remain usable without grants and offline. Revocation never flips a product-enabled flag to a misleading successful state. Native validation rejects unsupported fields/rules, stale revisions and invalid dates. Reset and restore must clear relevant UI caches. Verify source toggles during ingestion, separate pause scopes, retention preview conflicts, theme/motion persistence and unsupported backup states with T-16, D-01, B-01–B-07 and X-02 in [Testing](TESTING.md).

The [browser study](../design/prototype/README.md) demonstrates theme, motion and enlarged-text preferences in memory. Reload resets them; that deliberately does not implement the native persistence catalog above. System reduced motion cannot be overridden with more motion. Reminder channels start off; no engagement campaigns or guilt-based reactivation settings are introduced. See [Human factors](HUMAN-FACTORS.md).
