import { Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';
export function BackupScreen() {
  return (
    <Screen>
      <Heading>A copy you control.</Heading>
      <Panel title="Export & restore">
        <Paragraph>
          Portable encrypted export and restore are not available in this
          preview. No archive has been created or transferred.
        </Paragraph>
      </Panel>
      <Panel title="Your destination, your choice">
        <Paragraph>
          Future manual export will let you select a destination and keep the
          recovery credential yourself. Restoring will show what would be
          replaced before changing live history.
        </Paragraph>
      </Panel>
      <Panel title="Automatic backup">
        <Paragraph>
          Automatic personal-cloud backup is unavailable. No provider account is
          connected.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
