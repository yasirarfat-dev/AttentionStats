# Current state

**Status:** MVP 1.0.0 (versionCode 10) built and previously phone-verified; maintainer reports closed-beta publication. Further development uses `develop`. Updated 2026-11-05.
**Responsibility:** Records what exists now, how it was verified, and what is next. Older detail lives in git history and the [implementation log](IMPLEMENTATION_LOG/README.md).
**Related specifications:** [Index](INDEX.md), [Tracker](FEATURE-TRACKER.md), [Decisions](DECISIONS.md), [Adapters](SOCIAL-ADAPTERS.md), [Release](RELEASE.md).
**Unresolved feasibility:** Broader device/language fidelity and live playback/social-action qualification remain open. Snapchat fixtures now exist on `develop`, with runtime acceptance recorded separately below. F-05 (direct Drive API integration) remains blocked; separately authorized Android-managed backup is under AS-058.

## Product

- **Attention Stats**, Android package `attention.stats` (development build `com.attentionstats.debug`), Apache-2.0, public repo `yasirarfat-dev/AttentionStats`. Mobile only; iOS later via contributors.
- React Native (Community CLI, TypeScript, New Architecture) UI; Kotlin accessibility service, overlays and SQLCipher/Room storage; pure Kotlin `reels-core` (counting + detection) and `analytics-core`.

## Implemented (1.0.0)

| Area | What exists |
|---|---|
| Detection (ADR-066) | Adapters for Instagram, Facebook, YouTube, TikTok (+ trill, lite) and Snapchat; ID-first, geometry fallback; inline feed videos on Instagram/Facebook; ads/photos classified separately; first item counted on entry; revisits excluded from totals. |
| Counting store | Room 6: exposures with surface, dwell, revisit, app version, method, device-local item key, unlock session/ordinal; reels timers; app-usage events; intervention events; per-app block limits. |
| Live counter | Draggable see-through HUD (this unlock / today) with a ring toward the next break; tap for Videos/Photos/Ads breakdown, open app, pause, hide for this visit, auto-scroll controls. |
| Interventions | Mindful breaks (N videos / M minutes, countdown, audio-focus pause, "I'm done" → Home); per-app blocks (off / daily limit / always) that leave the player with Back and never trap. |
| App UI | Trust-first onboarding with prominent disclosure and guided setup; Today (hero count, per-app with logos, hourly rhythm, screen time); Insights (7/30 days, equivalents, peak hour, breaks); Protect; Settings (sources, permissions, extras, report a problem, privacy, delete). |
| Widget | Configurable home-screen widget: Today at a glance (videos, watching, screen time, session in four tiles) or a single stat (videos, session, watching, screen time, breaks); reconfigurable by long-press; "Add widget" button in Settings. |

## Verification (2026-10-30)

- `:reels-core:verifyReels`: 29 encounter checks + 79 checks over 26 sanitized real layouts (all five route families and negatives).
- `pnpm typecheck`, `pnpm lint` (oxlint + architecture boundaries), `pnpm test` (66), `python tools/check_docs.py`: pass.
- Live phone, 5 swipes each: Instagram Reels +5, Facebook Reels tab +5, YouTube Shorts +5, TikTok +5 (one run +4 where the feed itself did not advance); Instagram Home feed videos counted; session continues across apps until screen-off; Facebook block leaves the player and lands on Facebook Home; break after 5 videos with countdown; "I'm done" goes Home.
- Also verified live: auto-scroll advances when a video ends and moves past photos/image ads after 5 s, stopping for a break; Instagram daily limit (30) shows "Daily limit reached" and "Okay" lands on Instagram Home; HUD pause shows a "Paused · tap to resume" pill and resuming continues counting; expanded HUD shows aligned Session/Today columns; three-step history erase deletes everything (counts 0, counting off) and onboarding re-enables counting; widgets render and reconfigure on the Samsung launcher.
- Historical 2026-10-30 gaps: Snapchat was not installed then; a later scoped Spotlight pass is in the 2026-11-04 log. The 5-minute break-run reset and other phones/languages remain unqualified.

## Next

1. Preserve the beta baseline on `main`; continue on `develop` under ADR-067. The maintainer reports the closed beta is published; exact Console artifact/source mapping, tester dates and review status have not been independently inspected.
2. Prioritize tester-reported correctness, privacy, crash and data-loss fixes. Turn sanitized detector reports into regression fixtures; qualify Snapchat, non-English layouts and the outstanding break reset on authorized phones.
3. Deliver encrypted export/restore (AS-006/AS-009/AS-019 prerequisites, then AS-020), preserving existing history and testing cancellation, corruption and replacement failure.
4. Complete the remaining history/coverage and accessibility work (AS-011/AS-014/AS-018/AS-037), then richer limits, focus/schedules and escape paths (AS-015–AS-017). Keep optional richer analytics and blocked Google backup separate.

## Active continuation (2026-11-03)

AS-052 establishes the development branch and current handoff. Clean `main` matched fetched `origin/main` at `1fc538221adfa3e66c1d94bdb9a0258509dc188c`; `develop` starts there. This is a source baseline, not a proven mapping to the Console binary. Fresh local `pnpm typecheck`, `pnpm lint`, `pnpm test` (66 tests) and `python tools/check_docs.py` pass before changes. No new Android build or phone trial was performed for this documentation-only continuation. See the [dated log](IMPLEMENTATION_LOG/2026-11-03.md).

