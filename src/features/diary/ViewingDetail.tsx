import { View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import type { DiarySnapshot } from '../../platform/diaryStatus';
import { Body, Card, SectionTitle, Stat } from '../../ui/Kit';
import { duration, exactCount } from '../../ui/format';

/** Media type, advertising and playback are separate facets; totals must not be added together. */
export function ViewingDetail({ days }: { days: DiarySnapshot['days'] }) {
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
      <SectionTitle>Viewing detail</SectionTitle>
      <Card>
        <View style={styles.row}>
          <Stat
            value={exactCount(sum('photoCount'))}
            label="Photos encountered"
          />
          <Stat value={exactCount(sum('adCount'))} label="Ads encountered" />
        </View>
        <View style={styles.row}>
          <Stat
            value={measured ? exactCount(sum('watchCount')) : '—'}
            label="3-second watches"
          />
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
          <Stat
            value={measured ? exactCount(sum('measuredVideoCount')) : '—'}
            label="Measured visits"
          />
        </View>
        <Body muted>
          Encountered means on screen, not watched. Photo and video counts
          describe media; ads are a separate facet and can overlap them.
          Playback is opt-in and only measured where numeric player times are
          exposed. Missing signals stay unknown. A watch needs 3 seconds of
          observed playback in one visit. Pauses and gaps add no watch time;
          completions require an observed run from the start.
        </Body>
      </Card>
    </>
  );
}
const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 16, justifyContent: 'space-between' },
});
