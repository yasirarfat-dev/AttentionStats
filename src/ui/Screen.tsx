import type { ReactNode } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { StyleSheet } from 'react-native-unistyles';

/** Scrolling page with an optional large title and subtitle (no navigation header). */
export function Screen({
  children,
  title,
  subtitle,
  header = false,
}: {
  children: ReactNode;
  title?: string;
  subtitle?: string;
  /** True when a navigation header already covers the status bar. */
  header?: boolean;
}) {
  const insets = useSafeAreaInsets();
  return (
    <View
      style={[
        styles.safe,
        { paddingLeft: insets.left, paddingRight: insets.right },
      ]}
    >
      <ScrollView
        contentContainerStyle={[
          styles.content,
          { paddingTop: header ? 8 : insets.top + 16, paddingBottom: 32 },
        ]}
      >
        {title ? (
          <View style={styles.titleBlock}>
            {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
            <Text accessibilityRole="header" style={styles.title}>
              {title}
            </Text>
          </View>
        ) : null}
        {children}
      </ScrollView>
      {/* Keeps scrolled content from running under the transparent status bar. */}
      <View style={[styles.statusShade, { height: insets.top }]} />
    </View>
  );
}

const styles = StyleSheet.create(theme => ({
  safe: { flex: 1, backgroundColor: theme.canvas },
  statusShade: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    backgroundColor: theme.canvas,
  },
  content: { paddingHorizontal: 18, gap: 14, flexGrow: 1 },
  titleBlock: { paddingHorizontal: 4, paddingBottom: 6, gap: 2 },
  title: {
    color: theme.text,
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '800',
    letterSpacing: -0.5,
  },
  subtitle: { color: theme.muted, fontSize: 14, fontWeight: '600' },
}));
