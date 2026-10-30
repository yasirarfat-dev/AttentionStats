import { useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import {
  reelsSources,
  reelsSourceKeys,
  type ReelsSourceKey,
} from '../../platform';
import type { DiarySnapshot } from '../../platform/diaryStatus';
import NativeDiary from '../../platform/specs/NativeDiary';
import { AppIcon, BarChart, StackedBar } from '../../ui/Charts';
import { duration, exactCount, hourLabel } from '../../ui/format';
import {
  Body,
  Button,
  Card,
  Divider,
  Meter,
  Row,
  SectionTitle,
  Stat,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { useAppIcons, useDiary } from '../diary';
import { countingReady, useReels } from '../sources';

export type SourceDay = {
  key: ReelsSourceKey;
  videos: number;
  reelsMs: number;
  appMs: number;
  ads: number;
  photos: number;
};

/** Per-source totals for one diary day. */
export function sourceDay(
  day: DiarySnapshot['days'][number] | undefined,
): SourceDay[] {
  return reelsSourceKeys.map(key => {
    const app = day?.apps.find(item => item.social === key);
    return {
      key,
      videos: Number(app?.reelsCount ?? 0),
      reelsMs: Number(app?.reelsMs ?? 0),
      appMs: Number(app?.appMs ?? 0),
      ads: Number(app?.adCount ?? 0),
      photos: Number(app?.photoCount ?? 0),
    };
  });
}

export function TodayScreen({
  onSetup,
  onProtect,
}: {
  onSetup: () => void;
  onProtect: () => void;
}) {
  const { theme } = useUnistyles();
  const diary = useDiary(1);
  const reels = useReels();
  const [showAll, setShowAll] = useState(false);
  const day = diary.snapshot?.days[0];
  const sources = sourceDay(day);
  const videos = sources.reduce((sum, item) => sum + item.videos, 0);
  const reelsMs = sources.reduce((sum, item) => sum + item.reelsMs, 0);
  const session = Number(reels.snapshot?.session.count ?? 0);
  const breaks = Object.entries(day?.interventions ?? {})
    .filter(([key]) => key.startsWith('break.'))
    .reduce((sum, [, n]) => sum + n, 0);
  const hours = (day?.hours ?? []).map(Number);
  const nowHour = new Date().getHours();
  const apps = useMemo(
    () =>
      (day?.apps ?? [])
        .filter(app => Number(app.appMs ?? 0) > 0)
        .sort((a, b) => Number(b.appMs) - Number(a.appMs)),
    [day],
  );
  const totalApp = apps.reduce((sum, app) => sum + Number(app.appMs ?? 0), 0);
  const visibleApps = showAll ? apps : apps.slice(0, 6);
  const icons = useAppIcons([
    ...reelsSourceKeys.map(key => reelsSources[key].packageName),
    ...visibleApps.map(app => app.packageName),
  ]);
  const installed = new Set(
    reels.snapshot?.sources.filter(s => s.installed).map(s => s.app),
  );
  const ready = countingReady(reels.snapshot);
  const date = new Date().toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });

  return (
    <Screen title="Today" subtitle={date}>
      {reels.snapshot && !ready && (
        <Card tone="warn">
          <Text style={styles.warnTitle}>Counting is not on yet</Text>
          <Body muted>
            Finish the two-step setup so Attention Stats can count your short
            videos.
          </Body>
          <Button label="Finish setup" onPress={onSetup} />
        </Card>
      )}

      <Card tone="hero">
        <Text style={styles.heroLabel}>Short videos today</Text>
        <Text
          style={styles.heroValue}
          accessibilityLabel={`${videos} short videos today`}
        >
          {exactCount(String(videos))}
        </Text>
        <StackedBar
          parts={sources.map(item => ({
            key: item.key,
            value: item.videos,
            color: reelsSources[item.key].color,
          }))}
        />
        <View style={styles.heroStats}>
          <Stat
            inverse
            value={exactCount(String(session))}
            label="This session"
          />
          <Stat inverse value={duration(reelsMs)} label="Watching" />
          <Stat
            inverse
            value={String(breaks)}
            label={breaks === 1 ? 'Break' : 'Breaks'}
          />
        </View>
      </Card>

      <SectionTitle>By app</SectionTitle>
      <Card padded={false}>
        {sources
          .filter(item => installed.has(item.key) || item.videos > 0)
          .map((item, index) => (
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
                detail={
                  [
                    item.reelsMs > 0
                      ? `${duration(item.reelsMs)} in ${reelsSources[item.key].observation.split(' ').slice(1).join(' ') || 'videos'}`
                      : null,
                    item.appMs > 0 ? `${duration(item.appMs)} in app` : null,
                    item.ads > 0
                      ? `${item.ads} ${item.ads === 1 ? 'ad' : 'ads'}`
                      : null,
                    item.photos > 0
                      ? `${item.photos} ${item.photos === 1 ? 'photo' : 'photos'}`
                      : null,
                  ]
                    .filter(Boolean)
                    .join(' · ') || 'Nothing yet today'
                }
                value={exactCount(String(item.videos))}
                accessibilityLabel={`${reelsSources[item.key].name}: ${item.videos} short videos`}
              />
            </View>
          ))}
      </Card>

      <SectionTitle>Your day</SectionTitle>
      <Card>
        <BarChart
          values={hours}
          highlight={nowHour}
          labels={[0, 6, 12, 18, 23].map(hourLabel)}
          accessibilityLabel={`Short videos by hour. Busiest hour: ${hourLabel(hours.indexOf(Math.max(...hours, 0)))}`}
        />
        <Body muted>Short videos by hour of the day.</Body>
      </Card>

      <SectionTitle>Screen time</SectionTitle>
      <Card padded={false}>
        {diary.snapshot && !diary.snapshot.usageEnabled ? (
          <View style={styles.padded}>
            <Body muted>
              See how long you spend in every app, the way Digital Wellbeing
              does, but kept only on your phone.
            </Body>
            <Button
              label="Turn on screen time"
              variant="tonal"
              onPress={async () => {
                await diary.configure('usage', true);
                if (!diary.snapshot?.usageGranted)
                  await NativeDiary?.openUsageSettings();
              }}
            />
          </View>
        ) : (
          <>
            <View style={styles.padded}>
              <Text style={styles.totalLabel}>Total</Text>
              <Text style={styles.totalValue}>{duration(totalApp)}</Text>
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
                  detail={app.appOpens ? `${app.appOpens} opens` : undefined}
                  trailing={
                    <View style={styles.meterBox}>
                      <Text style={styles.time}>
                        {duration(Number(app.appMs))}
                      </Text>
                      <Meter
                        value={
                          Number(app.appMs) /
                          Math.max(1, Number(apps[0]?.appMs ?? 1))
                        }
                        color={theme.accent}
                      />
                    </View>
                  }
                />
              </View>
            ))}
            {apps.length > 6 && (
              <Button
                label={showAll ? 'Show less' : `Show all ${apps.length} apps`}
                variant="text"
                onPress={() => setShowAll(!showAll)}
              />
            )}
            {apps.length === 0 && (
              <View style={styles.padded}>
                <Body muted>
                  {diary.snapshot?.usageGranted
                    ? 'No app time recorded yet today.'
                    : 'Usage access is off in Android settings.'}
                </Body>
              </View>
            )}
          </>
        )}
      </Card>

      <Card tone="soft">
        <Row
          icon="heart"
          title="Want a gentle nudge?"
          detail="Set mindful breaks or a daily limit."
          onPress={onProtect}
        />
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create(theme => ({
  warnTitle: { color: theme.warn, fontSize: 17, fontWeight: '800' },
  heroLabel: { color: theme.heroMuted, fontSize: 15, fontWeight: '600' },
  heroValue: {
    color: theme.onHero,
    fontSize: 64,
    lineHeight: 70,
    fontWeight: '800',
    letterSpacing: -1.5,
    fontVariant: ['tabular-nums'],
  },
  heroStats: { flexDirection: 'row', gap: 12, marginTop: 4 },
  padded: { padding: 12, gap: 10 },
  totalLabel: { color: theme.muted, fontSize: 13, fontWeight: '600' },
  totalValue: {
    color: theme.text,
    fontSize: 30,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  },
  meterBox: { width: 92, gap: 6, alignItems: 'flex-end' },
  time: {
    color: theme.text,
    fontSize: 15,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
}));
