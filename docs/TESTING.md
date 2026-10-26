# Verification and acceptance

**Status:** Documentation/browser checks and pure Kotlin calculation tests available; full native application scenarios not run. Updated 2026-10-27.
**Responsibility:** Owns acceptance scenarios, evidence quality and release gates.
**Related specifications:** [Capabilities](CAPABILITY-MATRIX.md), [Tracking](TRACKING-SPEC.md), [Protection](PROTECTION-SPEC.md), [Backup](BACKUP-RESTORE.md), [Security](SECURITY.md).
**Unresolved feasibility:** F-01–F-10 in [Decisions](DECISIONS.md); source/API/OEM support is not verified by calculation tests alone.

## Verification levels

Documentation: `python tools/check_docs.py` and `git diff --check`, plus human review of contradictions, public safety and source accuracy. Native unit tests: deterministic reducers/rules with injected time and minimized synthetic events. Persistence tests: transactions, dedup, migration, key loss and interruption. Contract tests: typed schemas, revisions, pagination, cancellations and dropped notifications. UI tests: partial grants, unknown gaps, accessibility and recovery. Device tests: source fidelity, reboot/OEM restrictions, overlays, background work and egress.

Each application evidence record needs commit/build, exact dependency/toolchain versions, Android API/build/OEM, target app/version/locale where relevant, fixture ID, expected/actual outcome, limitations and date. Public reports contain synthetic identifiers and aggregate outcomes only; no device identifiers, real accounts or usage traces. “Implemented” requires code plus passing relevant scenarios, not a written plan or mock screenshot.

## Observation and interpretation scenarios

| ID | Given / action | Required result |
|---|---|---|
| T-01 | Foreground from 10:00Z to 10:10Z, replay same ingestion window twice | One session, 600000 ms, no duplicate source/exposure rows; cursor and batch atomic |
| T-02 | Multiple activities inside `com.example.social`, switch away, return | One open per confirmed package entry; same-package activity change adds none |
| T-03 | App stays resumed while screen off/locked | Interactive union stops; no background audio counted as foreground attention |
| T-04 | Wake locked twice, then keyguard dismissed once | Two wakes if supported, one unlock; no biometric/credential data |
| T-05 | Move synthetic social app to work category, recategorize selected period | Grouped totals change without source session/time mutation |
| T-06 | Synthetic short feed/chat/search/story/live/comment structures and unsupported version | Correct distinct surface; unsupported is unknown, no generic scroll classification |
| T-07 | One stable item emits 50 callbacks, then confirmed next item, redraw, return swipe, interleaved identified ad and ambiguous eligibility | Exposure counts 1 then 2; redraw unchanged; return exposure distinguished from unique content; ads/nonqualifying media excluded; ambiguous transition withheld with incomplete coverage; rapid unconfirmed flings not invented |
| T-08 | Notification post, repeated updates, group summary, remove and later repost | Lifecycle dedup; new episode only when justified; removal never labelled read |
| T-09 | App open 20 seconds after observed post, then repeat during listener gap | First is labelled temporal association; second not asserted; no causation claim |
| T-10 | Supported public navigation, private/unknown mode, unsupported browser | Only supported allowed metadata; private/unknown/unsupported content omitted with coverage reason |
| T-11 | Password/editable nodes, auth/payment/chat, URL query token, content consent withdrawn mid-callback | No sensitive field read/persisted/logged; in-flight optional payload discarded |
| T-12 | Heuristic category conflicts with manual mapping; restart | Manual mapping wins, version persists, explanation shows correction |
| T-13 | Week has sparse/gapped coverage; baseline zero; zone changes | Withhold incomparable trend; no divide-by-zero, fake improvement or diagnostic label |
| T-14 | Less than prediction floor, model drift, deleted source history | insufficient_data or reset; no invented probability or autonomous intervention |
| T-15 | Topic consent off, then consented synthetic title, unknown title and user correction | No topic reading before consent; local classification with unknown fraction; no addiction/sensitive-trait inference |
| T-16 | Disable/pause/exclude source while batch pending; re-enable later | Revision fence drops prohibited writes, pause gap visible, no silent backfill |
| T-17 | Five-hour app interval, four-hour short-video interval, duplicate/overlapping primary intervals, facet overlays and gaps | App union conserved; disjoint surfaces plus unclassified equal app duration; conflicting evidence unclassified; facets never added to app time; per-app filters isolated |
| T-18 | Replayed transitions, settled revisits, ads/stories/posts, unsupported detector, contradictory identity and midnight | Count only qualified metric-specific exposures; duplicates unchanged, nonqualifying excluded, ambiguity/coverage explicit, unknown support null; half-open day assigns event once |
| T-19 | Click without outcome, reaction toggle/unlike, composer open, submit attempt/acknowledgement, share open/handoff and public post tally | Attempts distinct from observed UI outcomes; no server/delivery assertion or text inspection; exact replay dedup, conflicts withheld; signed net derived only from matching scope |
| T-20 | Network RX/TX snapshots repeated/revised/overlapping, ALL plus fine partitions, partial buckets, shared/reused UID, reinstall, VPN, offline/revocation | Canonical source mode and identity/transport attribution; no duplicate totals, proportional bytes or video attribution; coarse/freshness/coverage visible; missing data not zero; close resources/off-main-thread |
| T-21 | Safe primary contexts versus editable/message/password/payment/private/unknown fields across per-target versions | Coarse context only if safely qualified; no private text/input/recipient read or persistence; unsafe context unclassified and affected detector disabled |
| T-22 | Future supported user archive, duplicate import, unsupported schema, large/nested/malicious payload and private fields | Native bounded allowlisted parser, source/date/field preview; reject safely without live-store mutation; imported versus observed lanes distinct unless reconciliation proven |
| T-23 | Compatible/incompatible periods, zero/unknown denominator, signed net, coarse history and chosen time-equivalent | Split incompatible definitions/evidence, preserve unknown fractions; no invented zero/rate/drill-down; four covered hours is eight chosen 30-minute sessions, no life-outcome promise |

