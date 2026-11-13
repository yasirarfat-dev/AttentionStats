import type { SettingExplanation } from './SettingsGroup';

export const settingsHelp = {
  counting: [
    {
      term: 'Counting Sources',
      detail:
        'Choose the apps whose short-video encounters you want to record. Turning a source on still asks for your agreement. Ready, Off and Last Seen describe its current state.',
    },
    {
      term: 'Pause All Counting',
      detail:
        'Stops recording until you resume. Your saved history stays in place.',
    },
    {
      term: 'Count Ads as Videos',
      detail:
        'Includes recognized video ads in the video total. Ads remain a separate tally and can overlap media counts.',
    },
  ],
  permissions: [
    {
      term: 'Accessibility',
      detail:
        'Recognizes supported short-video screens and provides chosen protection controls. A permission grant and your source choices are separate. A disconnected service warning stays visible in its row.',
    },
    {
      term: 'Usage Access',
      detail:
        'Provides recorded whole-app time and observed app entries. It does not prove playback or attention. The badge shows whether usage measurement and Android access are both on.',
    },
    {
      term: 'Battery',
      detail:
        'Some phones stop background helpers. If counting stops, open the app’s battery settings and consider Unrestricted. This does not grant source access.',
    },
  ],
  measurement: [
    {
      term: 'Photo Encounters',
      detail:
        'Counts recognized photos and carousels separately in enabled sources. One carousel post is one encounter.',
    },
    {
      term: 'Confirmed Playback',
      detail:
        'Experimental and optional. Uses exposed numeric player times for 3-second watches, playback time and observed completions. Pauses and missing signals add no time; availability varies by app and layout.',
    },
    {
      term: 'Auto-scroll',
      detail:
        'Experimental and optional. Adds a control to the floating counter to move to the next video when readable progress confirms the end. Missing progress suspends automatic swipes. Use the same control to stop.',
    },
    {
      term: 'Home-screen Widgets',
      detail:
        'Preview five widgets, then choose one to add through your launcher. Long-press a placed widget to customize its layout, stats, order, colors, transparency and appearance.',
    },
    {
      term: 'Not Measured',
      detail:
        'Text posts, likes, reactions and submitted comments are not measured.',
    },
  ],
  privacy: [
    {
      term: 'Local History',
      detail:
        'History stays encrypted on this phone. No data is sent to the developer.',
    },
    {
      term: 'Android Backup',
      detail:
        'Android-managed backup starts enabled, while saved off choices stay off. Android can transfer history to your device backup account. Open Backup & Restore for its current controls and recovery.',
    },
    {
      term: 'Manual Files',
      detail:
        'Manual archives use a public app key. Anyone holding a file and the app key can open it; it is not a personal password.',
    },
    {
      term: 'Erase History',
      detail:
        'Permanent local deletion requires three confirmations and turns automatic backup off. Copies already outside the app may remain.',
    },
    {
      term: 'Privacy & Source',
      detail:
        'Read the privacy policy or inspect the Apache-2.0 source on GitHub. These links open outside the app.',
    },
  ],
  appearance: [
    {
      term: 'Theme',
      detail: 'Choose Light, Dark or follow the phone’s System appearance.',
    },
    {
      term: 'Accent Color',
      detail:
        'Choose Graphite, Blue, Teal or Violet. This changes presentation without changing your data or rules.',
    },
    {
      term: 'Gentle Animations',
      detail:
        'Adds soft tab transitions. The phone’s reduced-motion setting takes priority.',
    },
  ],
  help: [
    {
      term: 'Report a Problem',
      detail:
        'Choose Email or a GitHub issue after review. The draft includes phone model and app versions, never your history or what you watched.',
    },
    {
      term: 'Share & Rate',
      detail:
        'Sharing opens your phone’s share sheet. Rating opens Google Play. Both are optional.',
    },
    {
      term: 'Welcome Tour',
      detail:
        'Reopens the introduction. Source and permission choices stay under your control.',
    },
  ],
} satisfies Record<string, SettingExplanation[]>;
