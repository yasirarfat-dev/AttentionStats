import { Text, View } from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { AppIcon } from '../../ui/Charts';
import { useAppIcons } from './useAppIcons';

/** Read-only app identities, including retained choices for apps no longer installed. */
export function AppIdentityList({
  packages,
  apps,
}: {
  packages: string[];
  apps: { packageName: string; name: string }[];
}) {
  const { theme } = useUnistyles();
  const icons = useAppIcons(packages);
  return (
    <View style={styles.list}>
      {packages.map(packageName => {
        const name =
          apps.find(app => app.packageName === packageName)?.name ??
          packageName;
        return (
          <View
            key={packageName}
            style={styles.row}
            accessible
            accessibilityLabel={name}
          >
            <AppIcon
              uri={icons[packageName]}
              mark={name.slice(0, 1)}
              color={theme.accentFill}
              size={32}
            />
            <Text style={styles.name}>{name}</Text>
          </View>
        );
      })}
    </View>
  );
}
const styles = StyleSheet.create(theme => ({
  list: { gap: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 40 },
  name: { flex: 1, color: theme.text, fontSize: 15 },
}));