## Lifecycle, time and scale

| ID | Failure/boundary | Required result |
|---|---|---|
| L-01 | Missing start/end transition or event arrives out of order | Censored/uncertain interval, bounded correction; no unbounded duration or fake zero |
| L-02 | Session crosses midnight, DST gap/fold, device/view timezone changes | Source session preserved; deterministic half-open bins with actual civil boundaries |
| L-03 | Wall clock moved backward/forward in same boot | Monotonic duration where available; explicit anomaly without negative totals |
| L-04 | Reboot before unlock and during active schedule | No credential-encrypted reads before unlock; permitted reevaluation; shutdown gap explicit |
| L-05 | JS death, native process death and Android force-stop separately | Native independence where permitted; bounded idempotent recovery; force-stop gap admitted |
| L-06 | Permission revoked, listener disconnect, background restricted/OEM kill | State updates on observation; no stale successful enforcement; safe recovery guidance |
| L-07 | Offline throughout seven days; grants/settings navigation partly unavailable | Core local history/rules work where supported, no product login/network requirement |
| L-08 | One million minimized events, one-year history, multi-window and secondary display | Bounded paging/memory; no duplicate aggregate duration; overlap/coverage labelled |

## Protection scenarios

| ID | Given / action | Required result |
|---|---|---|
| P-01 | App and category budgets overlap with focus rule and per-rule exception | Count usage once; deterministic precedence/reasons; one exception doesn't waive unrelated rules |
| P-02 | Overnight weekday schedule/focus crosses reboot, travel or DST | Correct start-day/zone policy, deadline reevaluation, no claimed exact-time reliability |
| P-03 | Bedtime DND changes; user changes DND again before routine ends | No overwrite of newer user/system setting; unsupported API degrades visibly |
| P-04 | Selective surface rule active; navigate to chat, unknown layout and app level | Only confirmed chosen surface blocks; unknown releases; no silent whole-app fallback |
| P-05 | Duplicate trigger callbacks and failed renderer | One intervention per episode/frequency policy; failed render not counted displayed |
| P-06 | Multiple rules; immediate override for one; all-active override; emergency exit | Scope/expiry respected; usage retained; emergency dismisses all applicable blocks immediately |
| P-07 | Voluntary delay/commitment mode | Previewed bounded ordinary friction; Android recovery/uninstall settings and emergency route remain available |
| P-08 | Overlay/accessibility grant missing, service stopped or prediction recommends a rule | Accurate unavailable/fallback state; no autonomous action or false “blocked” status |

