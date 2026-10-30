import { useEffect, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import {
  reelsSources,
  reelsSourceKeys,
  type ReelsSourceKey,
} from '../../platform';
import { AppIcon } from '../../ui/Charts';
import {
  Body,
  Card,
  Divider,
  SectionTitle,
  Segmented,
  Stepper,
  ToggleRow,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { useAppIcons, useDiary } from '../diary';
import { useReels } from '../sources';

type BlockMode = 'off' | 'limit' | 'always';
/** Native contract: count 1000 means "always block"; 1..999 is a daily limit. */
const ALWAYS = 1000;

export function ProtectScreen() {
  const diary = useDiary(1);
  const reels = useReels();
  const snapshot = diary.snapshot;
  const icons = useAppIcons(
    reelsSourceKeys.map(key => reelsSources[key].packageName),
  );
  const [count, setCount] = useState(20);
  const [minutes, setMinutes] = useState(15);
  const [seconds, setSeconds] = useState(10);
  const [limits, setLimits] = useState<Partial<Record<ReelsSourceKey, number>>>(
    {},
  );
  useEffect(() => {
    if (!snapshot) return;
    setCount(snapshot.reminderCount);
    setMinutes(snapshot.reminderMinutes);
    setSeconds(snapshot.breakSeconds);
  }, [snapshot?.revision]); // eslint-disable-line react-hooks/exhaustive-deps

  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** Steppers save once the person stops tapping, not on every tap. */
  const later = (save: () => unknown) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => void save(), 700);
  };
  const saveReminder = (
    enabled: boolean,
    nextCount = count,
    nextMinutes = minutes,
  ) => diary.configure('reminder', enabled, nextCount, nextMinutes);
  const blocker = (app: ReelsSourceKey) =>
    snapshot?.blockers.find(item => item.app === app);
  const modeOf = (app: ReelsSourceKey): BlockMode => {
    const rule = blocker(app);
    if (!rule?.enabled) return 'off';
    return rule.dailyLimit > 0 ? 'limit' : 'always';
  };
  const setBlock = (
    app: ReelsSourceKey,
    mode: BlockMode,
    limit = blocker(app)?.dailyLimit || 30,
  ) =>
    diary.configure(
      `block.${app}`,
      mode !== 'off',
      mode === 'always' ? ALWAYS : limit,
    );
  const installed = new Set(
    reels.snapshot?.sources.filter(s => s.installed).map(s => s.app),
  );

  return (
    <Screen title="Protect" subtitle="Gentle tools you choose">
      <SectionTitle>Mindful breaks</SectionTitle>
      <Card>
        <ToggleRow
          icon="heart"
          title="Offer a break"
          detail="A calm full-screen pause after a run of short videos. You can always keep watching."
          value={!!snapshot?.reminderEnabled}
          onChange={value => saveReminder(value)}
          disabled={!snapshot}
        />
        {snapshot?.reminderEnabled && (
          <View style={styles.controls}>
            <Text style={styles.label}>After this many videos</Text>
            <Stepper
              label="Videos before a break"
              value={count}
              min={5}
              max={300}
              step={5}
              suffix="videos"
              onChange={value => {
                setCount(value);
                later(() => saveReminder(true, value, minutes));
              }}
            />
            <Text style={styles.label}>Or after this much watching</Text>
            <Stepper
              label="Minutes before a break"
              value={minutes}
              min={5}
              max={120}
              step={5}
              suffix="minutes"
              onChange={value => {
                setMinutes(value);
                later(() => saveReminder(true, count, value));
              }}
            />
            <Text style={styles.label}>
              Pause before “Keep watching” unlocks
            </Text>
            <Stepper
              label="Pause length"
              value={seconds}
              min={3}
              max={30}
              step={1}
              suffix="seconds"
              onChange={value => {
                setSeconds(value);
                later(() => diary.configure('break', true, value));
              }}
            />
            <Body muted>
              A run starts over when you lock your phone or spend 5 minutes away
              from short videos. After a break, the next run begins.
            </Body>
          </View>
        )}
      </Card>

      <SectionTitle>Short-video blocks</SectionTitle>
      <Card>
        <Body muted>
          Choose per app. A block leaves the short-video player and shows a
          pause screen. Everything else in the app keeps working, and you can
          allow 5 minutes when you really need it.
        </Body>
        {reelsSourceKeys
          .filter(key => installed.has(key) || modeOf(key) !== 'off')
          .map((key, index) => {
            const mode = modeOf(key);
            return (
              <View key={key} style={styles.block}>
                {index > 0 && <Divider />}
                <View style={styles.blockHead}>
                  <AppIcon
                    uri={icons[reelsSources[key].packageName]}
                    mark={reelsSources[key].mark}
                    color={reelsSources[key].color}
                    size={36}
                  />
                  <Text style={styles.appName}>
                    {reelsSources[key].observation}
                  </Text>
                </View>
                <Segmented
                  label={`${reelsSources[key].observation} block`}
                  value={mode}
                  onChange={next => setBlock(key, next)}
                  options={[
                    { value: 'off', label: 'Off' },
                    { value: 'limit', label: 'Daily limit' },
                    { value: 'always', label: 'Always' },
                  ]}
                />
                {mode === 'limit' && (
                  <Stepper
                    label={`${reelsSources[key].name} daily limit`}
                    value={limits[key] ?? (blocker(key)?.dailyLimit || 30)}
                    min={5}
                    max={500}
                    step={5}
                    suffix="videos a day"
                    onChange={value => {
                      setLimits({ ...limits, [key]: value });
                      later(() => setBlock(key, 'limit', value));
                    }}
                  />
                )}
              </View>
            );
          })}
      </Card>

      <SectionTitle>Live counter</SectionTitle>
      <Card>
        <ToggleRow
          icon="eye"
          title="Show the floating counter"
          detail="A small, see-through count on top of short videos. Tap it for more; drag to move."
          value={!!reels.snapshot?.overlayEnabled}
          onChange={value => reels.setOverlay(value)}
          disabled={!reels.snapshot}
        />
        <ToggleRow
          icon="layers"
          title="Today’s total across all apps"
          detail="Off shows only the current app’s count for today."
          value={!!reels.snapshot?.counterAppearance.allAppsToday}
          onChange={value =>
            reels.setAppearance(
              !!reels.snapshot?.counterAppearance.showIcons,
              value,
            )
          }
          disabled={!reels.snapshot}
        />
        <ToggleRow
          icon="info"
          title="Show icons on the counter"
          value={!!reels.snapshot?.counterAppearance.showIcons}
          onChange={value =>
            reels.setAppearance(
              value,
              !!reels.snapshot?.counterAppearance.allAppsToday,
            )
          }
          disabled={!reels.snapshot}
        />
      </Card>
      {diary.error ? <Body muted>{diary.error}</Body> : null}
    </Screen>
  );
}

const styles = StyleSheet.create(theme => ({
  controls: { gap: 10, paddingHorizontal: 12 },
  label: { color: theme.muted, fontSize: 13, fontWeight: '600', marginTop: 4 },
  block: { gap: 10 },
  blockHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginTop: 6,
  },
  appName: { color: theme.text, fontSize: 16, fontWeight: '700' },
}));
