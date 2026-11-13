import { useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import type { ScrollViewInstance } from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { reelsSources } from '../../platform';
import NativeDiary from '../../platform/specs/NativeDiary';
import { fixedRange } from './dateRange';
import { AppIcon, BarChart } from '../../ui/Charts';
import { duration, exactCount, hourLabel, sumCounts } from '../../ui/format';
import {
  Body,
  Button,
  Card,
  Divider,
  Explanation,
  Meter,
  Row,
  SectionTitle,
  Segmented,
  Stat,
  StatDivider,
  Title,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { useAppIcons, useHistoryPage, ViewingDetail } from '../diary';
import { AppHistoryExplorer } from './AppHistoryExplorer';
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
    year: short ? undefined : 'numeric',
  });
}
const time = (value: string | null) =>
  duration(value === null ? null : Number(value));

export function InsightsScreen() {
  const scroll = useRef<ScrollViewInstance>(null);
  const explorerY = useRef(0);
  const insets = useSafeAreaInsets();
  const toTop = () => scroll.current?.scrollTo({ y: 0, animated: false });
  const toExplorer = () =>
    scroll.current?.scrollTo({
      y: Math.max(0, explorerY.current - insets.top - 16),
      animated: false,
    });
  const { theme } = useUnistyles();
  const [range, setRange] = useState<'7' | '30' | 'all'>('7');
  const [endDay, setEndDay] = useState('');
  const [dates, setDates] = useState({ start: '', end: '' });
  const [dateError, setDateError] = useState('');
  const [view, setView] = useState<'day' | 'app'>('day');
  const [selected, setSelected] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);
  const [showHours, setShowHours] = useState(false);
  const [showSources, setShowSources] = useState(false);
  const lifetime = useHistoryPage(
    true,
    endDay,
    dates.start,
    dates.end,
    range === '7' ? 7 : 30,
    range === 'all' ? 0 : range === '7' ? 7 : 30,
  );
  const history = lifetime.history;
  const diary = { ...lifetime, snapshot: history?.snapshot ?? null };
  const changeRange = (value: '7' | '30' | 'all') => {
    setRange(value);
    setEndDay('');
    setSelected(null);
    setShowAll(false);
    setDateError('');
    setDates({ start: '', end: '' });
  };
  const pickDate = async (field: 'start' | 'end') => {
    if (!history) return;
    const today = history.throughDay;
    const size = range === '7' ? 7 : 30;
    const start = history.rangeStart!;
    const end = history.rangeEnd!;
    try {
      const chosen = await NativeDiary?.pickDate(
        field === 'start' ? 'Start Date' : 'End Date',
        field === 'start' ? start : end,
        field === 'start' ? history.earliestDay! : start,
        field === 'end' ? today : range === 'all' ? end : today,
      );
      if (!chosen) return;
      if (chosen < history.installDay!) {
        setDateError('Choose the installation date or a later date.');
        return;
      }
      setDates(
        range === 'all'
          ? {
              start: field === 'start' ? chosen : start,
              end: field === 'end' ? chosen : end,
            }
          : fixedRange(chosen, size),
      );
      setEndDay('');
      setSelected(null);
      setShowAll(false);
      setDateError('');
    } catch {
      setDateError('Could not open the date picker. Try again.');
    }
  };
  const days = orderedDays(diary.snapshot?.days ?? []);
  const day = resolveDay(days, selected);
  const summary = historySummary(days);
  const allTimeVideos = history
    ? sumCounts(history.sources.map(source => source.videos))
    : null;
  const recordedTimes =
    history?.sources.flatMap(source =>
      source.reelsMs === null ? [] : [source.reelsMs],
    ) ?? [];
  const allTimeMs = recordedTimes.length ? sumCounts(recordedTimes) : null;
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
    <Screen
      title="Insights"
      subtitle="Understand your days, at your pace"
      scrollRef={scroll}
    >
      <Segmented
        label="Time range"
        value={range}
        onChange={changeRange}
        options={[
          { value: '7', label: 'Week' },
          { value: '30', label: 'Month' },
          { value: 'all', label: 'All Time' },
        ]}
      />
      {history && (
        <Card>
          <View style={styles.stats}>
            <View style={styles.grow}>
              <Row
                icon="clock"
                title="Start Date"
                detail={dateLabel(history.rangeStart!)}
                onPress={() => void pickDate('start')}
              />
            </View>
            <View style={styles.grow}>
              <Row
                icon="clock"
                title="End Date"
                detail={dateLabel(history.rangeEnd!)}
                onPress={
                  range === 'all' ? () => void pickDate('end') : undefined
                }
              />
            </View>
          </View>
          {range !== 'all' && (
            <Body muted>
              {range === '7' ? '7' : '30'} days · End date follows the start
              date.
            </Body>
          )}
        </Card>
      )}
      {history && (
        <Body muted>
          Available From {dateLabel(history.installDay!, true)}
          {history.rangeEnd! > history.throughDay
            ? ' · Observations Through Today'
            : ''}
        </Body>
      )}
      {dateError !== '' && <Body>{dateError}</Body>}
      {endDay !== '' && (
        <Button
          label="Return to latest dates"
          variant="text"
          disabled={diary.busy}
          onPress={() => {
            setEndDay('');
            setSelected(null);
            toTop();
          }}
        />
      )}
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
          {diary.busy && <Body muted>Refreshing retained observations…</Body>}
          <Card tone="hero">
            <Text style={styles.eyebrow}>
              VIDEO ENCOUNTERS ·{' '}
              {range === 'all' ? 'SELECTED RANGE' : `${range} DAYS`}
            </Text>
            <Text style={styles.heroValue}>
              {exactCount(allTimeVideos ?? summary.videos)}
            </Text>
            <View style={styles.stats}>
              <Stat
                inverse
                value={time(history ? allTimeMs : summary.reelsMs)}
                label="Recorded video surface time"
              />
              {!history && (
                <>
                  <StatDivider inverse />
                  <Stat
                    inverse
                    value={time(summary.appMs)}
                    label="Recorded app time"
                  />
                </>
              )}
            </View>
            {history && (
              <Text style={styles.heroNote}>
                {history.firstDay
                  ? `${history.rangeStart} – ${history.rangeEnd}`
                  : 'No retained observations yet.'}
              </Text>
            )}
            <Text style={styles.heroNote}>
              On screen does not mean watched. Missing time is not zero.
            </Text>
          </Card>
          {history && (
            <>
              <Card>
                <Button
                  label={
                    showSources ? 'Hide Range Sources' : 'Show Range Sources'
                  }
                  variant="tonal"
                  onPress={() => setShowSources(value => !value)}
                />
                {showSources &&
                  history.sources.map(source => (
                    <Row
                      key={source.key}
                      title={reelsSources[source.key].name}
                      detail={`${exactCount(source.videos)} video encounters · ${time(source.reelsMs)} recorded surface time`}
                    />
                  ))}
                <Explanation title="About This Range">
                  Totals cover the selected range. Missing time is unknown;
                  calendar dates do not prove complete coverage. App time and
                  app rankings cover only the date page below. Dates use{' '}
                  {history.zone}.
                </Explanation>
              </Card>
              <SectionTitle>Browse Dates</SectionTitle>
              <Card>
                <Title>
                  {history.pageStart} – {history.pageEnd}
                </Title>
                <Body muted>
                  Daily and app details below cover only these {days.length}{' '}
                  days.
                </Body>
                <View style={styles.stats}>
                  <Stat
                    value={exactCount(summary.videos)}
                    label="Video encounters in this page"
                  />
                  <StatDivider />
                  <Stat
                    value={time(summary.appMs)}
                    label="Recorded app time in this page"
                  />
                </View>
                <View style={styles.stats}>
                  <View style={styles.grow}>
                    <Button
                      label="Older dates"
                      variant="tonal"
                      disabled={diary.busy || history.olderEnd === null}
                      onPress={() => {
                        if (history.olderEnd) {
                          setEndDay(history.olderEnd);
                          setSelected(null);
                          toTop();
                        }
                      }}
                    />
                  </View>
                  <View style={styles.grow}>
                    <Button
                      label="Newer dates"
                      variant="tonal"
                      disabled={diary.busy || history.newerEnd === null}
                      onPress={() => {
                        if (history.newerEnd) {
                          setEndDay(history.newerEnd);
                          setSelected(null);
                          toTop();
                        }
                      }}
                    />
                  </View>
                </View>
              </Card>
            </>
          )}
          <View
            onLayout={event => {
              explorerY.current = event.nativeEvent.layout.y;
            }}
          >
            <Segmented
              label="Explore history"
              value={view}
              onChange={setView}
              options={[
                { value: 'day', label: 'By day' },
                { value: 'app', label: 'By app' },
              ]}
            />
          </View>
          {view === 'app' ? (
            <>
              <AppHistoryExplorer
                days={days}
                zone={diary.snapshot.zone}
                onNavigate={toExplorer}
                onDay={key => {
                  selectDay(key);
                  setView('day');
                  toExplorer();
                }}
              />
              <Body muted>{usageExplanation(diary.snapshot.usageStatus)}</Body>
            </>
          ) : (
            <>
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
                      <StatDivider />
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
                  still in progress. Dates with no saved encounters do not
                  establish complete coverage.
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
                                  ? Number(source.videos) /
                                    Number(detail.videos)
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
                      Most recorded encounters: {hourLabel(peak)}. This
                      describes encounters, including photos and ads, not
                      attention or completed viewing.
                    </Body>
                  </>
                )}
                <Button
                  label={
                    showHours ? 'Hide hourly values' : 'Show hourly values'
                  }
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
                  <Body muted>
                    {usageExplanation(diary.snapshot.usageStatus)}
                  </Body>
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
                      showAll
                        ? 'Show fewer apps'
                        : `Show all ${apps.length} apps`
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
                      <StatDivider />
                      <Stat
                        value={String(day?.interventions['break.left'] ?? 0)}
                        label="Chose to leave"
                      />
                    </View>
                    <Body muted>
                      Your recorded reminder choices for this day. You can
                      adjust your reminders in Protect.
                    </Body>
                  </Card>
                </>
              )}
              <Body muted>
                These details describe the selected day. Video surface time
                overlaps app time; do not add them together.
              </Body>
            </>
          )}
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
