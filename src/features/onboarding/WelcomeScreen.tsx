import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {
  BackHandler,
  Image,
  Linking,
  ScrollView,
  Text,
  View,
  type LayoutChangeEvent,
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

/**
 * Fit steps, tried in order until a page fits the window: spacing shrinks first, then text a
 * little, then the optional help card goes. If the smallest step still overflows (very large
 * system text), the page scrolls with its actions at the end; nothing ever sits under them.
 */
type Fit = { space: number; text: number; trim: boolean };
const FULL: Fit = { space: 1, text: 1, trim: false };
const FIT: Fit[] = [
  FULL,
  { space: 0.8, text: 1, trim: false },
  { space: 0.65, text: 0.95, trim: false },
  { space: 0.55, text: 0.95, trim: true },
  { space: 0.5, text: 0.9, trim: true },
  { space: 0.45, text: 0.85, trim: true },
  { space: 0.4, text: 0.8, trim: true },
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

  // Measure the page against the window and step down until it fits (see FIT).
  const [viewport, setViewport] = useState(0);
  const sizes = useRef({ top: 0, footer: 0 });
  const key = `${step}|${viewport}|${allowed}|${accessibility}|${usage}`;
  const [fitState, setFitState] = useState({
    key: '',
    level: 0,
    settled: false,
  });
  const current =
    fitState.key === key ? fitState : { key, level: 0, settled: false };
  const fit = FIT[current.level] ?? FULL;
  const padTop = insets.top + Math.round(12 * fit.space);
  const padBottom = insets.bottom + 8;
  const evaluate = useCallback(() => {
    const { top, footer } = sizes.current;
    if (!viewport || !top || !footer) return;
    const gap = Math.round(16 * fit.space);
    const fits = padTop + top + gap + footer + padBottom <= viewport;
    if (!fits && current.level < FIT.length - 1)
      setFitState({ key, level: current.level + 1, settled: false });
    else if (!current.settled)
      setFitState({ key, level: current.level, settled: true });
  }, [
    viewport,
    fit.space,
    padTop,
    padBottom,
    current.level,
    current.settled,
    key,
  ]);
  useEffect(() => {
    if (current.settled) return;
    // onLayout does not fire when a step leaves a size unchanged; re-check shortly.
    const timer = setTimeout(evaluate, 120);
    return () => clearTimeout(timer);
  }, [current.settled, evaluate]);
  const measure = (part: 'top' | 'footer') => (event: LayoutChangeEvent) => {
    sizes.current[part] = event.nativeEvent.layout.height;
    evaluate();
  };
  const sp = (value: number) => Math.round(value * fit.space);
  const next = !allowed ? 1 : !accessibility ? 2 : !usage ? 3 : 0;
  const tx = (size: number, line: number) => ({
    fontSize: size * fit.text,
    lineHeight: Math.round(line * fit.text),
  });

  async function finish() {
    await diary.configure('onboarding', true);
    onDone();
  }

  // The actions end the page, under the content; the page fits the window (see FIT).
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
    ) : fit.trim ? (
      // Tight windows: Back and the finish action share one row.
      <View style={styles.footerRow}>
        <View style={styles.flex}>
          <Button label="Back" variant="text" onPress={() => setStep(1)} />
        </View>
        <View style={styles.wide}>
          <Button
            label={countingReady(reels.snapshot) ? 'Start' : 'Finish later'}
            variant={countingReady(reels.snapshot) ? 'filled' : 'tonal'}
            onPress={finish}
          />
        </View>
      </View>
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

  const item = (entry: { icon: IconName; title: string; detail: string }) => (
    <View key={entry.title} style={[styles.promise, { gap: sp(14) }]}>
      <View
        style={[
          styles.promiseIcon,
          { width: 40 * fit.text, height: 40 * fit.text },
        ]}
      >
        <Icon name={entry.icon} color={theme.accent} size={22 * fit.text} />
      </View>
      <View style={styles.flex}>
        <Text
          textBreakStrategy="balanced"
          style={[styles.promiseTitle, tx(16, 22)]}
        >
          {entry.title}
        </Text>
        <Text
          textBreakStrategy="balanced"
          style={[styles.promiseDetail, tx(14, 20)]}
        >
          {entry.detail}
        </Text>
      </View>
    </View>
  );
  const body = (text: string) => (
    <Text textBreakStrategy="balanced" style={[styles.bodyText, tx(15, 22)]}>
      {text}
    </Text>
  );

  return (
    <View
      style={styles.scroll}
      onLayout={event => setViewport(event.nativeEvent.layout.height)}
    >
      <ScrollView
        ref={scrollRef}
        style={[styles.scroll, { opacity: current.settled ? 1 : 0 }]}
        contentContainerStyle={[
          styles.page,
          { paddingTop: padTop, paddingBottom: padBottom, gap: sp(16) },
        ]}
      >
        <View onLayout={measure('top')} style={{ gap: sp(12) }}>
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
            <View style={{ gap: sp(14) }}>
              <View
                style={[styles.hero, { gap: sp(12), paddingVertical: sp(8) }]}
              >
                <Image
                  source={require('../../assets/app-icon.png')}
                  style={[
                    styles.logo,
                    {
                      width: Math.max(56, 96 * fit.space),
                      height: Math.max(56, 96 * fit.space),
                    },
                  ]}
                  accessibilityIgnoresInvertColors
                  accessibilityLabel="Attention Stats"
                />
                <Text
                  textBreakStrategy="balanced"
                  accessibilityRole="header"
                  style={[styles.headline, tx(28, 34)]}
                >
                  Your attention,{'\n'}in your hands.
                </Text>
                <Text
                  textBreakStrategy="balanced"
                  style={[styles.bodyText, styles.center, tx(15, 22)]}
                >
                  A private, open-source companion that helps you see and shape
                  how you scroll.
                </Text>
              </View>
              <Panel fit={fit}>{promises.map(item)}</Panel>
            </View>
          )}

          {step === 1 && (
            <View style={{ gap: sp(14) }}>
              <View
                style={[styles.hero, { gap: sp(12), paddingVertical: sp(8) }]}
              >
                <View style={styles.appStrip}>
                  {reelsSourceKeys.map(key => (
                    <AppIcon
                      key={key}
                      uri={sourceIcon(icons, key)}
                      mark={reelsSources[key].mark}
                      color={reelsSources[key].color}
                      size={Math.round(44 * Math.max(fit.space, 0.75))}
                    />
                  ))}
                </View>
                <Text
                  textBreakStrategy="balanced"
                  accessibilityRole="header"
                  style={[styles.headline, tx(28, 34)]}
                >
                  Understand your scrolling,{'\n'}without judgement.
                </Text>
              </View>
              <Panel fit={fit}>{features.map(item)}</Panel>
              {!fit.trim && (
                <Card
                  title="A little help, when you need it"
                  info="Card information stays collapsed by default. Tap the same information button again to hide it; opening explanations does not change your choices."
                >
                  <Body muted>
                    Tap (i) at the top of a card to understand its stats or
                    controls.
                  </Body>
                </Card>
              )}
            </View>
          )}

          {step === 2 && (
            <View style={{ gap: sp(14) }}>
              <Text
                textBreakStrategy="balanced"
                accessibilityRole="header"
                style={[styles.headlineSmall, tx(26, 32)]}
              >
                Two quick steps
              </Text>
              <Panel fit={fit} soft>
                <Text
                  textBreakStrategy="balanced"
                  style={[styles.disclosureTitle, tx(16, 22)]}
                >
                  How counting works
                </Text>
                {body(
                  'Attention Stats uses Android’s Accessibility service to notice when a short-video player or a feed video is on screen in Instagram, Facebook, YouTube, TikTok or Snapchat. It looks at the screen’s layout and labels in memory, only to tell one video from the next.',
                )}
                {body(
                  'It never records, saves or sends what you watch, type or message. Counts are stored encrypted on this phone and are eligible for Android backup. It only presses Back, swipes or taps once to pause a video for you when you turn on blocking, auto-scroll or mindful breaks.',
                )}
              </Panel>

              <SetupStep
                fit={fit}
                brief={fit.trim && next !== 1}
                lean={fit.trim}
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
                fit={fit}
                brief={fit.trim && next !== 2}
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
                fit={fit}
                brief={fit.trim && next !== 3}
                number={3}
                done={usage}
                optional
                title="See screen time per app"
                detail="Optional. Shows how long you spend in each app every day."
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
        </View>
        <View onLayout={measure('footer')} style={styles.footer}>
          {footer}
        </View>
      </ScrollView>
      <View
        pointerEvents="none"
        style={[styles.statusShade, { height: insets.top }]}
      />
    </View>
  );
}

/** A card whose padding and spacing follow the page's fit step. */
function Panel({
  fit,
  soft = false,
  children,
}: {
  fit: Fit;
  soft?: boolean;
  children: ReactNode;
}) {
  const { theme } = useUnistyles();
  return (
    <View
      style={[
        styles.panel,
        soft && styles.panelSoft,
        {
          padding: Math.round(theme.spacing * Math.max(fit.space, 0.6)),
          gap: Math.round(14 * fit.space),
        },
      ]}
    >
      {children}
    </View>
  );
}

function SetupStep({
  fit,
  brief,
  lean = false,
  number,
  done,
  title,
  detail,
  action,
  optional = false,
}: {
  fit: Fit;
  brief: boolean;
  /** Hide the hint but keep the action (its label says the same). */
  lean?: boolean;
  number: number;
  done: boolean;
  title: string;
  detail: string;
  action?: { label: string; run: () => unknown };
  optional?: boolean;
}) {
  const { theme } = useUnistyles();
  return (
    <Panel fit={fit}>
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
          style={[
            styles.promiseTitle,
            styles.flex,
            { fontSize: 16 * fit.text, lineHeight: Math.round(22 * fit.text) },
          ]}
        >
          {title}
        </Text>
        {done ? (
          <Pill label="Done" />
        ) : optional ? (
          <Pill label="Optional" tone="off" />
        ) : null}
      </View>
      {!done && !brief && !lean && (
        <Text
          textBreakStrategy="balanced"
          style={[
            styles.bodyText,
            { fontSize: 15 * fit.text, lineHeight: Math.round(22 * fit.text) },
          ]}
        >
          {detail}
        </Text>
      )}
      {action && !brief && (
        <Button
          label={action.label}
          variant="tonal"
          onPress={() => void action.run()}
        />
      )}
    </Panel>
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
  page: {
    flexGrow: 1,
    paddingHorizontal: 20,
    justifyContent: 'space-between',
  },
  dots: { flexDirection: 'row', gap: 6, justifyContent: 'center' },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: theme.track },
  dotOn: { width: 24, backgroundColor: theme.accent },
  hero: { alignItems: 'center' },
  panel: {
    backgroundColor: theme.surface,
    borderRadius: theme.corners,
    borderWidth: 1,
    borderColor: theme.border,
  },
  panelSoft: {
    backgroundColor: theme.surfaceAlt,
    borderColor: theme.surfaceAlt,
  },
  bodyText: { color: theme.muted },
  center: { textAlign: 'center', alignSelf: 'stretch' },
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
    alignSelf: 'stretch',
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
  footer: { gap: 4, alignItems: 'stretch' },
  footerRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  wide: { flex: 2 },
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
