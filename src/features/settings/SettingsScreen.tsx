import { useEffect, useRef, useState } from 'react';
import { Alert, BackHandler, Linking, Share, Text, View } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import { StyleSheet } from 'react-native-unistyles';
import {
  NativeReels,
  parseAutoScrollStatus,
  reelsSources,
  reelsSourceKeys,
} from '../../platform';
import NativeDiary from '../../platform/specs/NativeDiary';
import { AppIcon } from '../../ui/Charts';
import { ago } from '../../ui/format';
import {
  Body,
  Button,
  Caption,
  Divider,
  Pill,
  Row,
  SectionTitle,
  ToggleRow,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { AppearanceEditor } from './AppearanceEditor';
import { SettingsGroup } from './SettingsGroup';
import { LicensesScreen } from './LicensesScreen';
import { settingsHelp } from './settingsHelp';
import { ProtectionToolScreen } from '../protection';
import { clearHistoryCache } from '../diary';
import { sourceIcon, useAppIcons, useDiary } from '../diary';
import { useDeleteHistory } from './DeleteHistory';
import { accessibilityReconnectNeeded, useReels } from '../sources';
import { applyAppearance, useAppearance } from '../../ui/theme';
import { saveAppearance } from '../../platform/appearance';

const REPO = 'https://github.com/yasirarfat-dev/AttentionStats';
const PRIVACY = `${REPO}/blob/main/policies/PRIVACY.md`;
const EMAIL = 'consciousbiobot@gmail.com';
const IDEAS =
  'https://vibedprojects.com/builders/yasir/projects/attentionstats';
const STORE = 'https://play.google.com/store/apps/details?id=attention.stats';

export function SettingsScreen({ onWelcome }: { onWelcome: () => void }) {
  const [counter, setCounter] = useState(false);
  const [licenses, setLicenses] = useState(false);
  const [colorDragging, setColorDragging] = useState(false);
  const focused = useIsFocused();
  useEffect(() => {
    if (!focused || (!counter && !licenses)) return;
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      setCounter(false);
      setLicenses(false);
      return true;
    });
    return () => back.remove();
  }, [focused, counter, licenses]);
  const reels = useReels();
  const diary = useDiary(1);
  const snapshot = reels.snapshot;
  const icons = useAppIcons(
    reelsSourceKeys.map(key => reelsSources[key].packageName),
  );
  const [autoScroll, setAutoScroll] = useState(false);
  const [skipAds, setSkipAds] = useState(false);
  const appearance = useAppearance();
  const appearancePending = useRef(false);
  const appearanceQueued = useRef<typeof appearance | null>(null);
  // Template taps apply at once, so quick taps queue: the latest choice always wins.
  async function chooseAppearance(next: typeof appearance) {
    if (appearancePending.current) {
      appearanceQueued.current = next;
      return;
    }
    appearancePending.current = true;
    let current: typeof appearance | null = next;
    try {
      while (current) {
        appearanceQueued.current = null;
        applyAppearance(await saveAppearance(current));
        current = appearanceQueued.current;
      }
    } catch {
      Alert.alert(
        'Could not save appearance',
        'Your choice was not saved. Please try again.',
      );
    } finally {
      appearancePending.current = false;
    }
  }
  useEffect(() => {
    NativeReels?.getAutoScrollSnapshot()
      .then(raw => {
        const status = parseAutoScrollStatus(raw);
        setAutoScroll(status.enabled);
        setSkipAds(status.skipStandaloneAds);
      })
      .catch(() => {});
  }, []);
  const paused = !!snapshot?.sources.some(
    source => source.enabled && source.paused,
  );

  function deviceReport(): string {
    const device = snapshot?.device;
    const apps = snapshot?.sources
      .map(
        source =>
          `- ${source.app}: ${source.installed ? source.version : 'not installed'}${source.enabled ? '' : ' (off)'}`,
      )
      .join('\n');
    return [
      `Attention Stats ${device?.appVersion ?? ''}`,
      `Device: ${device?.manufacturer ?? ''} ${device?.model ?? ''}, Android ${device?.release ?? ''} (SDK ${device?.sdk ?? ''})`,
      `Accessibility: ${snapshot?.accessibilityEnabled ? 'on' : 'off'}${snapshot?.serviceConnected ? ', connected' : ''}`,
      'Apps:',
      apps ?? '',
    ].join('\n');
  }

  function reportProblem() {
    const body = encodeURIComponent(
      `**What happened?**\n\n\n**What did you expect?**\n\n\n**Which app and screen?** (e.g. Instagram → tapped a video in Home)\n\n\n` +
        '```\n' +
        deviceReport() +
        '\n```',
    );
    Alert.alert(
      'Report a problem',
      'Your report includes your phone model, Android and app versions and which supported apps are installed or turned off, never your history or what you watched.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Email',
          onPress: () =>
            Linking.openURL(
              `mailto:${EMAIL}?subject=${encodeURIComponent('Attention Stats feedback')}&body=${body}`,
            ),
        },
        {
          text: 'GitHub issue',
          onPress: () =>
            Linking.openURL(
              `${REPO}/issues/new?title=${encodeURIComponent('Problem: ')}&body=${body}`,
            ),
        },
      ],
    );
  }

  /** Ideas go wherever people are comfortable: the app's listing, GitHub or email. */
  function suggestIdea() {
    Alert.alert(
      'Suggest an idea',
      'What would make Attention Stats more useful for you?\n\nShare your idea on VibeCodedProjects (vibedprojects.com) platform. You can also open a GitHub issue or send an email.',
      [
        { text: 'Project page', onPress: () => Linking.openURL(IDEAS) },
        {
          text: 'GitHub',
          onPress: () =>
            Linking.openURL(
              `${REPO}/issues/new?title=${encodeURIComponent('Idea: ')}`,
            ),
        },
        {
          text: 'Email',
          onPress: () =>
            Linking.openURL(
              `mailto:${EMAIL}?subject=${encodeURIComponent('Attention Stats idea')}`,
            ),
        },
      ],
      { cancelable: true },
    );
  }

  const deleter = useDeleteHistory({
    onPause: () => reels.setPaused(true),
    onDelete: async () => {
      if (await reels.deleteAll()) clearHistoryCache();
    },
    storageBytes: Number(snapshot?.storageBytes ?? 0),
  });

  if (licenses) return <LicensesScreen onBack={() => setLicenses(false)} />;
  if (counter)
    return (
      <ProtectionToolScreen
        tool="Counter"
        onBack={() => setCounter(false)}
        onSchedules={() => setCounter(false)}
      />
    );
  return (
    <Screen
      title="Settings"
      subtitle="Yours to shape"
      scrollEnabled={!colorDragging}
    >
      <SettingsGroup title="Counting" entries={settingsHelp.counting}>
        {reelsSourceKeys.map((key, index) => {
          const source = snapshot?.sources.find(item => item.app === key);
          const detail = !source?.installed
            ? 'Not installed'
            : source.lastRecognizedMs
              ? `Last seen ${ago(Number(source.lastRecognizedMs))}`
              : source.enabled && source.consentCurrent
                ? 'Ready'
                : 'Off';
          return (
            <View key={key}>
              {index > 0 && <Divider />}
              <ToggleRow
                leading={
                  <AppIcon
                    uri={sourceIcon(icons, key)}
                    mark={reelsSources[key].mark}
                    color={reelsSources[key].color}
                    size={36}
                  />
                }
                title={reelsSources[key].observation}
                detail={detail}
                value={!!source?.enabled && !!source.consentCurrent}
                onChange={value => {
                  if (!value) return void reels.setSource(key, false);
                  Alert.alert(
                    `Count ${reelsSources[key].observation}?`,
                    `Attention Stats will use Android Accessibility to read ${reelsSources[key].name}'s screen layout and labels in memory while it is open, only to recognise short videos and tell them apart. Nothing you watch, type or message is saved or sent. Counts stay encrypted on this phone.`,
                    [
                      { text: 'Not now', style: 'cancel' },
                      {
                        text: 'I agree',
                        onPress: () => void reels.setSource(key, true),
                      },
                    ],
                  );
                }}
                disabled={!snapshot}
              />
            </View>
          );
        })}
        <Divider />
        <ToggleRow
          icon="pause"
          title="Pause all counting"
          value={paused}
          onChange={value => reels.setPaused(value)}
          disabled={!snapshot}
        />
        <Divider />
        <ToggleRow
          icon="chart"
          title="Count ads as videos"
          value={!!diary.snapshot?.includeRecognizedAds}
          onChange={value => diary.configure('ads', value)}
          disabled={!diary.snapshot}
        />
      </SettingsGroup>

      <SettingsGroup title="Permissions" entries={settingsHelp.permissions}>
        <Row
          icon="eye"
          title="Accessibility"
          detail={
            accessibilityReconnectNeeded(snapshot)
              ? 'Disconnected. Turn this service off, then on in Android Settings.'
              : undefined
          }
          trailing={
            <Pill
              label={
                accessibilityReconnectNeeded(snapshot)
                  ? 'Disconnected'
                  : snapshot?.accessibilityEnabled
                    ? 'On'
                    : 'Off'
              }
              tone={
                snapshot?.accessibilityEnabled && snapshot.serviceConnected
                  ? 'ok'
                  : 'warn'
              }
            />
          }
          onPress={() => reels.openAccessibility()}
        />
        <Divider />
        <Row
          icon="clock"
          title="Usage access"
          trailing={
            <Pill
              label={
                diary.snapshot?.usageEnabled && diary.snapshot.usageGranted
                  ? 'On'
                  : 'Off'
              }
              tone={
                diary.snapshot?.usageEnabled && diary.snapshot.usageGranted
                  ? 'ok'
                  : 'off'
              }
            />
          }
          onPress={async () => {
            if (!diary.snapshot?.usageEnabled)
              await diary.configure('usage', true);
            await NativeDiary?.openUsageSettings();
          }}
        />
        <Divider />
        <Row
          icon="battery"
          title="Battery"
          trailing={
            <Pill
              label={
                diary.snapshot?.batteryOptimized ? 'Optimized' : 'Unrestricted'
              }
              tone={diary.snapshot?.batteryOptimized ? 'off' : 'ok'}
            />
          }
          onPress={() => NativeDiary?.openBatterySettings()}
        />
      </SettingsGroup>

      <SettingsGroup
        title="Display & experience"
        entries={settingsHelp.measurement}
      >
        <Row icon="eye" title="Live Counter" onPress={() => setCounter(true)} />
        <Divider />
        <ToggleRow
          icon="layers"
          title="Count photo encounters"
          value={diary.snapshot?.countPhotos ?? true}
          disabled={!diary.snapshot}
          onChange={value => diary.configure('photos', value)}
        />
        {diary.snapshot?.measurePlayback && (
          <ToggleRow
            icon="play"
            title="Legacy playback measurement"
            value={true}
            onChange={() => diary.configure('playback', false)}
          />
        )}
        <View style={styles.subgroup}>
          <SectionTitle>Extras</SectionTitle>
          <ToggleRow
            icon="play"
            title="Auto-scroll (experimental)"
            value={autoScroll}
            onChange={value => {
              const configure = async () => {
                try {
                  const status = parseAutoScrollStatus(
                    await NativeReels!.configureAutoScroll(value, 2),
                  );
                  setAutoScroll(status.enabled);
                  setSkipAds(status.skipStandaloneAds);
                } catch {
                  Alert.alert(
                    'Could not change auto-scroll',
                    'Please try again.',
                  );
                }
              };
              if (!value) {
                void configure();
                return;
              }
              Alert.alert(
                'Allow auto-scroll for this session?',
                'After you press Start on the floating counter, Attention Stats swipes past finished videos when readable playback confirms the end. Unknown video progress waits. You can separately choose to skip recognized ads. Use the same counter control to stop. Permission clears when the tracking service restarts.',
                [
                  { text: 'Cancel', style: 'cancel' },
                  { text: 'Allow', onPress: () => void configure() },
                ],
              );
            }}
          />
          <ToggleRow
            icon="play"
            title="Skip ads (experimental)"
            value={skipAds}
            disabled={!autoScroll}
            onChange={value => {
              const configure = async () => {
                try {
                  const status = parseAutoScrollStatus(
                    await NativeReels!.configureAutoScrollAds(value, 2),
                  );
                  setSkipAds(status.skipStandaloneAds);
                } catch {
                  Alert.alert(
                    'Could not change ad skipping',
                    'Please try again.',
                  );
                }
              };
              if (!value) {
                void configure();
                return;
              }
              Alert.alert(
                'Skip recognized ads?',
                'During a started auto-scroll session, immediately swipe past ads recognized by their own Sponsored or Ad label in TikTok, Instagram Reels and YouTube Shorts, and standalone Facebook Reels ads. Paid partnerships are not skipped. No ad links are opened. Ads playing in front of another video and provider Skip Ad buttons are not included. This choice is temporary and starts off.',
                [
                  { text: 'Cancel', style: 'cancel' },
                  { text: 'Allow', onPress: () => void configure() },
                ],
              );
            }}
          />
          <Row
            icon="play"
            title="Built-in Reels auto-scroll"
            onPress={() =>
              Alert.alert(
                'Facebook & Instagram',
                'Open a Reel and check its playback or options menu for Auto-scroll. Availability varies by app version and account. If the option is present, you can turn it on there.\n\nStop Attention Stats auto-scroll first to avoid two scrolling systems running together. Our Stop control only stops our own swipes; turn off built-in auto-scroll inside the source app.\n\nAttention Stats auto-scroll works independently and does not change either app’s built-in setting. Missing playback progress still waits.',
                [{ text: 'Got it' }],
              )
            }
          />
        </View>
        <Divider />
        <Row
          icon="layers"
          title="Add a home-screen widget"
          onPress={async () => {
            const ok = await NativeDiary?.pinWidget();
            if (!ok)
              Alert.alert(
                'Add it from your home screen',
                'Long-press an empty spot on your home screen, choose Widgets, then find Attention Stats.',
              );
          }}
        />
      </SettingsGroup>

      <SettingsGroup title="Data & privacy" entries={settingsHelp.privacy}>
        <Row
          icon="lock"
          title="Backup & restore"
          onPress={async () => {
            if (!(await NativeDiary?.openBackup()))
              Alert.alert(
                'Backup unavailable',
                'Could not open backup. Please try again.',
              );
          }}
        />
        <Divider />
        <Row
          icon="lock"
          title="Privacy policy"
          onPress={() => Linking.openURL(PRIVACY)}
        />
        <Divider />
        <Row
          icon="code"
          title="Source code"
          detail="GPL-3.0 on GitHub"
          onPress={() => Linking.openURL(REPO)}
        />
        <Divider />
        <Row
          icon="code"
          title="Open-source licenses"
          onPress={() => setLicenses(true)}
        />
        <Divider />
        <Row
          icon="trash"
          title="Erase history"
          detail="Permanent. Three confirmations."
          onPress={deleter.start}
        />
      </SettingsGroup>
      {deleter.dialog}
      <SettingsGroup
        title="Appearance"
        entries={settingsHelp.appearance}
        padded
      >
        <AppearanceEditor
          value={appearance}
          onSave={chooseAppearance}
          onDragChange={setColorDragging}
        />
      </SettingsGroup>

      <SettingsGroup title="Help & feedback" entries={settingsHelp.help}>
        <Row icon="bug" title="Report a problem" onPress={reportProblem} />
        <Divider />
        <Row icon="idea" title="Suggest an idea" onPress={suggestIdea} />
        <Divider />
        <Row
          icon="heart"
          title="Share Attention Stats"
          onPress={() =>
            Share.share({
              message: `I'm using Attention Stats to see how much I scroll. Free, private, open source: ${STORE}`,
            })
          }
        />
        <Divider />
        <Row
          icon="spark"
          title="Rate on Google Play"
          onPress={() => Linking.openURL(STORE)}
        />
        <Divider />
        <Row icon="info" title="Show the welcome tour" onPress={onWelcome} />
      </SettingsGroup>

      <View style={styles.footer}>
        <Text textBreakStrategy="balanced" style={styles.brand}>
          Attention Stats{' '}
          {snapshot?.device.appVersion.replace(/-.*/, '') ?? '1.1.1'}
        </Text>
        <Caption>No developer telemetry, no ads ever, open source</Caption>
        <Text textBreakStrategy="balanced" style={styles.maker}>
          Made with care by{' '}
          <Text
            textBreakStrategy="balanced"
            accessibilityRole="link"
            onPress={() => Linking.openURL('https://yasirarfat.com')}
            style={styles.link}
          >
            Yasir Arfat
          </Text>
        </Text>
        <Text textBreakStrategy="balanced" style={styles.publisher}>
          Published by{' '}
          <Text
            textBreakStrategy="balanced"
            accessibilityRole="link"
            onPress={() =>
              Linking.openURL(
                'https://play.google.com/store/apps/developer?id=Convenience+Labs',
              )
            }
            style={styles.link}
          >
            Convenience Labs
          </Text>
        </Text>
      </View>
      {reels.error ? <Body muted>{reels.error}</Body> : null}
      {snapshot && !snapshot.accessibilityEnabled && (
        <Button
          label="Turn on accessibility"
          onPress={() => reels.openAccessibility()}
        />
      )}
    </Screen>
  );
}

const styles = StyleSheet.create(theme => ({
  subgroup: {
    backgroundColor: theme.surfaceAlt,
    borderRadius: 16,
    padding: 8,
    marginTop: 8,
  },
  footer: { alignItems: 'center', gap: 4, paddingVertical: 16 },
  brand: { color: theme.muted, fontSize: 14, fontWeight: '700' },
  maker: { color: theme.muted, fontSize: 13, fontWeight: '600', marginTop: 8 },
  link: { color: theme.accent, fontWeight: '700' },
  publisher: { color: theme.faint, fontSize: 12 },
}));