## Storage and backup scenarios

| ID | Given / action | Required result |
|---|---|---|
| D-01 | Delete app/time/content rows, change retention, then replay Android overlap | Delete derivatives/caches/models; history cannot resurrect; rules retained unless included; archive marked stale |
| D-02 | Crash during batch/migration, disk full, corrupt DB or invalidated device key | Committed data preserved; cursor not advanced before commit; no destructive fallback reset |
| D-03 | Concurrent query/mutation, stale cursor/revision, duplicate command and lost event | Consistent tagged error/idempotent outcome; re-query restores current generation |
| D-04 | Raw→day/week/month/year facts, custom zone/week, duplicate facts, unknown partitions, mixed semantic revisions and DST | Conservation of additive bases/coverage, no overlapping authority or averaging averages; incomplete bins explicit; boundary-crossing facts split/rejected, not prorated; preview irreversible losses |
| D-05 | Cancel/crash/disk full/consent or deletion revision change at each durable compaction stage | Before commit preserve detail; atomic validated representation replacement; idempotent cleanup, stale preview rejected; no implicit purge or source resurrection |
| D-06 | Summary-only export/restore and deletion finer than retained app/time/topic dimensions | Archive manifest/resolution truthful; failed restore preserves data; whole-bucket deletion or cancel, never fractional fake redaction; no reconstructed detail or reactivated sources |
| B-01 | Wrong recovery credential on existing populated installation | Authentication failure; live store unchanged; no plaintext in logs/temp/UI state |
| B-02 | Altered tag/header/payload, malformed counts, path traversal, decompression/KDF bomb | Bounded rejection; no partial import or resource-exhaustion loop |
| B-03 | Kill/cancel/export/provider timeout/import interruption at every stage | Before commit preserve live DB; after commit report committed generation; partial external file not “good backup” |
| B-04 | Older supported format, unsupported future format, missing migrations | Staged migration or clear reject; source archive/live data remain intact on failure |
| B-05 | Fresh installation restore, full disk and concurrent ingestion | Validated explicit replacement, no merge; current grants independent; imported sources/rules inactive pending review |
| B-06 | Automatic Google backup selected while F-05 unresolved | Blocked explanation; no OAuth prompt, upload, misleading schedule or silently substituted transport |
| B-07 | Picker cancelled, URI revoked, provider offline/quota, account switched | Safe cancel/failure with current history preserved; manual transport status distinct from automatic backup |

## Privacy and experience gates

