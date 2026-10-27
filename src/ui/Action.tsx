import { Pressable, Text } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';

export function Action({
  label,
  onPress,
  disabled = false,
  variant = 'filled',
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  variant?: 'filled' | 'tonal' | 'text' | 'danger';
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        variant !== 'filled' && styles.secondary,
        pressed && styles.pressed,
        disabled && styles.disabled,
      ]}
    >
      <Text
        style={[
          styles.label,
          variant !== 'filled' && styles.secondaryLabel,
          variant === 'danger' && styles.dangerLabel,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create(theme => ({
  button: {
    minHeight: 48,
    padding: 16,
    borderRadius: 28,
    backgroundColor: theme.accent,
    justifyContent: 'center',
    alignItems: 'center',
  },
  pressed: { borderWidth: 2, borderColor: theme.text },
  disabled: { opacity: 0.6 },
  secondary: { backgroundColor: theme.tint },
  secondaryLabel: { color: theme.text },
  dangerLabel: { color: theme.danger },
  label: {
    color: theme.onAccent,
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
}));
