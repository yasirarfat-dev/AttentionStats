import { useRef, useState, type ComponentRef } from 'react';
import {
  AccessibilityInfo,
  findNodeHandle,
  Modal,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { Icon } from '../../ui/Icon';
import { Button, Row } from '../../ui/Kit';
import { FocusQuickActions, type QuickFocusRequest } from '../protection';

export type QuickDestination = 'Focus' | 'Breaks' | 'Blocks';

/** Centered shortcuts open existing review/edit flows; opening the card changes no settings. */
export function QuickActions({
  onNavigate,
}: {
  onNavigate: (
    destination: QuickDestination,
    quickStart?: QuickFocusRequest,
  ) => void;
}) {
  const { theme } = useUnistyles();
  const [visible, setVisible] = useState(false);
  const trigger = useRef<ComponentRef<typeof View>>(null);
  const heading = useRef<ComponentRef<typeof Text>>(null);
  const insets = useSafeAreaInsets();
  const close = (restoreFocus = true) => {
    setVisible(false);
    if (restoreFocus)
      requestAnimationFrame(() => {
        const handle = findNodeHandle(trigger.current);
        if (handle) AccessibilityInfo.setAccessibilityFocus(handle);
      });
  };
  const open = (
    destination: QuickDestination,
    quickStart?: QuickFocusRequest,
  ) => {
    close(false);
    onNavigate(destination, quickStart);
  };
  return (
    <>
      <Pressable
        ref={trigger}
        accessibilityRole="button"
        accessibilityLabel="Quick Actions"
        accessibilityHint="Open Focus, Breaks and Video Limits"
        accessibilityState={{ expanded: visible }}
        style={styles.trigger}
        onPress={() => setVisible(true)}
      >
        <Icon name="bolt" size={24} color={theme.accent} />
      </Pressable>
      <Modal
        visible={visible}
        transparent
        animationType="none"
        onRequestClose={() => close()}
        onShow={() => {
          const handle = findNodeHandle(heading.current);
          if (handle) AccessibilityInfo.setAccessibilityFocus(handle);
        }}
      >
        <View
          style={[
            styles.overlay,
            {
              paddingTop: insets.top + 16,
              paddingBottom: insets.bottom + 16,
              paddingLeft: insets.left + 16,
              paddingRight: insets.right + 16,
            },
          ]}
        >
          <Pressable
            style={styles.backdrop}
            accessibilityRole="button"
            accessibilityLabel="Close quick actions"
            onPress={() => close()}
          />
          <View
            accessibilityViewIsModal
            style={[
              styles.sheet,
              {
                paddingBottom: 18,
                paddingLeft: 18,
                paddingRight: 18,
              },
            ]}
          >
            <ScrollView contentContainerStyle={styles.content}>
              <Text
                ref={heading}
                accessibilityRole="header"
                style={styles.title}
              >
                Quick Actions
              </Text>
              {visible && (
                <FocusQuickActions onSelect={choice => open('Focus', choice)} />
              )}
              <Row
                icon="clock"
                title="Choose a Focus Session"
                detail="Customize apps, groups and duration, then review."
                onPress={() => open('Focus')}
              />
              <Row
                icon="heart"
                title="Adjust mindful breaks"
                detail="Choose when to offer a pause."
                onPress={() => open('Breaks')}
              />
              <Row
                icon="block"
                title="Manage video limits"
                detail="Review short-video blocks for each app."
                onPress={() => open('Blocks')}
              />
              <Button
                label="Close quick actions"
                variant="text"
                onPress={() => close()}
              />
            </ScrollView>
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create(theme => ({
  trigger: {
    minWidth: 48,
    minHeight: 48,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.accentSoft,
  },
  overlay: { flex: 1, justifyContent: 'center' },
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: '#00000080' },
  sheet: {
    maxHeight: '85%',
    backgroundColor: theme.surface,
    borderRadius: 28,
    width: '100%',
    maxWidth: 480,
    alignSelf: 'center',
    paddingTop: 24,
  },
  content: { gap: 12 },
  title: {
    color: theme.text,
    fontSize: 24,
    fontWeight: '700',
    paddingHorizontal: 12,
  },
}));
