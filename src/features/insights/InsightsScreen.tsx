import { useState } from 'react';
import { Text, View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { reelsSources, reelsSourceKeys } from '../../platform';
import { AppIcon, BarChart } from '../../ui/Charts';
import { duration, exactCount, hourLabel } from '../../ui/format';
import {
  Body,
  Card,
  Divider,
  Meter,
  Row,
  SectionTitle,
  Segmented,
  Stat,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { useAppIcons, useDiary } from '../diary';
import { sourceDay } from '../today';

/** Rough, friendly equivalents. Shown as "about" figures, never as precise claims. */
function equivalents(ms: number, videos: number) {
  const minutes = ms / 60_000;
  return [
    { label: 'book pages', value: Math.round(minutes / 2) }, // ~2 min per page
    { label: 'km walked', value: Math.round((minutes / 60) * 5 * 10) / 10 }, // 5 km/h
    { label: 'm scrolled', value: Math.round(videos * 0.12) }, // ~12 cm per swipe
  ].filter(item => item.value > 0);
}

export function InsightsScreen() {
  const [range, setRange] = useState<'7' | '31'>('7');
  const diary = useDiary(range === '7' ? 7 : 31);
  const days = diary.snapshot?.days ?? [];
  const perDay = days.map(day => sourceDay(day));
  const dailyVideos = perDay.map(list =>
    list.reduce((sum, item) => sum + item.videos, 0),
  );
  const total = dailyVideos.reduce((a, b) => a + b, 0);
  const reelsMs = perDay.flat().reduce((sum, item) => sum + item.reelsMs, 0);
  const activeDays = dailyVideos.filter(value => value > 0).length;
  const average = activeDays ? Math.round(total / days.length) : 0;
  const busiest = dailyVideos.indexOf(Math.max(...dailyVideos, 0));
  const hours = Array.from({ length: 24 }, (_, hour) =>
    days.reduce((sum, day) => sum + Number(day.hours?.[hour] ?? 0), 0),
  );
  const peakHour = hours.indexOf(Math.max(...hours));
  const bySource = reelsSourceKeys
    .map(key => ({
      key,
      videos: perDay.reduce(
        (sum, list) => sum + (list.find(item => item.key === key)?.videos ?? 0),
        0,
      ),
      ms: perDay.reduce(
        (sum, list) =>
          sum + (list.find(item => item.key === key)?.reelsMs ?? 0),
        0,
      ),
    }))
    .sort((a, b) => b.videos - a.videos);
  const interventions = days.reduce<Record<string, number>>((acc, day) => {
    for (const [key, value] of Object.entries(day.interventions ?? {}))
      acc[key] = (acc[key] ?? 0) + value;
    return acc;
  }, {});
  const breaksShown = Object.entries(interventions)
    .filter(([k]) => k.startsWith('break.'))
    .reduce((s, [, v]) => s + v, 0);
  const breaksTaken = interventions['break.left'] ?? 0;
  const icons = useAppIcons(
    reelsSourceKeys.map(key => reelsSources[key].packageName),
  );
  const label = (day: string, index: number) => {
    const date = new Date(day + 'T12:00:00');
    if (range === '7')
      return date.toLocaleDateString(undefined, { weekday: 'narrow' });
    return index % 7 === 0 || index === days.length - 1
      ? String(date.getDate())
      : '';
  };
  const equal = equivalents(reelsMs, total);

  return (
    <Screen title="Insights" subtitle="Your patterns over time">
      <Segmented
        label="Time range"
        value={range}
        onChange={setRange}
        options={[
          { value: '7', label: 'Last 7 days' },
          { value: '31', label: 'Last 30 days' },
        ]}
      />

      <Card>
        <View style={styles.stats}>
          <Stat value={exactCount(String(total))} label="Short videos" />
          <Stat value={exactCount(String(average))} label="Daily average" />
          <Stat value={duration(reelsMs)} label="Watching" />
        </View>
        <BarChart
          values={dailyVideos}
          labels={days.map((day, index) => label(day.day, index))}
          highlight={days.length - 1}
          height={140}
          accessibilityLabel={`Short videos per day. Total ${total}. Busiest day ${days[busiest]?.day ?? 'none'}.`}
        />
        {total > 0 && days[busiest] && (
          <Body muted>
            Busiest day:{' '}
            {new Date(days[busiest].day + 'T12:00:00').toLocaleDateString(
              undefined,
              { weekday: 'long', month: 'short', day: 'numeric' },
            )}{' '}
            with {dailyVideos[busiest]} videos.
          </Body>
        )}
      </Card>

      {equal.length > 0 && (
        <>
          <SectionTitle>That time could also be</SectionTitle>
          <Card tone="soft">
            <View style={styles.stats}>
              {equal.map(item => (
                <Stat
                  key={item.label}
                  value={`≈${item.value.toLocaleString()}`}
                  label={item.label}
                />
              ))}
            </View>
            <Body muted>
              Rough comparisons to help the numbers feel real. No judgement,
              just perspective.
            </Body>
          </Card>
        </>
      )}

      <SectionTitle>When you watch</SectionTitle>
      <Card>
        <BarChart
          values={hours}
          labels={[0, 6, 12, 18, 23].map(hourLabel)}
          highlight={peakHour}
          accessibilityLabel={`Short videos by hour. Peak hour ${hourLabel(peakHour)}`}
        />
        {total > 0 && (
          <Body muted>
            You watch most around {hourLabel(peakHour)}
            {peakHour >= 22 || peakHour < 5 ? ', late at night' : ''}.
          </Body>
        )}
      </Card>

      <SectionTitle>By app</SectionTitle>
      <Card padded={false}>
        {bySource.map((item, index) => (
          <View key={item.key}>
            {index > 0 && <Divider />}
            <Row
              leading={
                <AppIcon
                  uri={icons[reelsSources[item.key].packageName]}
                  mark={reelsSources[item.key].mark}
                  color={reelsSources[item.key].color}
                />
              }
              title={reelsSources[item.key].name}
              detail={item.ms > 0 ? `${duration(item.ms)} watching` : undefined}
              trailing={
                <View style={styles.share}>
                  <Text style={styles.shareValue}>
                    {exactCount(String(item.videos))}
                  </Text>
                  <Meter
                    value={total ? item.videos / total : 0}
                    color={reelsSources[item.key].color}
                  />
                </View>
              }
            />
          </View>
        ))}
      </Card>

      {breaksShown > 0 && (
        <>
          <SectionTitle>Mindful breaks</SectionTitle>
          <Card>
            <View style={styles.stats}>
              <Stat value={String(breaksShown)} label="Breaks offered" />
              <Stat value={String(breaksTaken)} label="You stopped" />
            </View>
            <Body muted>
              {breaksTaken > 0
                ? 'Every pause you take is a small win. Keep going.'
                : 'Breaks are a nudge, not a rule. Stopping even once is progress.'}
            </Body>
          </Card>
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create(theme => ({
  stats: { flexDirection: 'row', gap: 12 },
  share: { width: 96, gap: 6, alignItems: 'flex-end' },
  shareValue: {
    color: theme.text,
    fontSize: 16,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
}));
