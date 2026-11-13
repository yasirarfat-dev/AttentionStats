# Settings specification

**Status:** Planned catalog with working narrow Reels controls and experimental settings routes; broader preferences remain unimplemented. Updated 2026-11-13.
**Responsibility:** Defines user controls, defaults, persistence owner and change effects.
**Related specifications:** [UX](UX-SPEC.md), [Privacy](PRIVACY.md), [Protection](PROTECTION-SPEC.md), [Backup](BACKUP-RESTORE.md), [Models](DATA-MODELS.md).
**Unresolved feasibility:** F-01–F-08 in [Decisions](DECISIONS.md); unavailable settings must state the gate rather than appear functional.

Current backup control (ADR-073/AS-058): native Backup & restore offers automatic-by-default Android-managed backup (ADR-079), preserving saved opt-outs and local-erase off choices, device-backup settings, a timing request with unconfirmed-upload status, pending-system-restore review/discard and manual no-key export/import. The public package credential requires no user memorization and does not protect a file from its holder. Device-local consent/preferences never travel in a backup.

## Persistence and change rules

Current development (ADR-068/ADR-069): Protect opens the native app-group/multi-period editor with explicit activation previews. Settings adds manual encrypted backup/restore and independent photo-collection and experimental numeric-playback controls. Playback initially stays off; photo collection preserves existing disclosed behavior and can be disabled prospectively without deleting history. Native-authoritative choices are revision-checked. Restore leaves tracking, playback, reminders, viewer blocks, schedules and HUD off. No working automatic-Drive or social-action toggle is shown while unavailable. AS-056 retains the exact BrainRot service label and direct Android permission entry under ADR-072; the maintainer rejects an intermediate hand guide. Returning rechecks actual grants. Earlier preview descriptions below retain historical scope; State owns current evidence.

AS-043 exposes a narrow working implementation of these planned controls in the React Native Reels route: independently disclosed Instagram/Facebook source switches (default off), persisted pause/resume of enabled sources, separately persisted live HUD switch (default off), actual accessibility health/settings launch and confirmed all-Reels-history deletion. Deletion also disables both sources/HUD. Unsupported versions stay count-unavailable even after consent. A separate, volatile ten-minute structural diagnostic requires its own affirmative disclosure and source/grant checks; stop removes the local file. Export, restore, per-app deletion, retention tiers and broader settings remain planned. Native configuration revisions protect saves; a failed/stale save is visibly unconfirmed and requires refresh.

N = native authoritative Room configuration, U = non-sensitive UI preference in MMKV, K = secure native credential/token storage. Defaults apply to new installation, never as a silent reset on update. Source/detail expansions need affirmative consent. Data mutations return native revision/success before the UI reports saved; conflict/failure keeps a visible unsaved draft. Display current defaults, effect scope and relevant capability health.

AS-045 adds native Settings destinations for sources/health, local storage, backup/recovery, app appearance, floating counter, convenience and about. ADR-044 adds two actual native-owned display preferences under Floating counter: optional session/Today icons (initially on) and Today across all observed apps (default on; off means current app). Plain numbers have no tilde; spoken/expanded/app explanations retain estimate and scope semantics. These preferences are saved by revision-checked native mutation without enabling access/sources/display or resetting visits. Native ownership, rather than JS MMKV, supports the counter when JS is stopped. Other read-only unavailable rows describe unimplemented controls; they do not persist fake preferences. System theme remains automatic. Position lasts for the service session with accessible move/reset actions; full appearance profiles remain planned. AS-044 now supplies separate experimental session-only auto-scroll controls under ADR-051; source-specific live action acceptance remains open.

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

## AS-044 session controls and visit-only HUD hide

The experimental Convenience screen offers a separately disclosed Allow controls switch, off initially, scoped to the current service session. This enables no observation or live display and starts no run. Version-specific availability stays visible; unqualified video-end signals remain unavailable. A compact expanded-HUD toggle accesses the same choice; consent, when needed, opens this app screen instead of expanding explanations in the HUD. Allow is remembered only in memory for the service session; toggling off then on does not repeat the notice during that session. Start/Stop is explicit in the qualified viewer, with Stop visible while collapsed. Restart clears the choice/run. See [Auto-scroll](AUTOSCROLL-SPEC.md).

HUD Hide affects only the current app visit; it keeps collecting and never writes setOverlay(false). The native saved Floating counter switch is the persistent display preference. Pause counting stops collection until resumed and is distinct from both forms of display hiding.

ADR-055/AS-050 adds experimental domain-grouped cards: tracking and sources/health, reminders/convenience, display, and privacy/storage/about. Source health is removed from Today. Usage Access and accessibility setup give actionable instructions and recheck actual access on return. App timing and reminders start disabled; no grant or rule is enabled by navigation. Native configuration revisions reject stale saves. Reminder thresholds accept 1–1000 encounters and 1–240 minutes; either threshold triggers, across enabled supported Reels sources. Saving thresholds while disabled does not activate the rule. Existing appearance/recovery features retain their actual unavailable states.

ADR-058 adds an optional Battery & background card: actual own-package optimization status, API-28+ Android background restriction, a package-scoped Open Android app settings button and device-dependent Battery/Unrestricted guidance if tracking stops. Navigation changes no preference or grant; status refreshes on return. Explain possible battery cost, foreground reconciliation for app events and service dependence for live Reels. An exemption cannot promise continuous collection. No exemption-request permission or startup gate.

ADR-059/ADR-060 add Reels counting and Block Reels cards. The former controls recognized-ad inclusion, default on, with persistent independent ad counts and no history deletion. The latter has four disabled-by-default source switches, explicit activation disclosure, actual availability and immediate bypass instructions. No paywall/subscription copy. Configuration does not enable source observation or Android grants. Statistics-session semantics are unlock-to-lock across apps; the blocker bypass and HUD Hide explicitly remain scoped to an app visit.

