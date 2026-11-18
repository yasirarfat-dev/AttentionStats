import { Text, View } from 'react-native';
import { StyleSheet, useUnistyles } from 'react-native-unistyles';
import { Icon } from '../../ui/Icon';
import { Card } from '../../ui/Kit';
import { duration } from '../../ui/format';
import {
  distancePerspective,
  PERSPECTIVE_ASSUMPTIONS,
  achievements,
} from './perspective';

export function PerspectiveCard({
  ms,
  videos,
  seed,
  title = 'Perspective',
  rangeLabel,
}: {
  ms: number;
  videos: number;
  seed: number;
  title?: string;
  rangeLabel?: string;
}) {
  const { theme } = useUnistyles();
  const times = achievements(ms, 4, seed);
  const distance = distancePerspective(videos);
  if (!times.length && !distance) return null;
  return (
    <Card title={title} info={PERSPECTIVE_ASSUMPTIONS} scope={rangeLabel}>
      {times.length > 0 && (
        <Text textBreakStrategy="balanced" style={styles.lead}>
          {duration(ms)} of short videos.{'\n'}In that time you could have:
        </Text>
      )}
      <View style={styles.rows}>
        {[...times, ...(distance ? [distance] : [])].map(item => (
          <View
            key={item.key}
            style={styles.row}
            accessible
            accessibilityLabel={item.accessibilityLabel}
          >
            <Icon name={item.icon} color={theme.accent} />
            <Text textBreakStrategy="balanced" style={styles.text}>
              {item.text}
            </Text>
          </View>
        ))}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create(theme => ({
  lead: { color: theme.muted, fontSize: 14 },
  rows: { gap: theme.spacing / 2 },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: theme.spacing / 2,
  },
  text: { color: theme.text, fontSize: 16, flex: 1, flexShrink: 1 },
}));
