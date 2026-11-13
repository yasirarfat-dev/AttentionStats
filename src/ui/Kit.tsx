import { useState, type ReactNode } from 'react';
import {
  Pressable,
  Switch,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { Icon, type IconName } from './Icon';

/** A grouped surface. `tone` changes the surface for emphasis without adding visual noise. */
export function Card({
  children,
  tone = 'plain',
  padded = true,
}: {
  children: ReactNode;
  tone?: 'plain' | 'soft' | 'hero' | 'warn';
  padded?: boolean;
}) {
  return (
    <View
      style={[
        styles.card,
        !padded && styles.unpadded,
        tone === 'soft' && styles.soft,
        tone === 'hero' && styles.hero,
        tone === 'warn' && styles.warn,
      ]}
    >
      {children}
    </View>
  );
}

export function SectionTitle({
  children,
  action,
}: {
  children: string;
  action?: ReactNode;
}) {
  return (
    <View style={styles.sectionRow}>
      <Text accessibilityRole="header" style={styles.section}>
        {children}
      </Text>
      {action}
    </View>
  );
}

export function Title({ children }: { children: ReactNode }) {
  return (
    <Text accessibilityRole="header" style={styles.title}>
      {children}
    </Text>
  );
}

export function Body({
  children,
  muted = false,
  center = false,
}: {
  children: ReactNode;
  muted?: boolean;
  center?: boolean;
}) {
  return (
    <Text style={[styles.body, muted && styles.muted, center && styles.center]}>
      {children}
    </Text>
  );
}

export function Caption({ children }: { children: ReactNode }) {
  return <Text style={styles.caption}>{children}</Text>;
}

/** Optional explanations stay discoverable without occupying the default view. */
export function InfoButton({
  label,
  expanded,
  onPress,
  inverse = false,
}: {
  label: string;
  expanded: boolean;
  onPress: () => void;
  inverse?: boolean;
}) {
  const { theme } = useUnistyles();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`About ${label}`}
      accessibilityState={{ expanded }}
      onPress={onPress}
      style={styles.infoButton}
    >
      <Icon
        name="info"
        size={22}
        color={inverse ? theme.onHero : theme.accent}
      />
    </Pressable>
  );
}

export function Explanation({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  const [expanded, setExpanded] = useState(false);
  return (
    <>
      <SectionTitle
        action={
          <InfoButton
            label={title}
            expanded={expanded}
            onPress={() => setExpanded(!expanded)}
          />
        }
      >
        {title}
      </SectionTitle>
      {expanded && <Body muted>{children}</Body>}
    </>
  );
}

/** List row: optional leading visual, title/detail, trailing value or control. */
export function Row({
  leading,
  icon,
  title,
  detail,
  value,
  trailing,
  onPress,
  accessibilityLabel,
}: {
  leading?: ReactNode;
  icon?: IconName;
  title: string;
  detail?: string;
  value?: string;
  trailing?: ReactNode;
  onPress?: () => void;
  accessibilityLabel?: string;
}) {
  const { theme } = useUnistyles();
  const content = (
    <>
      {leading ??
        (icon && (
          <View style={styles.iconBubble}>
            <Icon name={icon} color={theme.accent} size={20} />
          </View>
        ))}
      <View style={styles.rowText}>
        <Text style={styles.rowTitle} numberOfLines={2}>
          {title}
        </Text>
        {detail ? <Text style={styles.rowDetail}>{detail}</Text> : null}
      </View>
      {value ? (
        <Text style={styles.rowValue} numberOfLines={1}>
          {value}
        </Text>
      ) : null}
      {trailing}
      {onPress && !trailing ? (
        <Icon name="chevron" color={theme.faint} size={18} />
      ) : null}
    </>
  );
  if (!onPress)
    return (
      <View
        style={styles.row}
        accessible
        accessibilityLabel={
          accessibilityLabel ??
          [title, value, detail].filter(Boolean).join(', ')
        }
      >
        {content}
      </View>
    );
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={
        accessibilityLabel ?? [title, value, detail].filter(Boolean).join(', ')
      }
      onPress={onPress}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}
    >
      {content}
    </Pressable>
  );
}

