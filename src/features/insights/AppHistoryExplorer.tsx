import { useEffect, useMemo, useState } from 'react';
import { BackHandler, Keyboard, TextInput, View } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { AppIcon } from '../../ui/Charts';
import { duration, exactCount } from '../../ui/format';
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
import { useAppIcons } from '../diary';
import {
  appHistories,
  filterAppHistories,
  type AppHistorySort,
} from './appHistory';
import type { HistoryDay } from './historySummary';

const time = (value: string | null) =>
  duration(value === null ? null : Number(value));
const opens = (value: string | null) =>
  value === null ? 'Opens unavailable' : `${exactCount(value)} observed opens`;
const dateLabel = (day: string) =>
  new Date(day + 'T12:00:00Z').toLocaleDateString(undefined, {
    timeZone: 'UTC',
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

export function AppHistoryExplorer({
  days,
  zone,
  onDay,
  onNavigate,
}: {
  days: HistoryDay[];
  zone: string;
  onDay: (day: string) => void;
  onNavigate: () => void;
}) {
  const { theme } = useUnistyles();
  const focused = useIsFocused();
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<AppHistorySort>('time');
  const [selected, setSelected] = useState<string | null>(null);
  const [limit, setLimit] = useState(10);
  const [allDays, setAllDays] = useState(false);
  const [explain, setExplain] = useState(false);
  const apps = useMemo(() => appHistories(days), [days]);
  const matches = useMemo(
    () => filterAppHistories(apps, query, sort),
    [apps, query, sort],
  );
  const app = apps.find(item => item.packageName === selected);
  const packageName = app?.packageName;
  useEffect(() => {
    if (!focused || !packageName) return;
    const subscription = BackHandler.addEventListener(
      'hardwareBackPress',
      () => {
        setSelected(null);
        onNavigate();
        return true;
      },
    );
    return () => subscription.remove();
  }, [focused, packageName, onNavigate]);
  const visible = matches.slice(0, limit);
  const icons = useAppIcons(
    app ? [app.packageName] : visible.map(item => item.packageName),
  );

  if (app) {
    const daily = [...app.daily].reverse();
    const max = Math.max(1, ...daily.map(row => Number(row.appMs ?? 0)));
    return (
      <>
        <Button
          label="Back to app list"
          variant="tonal"
          onPress={() => {
            setSelected(null);
            onNavigate();
          }}
        />
        <Card>
          <View style={styles.heading}>
            <AppIcon
              uri={icons[app.packageName]}
              mark={app.name.slice(0, 1).toUpperCase()}
              color={theme.hero}
              size={48}
            />
            <View style={styles.name}>
              <Title>{app.name}</Title>
              <Body muted>{days.length}-day app history</Body>
            </View>
          </View>
          <View style={styles.stats}>
            <Stat value={time(app.appMs)} label="Recorded app time" />
            <Stat
              value={app.appOpens === null ? '—' : exactCount(app.appOpens)}
              label="Observed opens"
            />
          </View>
          <Body muted>
            Time values available on {app.timeDays} of {days.length} days; opens
            on {app.opensDays}. These are known subtotals, not complete
            coverage.
          </Body>
          <Button
            label={
              explain ? 'Hide measurement details' : 'How this is measured'
            }
            variant="text"
            onPress={() => setExplain(!explain)}
          />
          {explain && (
            <>
              <Body>
                App time estimates foreground use from Android usage events.
                Opens are observed entries and returns from another app, not
                cold launches. Missing days or values are unavailable, not zero.
              </Body>
              <Body muted>
                App identifier: {app.packageName}. Other editions are listed
                separately. Video encounters appear in By day and may combine
                editions. This view summarizes days; it does not show individual
                sessions or measure attention.
              </Body>
            </>
          )}
        </Card>
        <SectionTitle>Daily app history</SectionTitle>
        <Body muted>
          Dates use {zone}. Select a day to see its full overview. Bars compare
          recorded app time within this period; missing values have no bar.
        </Body>
        <Card padded={false}>
          {(allDays ? daily : daily.slice(0, 7)).map((row, index) => (
            <View key={row.day}>
              {index > 0 && <Divider />}
              <Row
                title={dateLabel(row.day)}
                value={time(row.appMs)}
                detail={opens(row.appOpens)}
                onPress={() => onDay(row.day)}
                accessibilityLabel={`View day ${dateLabel(row.day)}, recorded app time ${time(row.appMs)}, ${opens(row.appOpens)}`}
              />
              {row.appMs !== null && (
                <View style={styles.bar}>
                  <Meter value={Number(row.appMs) / max} color={theme.accent} />
                </View>
              )}
            </View>
          ))}
          {daily.length > 7 && (
            <Button
              label={
                allDays ? 'Show recent 7 days' : `Show all ${daily.length} days`
              }
              variant="text"
              onPress={() => setAllDays(!allDays)}
            />
          )}
        </Card>
      </>
    );
  }

  return (
    <>
      <SectionTitle>Explore an app</SectionTitle>
      <Card>
        <Body muted>
          Find an app and follow its recorded time and opens across this period.
        </Body>
        <TextInput
          accessibilityLabel="Search app history"
          placeholder="Search apps"
          placeholderTextColor={theme.muted}
          value={query}
          onChangeText={value => {
            setQuery(value);
            setLimit(10);
          }}
          maxLength={120}
          autoCorrect={false}
          autoCapitalize="none"
          returnKeyType="search"
          onSubmitEditing={Keyboard.dismiss}
          style={styles.search}
        />
        {query.length > 0 && (
          <Button
            label="Clear app search"
            variant="text"
            onPress={() => {
              setQuery('');
              setLimit(10);
            }}
          />
        )}
        <Segmented
          label="Sort app history"
          value={sort}
          onChange={value => {
            setSort(value);
            setLimit(10);
          }}
          options={[
            { value: 'time', label: 'App time' },
            { value: 'opens', label: 'Opens' },
            { value: 'name', label: 'A–Z' },
          ]}
        />
        <Body muted>
          {matches.length} {matches.length === 1 ? 'app' : 'apps'} ·{' '}
          {days.length} days. Missing values sort after recorded values.
        </Body>
      </Card>
      <Card padded={false}>
        {visible.map((item, index) => (
          <View key={item.packageName}>
            {index > 0 && <Divider />}
            <Row
              leading={
                <AppIcon
                  uri={icons[item.packageName]}
                  mark={item.name.slice(0, 1).toUpperCase()}
                  color={theme.hero}
                  size={36}
                />
              }
              title={item.name}
              accessibilityLabel={`Explore app history for ${item.name}, recorded app time ${time(item.appMs)}, ${opens(item.appOpens)}`}
              value={time(item.appMs)}
              detail={opens(item.appOpens)}
              onPress={() => {
                Keyboard.dismiss();
                setSelected(item.packageName);
                setAllDays(false);
                setExplain(false);
                onNavigate();
              }}
            />
          </View>
        ))}
        {matches.length === 0 && (
          <View style={styles.empty}>
            <Title>
              {query.trim() ? 'No matching apps' : 'No app observations yet'}
            </Title>
            <Body muted>
              {query.trim()
                ? 'Try another name or clear the search.'
                : 'Available app history will appear here. Missing history does not establish zero use.'}
            </Body>
          </View>
        )}
        {matches.length > limit && (
          <Button
            label={`Show All ${matches.length} Apps`}
            variant="text"
            onPress={() => setLimit(matches.length)}
          />
        )}
        {limit > 10 && matches.length > 10 && (
          <Button
            label="Show Fewer Apps"
            variant="text"
            onPress={() => setLimit(10)}
          />
        )}
      </Card>
    </>
  );
}

const styles = StyleSheet.create(theme => ({
  search: {
    minHeight: 52,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: theme.border,
    color: theme.text,
    backgroundColor: theme.canvas,
    paddingHorizontal: 16,
    fontSize: 16,
  },
  stats: { flexDirection: 'row', flexWrap: 'wrap', gap: 16 },
  heading: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  name: { flex: 1, gap: 4 },
  bar: { paddingHorizontal: 12, paddingBottom: 12 },
  empty: { padding: 16, gap: 10 },
}));
