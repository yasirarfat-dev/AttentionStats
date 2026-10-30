import { Image, Text, View } from 'react-native';
import Svg, { Rect } from 'react-native-svg';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';

/** Vertical bars with sparse labels; the highlighted bar (e.g. "now" or "today") is solid. */
export function BarChart({
  values,
  labels,
  highlight,
  height = 120,
  accessibilityLabel,
  color,
}: {
  values: number[];
  labels: string[];
  highlight?: number;
  height?: number;
  accessibilityLabel: string;
  color?: string;
}) {
  const { theme } = useUnistyles();
  const max = Math.max(1, ...values);
  const fill = color ?? theme.accent;
  return (
    <View
      accessible
      accessibilityLabel={accessibilityLabel}
      style={styles.chart}
    >
      <Svg
        width="100%"
        height={height}
        viewBox={`0 0 ${values.length * 10} ${height}`}
        preserveAspectRatio="none"
      >
        {values.map((value, index) => {
          const h = value > 0 ? Math.max(3, (value / max) * (height - 4)) : 2;
          return (
            <Rect
              key={index}
              x={index * 10 + 1.5}
              y={height - h}
              width={7}
              height={h}
              rx={2.5}
              fill={value > 0 ? fill : theme.track}
              opacity={
                highlight === undefined || highlight === index || value === 0
                  ? 1
                  : 0.55
              }
            />
          );
        })}
      </Svg>
      <View style={styles.axis}>
        {labels.map((label, index) => (
          <Text key={index} style={styles.axisLabel} numberOfLines={1}>
            {label}
          </Text>
        ))}
      </View>
    </View>
  );
}

/** One rounded bar split into coloured segments (share per app). */
export function StackedBar({
  parts,
}: {
  parts: { value: number; color: string; key: string }[];
}) {
  const { theme } = useUnistyles();
  const total = parts.reduce((sum, part) => sum + part.value, 0);
  return (
    <View
      style={[styles.stack, { backgroundColor: theme.track }]}
      importantForAccessibility="no-hide-descendants"
    >
      {total > 0 &&
        parts
          .filter(part => part.value > 0)
          .map(part => (
            <View
              key={part.key}
              style={{ flex: part.value, backgroundColor: part.color }}
            />
          ))}
    </View>
  );
}

/** App logo from a data URI, or a coloured monogram when the app is not installed. */
export function AppIcon({
  uri,
  mark,
  color,
  size = 40,
}: {
  uri?: string;
  mark: string;
  color: string;
  size?: number;
}) {
  if (uri)
    return (
      <Image
        source={{ uri }}
        style={{ width: size, height: size, borderRadius: size * 0.28 }}
        accessibilityIgnoresInvertColors
      />
    );
  return (
    <View
      style={[
        styles.mark,
        {
          width: size,
          height: size,
          borderRadius: size * 0.28,
          backgroundColor: color,
        },
      ]}
    >
      <Text style={[styles.markText, { fontSize: size * 0.36 }]}>{mark}</Text>
    </View>
  );
}

const styles = StyleSheet.create(theme => ({
  chart: { gap: 6 },
  axis: { flexDirection: 'row', justifyContent: 'space-between' },
  axisLabel: {
    color: theme.faint,
    fontSize: 11,
    fontVariant: ['tabular-nums'],
  },
  stack: {
    height: 10,
    borderRadius: 5,
    overflow: 'hidden',
    flexDirection: 'row',
    gap: 2,
  },
  mark: { alignItems: 'center', justifyContent: 'center' },
  markText: { color: '#FFFFFF', fontWeight: '800' },
}));
