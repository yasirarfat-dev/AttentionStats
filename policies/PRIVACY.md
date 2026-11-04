# Attention Stats privacy policy

Last updated: 4 November 2026 · App: **Attention Stats** (`attention.stats`), version 1.0.0 and development continuation · Contact: **consciousbiobot@gmail.com**

Attention Stats is a free, open-source digital wellbeing app created by Yasir Arfat ([yasirarfat.com](https://yasirarfat.com)) and published on Google Play by Convenience Labs. Its [source code](https://github.com/yasirarfat-dev/AttentionStats) is public, so anyone can verify everything below.

## The short version

- **Local by default.** No developer account, server, telemetry, advertising or automatic crash reporting. The app has no Internet permission. When available and explicitly chosen, manual export writes a file to your selected destination; that provider may upload it under its own policy. You can also opt into Android-managed automatic backup.
- **We never receive your history automatically.** Local history is encrypted. You control optional external archives and support messages.
- **You are in control.** Every feature is optional, and you can pause counting or delete everything at any time.

## What the app uses, and why

### Accessibility service (counting and optional protection)

When you turn it on, Attention Stats uses Android's Accessibility service **only while Instagram, Facebook, YouTube, TikTok or Snapchat is on screen**. It reads the layout of the screen and its on-screen labels (for example a video's caption or author name) **in memory** to:

- recognise when a short-video player (Reels, Shorts, TikTok, Spotlight) or a video in a feed is on screen; and
- distinguish settled video/photo encounters and returns. An encounter does not establish a watch. One carousel post is one encounter.

The labels are turned into an irreversible fingerprint in memory and then discarded. The app **does not save, record or send** what you watch, captions, names, messages, what you type, passwords, screenshots, audio or video. Optional numeric playback measurement, initially off, records advancing player time, three-second watches and observed completions where exposed; missing signals remain unknown. Text posts and social-action counts are not measured in this build.

Optional app schedules check only foreground package ownership for apps or groups you choose, never their screen content. A cover offers immediate recovery and a five-minute allowance; system navigation, Settings, launcher, keyboard and emergency/dialer access are excluded. Android may stop the service, so blocking is best effort.

The accessibility service also draws the optional floating counter, and, **only if you turn these features on**, it shows mindful-break screens, presses Back to leave a short-video player you chose to block, or swipes to the next video (auto-scroll). It never likes, comments, follows, messages or buys anything.

### Usage access (optional screen time)

If you allow Usage Access, the app reads Android's app-usage events (app package names and times) to show how long you spend in each app and how often you open it.

## What is stored on your device

- For each short video counted: time, app, whether it was in a full-screen player or a feed, its kind (video, photo or ad), how long it stayed on screen, whether you had already seen it in this unlock session, the app version, and a random key derived on your device so repeat views can be recognised. No caption, name or other content is stored.
- Time a recognized surface was on screen, separate optional numeric playback evidence and, if allowed, time and observed opens per app. Paused visibility is not automatically a watched video.
- Break and block events, and what you chose (for example "took a break").
- Your settings, consent choices and selected app-group/schedule configuration. Appearance and motion are device-local visual preferences.

This data is kept in an encrypted database in the app's private storage, protected by a key in the Android Keystore. The live database, device keys and preferences are excluded from Android backup and device transfer. If you explicitly enable Android-managed backup, only a bounded logical copy of your history and supported choices is offered to Android. Local history stays until you delete it.

## Optional backups

Backup files require no password to remember. They use the app's public package name as their key; anyone who obtains a file can read or modify it. Keep manual exports in private storage and protect your cloud account. The public app key is not secret encryption protection.

If you opt into automatic Android backup, Android uses the backup account/provider configured on your device. Android controls timing, typically roughly nightly when eligible, and may delay or skip runs. Our agent requires Android 9 or newer, a secure screen lock and a transport reporting client-side encryption; device-to-device transfer remains excluded. Android's transport protection is separate from the public archive key. The app does not read your backup-account email or authorize the Drive API. No data is sent to the developer.

Restored files are kept in private staging for review before replacement; tracking and protection remain off until you choose to enable them. Disabling backup stops future app participation. Deleting local history disables future system backup and removes pending restores but cannot erase files or older copies already held by your provider. Manual exports are chosen through Android's file picker. In-app daily/weekly scheduling and continuous cross-device synchronization are not implemented.

## Sharing

We do not automatically receive, sell or share your activity history. Enabled activity is recorded locally; optional backup transfers go to your chosen storage or Android backup provider. No advertising or developer analytics SDK receives it. If you tap a link (for example to this policy, GitHub or Google Play), your browser or that service handles it under its own policy.

If you contact us or open a GitHub issue, we receive only what you choose to send. The in-app "Report a problem" form pre-fills your phone model, Android version and app versions so we can fix detection; it never includes your history. You can edit it before sending.

## Your choices and deletion

- Turn counting on or off per app, pause everything, or hide the counter at any time.
- Turn off the accessibility service or usage access in Android settings at any time.
- **Settings → Delete all history** permanently removes every count, timer, event and setting. Uninstalling the app or clearing its storage also removes all data.

## Children

Attention Stats is intended for people aged 13 and over and is not directed at children. It does not provide medical advice.

## Changes

If the app's data handling ever changes, we will update this policy and ask for your agreement in the app before anything new is used.

Questions: **consciousbiobot@gmail.com** · [Data deletion](DATA-DELETION.md) · [Support](SUPPORT.md)
