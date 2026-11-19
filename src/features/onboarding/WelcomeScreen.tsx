import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  BackHandler,
  Image,
  Linking,
  ScrollView,
  Text,
  View,
} from 'react-native';
import type { ScrollViewInstance } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { reelsSources, reelsSourceKeys } from '../../platform';
import NativeDiary from '../../platform/specs/NativeDiary';
import { Icon, type IconName } from '../../ui/Icon';
import { AppIcon } from '../../ui/Charts';
import { Body, Button, Card, Pill } from '../../ui/Kit';
import { sourceIcon, useAppIcons, useDiary } from '../diary';
import { countingReady, useReels } from '../sources';

const SOURCE_URL = 'https://github.com/yasirarfat-dev/AttentionStats';

const promises: { icon: IconName; title: string; detail: string }[] = [
  {
    icon: 'phone',
    title: 'Local and encrypted by default',
    detail:
      'No developer server or app account. Automatic backup uses your Android backup settings; turn it off in Settings → Backup & restore.',
  },
  {
    icon: 'noads',
    title: 'No ads or developer telemetry',
    detail:
      'No data sent to us. Android may back up your history to your device backup account. No data sales, nothing to pay for.',
  },
  {
    icon: 'code',
    title: 'Open source',
    detail: 'Every line is public. Anyone can check what it does.',
  },
  {
    icon: 'lock',
    title: 'You stay in control',
    detail: 'Pause counting or delete everything whenever you like.',
  },
];

const features: { icon: IconName; title: string; detail: string }[] = [
  {
    icon: 'play',
    title: 'Counts recognized short videos',
    detail:
      'Reels, Shorts, TikToks and Spotlight. Detection is an estimate and varies with app layouts.',
  },
  {
    icon: 'clock',
    title: 'Shows where your time goes',
    detail: 'Time per app and in short videos, like a diary of your day.',
  },
  {
    icon: 'heart',
    title: 'Gentle breaks',
    detail: 'A calm pause after the number of videos you choose.',
  },
  {
    icon: 'block',
    title: 'Blocks, only if you want',
    detail: 'Turn off short videos per app, or set a daily limit.',
  },
];