export function ToggleRow({
  icon,
  leading,
  title,
  detail,
  value,
  onChange,
  disabled = false,
  discloseDetail = false,
}: {
  icon?: IconName;
  leading?: ReactNode;
  title: string;
  detail?: string;
  value: boolean;
  onChange: (next: boolean) => void;
  disabled?: boolean;
  discloseDetail?: boolean;
}) {
  const { theme } = useUnistyles();
  const [expanded, setExpanded] = useState(false);
  const row = (
    <Row
      icon={icon}
      leading={leading}
      title={title}
      detail={discloseDetail ? undefined : detail}
      onPress={disabled ? undefined : () => onChange(!value)}
      accessibilityLabel={`${title}, ${value ? 'on' : 'off'}`}
      trailing={
        <Switch
          accessibilityLabel={title}
          value={value}
          disabled={disabled}
          onValueChange={onChange}
          trackColor={{ true: theme.accentFill, false: theme.track }}
          thumbColor={value ? theme.onAccent : theme.surface}
        />
      }
    />
  );
  if (!discloseDetail || !detail) return row;
  return (
    <View>
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <View style={{ flex: 1 }}>
          <Row
            icon={icon}
            leading={leading}
            title={title}
            onPress={disabled ? undefined : () => onChange(!value)}
            trailing={<View />}
            accessibilityLabel={`${title}, ${value ? 'on' : 'off'}`}
          />
        </View>
        <InfoButton
          label={title}
          expanded={expanded}
          onPress={() => setExpanded(!expanded)}
        />
        <View style={{ paddingRight: 12 }}>
          <Switch
            accessibilityLabel={title}
            value={value}
            disabled={disabled}
            onValueChange={onChange}
            trackColor={{ true: theme.accentFill, false: theme.track }}
            thumbColor={value ? theme.onAccent : theme.surface}
          />
        </View>
      </View>
      {expanded && (
        <View style={{ paddingHorizontal: 12, paddingBottom: 12 }}>
          <Body muted>{detail}</Body>
        </View>
      )}
    </View>
  );
}

export function Divider() {
  return <View style={styles.divider} />;
}

/** Exact values aligned with their meaning; adapt to large type rather than clip digits. */
export function MetricLine({ label, value }: { label: string; value: string }) {
  const { fontScale, width } = useWindowDimensions();
  return (
    <View
      accessible
      accessibilityLabel={`${label}: ${value}`}
      style={[
        styles.metricLine,
        (fontScale > 1.3 || width < 340) && {
          flexDirection: 'column',
          alignItems: 'flex-start',
        },
      ]}
    >
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue}>{value}</Text>
    </View>
  );
}

export function CheckRow({
  title,
  value,
  onChange,
  disabled = false,
  leading,
  compact = false,
}: {
  title: string;
  value: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
  leading?: ReactNode;
  compact?: boolean;
}) {
  const { theme } = useUnistyles();
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={title}
      accessibilityState={{ checked: value, disabled }}
      disabled={disabled}
      onPress={() => onChange(!value)}
      style={[
        styles.row,
        compact && { minHeight: 48, paddingVertical: 8 },
        disabled && styles.disabled,
      ]}
    >
      {leading}
      <Text style={[styles.rowTitle, { flex: 1 }]}>{title}</Text>
      <View
        style={{
          width: 24,
          height: 24,
          borderRadius: 6,
          borderWidth: 2,
          borderColor: value ? theme.accent : theme.muted,
          backgroundColor: value ? theme.accentFill : 'transparent',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {value && <Icon name="check" size={18} color={theme.onAccent} />}
      </View>
    </Pressable>
  );
}

/** Short visual separator; decorative and excluded from accessibility traversal. */
export function StatDivider({ inverse = false }: { inverse?: boolean }) {
  const { theme } = useUnistyles();
  return (
    <View
      accessible={false}
      importantForAccessibility="no"
      pointerEvents="none"
      style={{
        width: 1,
        height: 30,
        alignSelf: 'center',
        backgroundColor: inverse ? theme.heroMuted : theme.border,
        opacity: inverse ? 0.3 : 0.65,
      }}
    />
  );
}

/** Large number with a small label, for KPI tiles. */
export function Stat({
  value,
  label,
  accessibilityLabel,
  inverse = false,
}: {
  value: string;
  label: string;
  accessibilityLabel?: string;
  inverse?: boolean;
}) {
  return (
    <View
      style={styles.stat}
      accessible
      accessibilityLabel={accessibilityLabel ?? `${label}: ${value}`}
    >
      <Text
        style={[styles.statValue, inverse && styles.inverse]}
        numberOfLines={1}
        adjustsFontSizeToFit
      >
        {value}
      </Text>
      <Text style={[styles.statLabel, inverse && styles.inverseMuted]}>
        {label}
      </Text>
    </View>
  );
}

export function Pill({
  label,
  tone = 'ok',
}: {
  label: string;
  tone?: 'ok' | 'warn' | 'off';
}) {
  return (
    <View
      style={[
        styles.pill,
        tone === 'warn' && styles.pillWarn,
        tone === 'off' && styles.pillOff,
      ]}
    >
      <Text
        style={[
          styles.pillText,
          tone === 'warn' && styles.pillWarnText,
          tone === 'off' && styles.pillOffText,
        ]}
      >
        {label}
      </Text>
    </View>
  );
}

export function Button({
  label,
  onPress,
  variant = 'filled',
  disabled = false,
  icon,
}: {
  label: string;
  onPress: () => void;
  variant?: 'filled' | 'tonal' | 'text' | 'danger' | 'safe';
  disabled?: boolean;
  icon?: IconName;
}) {
  const { theme } = useUnistyles();
  const color =
    variant === 'filled'
      ? theme.onAccent
      : variant === 'safe'
        ? theme.safe
        : variant === 'danger'
          ? theme.danger
          : theme.accent;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        variant === 'tonal' && styles.tonal,
        variant === 'text' && styles.textButton,
        variant === 'danger' && styles.dangerButton,
        variant === 'safe' && { backgroundColor: theme.safeSoft },
        pressed && styles.buttonPressed,
        disabled && styles.disabled,
      ]}
    >
      {icon ? <Icon name={icon} color={color} size={20} /> : null}
      <Text style={[styles.buttonLabel, { color }]}>{label}</Text>
    </Pressable>
  );
}

