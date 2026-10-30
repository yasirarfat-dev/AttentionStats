import { StyleSheet } from 'react-native-unistyles';

/**
 * Calm, trustworthy palette: forest greens (growth, safety), warm paper neutrals, and one
 * gentle amber for attention. Contrast is checked for WCAG AA on text/background pairs.
 */
export const themes = {
  light: {
    canvas: '#F5F6F1',
    surface: '#FFFFFF',
    surfaceAlt: '#EEF2EC',
    raised: '#FFFFFF',
    text: '#16201B',
    muted: '#56625B',
    faint: '#8A958F',
    accent: '#1E7A57',
    accentSoft: '#DCEFE5',
    onAccent: '#FFFFFF',
    border: '#E1E6DF',
    tint: '#E6F1EA',
    lavender: '#ECE6F7',
    onLavender: '#4F378B',
    warn: '#9A5B00',
    warnSoft: '#FCEFD9',
    danger: '#B3261E',
    dangerSoft: '#FBE4E2',
    hero: '#123B2C',
    onHero: '#F1FBF5',
    heroMuted: '#A9D2BF',
    track: '#E6EBE4',
  },
  dark: {
    canvas: '#0D1311',
    surface: '#161E1A',
    surfaceAlt: '#1D2722',
    raised: '#1F2A25',
    text: '#E7EEE9',
    muted: '#A6B3AC',
    faint: '#73807A',
    accent: '#8FE0BB',
    accentSoft: '#1E3B2F',
    onAccent: '#06301F',
    border: '#26322C',
    tint: '#1F3329',
    lavender: '#2D2540',
    onLavender: '#D0BCFF',
    warn: '#F2C27A',
    warnSoft: '#3A2D16',
    danger: '#F2B8B5',
    dangerSoft: '#3D1F1D',
    hero: '#173E30',
    onHero: '#EAF8F0',
    heroMuted: '#9CCBB5',
    track: '#26322C',
  },
};

export type AppTheme = typeof themes.light;
type AppThemes = typeof themes;
declare module 'react-native-unistyles' {
  export interface UnistylesThemes extends AppThemes {}
}

StyleSheet.configure({ themes, settings: { adaptiveThemes: true } });
