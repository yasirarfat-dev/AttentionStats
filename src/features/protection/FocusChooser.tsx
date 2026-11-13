import { Modal, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { StyleSheet } from 'react-native-unistyles';
import { Button } from '../../ui/Kit';
import { FocusQuickActions } from './FocusQuickActions';

/** Shared by Today and Protect so both entries review the same native focus flow. */
export function FocusChooser({
  visible,
  onClose,
  onCustomize,
}: {
  visible: boolean;
  onClose: () => void;
  onCustomize: () => void;
}) {
  const insets = useSafeAreaInsets();
  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
    >
      <View
        style={[
          styles.overlay,
          { paddingTop: insets.top + 24, paddingBottom: insets.bottom + 24 },
        ]}
      >
        <View style={styles.sheet} accessibilityViewIsModal>
          {visible && <FocusQuickActions onSelect={onCustomize} />}
          <Button
            label="Close focus chooser"
            variant="text"
            onPress={onClose}
          />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create(theme => ({
  overlay: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
    backgroundColor: '#00000080',
  },
  sheet: {
    maxHeight: '90%',
    width: '100%',
    maxWidth: 480,
    alignSelf: 'center',
    backgroundColor: theme.surface,
    borderRadius: 28,
    padding: 20,
  },
}));
