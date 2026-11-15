import { View, useWindowDimensions } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import type { DiarySnapshot } from '../../platform/diaryStatus';
import { Body, Card, Stat, StatDivider } from '../../ui/Kit';
import { duration, exactCount } from '../../ui/format';
import { viewingSummary } from './viewingSummary';

/** Media type, advertising and playback are separate facets; totals must not be added together. */
export function ViewingDetail({
  days,
  scope,
  measurementEnabled,
  loading = false,
  unavailable = false,
}: {
  days: DiarySnapshot['days'];
  scope?: string;
  measurementEnabled?: boolean;
  loading?: boolean;
  unavailable?: boolean;
}) {
  const { width, fontScale } = useWindowDimensions();
  const stacked = width / fontScale < 330;
  const rowStyle = [styles.row, stacked && styles.stacked];
  const summary = viewingSummary(days);
  const count = (value: string | null) =>
    value === null ? '—' : exactCount(value);
  const status = unavailable
    ? 'Viewing details could not refresh. Saved history is preserved.'
    : loading
      ? 'Loading viewing details…'
      : measurementEnabled === false
        ? 'Playback measurement is off · enable in Settings.'
        : summary.visits === null
          ? 'No readable playback times recorded for this period.'
          : null;
  return (
    <>
      <Card
        title="Viewing Details"
        scope={scope}
        info="Encounters mean on screen, not watched. Photos and videos describe media; ads can overlap either. Enable Measure confirmed playback under Settings, Display & experience. Numeric player times must be readable; switching it on cannot recover earlier playback. A watch needs 3 seconds in one visit; observed completions need a continuous run from the start. Confirmed playback is time, not a completion count. Missing signals stay unknown; pauses and gaps add no time. Saved measurements remain visible when collection is off."
      >
        {status && <Body muted>{status}</Body>}
        <View style={rowStyle}>
          <Stat value={count(summary.photos)} label="Photos encountered" />
          {!stacked && <StatDivider />}
          <Stat value={count(summary.ads)} label="Ads encountered" />
        </View>
        <View style={rowStyle}>
          <Stat value={count(summary.watches)} label="3-second watches" />
          {!stacked && <StatDivider />}
          <Stat
            value={count(summary.completions)}
            label="Observed completions"
          />
        </View>
        <View style={rowStyle}>
          <Stat
            value={duration(
              summary.playbackMs === null ? null : Number(summary.playbackMs),
            )}
            label="Confirmed playback"
          />
          {!stacked && <StatDivider />}
          <Stat value={count(summary.visits)} label="Measured visits" />
        </View>
      </Card>
    </>
  );
}
const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 16, justifyContent: 'space-between' },
  stacked: { flexDirection: 'column' },
});