/** First run: earn trust first, explain value second, then a guided two-step setup. */
export function WelcomeScreen({ onDone }: { onDone: () => void }) {
  const [step, setStep] = useState(0);
  const scrollRef = useRef<ScrollViewInstance>(null);
  useLayoutEffect(() => {
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [step]);
  useEffect(() => {
    if (step === 0) return;
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      setStep(step - 1);
      return true;
    });
    return () => back.remove();
  }, [step]);
  const insets = useSafeAreaInsets();
  const { theme } = useUnistyles();
  const reels = useReels(2_000);
  const diary = useDiary(1);
  const icons = useAppIcons(
    reelsSourceKeys.map(key => reelsSources[key].packageName),
  );
  const allowed = !!reels.snapshot?.sources.some(
    source => source.enabled && source.consentCurrent,
  );
  const accessibility =
    !!reels.snapshot?.accessibilityEnabled &&
    !!reels.snapshot?.serviceConnected;
  const usage =
    !!diary.snapshot?.usageEnabled && !!diary.snapshot?.usageGranted;

  async function finish() {
    await diary.configure('onboarding', true);
    onDone();
  }

  // The actions stay pinned above the navigation bar on every height; only the content scrolls.
  const footer =
    step === 0 ? (
      <>
        <Button label="Continue" onPress={() => setStep(1)} />
        <Button
          label="Read the source code"
          variant="text"
          icon="code"
          onPress={() => Linking.openURL(SOURCE_URL)}
        />
      </>
    ) : step === 1 ? (
      <>
        <Button label="Set it up" onPress={() => setStep(2)} />
        <Button label="Back" variant="text" onPress={() => setStep(0)} />
      </>
    ) : (
      <>
        <Button
          label={countingReady(reels.snapshot) ? 'Start' : 'Finish later'}
          variant={countingReady(reels.snapshot) ? 'filled' : 'tonal'}
          onPress={finish}
        />
        <Text style={styles.footnote}>
          You can change any of this in Settings.
        </Text>
        <Button label="Back" variant="text" onPress={() => setStep(1)} />
      </>
    );

  return (
    <View style={styles.scroll}>
      <ScrollView
        ref={scrollRef}
        style={styles.scroll}
        contentContainerStyle={[
          styles.page,
          { paddingTop: insets.top + 12, paddingBottom: 16 },
        ]}
      >
        <View
          accessible
          accessibilityRole="text"
          style={styles.dots}
          accessibilityLabel={`Step ${step + 1} of 3`}
        >
          {[0, 1, 2].map(index => (
            <View
              key={index}
              style={[styles.dot, index === step && styles.dotOn]}
            />
          ))}
        </View>

        {step === 0 && (
          <View style={styles.body}>
            <View style={styles.hero}>
              <Image
                source={require('../../assets/app-icon.png')}
                style={styles.logo}
                accessibilityIgnoresInvertColors
                accessibilityLabel="Attention Stats"
              />
              <Text
                textBreakStrategy="balanced"
                accessibilityRole="header"
                style={styles.headline}
              >
                Your attention,{'\n'}in your hands.
              </Text>
              <Body muted center>
                A private, open-source companion that helps you see and shape
                how you scroll.
              </Body>
            </View>
            <Card>
              {promises.map(item => (
                <View key={item.title} style={styles.promise}>
                  <View style={styles.promiseIcon}>
                    <Icon name={item.icon} color={theme.accent} size={22} />
                  </View>
                  <View style={styles.flex}>
                    <Text
                      textBreakStrategy="balanced"
                      style={styles.promiseTitle}
                    >
                      {item.title}
                    </Text>
                    <Text
                      textBreakStrategy="balanced"
                      style={styles.promiseDetail}
                    >
                      {item.detail}
                    </Text>
                  </View>
                </View>
              ))}
            </Card>
          </View>
        )}

        {step === 1 && (
          <View style={styles.body}>
            <View style={styles.hero}>
              <View style={styles.appStrip}>
                {reelsSourceKeys.map(key => (
                  <AppIcon
                    key={key}
                    uri={sourceIcon(icons, key)}
                    mark={reelsSources[key].mark}
                    color={reelsSources[key].color}
                    size={44}
                  />
                ))}
              </View>
              <Text
                textBreakStrategy="balanced"
                accessibilityRole="header"
                style={styles.headline}
              >
                Understand your scrolling,{'\n'}without judgement.
              </Text>
            </View>
            <Card>
              {features.map(item => (
                <View key={item.title} style={styles.promise}>
                  <View style={styles.promiseIcon}>
                    <Icon name={item.icon} color={theme.accent} size={22} />
                  </View>
                  <View style={styles.flex}>
                    <Text
                      textBreakStrategy="balanced"
                      style={styles.promiseTitle}
                    >
                      {item.title}
                    </Text>
                    <Text
                      textBreakStrategy="balanced"
                      style={styles.promiseDetail}
                    >
                      {item.detail}
                    </Text>
                  </View>
                </View>
              ))}
            </Card>
            <Card
              title="A little help, when you need it"
              info="Card information stays collapsed by default. Tap the same information button again to hide it; opening explanations does not change your choices."
            >
              <Body muted>
                Tap (i) at the top of a card to understand its stats or
                controls.
              </Body>
            </Card>
          </View>
        )}

        {step === 2 && (
          <View style={styles.body}>
            <Text
              textBreakStrategy="balanced"
              accessibilityRole="header"
              style={styles.headlineSmall}
            >
              Two quick steps
            </Text>
            <Card tone="soft">
              <Text textBreakStrategy="balanced" style={styles.disclosureTitle}>
                How counting works
              </Text>
              <Body muted>
                Attention Stats uses Android’s Accessibility service to notice
                when a short-video player or a feed video is on screen in
                Instagram, Facebook, YouTube, TikTok or Snapchat. It looks at
                the screen’s layout and labels in memory, only to tell one video
                from the next.
              </Body>
              <Body muted>
                It never records, saves or sends what you watch, type or
                message. Counts are stored encrypted on this phone and are
                eligible for Android backup. It only presses Back, swipes or
                taps once to pause a video for you when you turn on blocking,
                auto-scroll or mindful breaks.
              </Body>
            </Card>

            <SetupStep
              number={1}
              done={allowed}
              title="Allow counting"
              detail="Agree to the explanation above for all five apps. You can turn apps off later."
              action={
                allowed
                  ? undefined
                  : {
                      label: 'I agree, allow counting',
                      run: () => reels.enableAll(true),
                    }
              }
            />
            <SetupStep
              number={2}
              done={accessibility}
              title="Turn on accessibility"
              detail={
                'In the next screen: Installed apps (or Downloaded apps) → Attention Stats → turn it on → Allow. Then come back here.'
              }
              action={
                accessibility
                  ? undefined
                  : {
                      label: 'Open accessibility settings',
                      run: () => reels.openAccessibility(),
                    }
              }
            />
            <SetupStep
              number={3}
              done={usage}
              optional
              title="See screen time per app"
              detail="Optional. Shows how long you spend in each app, like Digital Wellbeing."
              action={
                usage
                  ? undefined
                  : {
                      label: 'Allow usage access',
                      run: async () => {
                        if (!diary.snapshot?.usageEnabled)
                          await diary.configure('usage', true);
                        await NativeDiary?.openUsageSettings();
                      },
                    }
              }
            />
          </View>
        )}
      </ScrollView>
      <View
        pointerEvents="none"
        style={[styles.statusShade, { height: insets.top }]}
      />
      <View style={[styles.footer, { paddingBottom: insets.bottom + 8 }]}>
        {footer}
      </View>
    </View>
  );
}