| ID | Verification | Required result |
|---|---|---|
| S-01 | Inspect merged manifest, run OS cloud-backup and device-transfer attempts | No sensitive DB/WAL/files/preferences/keys/tokens/staging transferred, including OEM behavior; failures block release |
| S-02 | Capture release egress on first run, all denial/grant states, normal tracking, offline and explicit picker flow | No unexpected app-initiated traffic; distinguish provider/OS traffic; approved future transport only after opt-in |
| S-03 | Review logs, crash output, notification/widget surfaces, temp files, screenshots/clipboard behavior | No sensitive content/credentials/identifiers in diagnostics or public surfaces |
| S-04 | Inspect dependencies, lifecycle scripts, components/exports and permissions | No analytics/ad/session replay/automatic upload; minimum component/permission scope |
| S-05 | Delete/reset/prune history, inspect derivatives and export | No deleted details in caches/topics/archives created afterward; external copies explained; key/token reset verified |
| S-06 | Audit public Git tree, history, issue attachments and release artifact | No secrets, personal histories, device IDs, private reference material or copied assets |
| X-01 | Widget/tile while locked, paused, stale and permission revoked | Redacted lock-screen display, accurate native state and last-update time |
| X-02 | Skip onboarding; partial grants; 200% text; TalkBack; RTL; light/dark; reduced motion; chart alternatives | Core history, settings, emergency and restore journeys remain understandable/reachable |
| X-03 | Future iOS adapter lacks framework entitlement/API/data | Typed unsupported/degraded behavior, no fabricated Android parity or grants |
| X-04 | Review welcome, snapshot, reflection, focus end and reminder with synthetic data | Equal refusal, finite summary, optional no-change path, no guilt/streak/diagnosis or unsupported benefit; participant comfort review remains separate |
| X-05 | Open source/evidence/privacy explanations across covered, unknown and sparse histories | Fact versus estimate, source/coverage and limits understandable; optional provider transfer explained; no fake support/encryption/security audit |
| X-06 | Draft/discard/save rule, overlapping rules, interruption, ordinary continuation and emergency | Preview before demo save, ordinary scope/expiry explicit, immediate emergency release, no delayed or shaming exit; native engine still needs P-01/P-06 |
| X-07 | Both themes, live system theme, reduced motion, 320px/200% text, RTL and modal keyboard/back | Contrast, reachable controls, fixed available navigation, no horizontal clipping, focus containment/return and disabled decorative motion; native TalkBack/frame pacing still required |
| X-08 | Live counter: repeated item callbacks, next settled item, new visit, missing surface, hide, optional pause action | One increment per confirmed exposure; current visit already in today; duplicate adds none; unknown hides counter/no inferred count; hide affects display only; readable, movable without drag, no autonomous rule activation |
| X-09 | Native overlay: midnight/DST/zone change, process death, dropped snapshots, restore/delete, lock/chat/payment/settings, revoked access, OEM restrictions | Correct half-open day/visit continuity and generation invalidation, stale/partial labels, no reset to fake zero; sensitive/system context removed, no touch interception, safe available settings/emergency, bounded battery cost and declared policy/renderer evidence |
| X-10 | Each implemented mobile screen/route/state and behavior on authorized debug phone or dedicated emulator | Build/commit/environment/route/steps/outcome recorded; verify real navigation/back, loading/empty/partial/error, permissions, dark/light, text scale/TalkBack/RTL/motion where applicable; native source/lifecycle behavior additionally on physical device; no desktop-preview substitution |

## Kotlin analytics calculation verification

From `native/analytics-core`, use `pnpm test` or `pnpm run example`; the Python runner bootstraps verified latest-stable Kotlin tools in ignored repository-local caches. From root, `python tools/test_analytics.py --offline` uses cached tools. `python tools/check_architecture.py` verifies current domain package/import boundaries. CI runs these JVM checks independently of the browser reference.

Optional physical/virtual Android execution: `python tools/test_analytics.py --device --sdk <existing-sdk-path>` converts only synthetic bytecode with verified isolated latest-stable D8, requires exactly one authorized target, copies one uniquely named task-owned file, verifies its hash, runs it under ART and removes that exact file in a finally block. It installs no app, changes no grant or settings, reads no phone history and never prints/stores a device serial. Offline repetition is available after tool bootstrap. Do not reset adb or disrupt another agent's emulator; ambiguity requires an explicitly selected isolated target before extending the runner.

