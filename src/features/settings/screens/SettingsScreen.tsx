import { Linking } from 'react-native';
import { useState } from 'react';
import { Action } from '../../../ui/Action';
import { Destination, Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export type SettingsDestination =
  | 'Sources'
  | 'Storage'
  | 'Backup'
  | 'Appearance'
  | 'CounterAppearance'
  | 'Convenience'
  | 'About';
const destinations: {
  route: SettingsDestination;
  title: string;
  detail: string;
}[] = [
  {
    route: 'Sources',
    title: 'Sources & permissions',
    detail: 'Observation choices, access and service health',
  },
  {
    route: 'Storage',
    title: 'Local storage',
    detail: 'Saved history and deletion controls',
  },
  {
    route: 'Backup',
    title: 'Backup & recovery',
    detail: 'Portable encrypted archives',
  },
  {
    route: 'Appearance',
    title: 'App appearance',
    detail: 'Theme, type and accessible presentation',
  },
  {
    route: 'CounterAppearance',
    title: 'Floating counter',
    detail: 'Session/today display, position and appearance',
  },
  {
    route: 'Convenience',
    title: 'Convenience features',
    detail: 'Optional extras, separate from statistics',
  },
  {
    route: 'About',
    title: 'About Attention Stats',
    detail: 'Source code, licenses and this preview',
  },
];
export function SettingsScreen({
  onOpen,
  onPrivacy,
}: {
  onOpen: (route: SettingsDestination) => void;
  onPrivacy: () => void;
}) {
  return (
    <Screen>
      <Heading>Make it yours.</Heading>
      <Paragraph>Your choices, your history, your device.</Paragraph>
      <Panel title="Settings">
        {destinations.map(item => (
          <Destination
            key={item.route}
            title={item.title}
            detail={item.detail}
            onPress={() => onOpen(item.route)}
          />
        ))}
      </Panel>
      <Destination
        title="About your data"
        detail="What stays here and what is optional"
        onPress={onPrivacy}
      />
    </Screen>
  );
}
export function AboutScreen({
  onPrivacy,
  onWelcome,
}: {
  onPrivacy: () => void;
  onWelcome: () => void;
}) {
  const [error, setError] = useState(false);
  async function openSource() {
    try {
      await Linking.openURL('https://github.com/yasirarfat-dev/AttentionStats');
    } catch {
      setError(true);
    }
  }
  return (
    <Screen>
      <Heading>Attention Stats</Heading>
      <Paragraph>Open source. Local-first. Yours to inspect.</Paragraph>
      <Panel title="Developer preview">
        <Paragraph>
          The mobile shell and experimental Reels counter are available. Broader
          collection, history, protection and recovery features are still being
          connected. No full-product or independent security-audit claim.
        </Paragraph>
      </Panel>
      <Panel title="Open by design">
        <Paragraph>
          Apache License 2.0. No advertising, developer analytics or app
          backend. Optional external transfers will always need their own
          choice.
        </Paragraph>
        <Action label="View source code" onPress={openSource} variant="tonal" />
        {error && (
          <Paragraph>
            The source link could not be opened. You can find AttentionStats
            under yasirarfat-dev on GitHub.
          </Paragraph>
        )}
        <Action
          label="Read the introduction"
          onPress={onWelcome}
          variant="text"
        />
        <Action label="About your data" onPress={onPrivacy} variant="text" />
      </Panel>
    </Screen>
  );
}
