import { useSyncExternalStore } from 'react';
import { StyleSheet, UnistylesRuntime } from 'react-native-unistyles';

export type Appearance = {
  mode: 'system' | 'light' | 'dark';
  accent: 'blue' | 'teal' | 'violet';
  motion: boolean;
};
/** Preference-led palettes; no promise of a psychological or medical effect. */
const base = {
  light: {
    canvas: '#F5F7FC',
    surface: '#FFFFFF',
    surfaceAlt: '#EDF1F8',
    raised: '#FFFFFF',
    text: '#18253D',
    muted: '#53627B',
    faint: '#5C6C86',
    accent: '#285CD6',
    accentSoft: '#E7EEFF',
    onAccent: '#FFFFFF',
    border: '#DCE3EF',
    tint: '#EAF0FC',
    lavender: '#EEE9FB',
    onLavender: '#58408A',
    warn: '#855000',
    warnSoft: '#FFF0D8',
    danger: '#AD2929',
    dangerSoft: '#FDE8E7',
    hero: '#203B71',
    onHero: '#F7F9FF',
    heroMuted: '#CDDAF2',
    track: '#DCE4F2',
  },
  dark: {
    canvas: '#0D1421',
    surface: '#151F30',
    surfaceAlt: '#1B2940',
    raised: '#22314A',
    text: '#ECF1FA',
    muted: '#B0BFD6',
    faint: '#91A3BF',
    accent: '#ABC3FF',
    accentSoft: '#23385E',
    onAccent: '#112B59',
    border: '#30415D',
    tint: '#1F304D',
    lavender: '#302A49',
    onLavender: '#DBCAFF',
    warn: '#FFD18A',
    warnSoft: '#3D301C',
    danger: '#FFB7B1',
    dangerSoft: '#452829',
    hero: '#20365A',
    onHero: '#F2F6FF',
    heroMuted: '#C4D3ED',
    track: '#30415D',
  },
};
export type AppTheme = typeof base.light;
function palette(
  mode: 'light' | 'dark',
  accent: Appearance['accent'],
): AppTheme {
  const dark = mode === 'dark';
  if (accent === 'blue') return { ...base[mode] };
  if (accent === 'teal')
    return {
      ...base[mode],
      accent: dark ? '#8BDDD5' : '#096E6C',
      accentSoft: dark ? '#173E43' : '#DCF2EE',
      onAccent: dark ? '#073735' : '#FFFFFF',
      hero: dark ? '#183B46' : '#174F5A',
      heroMuted: dark ? '#B8DAD9' : '#D0E9E7',
    };
  return {
    ...base[mode],
    accent: dark ? '#D0BCFF' : '#6746B5',
    accentSoft: dark ? '#392F57' : '#EEE7FC',
    onAccent: dark ? '#30204F' : '#FFFFFF',
    hero: dark ? '#302B50' : '#443267',
    heroMuted: dark ? '#D9CDEA' : '#E4DBF2',
  };
}
export const themes = {
  light: palette('light', 'blue'),
  dark: palette('dark', 'blue'),
};
type AppThemes = typeof themes;
declare module 'react-native-unistyles' {
  export interface UnistylesThemes extends AppThemes {}
}
StyleSheet.configure({ themes, settings: { adaptiveThemes: true } });
let appearance: Appearance = { mode: 'system', accent: 'blue', motion: true };
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
