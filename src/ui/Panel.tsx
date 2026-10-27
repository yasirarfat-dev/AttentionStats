import type { ReactNode } from 'react';
import { Pressable, Text, View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';

export function Panel({
  title,
  children,
  tinted = false,
}: {
  title: string;
  children: ReactNode;
  tinted?: boolean;
}) {
  return (
    <View style={[styles.panel, tinted && styles.tinted]}>
      <Text accessibilityRole="header" style={styles.title}>
        {title}
      </Text>
      {children}
    </View>
  );
}

export function Destination({
  title,
  detail,
  onPress,
}: {
  title: string;
  detail: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed }) => [styles.destination, pressed && styles.pressed]}
    >
      <View style={styles.grow}>
        <Text style={styles.destinationTitle}>{title}</Text>
        <Text style={styles.detail}>{detail}</Text>
      </View>
      <Text importantForAccessibility="no" style={styles.arrow}>
        ›
      </Text>
    </Pressable>
  );
}

export function UnavailableMetric({
  label,
  detail,
}: {
  label: string;
  detail: string;
}) {
  return (
    <View style={styles.metric}>
      <Text style={styles.detail}>{label}</Text>
      <Text style={styles.value} accessibilityLabel={`${label}: unavailable`}>
        —
      </Text>
      <Text style={styles.detail}>{detail}</Text>
    </View>
  );
}

const styles = StyleSheet.create(theme => ({
  panel: {
    backgroundColor: theme.surface,
    borderRadius: 28,
    padding: 22,
    gap: 16,
  },
  tinted: { backgroundColor: theme.surfaceAlt },
  title: { fontSize: 21, lineHeight: 28, color: theme.text, fontWeight: '600' },
  destination: {
    minHeight: 72,
    paddingVertical: 12,
    gap: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },
  destinationTitle: {
    color: theme.text,
    fontSize: 17,
    lineHeight: 24,
    fontWeight: '500',
  },
  detail: { color: theme.muted, fontSize: 14, lineHeight: 21 },
  arrow: { fontSize: 30, color: theme.muted },
  grow: { flex: 1, gap: 4 },
  pressed: { backgroundColor: theme.tint, borderRadius: 16 },
  metric: { gap: 8 },
  value: {
    color: theme.text,
    fontSize: 48,
    fontWeight: '400',
    fontVariant: ['tabular-nums'],
  },
}));
