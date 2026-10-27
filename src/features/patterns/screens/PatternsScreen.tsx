import { Panel, Destination } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function PatternsScreen({
  onExplanation,
  onCategories,
  onPrivacy,
}: {
  onExplanation: () => void;
  onCategories: () => void;
  onPrivacy: () => void;
}) {
  return (
    <Screen>
      <Heading>Understand, without judging.</Heading>
      <Paragraph>
        Small observations can help you choose what feels useful. There is no
        score to chase.
      </Paragraph>
      <Panel title="No insight is better than a guess." tinted>
        <Paragraph>
          Patterns are not available yet. They will need enough supported
          history and a clear explanation of the evidence.
        </Paragraph>
      </Panel>
      <Panel title="A closer look">
        <Destination
          title="How patterns work"
          detail="Evidence, uncertainty and your control"
          onPress={onExplanation}
        />
        <Destination
          title="App categories"
          detail="Organize activity in a way that makes sense to you"
          onPress={onCategories}
        />
        <Destination
          title="About your data"
          detail="Local interpretation and optional detail"
          onPress={onPrivacy}
        />
      </Panel>
    </Screen>
  );
}
export function PatternExplanationScreen() {
  return (
    <Screen>
      <Heading>Evidence before interpretation.</Heading>
      <Panel title="A pattern is an observation">
        <Paragraph>
          Future patterns will name their source, period and missing coverage.
          They will describe observed activity, not diagnose addiction or assume
          how you feel.
        </Paragraph>
      </Panel>
      <Panel title="Your choice">
        <Paragraph>
          Predictions and content-topic analysis are separate optional features.
          Neither is active in this preview. Suggestions will never activate a
          rule for you.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
export function CategoriesScreen() {
  return (
    <Screen>
      <Heading>Your own way to organize.</Heading>
      <Panel title="App categories">
        <Paragraph>
          Category editing is not available yet. Future manual choices will take
          priority over automatic suggestions, and changing a category will not
          silently activate a boundary.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
