import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function PrivacyScreen() {
  return (
    <Screen>
      <Heading>Your choices come first.</Heading>
      <Paragraph>
        This build does not request tracking access or record your app activity.
        Enabling a source is not available yet.
      </Paragraph>
      <Paragraph>
        The planned app keeps history on your device by default, with no
        developer account or telemetry. Each source will have its own
        explanation and choice before it can collect anything.
      </Paragraph>
      <Paragraph>
        History storage, encryption, protection rules and encrypted export are
        still being developed. They are not active in this build.
      </Paragraph>
      <Paragraph>You can return and leave the app at any time.</Paragraph>
    </Screen>
  );
}
