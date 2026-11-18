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
  // Retain measured zero and historical facts; don't advertise unqualified metrics as empty tiles.
  const hasPlayback = [
    summary.watches,
    summary.completions,
    summary.playbackMs,
    summary.visits,
  ].some(value => value !== null);
  const count = (value: string | null) =>
    value === null ? '—' : exactCount(value);
  const status = unavailable
    ? 'Viewing details could not refresh. Saved history is preserved.'
    : loading
      ? 'Loading viewing details…'
      : hasPlayback && measurementEnabled === false
        ? 'Saved playback measurements · collection is off.'
        : null;
  return (
    <>
      <Card
        title="Viewing details"
        scope={scope}
        info={
          hasPlayback
            ? 'Encounters mean on screen, not watched. Photos and videos describe media; ads can overlap either. Saved experimental playback measurements are retained here. A watch needs 3 seconds of advancing numeric player time in one visit; an observed completion needs a continuous run from near the start to near the end. Confirmed playback is measured time. Missing signals, pauses and gaps add no time.'
            : 'Encounters mean on screen, not watched. Photos and videos describe media; ads can overlap either. One carousel post is one encounter.'
        }
      >
        {status && <Body muted>{status}</Body>}
        <View style={rowStyle}>
          <Stat value={count(summary.photos)} label="Photos encountered" />
          {!stacked && <StatDivider />}
          <Stat value={count(summary.ads)} label="Ads encountered" />
        </View>
        {hasPlayback && (
          <View style={rowStyle}>
            <Stat value={count(summary.watches)} label="3-second watches" />
            {!stacked && <StatDivider />}
            <Stat
              value={count(summary.completions)}
              label="Observed completions"
            />
          </View>
        )}
        {hasPlayback && (
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
        )}
      </Card>
    </>
  );
}
const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 16, justifyContent: 'space-between' },
  stacked: { flexDirection: 'column' },
});
