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
  Segmented,
  Stat,
  StatDivider,
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
  usageStatus,
}: {
  days: HistoryDay[];
  zone: string;
  usageStatus?: string;
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
  const apps = useMemo(() => appHistories(days), [days]);
  const windowKey = `${days[0]?.day}|${days[days.length - 1]?.day}`;
  useEffect(() => {
    setAllDays(false);
    setLimit(10);
  }, [windowKey]);
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
        <Card
          title={app.name}
          leading={
            <AppIcon
              uri={icons[app.packageName]}
              mark={app.name.slice(0, 1)}
              color={theme.accentFill}
              size={36}
            />
          }
          info={`Recorded app time and observed opens on this date page. Time is available on ${app.timeDays} of ${days.length} dates; opens on ${app.opensDays}. These are known subtotals, not complete coverage. Android usage events estimate foreground time and entries/returns, not exact cold launches or attention. Missing values are unknown. App identifier: ${app.packageName}. Other editions are separate.`}
        >
          <View style={styles.stats}>
            <Stat value={time(app.appMs)} label="Recorded App Time" />
            <StatDivider />
            <Stat
              value={app.appOpens === null ? '—' : exactCount(app.appOpens)}
              label="Observed Opens"
            />
          </View>
        </Card>
        <Card
          title="Daily App History"
          info={`Dates use ${zone}. Select a date to open its full-day overview. Bars compare recorded app time within the current window; missing values have no bar. Only up to 30 dates are loaded, and changing the date window replaces these rows.`}
          padded={false}
        >
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
      <Card
        title="Explore an App"
        info={`Search and sort recorded app time and observed opens for the current ${days.length}-day window. Missing values sort after recorded values. These are page subtotals, not all-time app totals.`}
      >
        {usageStatus && usageStatus !== 'partial' && (
          <Body muted>
            {
              (
                {
                  disabled: 'Screen Time Off',
                  permission_needed: 'Usage Access Needed',
                  unavailable: 'App Time Unavailable',
                  dense_history: 'App Time Unavailable',
                  interrupted: 'App Time Interrupted',
                } as Record<string, string>
              )[usageStatus]
            }
          </Body>
        )}
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
          {days.length} days
        </Body>
      </Card>
      <Card
        title="Apps in This Window"
        info="Select an app for daily values. Missing time or opens are shown as unavailable, not zero. More Apps adds ten rows at a time; date windows stay bounded separately."
        padded={false}
      >
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
          </View>
        )}
        {matches.length > limit && (
          <Button
            label="More Apps"
            variant="text"
            onPress={() =>
              setLimit(current => Math.min(matches.length, current + 10))
            }
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
