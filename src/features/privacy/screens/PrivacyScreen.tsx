import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';
import { Linking, Alert } from 'react-native';
import { Action } from '../../../ui/Action';

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
        no developer account, telemetry or automatic upload. The counter does
        not read photos, video, inputs or messages. For Facebook, it transiently
        compares accessibility descriptions with fixed interface labels to
        recognize the viewer. Unmatched descriptions are immediately discarded;
        raw descriptions, messages, titles and creators are never saved.
      </Paragraph>
      <Paragraph>
        For TikTok only, a separate updated source notice permits transient
        matching of visible title/caption text or descriptions. Raw values are
        discarded immediately. Keyed comparison tokens stay only in memory and
        are cleared on source, window, device-lock or service changes. No raw
        titles, captions or tokens enter history, diagnostics or uploads.
        Missing or identical captions can miss encounters; counts do not prove
        unique videos or playback.
      </Paragraph>
      <Paragraph>
        Development previews also offer a separately chosen, temporary structure
        check. It saves only layout identifiers, numeric scroll positions, media
        and ancestor bounds, and scroll-source bounds with event sequence and
        relative timing for enabled apps. For Facebook it also saves matched
        fixed interface tokens with their role, bounds and selected/clickable
        state. It never saves raw descriptions. It stops after ten minutes and
        removes its private diagnostic file when stopped. It does not upload
        anything automatically.
      </Paragraph>
      <Action
        label="Read the privacy policy"
        variant="tonal"
        onPress={() => {
          Linking.openURL(
            'https://github.com/yasirarfat-dev/AttentionStats/blob/main/policies/PRIVACY.md',
          ).catch(() =>
            Alert.alert(
              'Could not open the policy',
              'You can find the privacy policy in the AttentionStats GitHub repository.',
            ),
          );
        }}
      />
      <Paragraph>
        Pause stops new observations and removes the live display. Deleting
        Reels history disables observation and removes saved counts for both
        apps. Export, restore, protection and broader history remain
        unavailable.
      </Paragraph>
    </Screen>
  );
}
