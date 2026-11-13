import { useEffect, useRef, useState } from 'react';
import { Alert, Linking, Share, Text, View } from 'react-native';
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
  Segmented,
  ToggleRow,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { SettingsGroup } from './SettingsGroup';
import { settingsHelp } from './settingsHelp';
import { useAppIcons, useDiary } from '../diary';
import { useDeleteHistory } from './DeleteHistory';
import { useReels } from '../sources';
import { applyAppearance, useAppearance } from '../../ui/theme';
import { saveAppearance } from '../../platform/appearance';

const REPO = 'https://github.com/yasirarfat-dev/AttentionStats';
const PRIVACY = `${REPO}/blob/develop/policies/PRIVACY.md`;
const EMAIL = 'consciousbiobot@gmail.com';
const STORE = 'https://play.google.com/store/apps/details?id=attention.stats';

export function SettingsScreen({ onWelcome }: { onWelcome: () => void }) {
  const reels = useReels();
  const diary = useDiary(1);
  const snapshot = reels.snapshot;
  const icons = useAppIcons(
    reelsSourceKeys.map(key => reelsSources[key].packageName),
  );
  const [autoScroll, setAutoScroll] = useState(false);
  const appearance = useAppearance();
  const appearancePending = useRef(false);
  async function chooseAppearance(next: typeof appearance) {
    if (appearancePending.current) return;
    appearancePending.current = true;
    try {
      applyAppearance(await saveAppearance(next));
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
      .then(raw => setAutoScroll(parseAutoScrollStatus(raw).enabled))
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
          `- ${source.app}: ${source.installed ? source.version : 'not installed'}${source.enabled ? '' : ' (off)'}` +
          `${source.lastRecognizedMs ? `, last recognized ${ago(Number(source.lastRecognizedMs))}` : ''}`,
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
      'Your report includes your phone model and app versions, never your history or what you watched.',
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

  const deleter = useDeleteHistory({
    onPause: () => reels.setPaused(true),
    onDelete: () => reels.deleteAll(),
    storageBytes: Number(snapshot?.storageBytes ?? 0),
  });

  return (
    <Screen title="Settings">
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
                    uri={icons[reelsSources[key].packageName]}
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
            snapshot?.accessibilityEnabled && !snapshot?.serviceConnected
              ? 'Disconnected. Turn this service off, then on in Android Settings.'
              : undefined
          }
          trailing={
            <Pill
              label={snapshot?.accessibilityEnabled ? 'On' : 'Off'}
              tone={snapshot?.accessibilityEnabled ? 'ok' : 'warn'}
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
        title="What to Measure & Extras"
        entries={settingsHelp.measurement}
      >
        <ToggleRow
          icon="layers"
          title="Count photo encounters"
          value={diary.snapshot?.countPhotos ?? true}
          disabled={!diary.snapshot}
          onChange={value => diary.configure('photos', value)}
        />
        <ToggleRow
          icon="play"
          title="Measure confirmed playback (experimental)"
          value={diary.snapshot?.measurePlayback ?? false}
          disabled={!diary.snapshot}
          onChange={value => diary.configure('playback', value)}
        />
        <View style={styles.subgroup}>
          <SectionTitle>Extras</SectionTitle>
          <ToggleRow
            icon="play"
            title="Auto-scroll (experimental)"
            value={autoScroll}
            onChange={async value => {
              try {
                setAutoScroll(
                  parseAutoScrollStatus(
                    await NativeReels!.configureAutoScroll(value, 1),
                  ).enabled,
                );
              } catch {
                /* unchanged */
              }
            }}
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

      <SettingsGroup title="Data & Privacy" entries={settingsHelp.privacy}>
        <Row
          icon="lock"
          title="Backup & restore"
          detail="Android backup and encrypted files"
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
          detail="Apache-2.0 on GitHub"
          onPress={() => Linking.openURL(REPO)}
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
        <Segmented
          label="Theme"
          value={appearance.mode}
          options={[
            { value: 'light', label: 'Light' },
            { value: 'dark', label: 'Dark' },
            { value: 'system', label: 'System' },
          ]}
          onChange={mode => void chooseAppearance({ ...appearance, mode })}
        />
        <Segmented
          label="Accent color"
          value={appearance.accent}
          options={[
            { value: 'graphite', label: 'Graphite' },
            { value: 'blue', label: 'Blue' },
            { value: 'teal', label: 'Teal' },
            { value: 'violet', label: 'Violet' },
          ]}
          onChange={accent => void chooseAppearance({ ...appearance, accent })}
        />
        <ToggleRow
          title="Gentle animations"
          value={appearance.motion}
          onChange={motion => void chooseAppearance({ ...appearance, motion })}
        />
      </SettingsGroup>

      <SettingsGroup title="Help & Feedback" entries={settingsHelp.help}>
        <Row icon="bug" title="Report a problem" onPress={reportProblem} />
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
        <Text style={styles.brand}>
          Digital Wellbeing:Attention Stats{' '}
          {snapshot?.device.appVersion.replace(/-.*/, '') ?? '1.0.0'}
        </Text>
        <Caption>No Developer Telemetry, No Ads Ever, Open Source</Caption>
        <Text style={styles.maker}>
          Made with Care by{' '}
          <Text
            accessibilityRole="link"
            onPress={() => Linking.openURL('https://yasirarfat.com')}
            style={styles.link}
          >
            Yasir Arfat
          </Text>
        </Text>
        <Text style={styles.publisher}>
          Published By{' '}
          <Text
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
          label="Turn on Accessibility"
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
