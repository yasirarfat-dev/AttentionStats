import { Action } from '../../../ui/Action';
import { Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';
export function StorageScreen({ onReels }: { onReels: () => void }) {
  return (
    <Screen>
      <Heading>Your history stays yours.</Heading>
      <Panel title="Local observations">
        <Paragraph>
          Reels observations use encrypted storage on this device. Storage-size
          reporting and retention policies are not available yet. The app does
          not silently delete history to free space.
        </Paragraph>
        <Action label="Manage Reels data" onPress={onReels} variant="tonal" />
      </Panel>
      <Panel title="Deletion is a separate choice">
        <Paragraph>
          Pausing or hiding the counter keeps saved history. The Reels controls
          provide a confirmed deletion action for both preview sources.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