AS-035 has 37 passing synthetic checks on host JVM and connected Android ART. They cover only pure subsets of T-17/T-18/T-19/T-20/T-23/D-04 plus time/scale boundaries; they are not full scenario completions. No real source, UI, Room, compaction, encryption, network-collector, consent/lifecycle or backup behavior is established. [Dated log](IMPLEMENTATION_LOG/2026-10-27.md#continuation--detailed-mobile-diary-and-modular-analytics) records exact tools/environment and limitations. All future screens still require X-10, and all source detectors need independent physical-device qualification.

## Mobile toolchain probe verification

AS-002's [probe tool](../tools/probe_mobile_stack.py) validates its [dated evidence](evidence/mobile-stack-probe.json), prepares isolated profiles and audits actual installed direct versions. [Reproduction commands](DEVELOPMENT.md#as-002-compatibility-probe-reproduction) keep strict peers, script protection and release-age checks. The upgraded template is an expected failing diagnostic; the reduced candidate passes only its small syntax/type/transform checks and still fails reviewed-latest parity at the recorded time. Each smoke command records its own exit code; a successful diagnostic harness does not convert its failed transform/parser into a pass.

Guard and evidence-validator exercises check overwrite preservation, cache path bounds, non-latest selectors and artifact metadata mismatches. These are tooling checks, not T/L/P/D/B/S/X application scenario acceptance. No Codegen/native build, collector or X-10 screen evidence results from preparing or installing these profiles.

## Experimental mobile scaffold verification

Root `pnpm install --frozen-lockfile --ignore-scripts`, `python tools/check_mobile.py --installed`, `pnpm run lint`, `pnpm run typecheck`, `pnpm run format:check`, `pnpm test` and `pnpm run bundle:android` verify the pinned graph and actual mobile source. The 9 Jest cases cover the bootstrap response validator and stable failure mapping; they are not mocked screen acceptance or full D-03/X-03 completion. The dependency auditor understands pnpm's separate boot/project documents. The architecture checker enforces current relative-import layers and feature public APIs, alongside pure Kotlin boundaries.

[Android commands](DEVELOPMENT.md#application-setup-as-004) use the committed wrapper, explicit isolated SDK/architecture, latest reviewed native tools and a hashed pnpm compiler patch. Report Codegen, Gradle-domain checks, debug/release compilation, native ABI, installation and live screens as distinct results. Release output is unsigned. No existing ART/browser test result approves this app. AS-004 routes/bridge stay experimental until X-10 live evidence, and full AS-008 contracts, consent/storage/collectors and AS-018 behavior still need their own prerequisites.

## Design study verification

AS-033's [prototype](../design/prototype/README.md) runs `pnpm test` from `design/prototype`. Browser interactions, axe scans and token arithmetic cover the demonstration subset of X-02/X-04–X-07. The tests also check no automatic external requests/persistence and the loopback server's asset allowlist. These do not establish native privacy, collection, encryption, policy, accessibility or psychological benefit. The preview's static snapshots are synthetic; do not call them phone captures or production data.

Manual visual inspection should cover information hierarchy and theme readability. Consented participant tasks should evaluate understanding, agency and perceived comfort using [Human factors](HUMAN-FACTORS.md), with no hidden telemetry. Native review must separately check actual font metrics, keyboard/switch/TalkBack, insets, lifecycle and frame pacing before AS-018/C-30 implementation claims.

## Release thresholds and reporting

No flaky consent/data-loss/emergency tests accepted. All deterministic fixtures must pass exactly. Experimental detectors need declared app/version/locale scope and precision/recall evidence under [Social](SOCIAL-MEDIA-SPEC.md); unsupported ranges remain disabled. Native/UI cold and warm summaries target two seconds on a declared one-year dataset; battery/CPU/storage tests use a declared device and collection-disabled baseline for at least seven days. These are unmeasured targets today.

Record passed, failed, not run and blocked separately in the dated log and tracker. A documentation-only CI success is never an application test success. Apply only relevant checks to low-impact edits; broaden after new failure/risk, not by repeatedly rerunning the same suite without cause.
