import tokens from './paletteTokens.json';
import { scopedPalette } from './customPalette';
import { useSyncExternalStore } from 'react';
import { StyleSheet, UnistylesRuntime } from 'react-native-unistyles';

export type Appearance = {
  mode: 'system' | 'light' | 'dark';
  accent: 'graphite' | 'blue' | 'teal' | 'violet';
  motion: boolean;
  customColor: string;
  foregroundColor: string;
  backgroundColor: string;
  corners: number;
  spacing: number;
  numberSize: number;
};
/** Preference-led palettes; no promise of a psychological or medical effect. */
const base = tokens.base;
export type AppTheme = typeof base.light & {
  safe: string;
  safeSoft: string;
  corners: number;
  spacing: number;
  numberSize: number;
};
function palette(
  mode: 'light' | 'dark',
  accent: Appearance['accent'],
): AppTheme {
  return {
    ...base[mode],
    ...tokens.accents[accent][mode],
    safe: mode === 'dark' ? '#B5DECA' : '#285E49',
    safeSoft: mode === 'dark' ? '#233D32' : '#E2F1E9',
    corners: 24,
    spacing: 20,
    numberSize: 30,
  };
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
  customColor: '',
  foregroundColor: '',
  backgroundColor: '',
  corners: 24,
  spacing: 20,
  numberSize: 30,
};
const listeners = new Set<() => void>();
export function appearancePreview(next: Appearance, dark: boolean): AppTheme {
  return {
    ...scopedPalette(
      palette(dark ? 'dark' : 'light', next.accent),
      next.foregroundColor,
      next.backgroundColor,
      next.customColor,
    ),
    corners: next.corners,
    spacing: next.spacing,
    numberSize: next.numberSize,
  };
}
export function applyAppearance(next: Appearance) {
  UnistylesRuntime.updateTheme('light', () => ({
    ...scopedPalette(
      palette('light', next.accent),
      next.foregroundColor,
      next.backgroundColor,
      next.customColor,
    ),
    corners: next.corners,
    spacing: next.spacing,
    numberSize: next.numberSize,
  }));
  UnistylesRuntime.updateTheme('dark', () => ({
    ...scopedPalette(
      palette('dark', next.accent),
      next.foregroundColor,
      next.backgroundColor,
      next.customColor,
    ),
    corners: next.corners,
    spacing: next.spacing,
    numberSize: next.numberSize,
  }));
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
