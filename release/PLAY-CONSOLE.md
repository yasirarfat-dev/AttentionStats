# Attention Stats — Google Play Console information

Prepared 28 October 2026. **Draft for the 0.1.0 closed-test candidate, not a submitted listing.** Permanent package name: **attention.stats**. Keep this source under review and copy the current version to `C:\home\biobot\AppReleases\AttentionStats\PLAY-CONSOLE.md`. No Play account access, policy approval, uploaded bundle or testing start is implied.

## Create app

| Field | Value / action |
|---|---|
| App name | Attention Stats |
| Default language | English (United States), en-US |
| App or game | App |
| Free or paid | Free for this closed-test candidate; confirm pricing before creation |
| Package name | attention.stats — exactly these two lowercase words and one dot |
| Developer name | Pending maintainer's public name; must match the Console account and policy |
| Declarations | Account owner must read and accept Play policies, export-law and App Signing terms in Console |

## Provide app information and create your store listing

The dashboard labels and ordering can vary. These are prepared answers, with owner-only and artifact-dependent fields clearly marked.

### App access

Attention Stats has no sign-in, registration, subscription, payment or reviewer credentials. Its own screens can be opened without a grant. Observation requires a voluntary source choice and Android accessibility access. To exercise the cross-app detector, a reviewer needs a separately installed, supported Instagram version and their own access to that service. Do not supply the maintainer's personal social account. If Console asks whether any functionality is restricted, disclose this external-app dependency and provide the steps below rather than claiming unrestricted access to every function.

Reviewer instructions: Open Attention Stats → Reels & short videos. Read the separate observation disclosure and enable Instagram if desired. Enable Attention Stats in Android accessibility settings, then independently enable the live counter. Open Instagram's dedicated Reels viewer. Saved totals represent settled page exposures including return visits; loops/redraws should not increment. Home feed and unsupported target versions are unavailable. Facebook is not qualified. Pause, source off and Delete Reels history are available in the Reels screen. The app has no automated accessibility actions.

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

Declare **uses AccessibilityService** and **not an accessibility tool** (`isAccessibilityTool=false`). Purpose: app functionality, specifically the user-selected local Reels exposure counter and optional in-context display. No advertisements, marketing, account management, remote analytics, autonomous actions or accessibility gestures.

Suggested explanation: “After a separate in-app affirmative disclosure and a user-selected Android accessibility grant, Attention Stats inspects bounded layout identifiers, roles, visibility and numeric page positions for selected supported apps. It estimates settled viewer-page exposures and saves counts, times, categories and coverage in encrypted local storage. For Facebook it transiently compares accessibility descriptions with fixed interface labels and immediately discards nonmatches; raw descriptions are never saved. It does not read node text, images or video, perform clicks/scrolls, or send observation history to a server. It retains no messages or typed input. Users can pause, revoke access or delete history. Unsupported versions and contexts remain unavailable.”

The declaration's data question links to Google's off-device collection definition. Answer it using the exact artifact and current form; **do not conceal local sensitive-data access** even if an off-device collection answer is No. Provide a reviewer video showing first launch → separate disclosure and affirmative choice → Android accessibility enablement → counter use → pause/revocation. **Video pending.** Use a dedicated non-personal test account/content with permission; do not record the user's personal social content. Approval is pending and not guaranteed. Update the declaration if API use changes.

### Other app-content declarations

[The full permission plan](PERMISSION-PLAN.md) maps every known future access need to its actual feature gate. Current-build permissions alone are declared/requested; later implemented features can update the manifest and Console forms. Requesting sensitive access for unimplemented features violates [Google's current-feature requirement](https://support.google.com/googleplay/android-developer/answer/9888170). The initial candidate does not request speculative future grants.

No news publishing, government affiliation, financial services, lending, cryptocurrency, VPN, foreground service, exact alarms, broad package inventory, photo/video-library permission or advertising ID is present in this candidate. Mark not applicable/No only when that is the actual question; do not pre-answer unrelated questionnaires. Package visibility is limited to the four named counter targets. Android accessibility binding is separate from a runtime “draw over apps” grant; do not request unrelated grants. Recheck Console's current required declarations before submission.

## Main store listing — ready to paste

**App name** (15 / 30):

```text
Attention Stats
```

**Short description** (69 / 80):

```text
Understand your Reels viewing with private, on-device exposure counts
```

**Full description** (maximum 4,000 characters; validate with release tooling):

```text
Notice how you spend time with short videos, one deliberate choice at a time.

Attention Stats is an open-source personal digital wellbeing app. This early testing version estimates settled page exposures in supported Instagram Reels viewers and keeps your saved history on your device.

WHAT YOU CAN TRY
• See saved exposure totals and recognized video, photo/carousel, image-ad and unclassified layout counts.
• Choose an optional compact counter while you use a supported Reels viewer.
• Pause observation, turn a source off, hide the counter independently or delete your saved Reels history.

YOUR CHOICE, YOUR DEVICE
Observation starts only after a separate in-app disclosure, your source choice and Android accessibility access. Attention Stats uses the AccessibilityService API to inspect bounded layout identifiers, roles and numeric page positions for supported selected apps. It does not read screen text, messages, typed input, titles, creators, photos or video, and it does not click or scroll for you.

There is no Attention Stats account, advertising, remote analytics or automatic history upload. Observation records are stored in an encrypted local database. Export and recovery are unavailable in this testing version; clearing app storage or uninstalling can permanently remove history.

KNOW THE LIMITS
Counts estimate settled viewer-page exposures, including return visits. They are not unique videos, completed views or watch-time measurements. Layout and ad recognition can be incomplete. Support depends on the installed target-app version. Unsupported coverage is shown as unavailable.

Facebook counting and normal Home-feed counting are not available yet. Broader time tracking, protection, predictions, backup and restore remain unavailable. This testing version is intended for adults and provides no medical advice or diagnosis.

Attention Stats is independent of Instagram, Facebook and Meta. It is not affiliated with or endorsed by them.
```

Do not add Facebook support to this listing until its independent detector and real trials pass. Revisit this copy for each candidate; the future full app's features are not current store claims.

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
| Release name | 0.1.0 (1) — early counter candidate |
| versionName / versionCode | 0.1.0 / 1; reserve code 1 locally, verify Console has never used it before upload |
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
Early counter test: optional local Instagram Reels page-exposure counts, saved category totals, pause/delete controls and a compact live display. Support depends on the installed Instagram version. Return visits count again; unique videos and watch time are unavailable. Facebook and Home feeds are not supported yet. Please report reproducible issues without personal history or content.
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
