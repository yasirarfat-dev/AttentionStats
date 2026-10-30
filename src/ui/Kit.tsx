import type { ReactNode } from 'react';
import { Pressable, Switch, Text, View } from 'react-native';
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
        <Text style={styles.rowTitle} numberOfLines={1}>
          {title}
        </Text>
        {detail ? (
          <Text style={styles.rowDetail} numberOfLines={3}>
            {detail}
          </Text>
        ) : null}
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
}: {
  icon?: IconName;
  leading?: ReactNode;
  title: string;
  detail?: string;
  value: boolean;
  onChange: (next: boolean) => void;
  disabled?: boolean;
}) {
  const { theme } = useUnistyles();
  return (
    <Row
      icon={icon}
      leading={leading}
      title={title}
      detail={detail}
      trailing={
        <Switch
          accessibilityLabel={title}
          value={value}
          disabled={disabled}
          onValueChange={onChange}
          trackColor={{ true: theme.accent, false: theme.track }}
          thumbColor={value ? theme.onAccent : theme.surface}
        />
      }
    />
  );
}

export function Divider() {
  return <View style={styles.divider} />;
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
      <Text
        style={[styles.statLabel, inverse && styles.inverseMuted]}
        numberOfLines={1}
      >
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
  variant?: 'filled' | 'tonal' | 'text' | 'danger';
  disabled?: boolean;
  icon?: IconName;
}) {
  const { theme } = useUnistyles();
  const color =
    variant === 'filled'
      ? theme.onAccent
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
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (next: T) => void;
  label: string;
}) {
  return (
    <View
      style={styles.segmented}
      accessibilityRole="radiogroup"
      accessibilityLabel={label}
    >
      {options.map(option => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="radio"
            accessibilityState={{ selected }}
            onPress={() => onChange(option.value)}
            style={[styles.segment, selected && styles.segmentOn]}
          >
            <Text
              style={[styles.segmentText, selected && styles.segmentTextOn]}
            >
              {option.label}
            </Text>
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
            width: `${Math.max(2, Math.min(100, value * 100))}%`,
            backgroundColor: color,
          },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create(theme => ({
  card: {
    backgroundColor: theme.surface,
    borderRadius: 24,
    padding: 20,
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
    color: theme.muted,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  title: { color: theme.text, fontSize: 22, lineHeight: 28, fontWeight: '700' },
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
  rowTitle: { color: theme.text, fontSize: 16, fontWeight: '600' },
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
    fontSize: 30,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  statLabel: { color: theme.muted, fontSize: 13, fontWeight: '500' },
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
    backgroundColor: theme.accent,
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
  buttonLabel: { fontSize: 16, fontWeight: '700' },
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
  segmented: {
    flexDirection: 'row',
    backgroundColor: theme.surfaceAlt,
    borderRadius: 16,
    padding: 4,
    gap: 4,
  },
  segment: {
    flex: 1,
    minHeight: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 6,
  },
  segmentOn: { backgroundColor: theme.surface },
  segmentText: { color: theme.muted, fontSize: 14, fontWeight: '600' },
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
