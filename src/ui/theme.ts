import { StyleSheet } from 'react-native-unistyles';

export const themes = {
  light: {
    canvas: '#F6F5EF',
    surface: '#FFFEF9',
    text: '#243C32',
    muted: '#56645B',
    accent: '#305B48',
    onAccent: '#FFFFFF',
    border: '#79867B',
    tint: '#E0EADB',
  },
  dark: {
    canvas: '#17231F',
    surface: '#202F29',
    text: '#F2F1E8',
    muted: '#BAC8BD',
    accent: '#BED6AF',
    onAccent: '#203326',
    border: '#8B9D90',
    tint: '#314635',
  },
};

type AppThemes = typeof themes;
declare module 'react-native-unistyles' {
  export interface UnistylesThemes extends AppThemes {}
}

StyleSheet.configure({ themes, settings: { adaptiveThemes: true } });
