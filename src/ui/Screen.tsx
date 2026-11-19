import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ComponentRef,
} from 'react';
import type { ReactNode, Ref } from 'react';
import {
  Keyboard,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
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

/** Points an outside ref (callback or object) at the same scroll view as Screen's own. */
function assignRef<T>(ref: Ref<T> | undefined, value: T | null) {
  if (typeof ref === 'function') ref(value);
  else if (ref) (ref as { current: T | null }).current = value;
}

/**
 * Keeps the focused input above the keyboard on every page. Android draws this app edge to edge, so the
 * window is not resized when the keyboard opens: the page shrinks its own scroll viewport by the part
 * the keyboard covers, then scrolls the focused input into view.
 */
function useKeyboardInset(scrollRef: Ref<ScrollViewInstance> | undefined) {
  const outer = useRef<ComponentRef<typeof View>>(null);
  const scroll = useRef<ScrollViewInstance | null>(null);
  const [inset, setInset] = useState(0);
  const setScroll = useCallback(
    (node: ScrollViewInstance | null) => {
      scroll.current = node;
      assignRef(scrollRef, node);
    },
    [scrollRef],
  );
  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', event => {
      outer.current?.measureInWindow((_x, top, _width, height) => {
        const covered = Math.round(top + height - event.endCoordinates.screenY);
        setInset(Math.max(0, covered));
      });
    });
    const hide = Keyboard.addListener('keyboardDidHide', () => setInset(0));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);
  useEffect(() => {
    if (inset === 0) return;
    // After the viewport has shrunk, lift the focused input if it still sits low.
    const frame = requestAnimationFrame(() => {
      const view = scroll.current;
      const input = TextInput.State.currentlyFocusedInput();
      if (!view || !input) return;
      view.measureInWindow((_x, top, _width, height) => {
        input.measureInWindow((_inputX, inputTop, _inputWidth, inputHeight) => {
          if (
            scroll.current !== view ||
            TextInput.State.currentlyFocusedInput() !== input
          )
            return;
          const bottom = top + height;
          if (inputTop >= top + 16 && inputTop + inputHeight <= bottom - 16)
            return;
          const keyboard = Keyboard.metrics();
          // RN measures the input inside the scroll content. Add the viewport's own window origin and any
          // gap between the viewport and the keyboard so the input ends 16 dp above the visible bottom.
          view.scrollResponderScrollNativeHandleToKeyboard(
            input,
            top + Math.max(0, (keyboard?.screenY ?? bottom) - bottom) + 16,
            true,
          );
        });
      });
    });
    return () => cancelAnimationFrame(frame);
  }, [inset]);
  return { outer, setScroll, inset };
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
  const keyboard = useKeyboardInset(scrollRef);
  return (
    <View
      ref={keyboard.outer}
      style={[
        styles.safe,
        {
          paddingLeft: insets.left,
          paddingRight: insets.right,
          paddingBottom: keyboard.inset,
        },
      ]}
    >
      <ScrollView
        ref={keyboard.setScroll}
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
