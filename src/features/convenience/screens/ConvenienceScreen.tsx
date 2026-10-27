import { Switch } from 'react-native';
import { Panel } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';
export function ConvenienceScreen() {
  return (
    <Screen>
      <Heading>Extras, only if you want them.</Heading>
      <Paragraph>
        Convenience features are separate from attention statistics.
      </Paragraph>
      <Panel title="Auto-scroll at video end">
        <Switch
          value={false}
          disabled
          accessibilityLabel="Auto-scroll unavailable; off"
        />
        <Paragraph>
          Off. Not available in this preview. It will have its own setting and
          explicit start/stop controls after reliable video-end detection is
          supported.
        </Paragraph>
        <Paragraph>
          Statistics work independently. Opening this screen never starts
          scrolling or requests gesture access.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
