import { Action } from '../../../ui/Action';
import { Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';
import { Switch, Text, View } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { useCounterAppearance } from '../useCounterAppearance';
export function AppearanceScreen() {
  return (
    <Screen>
      <Heading>Comfortable to read.</Heading>
      <Panel title="Follows your device">
        <Paragraph>
          The app follows your Android light or dark theme and text size. Main
          navigation stays visible while pages scroll. Decorative transitions
          are off in this preview.
        </Paragraph>
      </Panel>
      <Panel title="More personal choices">
        <Paragraph>
          Saved theme, type and layout preferences are not available yet. No
          preference is saved by opening this page.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
export function CounterAppearanceScreen({ onReels }: { onReels: () => void }) {
  const { snapshot, busy, error, refresh, save } = useCounterAppearance();
  const ready = snapshot?.storage === 'ready' && !busy && !error;
  const preferences = snapshot?.counterAppearance;
  return (
    <Screen>
      <Heading>A glance, your way.</Heading>
      <Panel title="Floating counter" tinted>
        <Paragraph>
          The compact button shows this visit and today, like 10 | 200. Counts
          are estimates of settled viewer pages, including ads and return
          visits. Tap to expand category statistics; drag its header to move.
          Screen-reader actions move or reset it without dragging.
        </Paragraph>
        <Paragraph>
          Hiding the button keeps observation on. Its position lasts until the
          service restarts.
        </Paragraph>
        <Action label="Manage live counter" onPress={onReels} variant="tonal" />
      </Panel>
      <Panel title="Choose your display">
        {error && (
          <View accessibilityRole="alert">
            <Paragraph>{error}</Paragraph>
          </View>
        )}
        {!snapshot && !error && (
          <Paragraph>Opening your saved display settings…</Paragraph>
        )}
        <View style={styles.choice}>
          <Text style={styles.label}>Show session and Today icons</Text>
          <Switch
            accessibilityLabel="Show session and Today icons"
            value={preferences?.showIcons ?? true}
            disabled={!ready || !preferences}
            onValueChange={value => {
              if (preferences) save(value, preferences.allAppsToday);
            }}
          />
        </View>
        <Paragraph>
          Turn off for a compact numbers-only view. The first count is this app
          visit; the second is today.
        </Paragraph>
        <View style={styles.choice}>
          <Text style={styles.label}>Today across all apps</Text>
          <Switch
            accessibilityLabel="Today across all apps"
            value={preferences?.allAppsToday ?? true}
            disabled={!ready || !preferences}
            onValueChange={value => {
              if (preferences) save(preferences.showIcons, value);
            }}
          />
        </View>
        <Paragraph>
          {preferences?.allAppsToday === false
            ? 'Today shows saved observations for the app you are viewing.'
            : 'Today combines saved observations across apps. This is the default.'}{' '}
          Unsupported apps are not measured. Your current visit always belongs
          to the app you are viewing.
        </Paragraph>
        <Paragraph>
          These choices are saved on this device and do not enable observation
          or the live display.
        </Paragraph>
        {error && (
          <Action
            label="Refresh display settings"
            onPress={refresh}
            disabled={busy}
            variant="tonal"
          />
        )}
      </Panel>
      <Panel title="Customization">
        <Paragraph>
          Presets, colors, transparency, fonts, size and saved display profiles
          are planned. They are not configurable in this preview.
        </Paragraph>
      </Panel>
    </Screen>
  );
}

const styles = StyleSheet.create(theme => ({
  choice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    minHeight: 56,
  },
  label: { flex: 1, color: theme.text, fontSize: 16 },
}));
