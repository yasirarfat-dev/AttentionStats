import { useEffect, useState } from 'react';
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
  Card,
  Divider,
  Pill,
  Row,
  SectionTitle,
  ToggleRow,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { useAppIcons, useDiary } from '../diary';
import { useReels } from '../sources';

const REPO = 'https://github.com/yasirarfat-dev/AttentionStats';
const PRIVACY = `${REPO}/blob/main/policies/PRIVACY.md`;
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

  function confirmDelete() {
    Alert.alert(
      'Delete all history?',
      'This permanently removes every count, timer and setting stored by Attention Stats on this phone. It cannot be undone.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete everything',
          style: 'destructive',
          onPress: () => void reels.deleteAll(),
        },
      ],
    );
  }

  return (
    <Screen title="Settings">
      <SectionTitle>Counting</SectionTitle>
      <Card padded={false}>
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
          detail="Nothing is counted until you resume."
          value={paused}
          onChange={value => reels.setPaused(value)}
          disabled={!snapshot}
        />
        <Divider />
        <ToggleRow
          icon="chart"
          title="Count ads as videos"
          detail="Ads are always tallied separately too."
          value={!!diary.snapshot?.includeRecognizedAds}
          onChange={value => diary.configure('ads', value)}
          disabled={!diary.snapshot}
        />
      </Card>

      <SectionTitle>Permissions</SectionTitle>
      <Card padded={false}>
        <Row
          icon="eye"
          title="Accessibility"
          detail="Needed to recognise short videos"
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
          detail="For screen time per app"
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
          detail="Some phones stop helpers in the background; choose “Unrestricted” if counting stops."
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
      </Card>

      <SectionTitle>Extras</SectionTitle>
      <Card padded={false}>
        <ToggleRow
          icon="play"
          title="Auto-scroll (experimental)"
          detail="Adds a button to the floating counter that moves to the next video when one ends, where the app shows playback progress."
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
      </Card>

      <SectionTitle>Help & feedback</SectionTitle>
      <Card padded={false}>
        <Row
          icon="bug"
          title="Report a problem"
          detail="Counting missed something? Tell us which app and screen."
          onPress={reportProblem}
        />
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
      </Card>

      <SectionTitle>Privacy</SectionTitle>
      <Card padded={false}>
        <Row
          icon="lock"
          title="Privacy policy"
          detail="Short and plain: nothing leaves your phone."
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
          title="Delete all history"
          detail="Remove every count, timer and setting"
          onPress={confirmDelete}
        />
      </Card>

      <View style={styles.footer}>
        <Text style={styles.brand}>
          Attention Stats {snapshot?.device.appVersion ?? ''}
        </Text>
        <Caption>No servers · No ads · No tracking · Open source</Caption>
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
  footer: { alignItems: 'center', gap: 4, paddingVertical: 16 },
  brand: { color: theme.muted, fontSize: 14, fontWeight: '700' },
}));
