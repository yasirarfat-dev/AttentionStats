import { StyleSheet } from 'react-native-unistyles';

export const themes = {
  light: {
    canvas: '#F8F9FA',
    surface: '#FFFFFF',
    surfaceAlt: '#EDF2FA',
    text: '#1F1F1F',
    muted: '#5F6368',
    accent: '#1967D2',
    onAccent: '#FFFFFF',
    border: '#747775',
    tint: '#D3E3FD',
    lavender: '#E9DDF6',
    onLavender: '#4F378B',
    danger: '#B3261E',
  },
  dark: {
    canvas: '#101318',
    surface: '#1C2027',
    surfaceAlt: '#272D38',
    text: '#E3E3E3',
    muted: '#C4C7C5',
    accent: '#A8C7FA',
    onAccent: '#062E6F',
    border: '#8E918F',
    tint: '#233D65',
    lavender: '#34294D',
    onLavender: '#D0BCFF',
    danger: '#F2B8B5',
  },
};

type AppThemes = typeof themes;
declare module 'react-native-unistyles' {
  export interface UnistylesThemes extends AppThemes {}
}

StyleSheet.configure({ themes, settings: { adaptiveThemes: true } });