Subsequent implementation is underway on `develop`: AS-053 app groups/multiple recurring blocking periods; AS-054 photo classification/Snapchat qualification; AS-020 bounded manual encrypted export/restore; AS-055 separate encounters/optional playback evidence; AS-056 direct permission entry and the maintainer's exact “Attention Stats: Digital Wellbeing & BrainRot Shield” service label. These are development candidates until their actual build/device evidence is accepted. **A merge into `main` requires explicit agreement by the maintainer and the agent's readiness assessment.** Schema 7 adds schedule configuration; schema 8 adds media kind/playback facts and metric choices. No release version increment or Play upload has occurred.

Fresh TypeScript, lint/boundary and 66 Jest checks pass after initial integration. The schedule domain passes 27 host checks. Sanitized Snapchat Spotlight/next/Stories and Instagram photo layouts were captured from the connected API-36 phone. The earlier development APK was installed before upgrading to establish an existing database; updated build/device acceptance is in progress. A fixture or installed older APK does not accept the new UI, migration or collector. Playback defaults off; unavailable numerical signals stay unknown. Text posts, likes/reactions and comment outcomes remain unimplemented source qualification work.

Direct Drive backup was rechecked against official Google terms/policy on 2026-11-03 and remains blocked pending authoritative written clearance. Manual picker transport is distinct. The archive candidate rejects snapshots above 4 MiB / 100,000 records without truncation; wrong-key/corruption/cancellation/atomic-replacement checks and real picker flows must pass before acceptance.


## Latest continuation (2026-11-04)

ADR-073 replaces mandatory recovery-key UX with the maintainer-selected public package credential; files rely on storage-account security, not secret archive encryption. The maintainer accepts Android-managed opportunistic backup after verifying its roughly nightly, at-least-24-hour schedule. AS-058 adds off-by-default device-local consent, a protected-transport custom agent, single-file allowlists and quarantined restore review. Direct Drive API/OAuth remains blocked independently. No global backup account/transport setting is changed. The app-scoped request was accepted by the current Google transport; independent remote durability and reinstall recovery remain untested.

ADR-074/schema 9/logical format 2 preserve original per-fact IDs and installation origins through replacement and re-export; newly collected facts retain the destination origin. Merge/sync, deletion tombstones and a browser extension remain future work. The next required source trial is 50 actual feed posts with independent photo/video/ad classification and saved-count comparison, after backup checks. Record observed posts and mismatches rather than calling 50 swipes 50 qualified posts.

This resumed session confirms direct Accessibility entry, native one-app group creation, off-by-default period saving, activation-preview cancellation and cascade deletion of its task-owned group/period. Light/Violet was selected for native review. Earlier recovery-key export reached picker write/close success, but reopening that file was not verified before the user superseded that flow; it is not accepted recovery evidence. All new source remains experimental pending the current build/phone results and review. Main is untouched.


Backup verification now passes 47 isolated phone assertions and a successful app-scoped backup through the phone's existing Google transport. App automatic backup was explicitly enabled; global account/transport settings were unchanged. Actual fresh-device/reinstall recovery remains untested. Host types/lint/format, 66 Jest and 85 Python tests pass. Native preview build/lint, 41 encounter, 137 fixture and 27 schedule checks pass; all broader acceptance limits remain. See the dated log for exact scope.


Latest priority: Facebook photo qualification across varied shapes/formats, with as much authorized feed scrolling as needed; source-specific evidence must remain separate from Instagram. AS-059/ADR-075 adds a steady qualified Home/media HUD and one explicit Autoscroll control with drawn play/pause icons. Current source passes 56 encounter and 214 fixture host checks. Phone checks pass 47 isolated archive, 16 HUD choice/view-state and nine actual source-HUD interaction assertions; real play/pause icons were visually verified. Two Facebook runs completed 90 samples each, HUD visible throughout and idle delta zero, but independent review still finds multi-photo/loading fidelity gaps. Detector 13 repairs the observed 360-photo role and passes a targeted live reverse check; detector 14 additionally handles observed Photo-role button albums/image ads. The final 20-sample repaired-layout retest passes narrow independent visual/count comparison, idle-zero and HUD 20/20 with nine interactions; this does not accept every source layout. Prior failed sweeps are retained in the dated log; no universal accuracy or main-merge readiness is claimed.

See the [November 5 log](IMPLEMENTATION_LOG/2026-11-05.md) and [aggregate candidate evidence](evidence/beta-continuation.json) for historical failed trials, exact artifact hashes and final scope. Recorded photo/video additions are not accepted precision/recall. Instagram centered-carousel coverage remains unqualified independently of Facebook.

Final installed detector-14 artifacts pass 47 isolated archive and 16 HUD state assertions; 56 encounter/214 fixture host checks pass. The four-tile image ad and explicit 360-photo continuity are confirmed in the final scoped run. Private source captures are removed after review. Main/release remain unchanged; automatic backup is enabled only for the authorized debug app and fresh-device recovery remains a gate.

Reviewed implementation `ac426c9` is committed and pushed to origin/develop with remote hash verified. Main stays at `1fc5382`; no merge, release bump, Play upload or remote workflow run occurred. Source/artifact evidence is in the linked beta continuation record.