/** −/+ control for small integers; large touch targets, value announced. */
export function Stepper({
  value,
  onChange,
  min,
  max,
  step = 1,
  suffix,
  label,
}: {
  value: number;
  onChange: (next: number) => void;
  min: number;
  max: number;
  step?: number;
  suffix: string;
  label: string;
}) {
  return (
    <View
      style={styles.stepper}
      accessible
      accessibilityLabel={`${label}: ${value} ${suffix}`}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={event =>
        onChange(
          Math.min(
            max,
            Math.max(
              min,
              value +
                (event.nativeEvent.actionName === 'increment' ? step : -step),
            ),
          ),
        )
      }
    >
      <Pressable
        accessibilityLabel={`Decrease ${label}`}
        onPress={() => onChange(Math.max(min, value - step))}
        style={({ pressed }) => [styles.stepButton, pressed && styles.pressed]}
      >
        <Text style={styles.stepSign}>−</Text>
      </Pressable>
      <View style={styles.stepValueBox}>
        <Text style={styles.stepValue}>{value}</Text>
        <Text style={styles.stepSuffix}>{suffix}</Text>
      </View>
      <Pressable
        accessibilityLabel={`Increase ${label}`}
        onPress={() => onChange(Math.min(max, value + step))}
        style={({ pressed }) => [styles.stepButton, pressed && styles.pressed]}
      >
        <Text style={styles.stepSign}>+</Text>
      </Pressable>
    </View>
  );
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
  label,
  disabled = false,
  singleLine = false,
}: {
  options: {
    value: T;
    label: string;
    icon?: IconName;
    accessibilityLabel?: string;
  }[];
  value: T;
  onChange: (next: T) => void;
  label: string;
  disabled?: boolean;
  singleLine?: boolean;
}) {
  const { theme } = useUnistyles();
  const { fontScale } = useWindowDimensions();
  const wrap = singleLine && fontScale > 1.3;
  return (
    <View
      style={[styles.segmented, wrap && styles.segmentedWrap]}
      accessibilityRole="radiogroup"
      accessibilityLabel={label}
    >
      {options.map(option => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="radio"
            accessibilityLabel={option.accessibilityLabel ?? option.label}
            accessibilityState={{ selected, disabled }}
            disabled={disabled}
            onPress={() => onChange(option.value)}
            style={[
              styles.segment,
              wrap && styles.segmentWrapped,
              selected && styles.segmentOn,
            ]}
          >
            <View style={styles.segmentContent}>
              {option.icon && (
                <Icon
                  name={option.icon}
                  size={16}
                  color={selected ? theme.text : theme.muted}
                />
              )}
              <Text
                numberOfLines={singleLine ? 1 : undefined}
                adjustsFontSizeToFit={singleLine}
                minimumFontScale={0.9}
                style={[styles.segmentText, selected && styles.segmentTextOn]}
              >
                {option.label}
              </Text>
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

/** Thin horizontal proportion bar. */
export function Meter({ value, color }: { value: number; color: string }) {
  return (
    <View style={styles.meterTrack}>
      <View
        style={[
          styles.meterFill,
          {
            width: `${Math.max(0, Math.min(100, value * 100))}%`,
            backgroundColor: color,
          },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create(theme => ({
  metricLine: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    gap: 8,
    paddingVertical: 5,
  },
  metricLabel: {
    color: theme.muted,
    fontSize: 14,
    lineHeight: 21,
    flexShrink: 1,
  },
  metricValue: {
    color: theme.text,
    fontSize: 19,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  infoButton: {
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: {
    backgroundColor: theme.surface,
    borderRadius: theme.corners,
    padding: theme.spacing,
    gap: 14,
    borderWidth: 1,
    borderColor: theme.border,
  },
  unpadded: { paddingHorizontal: 8, paddingVertical: 8, gap: 0 },
  soft: { backgroundColor: theme.surfaceAlt, borderColor: theme.surfaceAlt },
  hero: { backgroundColor: theme.hero, borderColor: theme.hero },
  warn: { backgroundColor: theme.warnSoft, borderColor: theme.warnSoft },
  sectionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 8,
    paddingHorizontal: 4,
  },
  section: {
    flexShrink: 1,
    textTransform: 'capitalize',
    color: theme.muted,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.8,
  },
  title: {
    textTransform: 'capitalize',
    color: theme.text,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '700',
  },
  body: { color: theme.text, fontSize: 15, lineHeight: 22 },
  muted: { color: theme.muted },
  center: { textAlign: 'center' },
  caption: { color: theme.faint, fontSize: 12, lineHeight: 17 },
  row: {
    minHeight: 60,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 16,
  },
  pressed: { backgroundColor: theme.tint },
  iconBubble: {
    width: 38,
    height: 38,
    borderRadius: 12,
    backgroundColor: theme.accentSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowText: { flex: 1, gap: 2 },
  rowTitle: {
    textTransform: 'capitalize',
    color: theme.text,
    fontSize: 16,
    fontWeight: '600',
  },
  rowDetail: { color: theme.muted, fontSize: 13, lineHeight: 18 },
  rowValue: {
    color: theme.text,
    fontSize: 16,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  divider: { height: 1, backgroundColor: theme.border, marginHorizontal: 12 },
  stat: { flex: 1, gap: 2, minWidth: 0 },
  statValue: {
    color: theme.text,
    fontSize: theme.numberSize,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  statLabel: {
    textTransform: 'capitalize',
    color: theme.muted,
    fontSize: 13,
    fontWeight: '500',
  },
  inverse: { color: theme.onHero },
  inverseMuted: { color: theme.heroMuted },
  pill: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    backgroundColor: theme.accentSoft,
  },
  pillWarn: { backgroundColor: theme.warnSoft },
  pillOff: { backgroundColor: theme.surfaceAlt },
  pillText: { color: theme.accent, fontSize: 12, fontWeight: '700' },
  pillWarnText: { color: theme.warn },
  pillOffText: { color: theme.muted },
  button: {
    minHeight: 52,
    borderRadius: 26,
    paddingHorizontal: 22,
    backgroundColor: theme.accentFill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  tonal: { backgroundColor: theme.accentSoft },
  textButton: { backgroundColor: 'transparent', minHeight: 44 },
  dangerButton: { backgroundColor: theme.dangerSoft },
  buttonPressed: { opacity: 0.8 },
  disabled: { opacity: 0.45 },
  buttonLabel: { textTransform: 'capitalize', fontSize: 16, fontWeight: '700' },
  stepper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.surfaceAlt,
    borderRadius: 18,
    padding: 4,
  },
  stepButton: {
    width: 48,
    height: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepSign: { color: theme.accent, fontSize: 26, fontWeight: '500' },
  stepValueBox: { flex: 1, alignItems: 'center' },
  stepValue: {
    color: theme.text,
    fontSize: 22,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  stepSuffix: { color: theme.muted, fontSize: 12 },
  segmentContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    flexShrink: 1,
  },
  segmentedWrap: { flexWrap: 'wrap' },
  segmentWrapped: { flexBasis: '45%', flexGrow: 1, flexShrink: 0 },
  segmented: {
    flexDirection: 'row',
    backgroundColor: theme.surfaceAlt,
    borderRadius: 16,
    padding: 4,
    gap: 4,
  },
  segment: {
    flex: 1,
    minHeight: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 6,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  segmentOn: { backgroundColor: theme.accentSoft, borderColor: theme.accent },
  segmentText: {
    textTransform: 'capitalize',
    color: theme.muted,
    fontSize: 14,
    fontWeight: '600',
  },
  segmentTextOn: { color: theme.text },
  meterTrack: {
    alignSelf: 'stretch',
    height: 6,
    borderRadius: 3,
    backgroundColor: theme.track,
    overflow: 'hidden',
  },
  meterFill: { height: 6, borderRadius: 3 },
}));
