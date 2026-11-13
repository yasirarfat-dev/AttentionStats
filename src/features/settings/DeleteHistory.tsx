import { useEffect, useState } from 'react';
import {
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  ScrollView,
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
  const [step, setStep] = useState<0 | 1 | 2 | 3>(0);
  const dialog =
    step === 3 ? (
      <TypeToConfirm
        visible
        onCancel={() => setStep(0)}
        onConfirm={() => {
          setStep(0);
          void onDelete();
        }}
      />
    ) : (
      <Modal
        visible={step !== 0}
        transparent
        animationType="none"
        onRequestClose={() => setStep(0)}
      >
        <View style={styles.backdrop}>
          <ScrollView
            style={styles.sheet}
            contentContainerStyle={{ gap: 14, paddingBottom: 22 }}
            accessibilityViewIsModal
          >
            <Text accessibilityRole="header" style={styles.title}>
              {step === 1
                ? 'Before erasing history'
                : 'Review permanent deletion'}
            </Text>
            <Body>
              {step === 1
                ? `Your local record uses ${size(storageBytes)}. You can keep your history or pause counting without deleting it.`
                : 'This erases history and choices from this phone. There is no local undo. The next step asks you to type DELETE and wait ten seconds.'}
            </Body>
            <Body muted>
              Erasing turns off automatic backups and removes pending restores.
              Copies already held by your backup provider or exported elsewhere
              may remain.
            </Body>
            <Button
              label="Keep my history"
              variant="safe"
              onPress={() => setStep(0)}
            />
            {step === 1 && (
              <Button
                label="Pause counting & keep history"
                variant="safe"
                onPress={() => {
                  setStep(0);
                  void onPause();
                }}
              />
            )}
            <Button
              label={
                step === 1
                  ? 'Review history deletion'
                  : 'Proceed to final erase confirmation'
              }
              variant="danger"
              onPress={() => setStep(step === 1 ? 2 : 3)}
            />
          </ScrollView>
        </View>
      </Modal>
    );
  return { start: () => setStep(1), dialog };
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
      animationType="none"
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
          <Button label="Keep my history" variant="safe" onPress={onCancel} />
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
    maxHeight: '85%',
    flexGrow: 0,
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
