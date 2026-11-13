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
import { StyleSheet } from 'react-native-unistyles';
import { Button, Row } from '../../ui/Kit';

export type QuickDestination = 'Focus' | 'Breaks' | 'Blocks';

/** Shortcuts open existing review/edit flows; opening the sheet changes no settings. */
export function QuickActions({
  onNavigate,
}: {
  onNavigate: (destination: QuickDestination) => void;
}) {
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
  const open = (destination: QuickDestination) => {
    close(false);
    onNavigate(destination);
  };
  return (
    <>
      <View
        ref={trigger}
        accessible
        accessibilityRole="button"
        accessibilityLabel="Quick actions"
        onAccessibilityTap={() => setVisible(true)}
      >
        <Button
          label="Quick actions"
          icon="spark"
          variant="tonal"
          onPress={() => setVisible(true)}
        />
      </View>
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
        <View style={styles.overlay}>
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
                paddingBottom: Math.max(18, insets.bottom),
                paddingLeft: 18 + insets.left,
                paddingRight: 18 + insets.right,
              },
            ]}
          >
            <ScrollView contentContainerStyle={styles.content}>
              <Text
                ref={heading}
                accessibilityRole="header"
                style={styles.title}
              >
                Quick actions
              </Text>
              <Row
                icon="clock"
                title="Choose a focus session"
                detail="Pick apps and duration, then review before starting."
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
  overlay: { flex: 1, justifyContent: 'flex-end' },
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: '#00000080' },
  sheet: {
    maxHeight: '85%',
    backgroundColor: theme.surface,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
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
