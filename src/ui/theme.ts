import tokens from './paletteTokens.json';
import { useSyncExternalStore } from 'react';
import { StyleSheet, UnistylesRuntime } from 'react-native-unistyles';

export type Appearance = {
  mode: 'system' | 'light' | 'dark';
  accent: 'graphite' | 'blue' | 'teal' | 'violet';
  motion: boolean;
};
/** Preference-led palettes; no promise of a psychological or medical effect. */
const base = tokens.base;
export type AppTheme = typeof base.light;
function palette(
  mode: 'light' | 'dark',
  accent: Appearance['accent'],
): AppTheme {
  return { ...base[mode], ...tokens.accents[accent][mode] };
}
export const themes = {
  light: palette('light', 'graphite'),
  dark: palette('dark', 'graphite'),
};
type AppThemes = typeof themes;
declare module 'react-native-unistyles' {
  export interface UnistylesThemes extends AppThemes {}
}
StyleSheet.configure({ themes, settings: { adaptiveThemes: true } });
let appearance: Appearance = {
  mode: 'system',
  accent: 'graphite',
  motion: true,
};
const listeners = new Set<() => void>();
export function applyAppearance(next: Appearance) {
  UnistylesRuntime.updateTheme('light', () => palette('light', next.accent));
  UnistylesRuntime.updateTheme('dark', () => palette('dark', next.accent));
  UnistylesRuntime.setAdaptiveThemes(next.mode === 'system');
  if (next.mode !== 'system') UnistylesRuntime.setTheme(next.mode);
  appearance = next;
  listeners.forEach(listener => listener());
}
const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};
export function useAppearance() {
  return useSyncExternalStore(subscribe, () => appearance);
}
