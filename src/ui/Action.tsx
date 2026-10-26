import { Pressable, Text } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';

export function Action({
  label,
  onPress,
}: {
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.button, pressed && styles.pressed]}
    >
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create(theme => ({
  button: {
    minHeight: 48,
    padding: 16,
    borderRadius: 14,
    backgroundColor: theme.accent,
    justifyContent: 'center',
    alignItems: 'center',
  },
  pressed: { borderWidth: 2, borderColor: theme.text },
  label: {
    color: theme.onAccent,
    fontSize: 17,
    fontWeight: '600',
    textAlign: 'center',
  },
}));
