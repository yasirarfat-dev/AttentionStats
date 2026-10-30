# Attention Stats privacy policy

Last updated: 30 October 2026 · App: **Attention Stats** (`attention.stats`), version 1.0.0 and later · Contact: **consciousbiobot@gmail.com**

Attention Stats is a free, open-source digital wellbeing app. Its [source code](https://github.com/yasirarfat-dev/AttentionStats) is public, so anyone can verify everything below.

## The short version

- **Nothing leaves your phone.** There is no account, no server, no analytics, no ads and no crash reporting. The app does not request the Internet permission, so it cannot send data anywhere.
- **We never see your data.** Your history is stored only on your device, encrypted.
- **You are in control.** Every feature is optional, and you can pause counting or delete everything at any time.

## What the app uses, and why

### Accessibility service (short-video counting)

When you turn it on, Attention Stats uses Android's Accessibility service **only while Instagram, Facebook, YouTube, TikTok or Snapchat is on screen**. It reads the layout of the screen and its on-screen labels (for example a video's caption or author name) **in memory** to:

- recognise when a short-video player (Reels, Shorts, TikTok, Spotlight) or a video in a feed is on screen; and
- tell one video from the next, so each video is counted once.

The labels are turned into an irreversible fingerprint in memory and then discarded. The app **does not save, record or send** what you watch, captions, names, messages, what you type, passwords, screenshots, audio or video. It does not read any other app.

The accessibility service also draws the optional floating counter, and, **only if you turn these features on**, it shows mindful-break screens, presses Back to leave a short-video player you chose to block, or swipes to the next video (auto-scroll). It never likes, comments, follows, messages or buys anything.

### Usage access (optional screen time)

If you allow Usage Access, the app reads Android's app-usage events (app package names and times) to show how long you spend in each app and how often you open it.

## What is stored on your device

- For each short video counted: time, app, whether it was in a full-screen player or a feed, its kind (video, photo or ad), how long it stayed on screen, whether you had already seen it in this unlock session, the app version, and a random key derived on your device so repeat views can be recognised. No caption, name or other content is stored.
- Time spent in short videos and, if allowed, time and opens per app.
- Break and block events, and what you chose (for example "took a break").
- Your settings and consent choices.

This data is kept in an encrypted database in the app's private storage, protected by a key in the Android Keystore. It is excluded from Android cloud backup and device transfer. It stays until you delete it.

## Sharing

We do not collect, sell or share any personal data. No third-party SDKs receive data. If you tap a link (for example to this policy, GitHub or Google Play), your browser or that service handles it under its own policy.

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
