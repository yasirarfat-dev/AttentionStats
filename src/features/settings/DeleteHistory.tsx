import { useEffect, useState } from 'react';
import {
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Text,
  TextInput,
  View,
} from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { Icon } from '../../ui/Icon';
import { Body, Button } from '../../ui/Kit';

const WORD = 'DELETE';
const WAIT_SECONDS = 10;

/**
 * Deleting irreplaceable, local-only history takes three deliberate steps:
 * a "before you delete" explanation that offers pausing instead, a second
 * confirmation naming what is lost, then typing DELETE after a short wait.
 */
/** "2.4 MB" style size for the reassurance copy. */
function size(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function useDeleteHistory({
  onPause,
  onDelete,
  storageBytes,
}: {
  onPause: () => unknown;
  onDelete: () => unknown;
  storageBytes: number;
}) {
  const [typing, setTyping] = useState(false);

  function start() {
    Alert.alert(
      'This is your private record',
      `It is the story of where your attention went, and only you have it. It lives on this phone alone: no account, no server, no backup. Nobody, not even us, can see it or bring it back.

It takes just ${size(storageBytes)}. Many people return months later to see how far they have come.

Need a break from tracking? Pausing keeps everything safe.`,
      [
        { text: 'Pause counting instead', onPress: () => void onPause() },
        { text: 'Keep my history', style: 'cancel' },
        { text: 'Continue', style: 'destructive', onPress: second },
      ],
    );
  }

  function second() {
    Alert.alert(
      'Erase it forever?',
      'Every short video, every minute of app time, your breaks and your blocks, all gone for good. There is no undo and no copy anywhere.\n\nFuture you may want to look back at this. Are you completely sure?',
      [
        { text: 'Keep my history', style: 'cancel' },
        {
          text: "I'm sure",
          style: 'destructive',
          onPress: () => setTyping(true),
        },
      ],
    );
  }

  const dialog = (
    <TypeToConfirm
      visible={typing}
      onCancel={() => setTyping(false)}
      onConfirm={() => {
        setTyping(false);
        void onDelete();
      }}
    />
  );
  return { start, dialog };
}

function TypeToConfirm({
  visible,
  onCancel,
  onConfirm,
}: {
  visible: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { theme } = useUnistyles();
  const [text, setText] = useState('');
  const [left, setLeft] = useState(WAIT_SECONDS);
  useEffect(() => {
    if (!visible) return;
    setText('');
    setLeft(WAIT_SECONDS);
    const timer = setInterval(
      () => setLeft(value => Math.max(0, value - 1)),
      1000,
    );
    return () => clearInterval(timer);
  }, [visible]);
  const ready = left === 0 && text.trim().toUpperCase() === WORD;
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onCancel}
    >
      <KeyboardAvoidingView behavior="padding" style={styles.backdrop}>
        <View style={styles.sheet}>
          <View style={styles.icon}>
            <Icon name="trash" color={theme.danger} size={26} />
          </View>
          <Text accessibilityRole="header" style={styles.title}>
            Type {WORD} to erase your history
          </Text>
          <Body muted>This is the last step. It cannot be undone.</Body>
          <TextInput
            value={text}
            onChangeText={value => {
              setText(value);
              // Close the keyboard once the word is right so the next tap reaches the button.
              if (value.trim().toUpperCase() === WORD) Keyboard.dismiss();
            }}
            autoCapitalize="characters"
            autoCorrect={false}
            placeholder={WORD}
            placeholderTextColor={theme.faint}
            accessibilityLabel={`Type ${WORD} to confirm`}
            style={styles.input}
            returnKeyType="done"
          />
          <Button label="Keep my history" onPress={onCancel} />
          <Button
            label={left > 0 ? `Erase everything · ${left}` : 'Erase everything'}
            variant="danger"
            disabled={!ready}
            onPress={onConfirm}
          />
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create(theme => ({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'center',
    padding: 20,
  },
  sheet: {
    backgroundColor: theme.surface,
    borderRadius: 24,
    padding: 22,
    gap: 14,
  },
  icon: {
    width: 48,
    height: 48,
    borderRadius: 16,
    backgroundColor: theme.dangerSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { color: theme.text, fontSize: 20, fontWeight: '800' },
  input: {
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: 14,
    paddingHorizontal: 14,
    minHeight: 52,
    color: theme.text,
    fontSize: 18,
    fontWeight: '700',
    letterSpacing: 2,
  },
}));
