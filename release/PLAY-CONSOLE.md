# Attention Stats — Google Play Console information

Prepared 29 October 2026. **Draft for the 0.1.0 closed-test candidate, not a submitted listing.** Permanent package name: **attention.stats**. Keep this source under review and copy the current version to `C:\home\biobot\AppReleases\AttentionStats\PLAY-CONSOLE.md`. No Play account access, policy approval, uploaded bundle or testing start is implied.

## Current implementation warning — 29 October 2026

This draft describes the current experimental MVP scope and must be checked against the exact qualified release artifact before submission. Four-source route fidelity, lifecycle and source-specific auto-scroll acceptance remain open. No Play approval or release readiness is claimed.

AS-050/AS-051 add separately consented Usage Access timing, Reels occupancy, optional reminders/blocking, unlock-session counts and recognized-ad choices. The source declares PACKAGE_USAGE_STATS and accessibility gesture capability. The authorized progress-bar-pixel investigation is test-only; production capture is absent. If a production fallback is admitted, update the disclosure, policy, declaration and demo before release. Local release signing is not provisioned. No uploaded AAB or Console-state verification is implied.

## Create app

| Field | Value / action |
|---|---|
| App name | Attention Stats |
| Default language | English (United States), en-US |
| App or game | App |
| Free or paid | Free; no subscription, paywall or payment features |
| Package name | attention.stats — exactly these two lowercase words and one dot |
| Developer name | Pending maintainer's public name; must match the Console account and policy |
| Declarations | Account owner must read and accept Play policies, export-law and App Signing terms in Console |

## Provide app information and create your store listing

The dashboard labels and ordering can vary. These are prepared answers, with owner-only and artifact-dependent fields clearly marked.

### App access

Attention Stats has no sign-in, registration, subscription, payment or reviewer credentials. Its own screens can be opened without a grant. Observation requires a voluntary source choice and Android accessibility access. To exercise the cross-app detector, a reviewer needs a separately installed, supported Instagram version and their own access to that service. Do not supply the maintainer's personal social account. If Console asks whether any functionality is restricted, disclose this external-app dependency and provide the steps below rather than claiming unrestricted access to every function.

Reviewer instructions: Complete or skip the introduction. In Settings → Reels sources & access, review each separate source notice and choose supported installed sources. Follow Android Accessibility → Downloaded apps/Installed services → Attention Stats → Use service, then return and check actual connection. The four version-pinned candidates are Instagram 452.1.0.54.64, Facebook 584.0.0.51.56, YouTube 21.40.165 and TikTok 47.5.3. Unsupported versions stay unavailable. Independently enable the floating counter if desired. Open a dedicated Reels/Shorts viewer; the first qualified encounter should appear before swiping. Encounters include return visits and are separate from unique videos or completed plays. Home-feed support is independently qualified for Facebook/Instagram; see the final artifact's source matrix.

For app time, use Settings → Whole-app time, accept its separate notice and choose Attention Stats in Android Usage Access. Today/History show measured app intervals separately from estimated Reels-surface occupancy. Disable access to verify that new collection stops. Optional reminder thresholds and per-source Reels blocking default off. Blocking covers only recognized dedicated viewers, offers immediate Allow/Open app, and can leave audio playing. For optional auto-scroll, review and Allow it in the app, open a supported dedicated viewer, tap the HUD to expand its menu and press Start. Stop stays visible while active. Missing progress/context stops it; no Home-feed automation. Pause, source off, session HUD hiding and local deletion remain available. No social credentials are provided by the developer.

### Ads

Select **No, my app does not contain ads** for this candidate. Observing a recognized ad layout inside another independently used app does not insert an ad into Attention Stats. No advertising SDK, paid placement or in-app advertising exists.

### Content rating

Complete the live IARC questionnaire truthfully; do not invent the resulting age rating. Suggested questionnaire facts: general personal utility; no game, in-app user-generated feed, chat, gambling, purchases, location sharing, web browser or content delivery. The app does not display or capture the videos/photos in other apps. If a question covers interaction with external services, disclose the selected-app observation accurately. Contact email: **consciousbiobot@gmail.com** (maintainer may replace it).

### Target audience and content

Proposed initial scope: **18 and over**, not directed at children, no Families enrollment and no child-appealing marketing. This is a release positioning choice, not an IARC rating or a claim to verify users' ages. Confirm that this matches the actual intended audience before submission. No diagnosis, treatment, clinical claim or medical-device purpose. Complete any health-app questionnaire against these facts and its live category definitions; do not silently classify a general wellbeing utility as medical or claim a health exemption.

