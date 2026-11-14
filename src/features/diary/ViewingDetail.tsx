import { View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import type { DiarySnapshot } from '../../platform/diaryStatus';
import { Card, Stat, StatDivider } from '../../ui/Kit';
import { duration, exactCount } from '../../ui/format';

/** Media type, advertising and playback are separate facets; totals must not be added together. */
export function ViewingDetail({
  days,
  scope,
}: {
  days: DiarySnapshot['days'];
  scope?: string;
}) {
  const apps = days.flatMap(day => day.apps).filter(app => app.social !== null);
  const sum = (
    key:
      | 'photoCount'
      | 'adCount'
      | 'watchCount'
      | 'completionCount'
      | 'measuredVideoCount'
      | 'playbackMs',
  ) =>
    apps.reduce((total, app) => total + BigInt(app[key] ?? '0'), 0n).toString();
  const measured = apps.some(app => app.measuredVideoCount !== null);
  return (
    <>
      <Card
        title="Viewing Details"
        scope={scope}
        info="Encounters mean on screen, not watched. Photos and videos describe media; ads can overlap either. Playback is opt-in where numeric player times are available. A watch needs 3 seconds in one visit; completions need an observed run from the start. Missing signals stay unknown; pauses and gaps add no time."
      >
        <View style={styles.row}>
          <Stat
            value={exactCount(sum('photoCount'))}
            label="Photos encountered"
          />
          <StatDivider />
          <Stat value={exactCount(sum('adCount'))} label="Ads encountered" />
        </View>
        <View style={styles.row}>
          <Stat
            value={measured ? exactCount(sum('watchCount')) : '—'}
            label="3-second watches"
          />
          <StatDivider />
          <Stat
            value={measured ? exactCount(sum('completionCount')) : '—'}
            label="Observed completions"
          />
        </View>
        <View style={styles.row}>
          <Stat
            value={measured ? duration(Number(sum('playbackMs'))) : '—'}
            label="Confirmed playback"
          />
          <StatDivider />
          <Stat
            value={measured ? exactCount(sum('measuredVideoCount')) : '—'}
            label="Measured visits"
          />
        </View>
      </Card>
    </>
  );
}
const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 16, justifyContent: 'space-between' },
});
