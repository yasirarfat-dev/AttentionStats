import type { ReactNode } from 'react';
import { Text } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';

export function Heading({ children }: { children: ReactNode }) {
  return (
    <Text accessibilityRole="header" style={styles.heading}>
      {children}
    </Text>
  );
}

export function Paragraph({ children }: { children: ReactNode }) {
  return <Text style={styles.body}>{children}</Text>;
}

const styles = StyleSheet.create(theme => ({
  heading: { color: theme.text, fontFamily: 'serif', fontSize: 36 },
  body: { color: theme.text, fontSize: 17, lineHeight: 26 },
}));