ADR-061 reserves a disabled per-app timer affordance in Today/History. Per-app budgets, app locking, typed/timed overrides and weekly detail navigation remain planned after MVP; no configuration or restriction is started by this UI.


ADR-082 exposes voluntary focus in Protect, initially inactive. UI duration starts at 25 minutes and allows 5–180 minutes in five-minute steps; an existing app group is required and targets are reviewed before Start. The native session continues independently of this screen. End focus now has no delay or reason requirement. Existing schedule/count rules remain active after ending focus; cover-based Allow 5 minutes is an immediate package-scoped visit allowance. Disconnected Accessibility is reported as unavailable enforcement, with the session timer continuing. No new grant or source activation follows focus selection.

Current navigation refinement (ADR-083): Protect now links to dedicated Mindful breaks, Short-video blocks and Live counter screens. Their native preferences/defaults and independent schedule semantics are unchanged. Pending stepper changes flush when leaving a tool. Today Quick actions opens these editors or focus setup; opening/dismissing the sheet never changes a preference or begins a session.

## Compact grouped settings (ADR-084)

Order: Counting → Permissions → What to Measure & Extras (one card, Extras inset background) → Data & Privacy (one card) → Appearance → Help & Feedback (last card) → credits. Optional measurement/extra and motion descriptions are toggled by 48dp labelled info buttons, independently of switches; expand/collapse never saves a choice. Experimental status stays in option labels. Keep data-loss/backup disclosures and permission health visible. Credits read “Digital Wellbeing:Attention Stats 1.0.0”, “No Developer Telemetry, No Ads Ever, Open Source”, “Made with Care by Yasir Arfat”, “Published By Convenience Labs”; version follows installed app version without preview suffix. Existing author/publisher links remain.


AS-027/ADR-086 widget continuation is underway at the maintainer's request: multiple adaptive styles, ordered stat choices, per-instance appearance and native preview, preserving bounded updates and source/consent ownership. See [Widget specification](WIDGETS-SPEC.md). Experimental; actual artifact/host/launcher results follow separately.


Widget controls (ADR-087/ADR-088, AS-027) are native and per-instance: five launcher products, seven selectable layouts, ordered statistics, theme/accent, optional source identity, background opacity, corner shape, stat number size and update timestamp. Configuration uses illustrative example previews or explicitly selected current stats, explicit Save/Cancel and version-1-compatible version-2 display preferences. Settings' Add Widget entry opens the five-preview native gallery; the launcher picker also offers all five named products. A display change never enables tracking, focus, consent or network transfer. [Widgets](WIDGETS-SPEC.md) owns exact behavior and acceptance.


ADR-089 uses card-level Settings help: one named info control at each section heading, with a progressive definition list immediately below that heading, before controls. Each concept has its own bold term, concise description and separator; Hide Explanations offers an explicit exit. Routine per-row descriptions/info controls are removed, while actual health/status and deletion warnings remain visible. Switches are the final controls at the right edge. Help changes only presentation. Today, Viewing Details and paired Insights/app-history statistics gain subtle partial-height decorative separators, with no new spoken nodes or metric changes.


ADR-090 keeps the native widget appearance page concise: preview/opacity/Theme/Accent first, with finer controls behind More Options and Tiles/List behind More Layouts. Current templates are curated rather than capped; changing presentation preserves chosen stats. Detailed options remain available without flooding the default view.


### Display, deletion and custom appearance (ADR-091)

Rename What to Measure & Extras to Display & Experience and include Live Counter configuration alongside measurement choices, auto-scroll and widgets. Native counter behavior and source consent remain independent. Erase History has three named confirmation steps with softly tinted green keep/pause-and-keep actions and red deletion-path actions; typed DELETE and the final ten-second delay stay mandatory. Colors supplement explicit action labels at every step.

Appearance supports an editable draft, Light/Dark/System, starting palettes or an arbitrary RGB/hex accent, a live illustrative preview, card corners (0–32dp), card spacing (12–24dp), statistic size (24–40sp, still following system text scale), motion, explicit Save and Reset Appearance Draft. Advanced layout controls stay collapsed initially. Accent text/buttons derive readable tones, preserving warning/destructive meanings. Custom accents reach native palette consumers; layout dimensions apply to React Native cards/statistics, with separate native/widget layout controls. Old appearance preferences read with defaults; no observation or rule changes follow appearance editing.

ADR-093/AS-064 adds a separate off-by-default **Skip Standalone Ads (Experimental)** toggle under Extras, disabled until the memory-only Auto-scroll permission is enabled. Both enabling actions have Allow/Cancel prompts; neither starts scrolling. The ad disclosure names the Facebook dedicated-Reels subset, immediate page swiping, no ad-link clicks and exclusion of pre-roll/provider Skip Ad controls. Actual HUD Start/Stop remains the single in-context action. Action contract 2/notice revision 2 carries the independent choice. Auto-scroll Off clears it; service restart clears both permissions. No new Android permission is requested.

## Proposed Protection PIN (ADR-092 / AS-063)


Planned, unavailable in the runtime: optional guardian PIN setup/confirmation, protected-action selection, verified offline recovery, change/disable and action-scoped challenge/reset screens. Protection owns exact rule/exception behavior; Security owns verifiers/recovery/rate limits and platform restrictions. Until qualified, no working PIN/uninstall toggle or child-protection guarantee. Self-managed daily exceptions must preserve usage and show scope/reset. Existing immediate allowances remain unchanged by the focus-menu delivery.