### Privacy policy

Public policy URL after repository publication:

https://github.com/yasirarfat-dev/AttentionStats/blob/main/policies/PRIVACY.md

Support: https://github.com/yasirarfat-dev/AttentionStats/blob/main/policies/SUPPORT.md

Local-data deletion: https://github.com/yasirarfat-dev/AttentionStats/blob/main/policies/DATA-DELETION.md

These are publicly rendered documents in the GitHub repository, not PDFs or editable public forms. Verify signed-out access and policy accuracy before submission. GitHub Pages is an optional later presentation; no Pages deployment or automatic workflow is enabled. The policy is also linked from the app's Privacy screen. Do not link the internal engineering privacy specification as the user policy.

### Data safety

Draft conditional on audit of the **exact release AAB and all active tracks**: no user data transmitted off device by Attention Stats or its SDKs, no sharing, no account creation. Google defines collection for this form as transmission off-device; locally accessed/recorded observations must still be fully disclosed in the privacy policy and accessibility notice. Candidate answer to collection/sharing screening: **No**, only after confirming the no-Internet merged manifest, absence of outbound SDK behavior/automatic reports, and any changed active-track artifacts. Do not infer this from synthetic tests alone. Do not advertise “zero data collection.”

There is no server account to delete; describe local deletion using the policy above. Do not claim an independent security review, an encryption-in-transit certification, a deletion-request backend or account-deletion URL requirement exemption for future account features. Answer follow-up questions only if the live form presents them, using the exact release behavior.

### AccessibilityService declaration

Declare **uses AccessibilityService** and **not an accessibility tool** (`isAccessibilityTool=false`). Purposes: user-selected local Reels encounter/occupancy statistics, optional in-context display, explicitly enabled reminders/viewer blocking and optional deterministic completion-triggered scrolling. No advertisements, account management, remote analytics or autonomous planning. The service declares `canPerformGestures=true` for a single revalidated upward swipe after separate Allow and Start.

Suggested explanation: After separate in-app affirmative notices and Android accessibility access, Attention Stats inspects bounded identifiers, roles, visibility and numeric positions in selected supported apps. It stores encounter/category counts and estimated surface occupancy in encrypted local storage. Facebook descriptions are compared only with fixed interface labels. With its source notice accepted, TikTok visible title/caption fields are processed transiently using memory-only keyed comparisons; raw fields and matching tokens are never saved or uploaded. Messages, typed input, editable/password contexts and unsupported layouts are excluded. Separately enabled reminders and dedicated-viewer covers remain immediately dismissible. Optional auto-scroll uses numeric playback evidence and one bounded upward gesture after session Allow and viewer Start; Stop is visible and context/access loss ends the run. No observation data is sent to a server.

The declaration's data question links to Google's off-device collection definition. Answer using the exact artifact and current form; **do not conceal local sensitive-data access** when off-device collection is No. Provide a reviewer video showing first launch, separate notices/choices, Android accessibility and Usage Access setup, first-encounter counter, Today/History, optional reminder/block/bypass, auto-scroll Allow/Start/Stop, pause and revocation. **Video pending.** Use non-personal test content with permission; do not film the user's personal social content. Approval is pending. Update the declaration if capture or another API use changes.

### Other app-content declarations

