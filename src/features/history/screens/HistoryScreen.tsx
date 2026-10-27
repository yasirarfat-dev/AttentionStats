import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { Panel, Destination, UnavailableMetric } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function HistoryScreen({
  onReels,
  onDiary,
  onPrivacy,
}: {
  onReels: () => void;
  onDiary: () => void;
  onPrivacy: () => void;
}) {
  const [period, setPeriod] = useState<'Day' | 'Week' | 'Month'>('Day');
  return (
    <Screen>
      <Heading>A clearer timeline.</Heading>
      <Paragraph>
        Keep whole-app activity and time inside Reels distinct.
      </Paragraph>
      <View style={styles.periods}>
        {(['Day', 'Week', 'Month'] as const).map(value => (
          <Pressable
            key={value}
            accessibilityRole="button"
            accessibilityState={{ selected: period === value }}
            onPress={() => setPeriod(value)}
            style={[styles.period, period === value && styles.selected]}
          >
            <Text style={styles.label}>{value}</Text>
          </Pressable>
        ))}
      </View>
      <Panel
        title={
          period === 'Day'
            ? 'Today’s activity'
            : period === 'Week'
              ? 'This week’s activity'
              : 'This month’s activity'
        }
        tinted
      >
        <UnavailableMetric
          label="App time"
          detail="No app-time timeline is available yet. This is unknown coverage, not zero use."
        />
        <UnavailableMetric
          label="Time inside Reels"
          detail="Viewer duration is not available yet. Page counts do not measure watch time."
        />
      </Panel>
      <Panel title="Explore your observations">
        <Destination
          title="Today’s Reels counts"
          detail="Saved viewer-page counts and per-app source controls"
          onPress={onReels}
        />
        <Destination
          title="Detailed activity diary"
          detail="Apps, surfaces, interactions and coverage"
          onPress={onDiary}
        />
      </Panel>
      <Destination
        title="About your data"
        detail="Local history and your choices"
        onPress={onPrivacy}
      />
    </Screen>
  );
}

export function DiaryScreen({
  onReels,
  onHealth,
}: {
  onReels: () => void;
  onHealth: () => void;
}) {
  return (
    <Screen>
      <Heading>Your activity, with context.</Heading>
      <Paragraph>
        Each metric keeps its source, time range and coverage. Unsupported
        details stay unavailable.
      </Paragraph>
      <Panel title="Activity explorer">
        <Destination
          title="Reels & short videos"
          detail="The current supported viewer-page estimates"
          onPress={onReels}
        />
        <UnavailableMetric
          label="App sessions and opens"
          detail="Not available yet"
        />
        <UnavailableMetric
          label="Interactions and network use"
          detail="Not available yet; these need separate supported sources"
        />
      </Panel>
      <Destination
        title="Source health"
        detail="Review observation and access"
        onPress={onHealth}
      />
    </Screen>
  );
}
const styles = StyleSheet.create(theme => ({
  periods: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  period: {
    minHeight: 48,
    minWidth: 72,
    padding: 12,
    borderRadius: 24,
    backgroundColor: theme.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  selected: {
    backgroundColor: theme.tint,
    borderWidth: 1,
    borderColor: theme.accent,
  },
  label: { color: theme.text, fontSize: 15, fontWeight: '500' },
}));
