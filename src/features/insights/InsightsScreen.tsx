import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { reelsSources } from '../../platform';
import { AppIcon, BarChart } from '../../ui/Charts';
import { duration, exactCount, hourLabel } from '../../ui/format';
import {
  Body,
  Button,
  Card,
  Divider,
  Meter,
  Row,
  SectionTitle,
  Segmented,
  Stat,
  Title,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { useAppIcons, useDiary, ViewingDetail } from '../diary';
import {
  historySummary,
  orderedDays,
  resolveDay,
  usageExplanation,
} from './historySummary';

function dateLabel(day: string, short = false) {
  return new Date(day + 'T12:00:00Z').toLocaleDateString(undefined, {
    timeZone: 'UTC',
    weekday: short ? 'short' : 'long',
    month: 'short',
    day: 'numeric',
  });
}
const time = (value: string | null) =>
  duration(value === null ? null : Number(value));

export function InsightsScreen() {
  const { theme } = useUnistyles();
  const [range, setRange] = useState<'7' | '31'>('7');
  const [selected, setSelected] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);
  const [showHours, setShowHours] = useState(false);
  const diary = useDiary(range === '7' ? 7 : 31);
  const days = orderedDays(diary.snapshot?.days ?? []);
  const day = resolveDay(days, selected);
  const summary = historySummary(days);
  const daily = days.map(item => historySummary([item]));
  const detail = historySummary(day ? [day] : []);
  const selectedIndex = days.findIndex(item => item.day === day?.day);
  const hours = day?.hours.map(Number) ?? [];
  const peak =
    hours.length && Math.max(...hours) > 0
      ? hours.indexOf(Math.max(...hours))
      : null;
  const apps = [...(day?.apps ?? [])]
    .filter(app => app.appMs !== null || app.appOpens !== null)
    .sort((a, b) => Number(b.appMs ?? 0) - Number(a.appMs ?? 0));
  const visibleApps = showAll ? apps : apps.slice(0, 5);
  const sources = detail.sources
    .filter(source => BigInt(source.videos) > 0n || source.reelsMs !== null)
    .sort((a, b) =>
      BigInt(a.videos) === BigInt(b.videos)
        ? 0
        : BigInt(a.videos) > BigInt(b.videos)
          ? -1
          : 1,
    );
  const icons = useAppIcons([
    ...sources.map(source => reelsSources[source.key].packageName),
    ...visibleApps.map(app => app.packageName),
  ]);
  const selectDay = (key: string) => {
    setSelected(key);
    setShowAll(false);
    setShowHours(false);
  };

  return (
    <Screen title="Insights" subtitle="Understand your days, at your pace">
      <Segmented
        label="Time range"
        value={range}
        onChange={value => {
          setRange(value);
          setShowAll(false);
        }}
        options={[
          { value: '7', label: 'Last 7 days' },
          { value: '31', label: 'Last 31 days' },
        ]}
      />
      {diary.error && (
        <Card tone="warn">
          <Title>History couldn’t refresh</Title>
          <Body>{diary.error}</Body>
          {diary.snapshot && (
            <Body muted>Showing the last loaded observations.</Body>
          )}
          <Button
            label="Try again"
            variant="tonal"
            disabled={diary.busy}
            onPress={() => {
              void diary.refresh();
            }}
          />
        </Card>
      )}
      {!diary.snapshot && !diary.error && (
        <Card>
          <Body>Loading your history…</Body>
        </Card>
      )}
      {diary.snapshot && (
        <>
          <Card tone="hero">
            <Text style={styles.eyebrow}>VIDEO ENCOUNTERS · {range} DAYS</Text>
            <Text style={styles.heroValue}>{exactCount(summary.videos)}</Text>
            <View style={styles.stats}>
              <Stat
                inverse
                value={time(summary.reelsMs)}
                label="Video surface time"
              />
              <Stat
                inverse
                value={time(summary.appMs)}
                label="Recorded app time"
              />
            </View>
            <Text style={styles.heroNote}>
              On screen does not mean watched. Missing time is not zero.
            </Text>
          </Card>
          <SectionTitle>Explore a day</SectionTitle>
          <Card>
            <BarChart
              values={daily.map(item => Number(item.videos))}
              labels={days.map((item, index) =>
                range === '7'
                  ? new Date(item.day + 'T12:00:00Z').toLocaleDateString(
                      undefined,
                      { timeZone: 'UTC', weekday: 'narrow' },
                    )
                  : index % 7 === 0 || index === days.length - 1
                    ? item.day.slice(8)
                    : '',
              )}
              highlight={selectedIndex}
              height={100}
              accessibilityLabel={`Recorded video encounters over ${days.length} days. Choose a date below for exact counts.`}
            />
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator
              accessibilityLabel="Choose a day"
            >
              <View style={styles.dates}>
                {[...days].reverse().map(item => (
                  <Pressable
                    key={item.day}
                    accessibilityRole="button"
                    accessibilityState={{ selected: item.day === day?.day }}
                    accessibilityLabel={`${dateLabel(item.day)}, ${exactCount(historySummary([item]).videos)} video encounters`}
                    onPress={() => selectDay(item.day)}
                    style={[
                      styles.dateChip,
                      item.day === day?.day && styles.selectedDate,
                    ]}
                  >
                    <Text style={styles.dateText}>
                      {dateLabel(item.day, true)}
                    </Text>
                  </Pressable>
                ))}
              </View>
            </ScrollView>
            {day && (
              <>
                <Title>{dateLabel(day.day)}</Title>
                <View style={styles.stats}>
                  <Stat
                    value={exactCount(detail.videos)}
                    label="Video encounters"
                  />
                  <Stat
                    value={time(detail.reelsMs)}
                    label="Video surface time"
                  />
                </View>
                <View style={styles.stats}>
                  <View style={styles.grow}>
                    <Button
                      label="Previous day"
                      variant="tonal"
                      disabled={selectedIndex <= 0}
                      onPress={() => {
                        const previous = days[selectedIndex - 1];
                        if (previous) selectDay(previous.day);
                      }}
                    />
                  </View>
                  <View style={styles.grow}>
                    <Button
                      label="Next day"
                      variant="tonal"
                      disabled={selectedIndex >= days.length - 1}
                      onPress={() => {
                        const next = days[selectedIndex + 1];
                        if (next) selectDay(next.day);
                      }}
                    />
                  </View>
                </View>
              </>
            )}
            <Body muted>
              Dates and hourly activity use {diary.snapshot.zone}. Today is
              still in progress. Dates with no saved encounters do not establish
              complete coverage.
            </Body>
          </Card>
          <SectionTitle>Video encounters by app</SectionTitle>
          <Card padded={false}>
            {sources.length === 0 ? (
              <View style={styles.padded}>
                <Body muted>
                  No saved video encounters or surface time for this day.
                </Body>
              </View>
            ) : (
              sources.map((source, index) => (
                <View key={source.key}>
                  {index > 0 && <Divider />}
                  <Row
                    leading={
                      <AppIcon
                        uri={icons[reelsSources[source.key].packageName]}
                        mark={reelsSources[source.key].mark}
                        color={reelsSources[source.key].color}
                      />
                    }
                    title={reelsSources[source.key].name}
                    detail={`${time(source.reelsMs)} video surface time`}
                    trailing={
                      <View style={styles.share}>
                        <Text style={styles.shareValue}>
                          {exactCount(source.videos)}
                        </Text>
                        <Meter
                          value={
                            Number(detail.videos) > 0
                              ? Number(source.videos) / Number(detail.videos)
                              : 0
                          }
                          color={reelsSources[source.key].color}
                        />
                      </View>
                    }
                  />
                </View>
              ))
            )}
          </Card>
          <SectionTitle>Hourly encounters</SectionTitle>
          <Card>
            {peak === null ? (
              <Body muted>No saved hourly encounters for this day.</Body>
            ) : (
              <>
                <BarChart
                  values={hours}
                  labels={[0, 6, 12, 18, 23].map(hourLabel)}
                  highlight={peak}
                  accessibilityLabel={`Recorded media encounters by hour. Busiest hour ${hourLabel(peak)}, ${day?.hours[peak]} encounters.`}
                />
                <Body muted>
                  Most recorded encounters: {hourLabel(peak)}. This describes
                  encounters, including photos and ads, not attention or
                  completed viewing.
                </Body>
              </>
            )}
            <Button
              label={showHours ? 'Hide hourly values' : 'Show hourly values'}
              variant="text"
              onPress={() => setShowHours(!showHours)}
            />
            {showHours &&
              day?.hours.map((count, hour) => (
                <Row
                  key={hour}
                  title={`${hourLabel(hour)}–${hourLabel((hour + 1) % 24)}`}
                  value={exactCount(count)}
                />
              ))}
          </Card>
          <SectionTitle>App time & opens</SectionTitle>
          <Card padded={false}>
            <View style={styles.padded}>
              <Stat value={time(detail.appMs)} label="Recorded app time" />
              <Body muted>{usageExplanation(diary.snapshot.usageStatus)}</Body>
            </View>
            {visibleApps.map(app => (
              <View key={app.packageName}>
                <Divider />
                <Row
                  leading={
                    <AppIcon
                      uri={icons[app.packageName]}
                      mark={app.name.slice(0, 1).toUpperCase()}
                      color={theme.faint}
                      size={36}
                    />
                  }
                  title={app.name}
                  value={time(app.appMs)}
                  detail={
                    app.appOpens === null
                      ? 'Opens unavailable'
                      : `${exactCount(app.appOpens)} observed opens`
                  }
                />
              </View>
            ))}
            {apps.length === 0 && (
              <View style={styles.padded}>
                <Body muted>
                  No app-time observations available for this day.
                </Body>
              </View>
            )}
            {apps.length > 5 && (
              <Button
                label={
                  showAll ? 'Show fewer apps' : `Show all ${apps.length} apps`
                }
                variant="text"
                onPress={() => setShowAll(!showAll)}
              />
            )}
          </Card>
          <ViewingDetail days={day ? [day] : []} />
          {Object.keys(day?.interventions ?? {}).some(key =>
            key.startsWith('break.'),
          ) && (
            <>
              <SectionTitle>Mindful breaks</SectionTitle>
              <Card>
                <View style={styles.stats}>
                  <Stat
                    value={String(
                      Object.entries(day?.interventions ?? {})
                        .filter(([key]) => key.startsWith('break.'))
                        .reduce((sum, [, count]) => sum + count, 0),
                    )}
                    label="Reminders recorded"
                  />
                  <Stat
                    value={String(day?.interventions['break.left'] ?? 0)}
                    label="Chose to leave"
                  />
                </View>
                <Body muted>
                  Your recorded reminder choices for this day. You can adjust
                  your reminders in Protect.
                </Body>
              </Card>
            </>
          )}
          <Body muted>
            These details describe the selected day. Video surface time overlaps
            app time; do not add them together.
          </Body>
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create(theme => ({
  stats: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  grow: { flex: 1, minWidth: 128 },
  eyebrow: {
    color: theme.heroMuted,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1,
  },
  heroValue: {
    color: theme.onHero,
    fontSize: 52,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  },
  heroNote: { color: theme.heroMuted, fontSize: 13, lineHeight: 19 },
  dates: { flexDirection: 'row', gap: 8, paddingVertical: 4 },
  dateChip: {
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: 14,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.surface,
  },
  selectedDate: {
    backgroundColor: theme.accentSoft,
    borderColor: theme.accent,
  },
  dateText: { color: theme.text, fontSize: 14, fontWeight: '600' },
  padded: { padding: 16, gap: 12 },
  share: { width: 90, gap: 6, alignItems: 'flex-end' },
  shareValue: {
    color: theme.text,
    fontSize: 16,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
}));