[The full permission plan](PERMISSION-PLAN.md) maps every known future access need to its actual feature gate. Current-build permissions alone are declared/requested; later implemented features can update the manifest and Console forms. Requesting sensitive access for unimplemented features violates [Google's current-feature requirement](https://support.google.com/googleplay/android-developer/answer/9888170). The initial candidate does not request speculative future grants.

No news publishing, government affiliation, financial services, lending, cryptocurrency, VPN, foreground service, exact alarms, QUERY_ALL_PACKAGES, photo/video-library permission or advertising ID is present. Mark not applicable/No only for the actual question. Package visibility comprises four named counter targets plus the launcher-app query used to label app-time rows. Android accessibility binding is separate from a runtime draw-over-apps grant. Recheck Console's current declarations before submission.

## Main store listing — candidate copy pending qualification

**App name** (15 / 30):

```text
Attention Stats
```

**Short description** (maximum 80 characters):

```text
Understand your app time and Reels habits. Free, open source, on your device.
```

**Full description** (maximum 4,000 characters; validate with release tooling):

```text
Notice how you spend time with short videos, one deliberate choice at a time.

Attention Stats is a free, open-source digital wellbeing app. This early testing version keeps your usage history on your device and gives you optional ways to interrupt endless scrolling.

WHAT YOU CAN TRY
• See app usage time and observed opens, Reels encounters and separate Reels-surface time on Today and History.
• Compare per-app figures for supported Instagram Reels, Facebook Reels, YouTube Shorts and TikTok versions.
• Choose a compact floating counter. Its session count follows the observed unlocked-phone interval across selected apps.
• Choose whether recognized ads contribute to Reels totals; separate recognized-ad counts remain available.
• Set optional count/time reminders or cover selected dedicated Reels viewers, with an immediate bypass.
• Start optional auto-scroll in qualified dedicated viewers, with a visible Stop control.
• Pause tracking, turn sources off, hide the counter for an app visit or delete local history.

YOUR CHOICE, YOUR DEVICE
Reels observation requires a separate in-app disclosure, your source choice and Android accessibility access. Whole-app timing independently requires its notice and Android Usage Access. AccessibilityService inspects bounded layout structure and numeric positions. Facebook fixed interface labels and separately disclosed, transient TikTok title/caption matching help recognize surfaces or transitions. Raw matching fields are not saved. Messages and typed input are excluded.

Reminders, Reels blocking and auto-scroll are optional. Auto-scroll requires Allow in the app and Start in the viewer; it uses playback progress to request one upward swipe at a time. It stops when progress or the viewing context becomes unavailable. Blocking covers the viewer rather than stopping playback, so audio can continue.

There is no Attention Stats account, advertising, remote analytics or automatic history upload. Observation records are stored in an encrypted local database. Export and recovery are unavailable in this testing version; clearing app storage or uninstalling can permanently remove history.

KNOW THE LIMITS
Counts estimate settled encounters, including return visits. They are not unique videos or completed views. Reels time estimates time on recognized surfaces, not uninterrupted playback. App time estimates observed foreground intervals. Gaps, layout/ad recognition and installed app versions can affect results; unknown coverage is shown as unavailable. Home-feed recognition is separate from dedicated-viewer support. Auto-scroll is limited to qualified dedicated viewers.

Patterns are being built. Per-app budgets/locking, individual weekly drill-down, backup and restore are not included. This testing version is intended for adults and provides no medical advice or diagnosis. Every included feature is free.

Attention Stats is independent of the social platforms named here and is not affiliated with or endorsed by them.
```

This copy targets the agreed MVP. Do not submit it until each advertised feature has exact-release evidence. In particular, four-source route qualification and completion-triggered scrolling remain release blockers. Any admitted pixel fallback requires an accurate disclosure and copy update before submission.

## Store settings and graphics

| Field | Prepared value / outstanding item |
|---|---|
| Category | Proposed: Tools; confirm against the live category list |
| Tags | Select only tags offered by Console that match the actual utility; no invented tags |
| Support email | consciousbiobot@gmail.com; maintainer may replace it |
| Support website | https://github.com/yasirarfat-dev/AttentionStats/blob/main/policies/SUPPORT.md |
| Phone | Optional unless the developer account's own verification rules require it; do not publish a personal number without instruction |
| External marketing | Proposed off for the early closed test |
| App icon | 512 × 512, 32-bit PNG, at most 1,024 KB; final original branded asset pending |
| Feature graphic | 1,024 × 500, JPEG or 24-bit PNG without alpha; final asset pending |
| Phone screenshots | At least two, JPEG or 24-bit PNG without alpha; 320–3,840 px, longest edge at most twice the shortest; current own-window captures are private qualification evidence, not approved marketing assets |
| Tablet/other form factors | No support claim until qualified; provide required assets for every supported form factor shown by Console |
| Preview video | Optional marketing video; separate accessibility demonstration remains pending |

Never publish personal totals or target-app screenshots. Use truthful app-only views with clearly disclosed synthetic sample data if needed; do not present unimplemented features as working.

## First closed-test release

| Field | Value / action |
|---|---|
| Track | Closed testing; suggested track name: early-access |
| Release name | 0.1.0 (1) — wellbeing MVP closed test |
| versionName / versionCode | 0.1.0 / 2; code 1 remains in the historical unsigned archive; verify Console has never used code 2 before upload |
| Bundle package | attention.stats; no suffix or additional words |
| Architecture | Initial locally built candidate is ARM64 only; do not claim other ABIs/minimum-API qualification |
| Play App Signing | Proposed: let Google generate/hold the app-signing key; maintainer controls a distinct upload key and encrypted backup |
| Upload key / signing | Not provisioned yet; no development-signed APK is a Play candidate |
| Countries | Maintainer chooses actual tester regions in Console; no country invented |
| Testers | Private Console email list or Google Group; never commit tester identities or invitations |
| Opt-in URL | PENDING — copy actual Console-provided URL after track setup; do not invent it |
| Testing start | PENDING — record actual rollout and continuous tester opt-in eligibility, not document/build date |

Proposed release notes (under 500 characters):

```text
Closed-test MVP: local app-time and Reels statistics, Today/History, optional floating counter, recognized-ad choices, reminders, dedicated-Reels blocking and supported-viewer auto-scroll. Features require separate choices and supported app versions. Counts include return encounters; surface time is not completed playback. Please report reproducible issues without personal history or content.
```

## What still blocks upload / rollout

- Final developer identity matching the Play account; support/privacy email supplied as consciousbiobot@gmail.com.
- Reviewed signed release AAB, exact manifest/bundle/provenance/license and native-alignment audit; upload-key ownership and recovery backup.
- Release-package physical-phone smoke/consent/delete/revoke/lifecycle checks, dependency/egress audit and unresolved applicable security/accessibility gates.
- Qualified Facebook, Instagram, YouTube Shorts and TikTok adapters and independent phone trials: ADR-048 supersedes the earlier two-app target. All four counters are required for the initial counter MVP before broader diary/protection expansion. The historical listing draft above must be rewritten for the actual qualified candidate before submission.
- Final icon, feature graphic, safe screenshots, accessibility reviewer video and accurate live questionnaires.
- Play account enrollment/verification, actual Console access, App Signing enrollment, accessibility review, track/country/tester setup and real rollout.

For newer personal accounts created after 13 November 2023, at least 12 testers must remain opted in continuously for at least 14 days before applying for production access. Account applicability and eligibility come from Console. Sideloads, preparing a listing, internal testing and a local timer do not start or satisfy that requirement. Continue fixing and testing during the closed test; production access requires a separate application and review. Do not fabricate engagement or feedback.

## Release folders

Source/build workspace stays `C:\home\biobot\projects\AttentionStats`; do not create a second editable app copy. Android/Gradle intermediate output stays there. Archive reviewed release artifacts under `C:\home\biobot\AppReleases\AttentionStats`:

```
AttentionStats/
  PLAY-CONSOLE.md
  README.md
  store/en-US/                 # versioned copy, graphics, safe screenshots
  policies/                    # copies of reviewed public policies
  releases/0.1.0+1/
    android/                   # reviewed AAB/APK; unsigned clearly named
    metadata/                  # checksums, provenance, manifest, checks
    notes/                     # candidate notes and known limitations
  testing/                     # private feedback and real test-period log
  private/signing/             # private upload material, never Git/public artifacts
```

Use a new `versionName+versionCode` directory for each future candidate; never overwrite an already archived release. No source duplication, credentials, private captures or personal data in the Git repository. Any unsigned investigation build is explicitly marked **not uploadable**.

## Official references checked 28 October 2026

- [Create app and listing fields](https://support.google.com/googleplay/android-developer/answer/113469)
- [Listing graphic requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Privacy/User Data](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Data safety definitions and testing-track coverage](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Accessibility declaration and disclosure](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Personal-account closed testing](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Target API requirement](https://support.google.com/googleplay/android-developer/answer/11926878): fetched guidance requires API 36+ for new phone apps from 31 August 2026; current source targets API 37. Recheck at upload.
- [App Signing](https://developer.android.com/studio/publish/app-signing)

Console fields and policy can change. This document prepares truthful content; it is not evidence of Google approval or a completed submission.

Maintainer update (2026-10-29): the owner reports that the Play Console app already exists with package **attention.stats**. Reuse that app; do not create a replacement package/listing. This report does not verify account eligibility, signing/upload access, tester opt-ins, policy review or a started testing period. The counter MVP is not yet ready: route/post-type and four-source fidelity, exact release runtime, signing/assets/declarations and applicable testing gates remain.
