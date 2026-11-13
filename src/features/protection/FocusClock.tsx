import { useEffect, useState } from 'react';
import { AppState, Text, View } from 'react-native';
import Svg, { Circle, Line } from 'react-native-svg';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
const monotonicNow = () =>
  (
    globalThis as unknown as { performance: { now: () => number } }
  ).performance.now();

/** Native remaining time is authoritative; interpolate only the displayed countdown. */
export function FocusClock({
  remainingMs,
  durationMs,
}: {
  remainingMs: string;
  durationMs?: string;
}) {
  const { theme } = useUnistyles();
  const [sample, setSample] = useState({
    at: monotonicNow(),
    remaining: Number(remainingMs),
  });
  const [now, setNow] = useState(monotonicNow());
  useEffect(() => {
    setSample({ at: monotonicNow(), remaining: Number(remainingMs) });
  }, [remainingMs]);
  useEffect(() => {
    const timer = setInterval(() => {
      if (AppState.currentState === 'active') setNow(monotonicNow());
    }, 1000);
    return () => clearInterval(timer);
  }, []);
  const left = Math.max(0, sample.remaining - Math.max(0, now - sample.at));
  const seconds = Math.ceil(left / 1000);
  const fraction = durationMs ? Math.min(1, left / Number(durationMs)) : 1;
  return (
    <View
      style={styles.clock}
      accessible
      accessibilityLabel={`${Math.ceil(left / 60_000)} minutes remaining. End whenever you need.`}
    >
      <Svg width={180} height={180} viewBox="0 0 180 180" accessible={false}>
        <Circle
          cx={90}
          cy={90}
          r={77}
          stroke={theme.track}
          strokeWidth={6}
          fill="none"
        />
        <Circle
          cx={90}
          cy={90}
          r={77}
          stroke={theme.accent}
          strokeWidth={6}
          strokeLinecap="round"
          fill="none"
          strokeDasharray={`${2 * Math.PI * 77}`}
          strokeDashoffset={2 * Math.PI * 77 * (1 - fraction)}
          rotation={-90}
          origin="90,90"
        />
        {Array.from({ length: 12 }, (_, i) => (
          <Line
            key={i}
            x1={90}
            y1={20}
            x2={90}
            y2={24}
            stroke={theme.muted}
            strokeWidth={1}
            rotation={i * 30}
            origin="90,90"
          />
        ))}
      </Svg>
      <View style={styles.face}>
        <Text style={styles.time}>
          {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
        </Text>
        <Text style={styles.label}>remaining</Text>
      </View>
    </View>
  );
}
const styles = StyleSheet.create(theme => ({
  clock: { width: 180, height: 180, alignSelf: 'center', marginVertical: 8 },
  face: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
  },
  time: {
    color: theme.text,
    fontSize: 32,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  label: { color: theme.muted, fontSize: 13, marginTop: 4 },
}));
