import { useEffect, useState } from 'react';
import { AppState, Text, View } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import { StyleSheet } from 'react-native-unistyles';
import {
  NativeReels,
  parseReelsSnapshot,
  type ReelsSnapshot,
} from '../../../platform';
import { Action } from '../../../ui/Action';
import { Panel } from '../../../ui/Panel';

export function ReelsOverview({ onOpen }: { onOpen: () => void }) {
  const focused = useIsFocused();
  const [snapshot, setSnapshot] = useState<ReelsSnapshot | null>(null);
  useEffect(() => {
    if (!focused) return;
    let active = true;
    let pending = false;
    async function read() {
      if (pending || AppState.currentState !== 'active' || !NativeReels) return;
      pending = true;
      try {
        const next = parseReelsSnapshot(await NativeReels.getSnapshot());
        if (active) setSnapshot(next);
      } catch {
        if (active) setSnapshot(null);
      } finally {
        pending = false;
      }
    }
    read();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') read();
    });
    const timer = setInterval(read, 2000);
    return () => {
      active = false;
      clearInterval(timer);
      subscription.remove();
    };
  }, [focused]);
  return (
    <Panel title="Reels & short videos" tinted>
      <View style={styles.row}>
        {(['instagram', 'facebook'] as const).map(app => {
          const source = snapshot?.sources.find(item => item.app === app);
          const available =
            snapshot?.storage === 'ready' &&
            source &&
            (source.today !== '0' ||
              (source.supported &&
                source.enabled &&
                source.consentCurrent &&
                snapshot.accessibilityEnabled &&
                !source.paused));
          return (
            <View key={app} style={styles.metric}>
              <Text style={styles.label}>
                {app === 'instagram' ? 'Instagram' : 'Facebook'}
              </Text>
              <Text style={styles.count}>
                {available ? `~${source.today}` : '—'}
              </Text>
              <Text style={styles.detail}>
                {available
                  ? 'Viewer pages today · partial'
                  : 'Count unavailable'}
              </Text>
            </View>
          );
        })}
      </View>
      <Text style={styles.detail}>
        Includes recognized ads. Video/photo layouts and unknown pages are
        separate in details. Returning to a page counts again, not as a unique
        item.
      </Text>
      <Action
        label="Open Reels & short videos"
        variant="tonal"
        onPress={onOpen}
      />
    </Panel>
  );
}
const styles = StyleSheet.create(theme => ({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 24 },
  metric: { flexGrow: 1, flexBasis: 120, gap: 8 },
  label: { color: theme.text, fontSize: 16, fontWeight: '500' },
  count: { color: theme.text, fontSize: 36, fontVariant: ['tabular-nums'] },
  detail: { color: theme.muted, fontSize: 14, lineHeight: 21 },
}));
