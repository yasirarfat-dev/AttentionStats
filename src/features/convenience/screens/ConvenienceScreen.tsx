import { useCallback, useState } from 'react';
import { Alert, Switch } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import {
  NativeReels,
  parseAutoScrollStatus,
  reelsSources,
  type AutoScrollStatus,
} from '../../../platform';
import { Action } from '../../../ui/Action';
import { Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';
export function ConvenienceScreen({ onPrivacy }: { onPrivacy: () => void }) {
  const [status, setStatus] = useState<AutoScrollStatus | null>(null);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  useFocusEffect(
    useCallback(() => {
      let alive = true;
      async function refresh() {
        try {
          if (!NativeReels) throw new Error('Unavailable');
          const next = parseAutoScrollStatus(
            await NativeReels.getAutoScrollSnapshot(),
          );
          if (alive) {
            setStatus(next);
            setError(false);
          }
        } catch {
          if (alive) {
            setStatus(null);
            setError(true);
          }
        }
      }
      refresh();
      const timer = setInterval(refresh, 1_000);
      return () => {
        alive = false;
        clearInterval(timer);
      };
    }, []),
  );
  async function configure(enabled: boolean) {
    if (!NativeReels || busy) return;
    setBusy(true);
    try {
      setStatus(
        parseAutoScrollStatus(
          await NativeReels.configureAutoScroll(enabled, 1),
        ),
      );
      setError(false);
    } catch {
      setError(true);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Screen>
      <Heading>Extras, only if you want them.</Heading>
      <Paragraph>
        Convenience features are separate from attention statistics.
      </Paragraph>
      <Panel title="Auto-scroll at video end">
        <Switch
          value={status?.enabled ?? false}
          disabled={!status || busy}
          accessibilityLabel="Allow experimental auto-scroll this app session"
          onValueChange={enabled =>
            enabled
              ? Alert.alert(
                  'Allow finish-and-swipe controls?',
                  'This separate convenience feature reads numeric playback progress in supported full-screen viewers. After you press Start auto-scroll on the floating counter, it can perform an upward swipe after an observed playback loop. It waits for a complete observable cycle, not a timer. Missing or ambiguous progress stops it. Start requires observation and the floating counter to be enabled separately. Stop remains visible, even when the counter is collapsed. Leaving the viewer, locking the device, pausing observation or hiding the counter stops further swipes. A swipe already dispatched may finish. This choice lasts only until the app service restarts; it does not enable any source or upload data.',
                  [
                    { text: 'Cancel', style: 'cancel' },
                    { text: 'Allow controls', onPress: () => configure(true) },
                  ],
                )
              : configure(false)
          }
        />
        <Paragraph>
          {status?.running
            ? 'Running. Stop is always available on the floating counter.'
            : status?.enabled
              ? 'Controls allowed for this app session. Press Start on the floating counter inside a supported viewer.'
              : 'Off. Allowing controls never starts scrolling. Your statistics work independently.'}
        </Paragraph>
        <Paragraph>
          Experimental: waits for a full observable playback cycle and advances
          after the video loops. Unknown layouts or missing progress stay
          unavailable. Photos, live streams and every app version are not
          supported.
        </Paragraph>
        {status?.sources.map(source => (
          <Paragraph key={source.app}>
            {reelsSources[source.app].name}:{' '}
            {source.candidate
              ? 'experimental playback candidate'
              : 'video-end signal not qualified — unavailable'}
          </Paragraph>
        ))}
        {status?.running && (
          <Action
            label="Stop auto-scroll"
            onPress={async () => {
              try {
                if (NativeReels)
                  setStatus(
                    parseAutoScrollStatus(await NativeReels.stopAutoScroll()),
                  );
              } catch {
                setError(true);
              }
            }}
          />
        )}
        {error && (
          <Paragraph>
            Auto-scroll controls could not be confirmed. Open the app again or
            check accessibility access. No successful change is assumed.
          </Paragraph>
        )}
      </Panel>
      <Action label="About your data" onPress={onPrivacy} variant="tonal" />
    </Screen>
  );
}