function SetupStep({
  number,
  done,
  title,
  detail,
  action,
  optional = false,
}: {
  number: number;
  done: boolean;
  title: string;
  detail: string;
  action?: { label: string; run: () => unknown };
  optional?: boolean;
}) {
  const { theme } = useUnistyles();
  return (
    <Card>
      <View style={styles.stepHead}>
        <View style={[styles.stepNumber, done && styles.stepDone]}>
          {done ? (
            <Icon name="check" color={theme.onAccent} size={18} />
          ) : (
            <Text textBreakStrategy="balanced" style={styles.stepNumberText}>
              {number}
            </Text>
          )}
        </View>
        <Text
          textBreakStrategy="balanced"
          style={[styles.promiseTitle, styles.flex]}
        >
          {title}
        </Text>
        {done ? (
          <Pill label="Done" />
        ) : optional ? (
          <Pill label="Optional" tone="off" />
        ) : null}
      </View>
      {!done && <Body muted>{detail}</Body>}
      {action && (
        <Button
          label={action.label}
          variant="tonal"
          onPress={() => void action.run()}
        />
      )}
    </Card>
  );
}

const styles = StyleSheet.create(theme => ({
  scroll: { flex: 1, backgroundColor: theme.canvas },
  statusShade: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    backgroundColor: theme.canvas,
  },
  page: { flexGrow: 1, paddingHorizontal: 20, gap: 12 },
  dots: { flexDirection: 'row', gap: 6, justifyContent: 'center' },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: theme.track },
  dotOn: { width: 24, backgroundColor: theme.accent },
  body: { flex: 1, gap: 14 },
  hero: { alignItems: 'center', gap: 12, paddingVertical: 8 },
  logo: { width: 96, height: 96, borderRadius: 26 },
  badge: {
    width: 76,
    height: 76,
    borderRadius: 26,
    backgroundColor: theme.hero,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headline: {
    color: theme.text,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '800',
    textAlign: 'center',
    letterSpacing: -0.4,
  },
  headlineSmall: {
    color: theme.text,
    fontSize: 26,
    fontWeight: '800',
    paddingHorizontal: 4,
  },
  appStrip: { flexDirection: 'row', gap: 10 },
  promise: { flexDirection: 'row', gap: 14, alignItems: 'flex-start' },
  promiseIcon: {
    width: 40,
    height: 40,
    borderRadius: 13,
    backgroundColor: theme.accentSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  promiseTitle: { color: theme.text, fontSize: 16, fontWeight: '700' },
  promiseDetail: {
    color: theme.muted,
    fontSize: 14,
    lineHeight: 20,
    marginTop: 2,
  },
  flex: { flex: 1 },
  footnote: {
    color: theme.muted,
    fontSize: 13,
    textAlign: 'center',
    paddingTop: 4,
  },
  footer: {
    paddingTop: 10,
    paddingHorizontal: 20,
    gap: 4,
    alignItems: 'stretch',
    backgroundColor: theme.canvas,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: theme.track,
  },
  disclosureTitle: { color: theme.text, fontSize: 16, fontWeight: '700' },
  stepHead: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  stepNumber: {
    width: 30,
    height: 30,
    borderRadius: 15,
    borderWidth: 2,
    borderColor: theme.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepDone: { backgroundColor: theme.accent, borderColor: theme.accent },
  stepNumberText: { color: theme.accent, fontWeight: '800' },
}));
