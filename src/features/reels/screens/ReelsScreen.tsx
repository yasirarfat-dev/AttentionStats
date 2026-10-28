import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, AppState, Switch, Text, View } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import { StyleSheet } from 'react-native-unistyles';
import {
  NativeReels,
  parseReelsSnapshot,
  type ReelsApp,
  type ReelsSnapshot,
} from '../../../platform';
import { Action } from '../../../ui/Action';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

const names = { instagram: 'Instagram', facebook: 'Facebook' };

export function ReelsScreen({ onPrivacy }: { onPrivacy: () => void }) {
  const native = NativeReels;
  const [snapshot, setSnapshot] = useState<ReelsSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [checkError, setCheckError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const request = useRef(0);
  const mounted = useRef(true);
  const focused = useIsFocused();
  const read = useCallback(async () => {
    if (busyRef.current || !NativeReels) return;
    const ticket = ++request.current;
    try {
      const value = parseReelsSnapshot(await NativeReels.getSnapshot());
      if (mounted.current && ticket === request.current) {
        setSnapshot(value);
        setError(null);
      }
    } catch {
      if (mounted.current && ticket === request.current)
        setError(
          'The counter is unavailable. Saved history is preserved. Try again when storage and the device are available.',
        );
    }
  }, []);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      request.current++;
    };
  }, []);
  useEffect(() => {
    if (!focused) return;
    read();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') read();
    });
    const timer = setInterval(() => {
      if (AppState.currentState === 'active') read();
    }, 2000);
    return () => {
      clearInterval(timer);
      subscription.remove();
    };
  }, [focused, read]);

  async function mutate(operation: () => Promise<string>) {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    const ticket = ++request.current;
    try {
      const next = parseReelsSnapshot(await operation());
      if (mounted.current && ticket === request.current) {
        setSnapshot(next);
        setError(null);
      }
    } catch {
      if (mounted.current)
        setError(
          'The change was not confirmed. Refresh, review the current state, and try again.',
        );
    } finally {
      busyRef.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  function consent(app: ReelsApp, enabled: boolean) {
    if (!NativeReels || !snapshot) return;
    const module = NativeReels;
    const revision = snapshot.revision;
    const apply = () =>
      mutate(() => module.configure(app, enabled, 2, revision));
    if (!enabled) {
      apply();
      return;
    }
    Alert.alert(
      `Observe ${names[app]} Reels?`,
      `Attention Stats uses Android accessibility to inspect layout identifiers, roles and numeric page positions in ${names[app]} when you use it. It saves estimated settled viewer-page counts and times in encrypted storage on this device, separating recognized video layouts, photo/carousel layouts, ads and unclassified pages. Recognized ads are kept separate from video/photo counts. Layouts do not prove that every ad is recognized. Normal-feed posts are not supported yet. It does not read screen text, messages, titles, creators, search terms, photos or video, and sends no history to us. Comments do not count. Coverage can be incomplete. You can pause, disable or delete this history at any time. Accessibility access is a separate setting you enable yourself.`,
      [
        { text: 'Keep off', style: 'cancel' },
        { text: 'I agree — enable observation', onPress: apply },
      ],
    );
  }
  async function calibration(enabled: boolean) {
    if (!NativeReels || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setCheckError(null);
    const ticket = ++request.current;
    try {
      await NativeReels.setCalibration(enabled);
      const next = parseReelsSnapshot(await NativeReels.getSnapshot());
      if (mounted.current && ticket === request.current) setSnapshot(next);
    } catch {
      if (mounted.current)
        setCheckError(
          'The structure check did not start. Check accessibility access and resume an enabled source, then try again.',
        );
    } finally {
      busyRef.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  const anyRunning =
    snapshot?.sources.some(
      source => source.enabled && source.consentCurrent && !source.paused,
    ) === true;
  const current = snapshot?.storage === 'ready' && !error;
  const canCheck =
    current &&
    snapshot.accessibilityEnabled &&
    snapshot.serviceConnected &&
    anyRunning;
  const checkReason = !snapshot?.accessibilityEnabled
    ? snapshot?.accessibilitySelected
      ? 'Access is selected, but disconnected. Open accessibility settings to reconnect this service.'
      : 'Turn on accessibility access first.'
    : !snapshot.serviceConnected
      ? 'Waiting for the accessibility service to connect.'
      : !anyRunning
        ? 'Enable a source and resume observation first.'
        : 'Ready for a temporary, metadata-only check.';

  function openTarget(app: ReelsApp) {
    NativeReels?.openTarget(app)
      .then(opened => {
        if (!opened)
          setError('The app could not be opened. You can open it normally.');
      })
      .catch(() => setError('The app could not be opened.'));
  }

  return (
    <Screen>
      <View style={styles.intro}>
        <Text style={styles.eyebrow}>TODAY</Text>
        <Heading>Your Reels</Heading>
        <Text style={styles.support}>
          A small moment of awareness, on your terms.
        </Text>
      </View>
      {!NativeReels && (
        <Paragraph>The counter is available on Android only.</Paragraph>
      )}
      {error && (
        <View accessibilityRole="alert" style={styles.notice}>
          <Paragraph>{error}</Paragraph>
          <Action
            label="Try again"
            onPress={read}
            disabled={busy}
            variant="tonal"
          />
        </View>
      )}
      {!snapshot && NativeReels && !error && (
        <Paragraph>Opening your local counter…</Paragraph>
      )}
      {snapshot && (
        <>
          <Text style={styles.support}>
            {snapshot.day} · {snapshot.zone}
          </Text>
          {!current && (
            <Paragraph>
              Current readings are unavailable. Refresh to check saved status.
            </Paragraph>
          )}
          <View style={styles.apps}>
            {snapshot.sources.map(source => {
              const available =
                current &&
                ((source.supported &&
                  source.enabled &&
                  source.consentCurrent &&
                  snapshot.accessibilityEnabled) ||
                  source.today !== '0');
              const status = !source.installed
                ? 'Not installed'
                : !source.enabled
                  ? 'Observation off'
                  : !source.consentCurrent
                    ? 'Review updated observation notice'
                    : source.paused
                      ? 'Paused'
                      : !source.supported
                        ? 'Needs verification'
                        : !snapshot.accessibilityEnabled
                          ? 'Access needed'
                          : 'Observation on';
              return (
                <View key={source.app} style={styles.appCard}>
                  <View style={styles.row}>
                    <View
                      style={[
                        styles.appMark,
                        source.app === 'instagram' && styles.instagramMark,
                      ]}
                      accessible={false}
                      importantForAccessibility="no"
                    >
                      <Text
                        style={[
                          styles.markText,
                          source.app === 'instagram' && styles.instagramText,
                        ]}
                      >
                        {source.app === 'instagram' ? 'IG' : 'f'}
                      </Text>
                    </View>
                    <View style={styles.grow}>
                      <Text accessibilityRole="header" style={styles.appName}>
                        {names[source.app]}
                      </Text>
                      <Text style={styles.support}>{status}</Text>
                    </View>
                    <Switch
                      style={styles.switchTarget}
                      accessibilityLabel={`Observe ${names[source.app]} Reels`}
                      value={source.enabled && source.consentCurrent}
                      disabled={busy || !source.installed}
                      onValueChange={value => consent(source.app, value)}
                    />
                  </View>
                  <View style={styles.metric}>
                    <Text style={styles.count}>
                      {available ? `~${source.today}` : '—'}
                    </Text>
                    <Text style={styles.support}>
                      {available
                        ? 'All viewer pages today · includes ads · partial'
                        : 'Count unavailable'}
                    </Text>
                  </View>
                  {(
                    [
                      ['reel', 'Video layouts'],
                      ['photo', 'Photo / carousel pages'],
                      ['post', 'Normal-feed posts'],
                      ['ad', 'Recognized image ads'],
                      ['unclassified', 'Unclassified pages'],
                    ] as const
                  ).map(([key, label]) => (
                    <Text key={key} style={styles.support}>
                      {label}:{' '}
                      {available && source.counts[key] !== null
                        ? `~${source.counts[key]}`
                        : '—'}
                    </Text>
                  ))}
                  {current && source.visit !== null && (
                    <Text style={styles.support}>
                      This visit ~{source.visit} · included in today
                    </Text>
                  )}
                  <Action
                    label={`Open ${names[source.app]}`}
                    variant="tonal"
                    onPress={() => openTarget(source.app)}
                    disabled={busy || !source.installed}
                  />
                </View>
              );
            })}
          </View>
          <Text style={styles.support}>
            Separate estimates of settled viewer pages, not unique items or
            completed views. Returning to an earlier page counts again; looping
            on the same page does not. Unique items and repeat breakdowns are
            unavailable. Recognized ads stay separate. Ad detection is
            incomplete; video/photo labels describe layouts. Normal-feed posts
            and Facebook counting are not available yet.
          </Text>

          <View style={styles.panel}>
            <Text accessibilityRole="header" style={styles.sectionTitle}>
              Observation
            </Text>
            <Text style={styles.body}>
              {snapshot.accessibilityEnabled
                ? snapshot.serviceConnected
                  ? 'Accessibility is connected.'
                  : 'Access is enabled. Waiting for the service.'
                : snapshot.accessibilitySelected
                  ? 'Access is selected, but the service is disconnected. In accessibility settings, turn Attention Stats Reels counter off and back on to reconnect.'
                  : 'Accessibility is off. No new Reels can be observed.'}
            </Text>
            <Action
              label="Accessibility settings"
              variant="tonal"
              disabled={busy}
              onPress={() =>
                NativeReels?.openAccessibilitySettings()
                  .then(opened => {
                    if (!opened)
                      setError(
                        'Open Android Settings → Accessibility → Attention Stats Reels counter.',
                      );
                  })
                  .catch(() =>
                    setError('Accessibility settings could not be opened.'),
                  )
              }
            />
            <Action
              label={anyRunning ? 'Pause observation' : 'Resume observation'}
              disabled={
                busy || !snapshot.sources.some(source => source.enabled)
              }
              onPress={() =>
                native &&
                mutate(() => native.setPaused(anyRunning, snapshot.revision))
              }
            />
            <View style={styles.row}>
              <View style={styles.grow}>
                <Text style={styles.settingTitle}>Live counter</Text>
                <Text style={styles.support}>
                  Session and today. Tap for details; drag to move.
                </Text>
              </View>
              <Switch
                style={styles.switchTarget}
                accessibilityLabel="Show live Reels counter"
                value={snapshot.overlayEnabled}
                disabled={busy}
                onValueChange={enabled => {
                  if (native)
                    mutate(() => native.setOverlay(enabled, snapshot.revision));
                }}
              />
            </View>
            <Text style={styles.support}>
              Hiding the display keeps observation on. Pausing stops both.
            </Text>
          </View>

          <View style={styles.panel}>
            <Text accessibilityRole="header" style={styles.sectionTitle}>
              Verify this preview
            </Text>
            <Text style={styles.body}>
              {snapshot.calibrationEnabled
                ? 'Structure check is on for up to ten minutes. You can switch apps now.'
                : 'A temporary check helps verify the installed Instagram and Facebook layouts.'}
            </Text>
            <Text style={styles.support}>
              Only layout IDs, roles and numeric positions. No text, messages,
              images or account details. One private local file can be read by
              the developer during this test; nothing uploads automatically.
            </Text>
            <Text accessibilityLiveRegion="polite" style={styles.checkStatus}>
              {snapshot.calibrationEnabled
                ? 'Structure check active'
                : checkReason}
            </Text>
            {checkError && (
              <View accessibilityRole="alert">
                <Paragraph>{checkError}</Paragraph>
              </View>
            )}
            <Action
              label={
                snapshot.calibrationEnabled
                  ? 'Stop structure check'
                  : 'Start structure check'
              }
              disabled={busy || (!snapshot.calibrationEnabled && !canCheck)}
              variant="tonal"
              onPress={() =>
                snapshot.calibrationEnabled
                  ? calibration(false)
                  : Alert.alert(
                      'Allow a temporary structure check?',
                      'Only for enabled apps, for up to ten minutes. This development check saves static layout IDs, numeric positions, media/control bounds, up to three ancestor layouts, and scroll-source bounds with event sequence and relative timing in a local diagnostic file. No text, descriptions, messages or images are read. Stopping removes the file.',
                      [
                        { text: 'Cancel', style: 'cancel' },
                        {
                          text: 'Allow structure check',
                          onPress: () => calibration(true),
                        },
                      ],
                    )
              }
            />
            <Text style={styles.support}>
              Stopping removes the diagnostic file. Counting waits for detector
              verification.
            </Text>
          </View>

          <View style={styles.panel}>
            <Text accessibilityRole="header" style={styles.sectionTitle}>
              Your data, your choice
            </Text>
            <Text style={styles.body}>
              Saved counts stay encrypted on this device.
            </Text>
            <Action
              label="About your data"
              onPress={onPrivacy}
              variant="tonal"
            />
            <Action
              label="Delete Reels history"
              variant="danger"
              disabled={busy}
              onPress={() =>
                Alert.alert(
                  'Delete local Reels history?',
                  'This removes saved Reels counts for both apps, disables observation and hides the live counter. This cannot be undone.',
                  [
                    { text: 'Keep history', style: 'cancel' },
                    {
                      text: 'Delete and stop',
                      style: 'destructive',
                      onPress: () =>
                        native &&
                        mutate(() => native.deleteHistory(snapshot.revision)),
                    },
                  ],
                )
              }
            />
          </View>
          <Action
            label="Refresh status"
            onPress={read}
            disabled={busy}
            variant="text"
          />
          <Text style={styles.support}>
            Developer preview · wider history, export and protection are still
            being built.
          </Text>
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create(theme => ({
  intro: { gap: 8 },
  eyebrow: {
    color: theme.muted,
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.5,
  },
  support: { color: theme.muted, fontSize: 14, lineHeight: 21, flexShrink: 1 },
  body: { color: theme.text, fontSize: 16, lineHeight: 24 },
  apps: { gap: 12 },
  appCard: {
    backgroundColor: theme.surfaceAlt,
    padding: 20,
    borderRadius: 28,
    gap: 16,
  },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  grow: { flex: 1, flexShrink: 1, gap: 4 },
  appMark: {
    width: 44,
    height: 44,
    borderRadius: 16,
    backgroundColor: theme.tint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  instagramMark: { backgroundColor: theme.lavender },
  markText: { color: theme.accent, fontSize: 25, fontWeight: '700' },
  instagramText: { color: theme.onLavender, fontSize: 16 },
  appName: { fontSize: 20, color: theme.text, fontWeight: '600' },
  metric: { gap: 2 },
  count: {
    fontSize: 46,
    color: theme.text,
    fontVariant: ['tabular-nums'],
    flexWrap: 'wrap',
  },
  panel: {
    backgroundColor: theme.surface,
    padding: 20,
    borderRadius: 28,
    gap: 16,
  },
  sectionTitle: { color: theme.text, fontSize: 20, fontWeight: '600' },
  settingTitle: { color: theme.text, fontSize: 16, fontWeight: '500' },
  switchTarget: { minWidth: 48, minHeight: 48 },
  checkStatus: {
    color: theme.accent,
    fontSize: 14,
    lineHeight: 21,
    fontWeight: '600',
  },
  notice: {
    backgroundColor: theme.tint,
    padding: 20,
    borderRadius: 24,
    gap: 12,
  },
}));
