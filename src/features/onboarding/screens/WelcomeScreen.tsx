import { Action } from '../../../ui/Action';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function WelcomeScreen({
  onPrivacy,
  onReels,
}: {
  onPrivacy: () => void;
  onReels: () => void;
}) {
  return (
    <Screen>
      <Heading>Notice your patterns. Choose what comes next.</Heading>
      <Paragraph>
        Your Reels counter starts with your choice. Set up observation for
        Instagram or Facebook, review what is supported, and pause whenever you
        need.
      </Paragraph>
      <Action label="Open Reels counter" onPress={onReels} />
      <Paragraph>
        Observation is off until you enable it. The live counter is a separate
        choice. Saved counts stay in encrypted storage on this device.
      </Paragraph>
      <Paragraph>
        This is a developer preview while the wider Attention Stats app is being
        built. Unsupported layouts stay unavailable.
      </Paragraph>
      <Action label="About your data" onPress={onPrivacy} />
    </Screen>
  );
}
