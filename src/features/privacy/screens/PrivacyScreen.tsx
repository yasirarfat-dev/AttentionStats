import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function PrivacyScreen() {
  return (
    <Screen>
      <Heading>Your choices come first.</Heading>
      <Paragraph>
        This preview can observe Reels only for apps you explicitly enable.
        Accessibility access and the optional live display are separate choices.
        You can keep observation off and use the app without granting access.
      </Paragraph>
      <Paragraph>
        Reels counts and observation times stay in an encrypted local database,
        protected with an installation-specific Android Keystore key. There is
        no developer account, telemetry or automatic upload. Screen text,
        messages, titles, creators, photos and video are not read by the
        counter.
      </Paragraph>
      <Paragraph>
        A separately chosen, temporary developer structure check saves only
        layout identifiers and numeric scroll positions for enabled apps. It
        stops after ten minutes and removes its private diagnostic file when
        stopped. It does not upload anything automatically.
      </Paragraph>
      <Paragraph>
        Pause stops new observations and removes the live display. Deleting
        Reels history disables observation and removes saved counts for both
        apps. Export, restore, protection and broader history remain
        unavailable.
      </Paragraph>
    </Screen>
  );
}
