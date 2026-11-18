import type { ReactNode, Ref } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import type { ScrollViewInstance } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { Icon } from './Icon';

export type ScreenBack = {
  /** Full spoken label, e.g. "Back to Protect"; the visible text is the destination. */
  label: string;
  onPress: () => void;
  disabled?: boolean;
};

/** Compact back control above the title: a chevron and the destination name. */
function BackLink({ back }: { back: ScreenBack }) {
  const { theme } = useUnistyles();
  const destination = back.label.replace(/^back to\s+/i, '');
  const text = destination.charAt(0).toUpperCase() + destination.slice(1);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={back.label}
      accessibilityState={{ disabled: !!back.disabled }}
      disabled={back.disabled}
      onPress={back.onPress}
      hitSlop={8}
      style={({ pressed }) => [
        styles.back,
        pressed && styles.backPressed,
        back.disabled && styles.backDisabled,
      ]}
    >
      <View style={styles.backIcon}>
        <Icon name="chevron" size={20} color={theme.accent} />
      </View>
      <Text
        textBreakStrategy="balanced"
        style={styles.backText}
        numberOfLines={1}
      >
        {text}
      </Text>
    </Pressable>
  );
}

/** Scrolling page with an optional large title and subtitle (no navigation header). */
export function Screen({
  children,
  title,
  subtitle,
  header = false,
  scrollRef,
  headerAction,
  scrollEnabled = true,
  back,
}: {
  children: ReactNode;
  title?: string;
  subtitle?: string;
  /** True when a navigation header already covers the status bar. */
  header?: boolean;
  scrollRef?: Ref<ScrollViewInstance>;
  headerAction?: ReactNode;
  scrollEnabled?: boolean;
  /** Sub-pages: a compact back link above the title. */
  back?: ScreenBack;
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
        ref={scrollRef}
        scrollEnabled={scrollEnabled}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        contentContainerStyle={[
          styles.content,
          {
            paddingTop: header ? 8 : insets.top + (back ? 4 : 16),
            paddingBottom: 32,
          },
        ]}
      >
        {back ? <BackLink back={back} /> : null}
        {title ? (
          <View style={[styles.titleBlock, back && styles.titleAfterBack]}>
            <View style={styles.titleCopy}>
              {subtitle ? (
                <Text textBreakStrategy="balanced" style={styles.subtitle}>
                  {subtitle}
                </Text>
              ) : null}
              <Text
                textBreakStrategy="balanced"
                accessibilityRole="header"
                style={styles.title}
              >
                {title}
              </Text>
            </View>
            {headerAction}
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
  content: { paddingHorizontal: 16, gap: 12, flexGrow: 1 },
  back: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 48,
    paddingRight: 14,
    paddingLeft: 2,
    borderRadius: 24,
    marginBottom: -10,
  },
  backPressed: { backgroundColor: theme.tint },
  backDisabled: { opacity: 0.45 },
  backIcon: { transform: [{ scaleX: -1 }] },
  backText: {
    color: theme.accent,
    fontSize: 15,
    fontWeight: '600',
    marginLeft: 2,
  },
  titleBlock: {
    paddingHorizontal: 4,
    paddingBottom: 4,
    gap: 12,
    flexDirection: 'row',
    alignItems: 'center',
  },
  titleAfterBack: { paddingTop: 0 },
  titleCopy: { flex: 1, gap: 2 },
  title: {
    color: theme.text,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '800',
    letterSpacing: -0.6,
  },
  subtitle: { color: theme.muted, fontSize: 14, fontWeight: '600' },
}));
