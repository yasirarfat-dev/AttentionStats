import { Action } from '../../../ui/Action';
import { Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';
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
  return (
    <Screen>
      <Heading>A glance, your way.</Heading>
      <Panel title="Floating counter" tinted>
        <Paragraph>
          The compact button shows session and today counts. Tap to expand
          category statistics; drag its header to move. Screen-reader actions
          move or reset it without dragging.
        </Paragraph>
        <Paragraph>
          Hiding the button keeps observation on. Its position lasts until the
          service restarts.
        </Paragraph>
        <Action label="Manage live counter" onPress={onReels} variant="tonal" />
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
