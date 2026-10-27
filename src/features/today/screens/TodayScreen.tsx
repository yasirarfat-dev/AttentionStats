import { Text } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { ReelsOverview } from '../../reels';
import { Action } from '../../../ui/Action';
import { Panel, UnavailableMetric } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function TodayScreen({
  onReels,
  onProtect,
  onHealth,
  onPrivacy,
}: {
  onReels: () => void;
  onProtect: () => void;
  onHealth: () => void;
  onPrivacy: () => void;
}) {
  return (
    <Screen>
      <Text style={styles.eyebrow}>
        {new Date().toLocaleDateString(undefined, {
          weekday: 'long',
          month: 'long',
          day: 'numeric',
        })}
      </Text>
      <Heading>Your day, at a glance.</Heading>
      <Paragraph>Notice your patterns. Choose what comes next.</Paragraph>
      <Panel title="Your time" tinted>
        <UnavailableMetric
          label="Whole-app time today"
          detail="App-time tracking is not available in this preview. Unobserved time stays unknown."
        />
        <Paragraph>
          App time and time inside Reels will be shown separately.
        </Paragraph>
        <Action label="View source health" variant="text" onPress={onHealth} />
      </Panel>
      <ReelsOverview onOpen={onReels} />
      <Panel title="A little room for yourself">
        <Paragraph>
          Explore focus sessions and boundaries at your own pace. Nothing is
          activated by opening them.
        </Paragraph>
        <Action
          label="Explore protection"
          variant="tonal"
          onPress={onProtect}
        />
      </Panel>
      <Action label="About your data" variant="text" onPress={onPrivacy} />
    </Screen>
  );
}
const styles = StyleSheet.create(theme => ({
  eyebrow: { color: theme.muted, fontSize: 14, lineHeight: 20 },
}));
