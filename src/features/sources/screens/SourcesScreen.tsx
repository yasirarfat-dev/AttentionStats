import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import {
  NativeReels,
  parseReelsSnapshot,
  type ReelsSnapshot,
} from '../../../platform';
import { Action } from '../../../ui/Action';
import { Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function SourcesScreen({ onReels }: { onReels: () => void }) {
  const [snapshot, setSnapshot] = useState<ReelsSnapshot | null>(null);
  const focused = useIsFocused();
  useEffect(() => {
    if (!focused) return;
    let active = true;
    async function read() {
      try {
        if (!NativeReels) return;
        const value = parseReelsSnapshot(await NativeReels.getSnapshot());
        if (active) setSnapshot(value);
      } catch {
        if (active) setSnapshot(null);
      }
    }
    read();
    const listener = AppState.addEventListener('change', state => {
      if (state === 'active') read();
    });
    return () => {
      active = false;
      listener.remove();
    };
  }, [focused]);
  return (
    <Screen>
      <Heading>Know what is observing.</Heading>
      <Paragraph>
        Android access, your observation choice and a working source are
        separate.
      </Paragraph>
      <Panel title="Reels source health" tinted>
        <Paragraph>
          {!snapshot
            ? 'Source health is unavailable.'
            : snapshot.storage === 'ready'
              ? 'Local storage is ready.'
              : 'Local storage is unavailable; saved history is preserved.'}
        </Paragraph>
        <Paragraph>
          {!snapshot
            ? 'Accessibility state is unknown.'
            : snapshot.serviceConnected && snapshot.accessibilityEnabled
              ? 'Accessibility is connected.'
              : snapshot.accessibilitySelected
                ? 'Accessibility is selected, but the service is disconnected.'
                : 'Accessibility is off.'}
        </Paragraph>
        {snapshot?.sources.map(source => (
          <Paragraph key={source.app}>
            {source.app === 'instagram' ? 'Instagram' : 'Facebook'}:{' '}
            {!source.enabled
              ? 'observation off'
              : !source.consentCurrent
                ? 'updated observation choice needed'
                : source.paused
                  ? 'paused'
                  : 'observation enabled'}{' '}
            ·{' '}
            {source.supported
              ? 'experimental viewer adapter'
              : 'counting unavailable'}
            .
          </Paragraph>
        ))}
        <Action label="Manage Reels sources" onPress={onReels} />
      </Panel>
      <Panel title="Other sources">
        <Paragraph>
          Whole-app time, notifications and network statistics are not active in
          this preview. Each will have its own explanation, source choice and
          supported access flow. No additional access is requested here.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
