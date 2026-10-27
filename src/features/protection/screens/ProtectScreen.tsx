import { Panel, Destination } from '../../../ui/Panel';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function ProtectScreen({
  onFocus,
  onRules,
  onPrivacy,
}: {
  onFocus: () => void;
  onRules: () => void;
  onPrivacy: () => void;
}) {
  return (
    <Screen>
      <Heading>Make room for what matters.</Heading>
      <Paragraph>
        Observation can be enough. Boundaries are yours to choose.
      </Paragraph>
      <Panel title="No protection is active" tinted>
        <Paragraph>
          Focus timers and enforcement are not available yet. The Reels counter
          observes; it does not block or close apps.
        </Paragraph>
      </Panel>
      <Panel title="Your options">
        <Destination
          title="Focus sessions"
          detail="A chosen interval, with an immediate way to end"
          onPress={onFocus}
        />
        <Destination
          title="Budgets & schedules"
          detail="Review the shape of a future boundary"
          onPress={onRules}
        />
        <Destination
          title="About your data"
          detail="Observation and protection are separate choices"
          onPress={onPrivacy}
        />
      </Panel>
    </Screen>
  );
}
export function FocusScreen() {
  return (
    <Screen>
      <Heading>A pause, on your terms.</Heading>
      <Panel title="Focus sessions">
        <Paragraph>
          Not available in this preview. A future session will let you choose a
          duration and targets, see what it can enforce, and stop immediately.
          No timer or restriction is running.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
export function RulesScreen() {
  return (
    <Screen>
      <Heading>Choose a boundary deliberately.</Heading>
      <Panel title="Budgets & schedules">
        <Paragraph>
          Rule creation is not available yet. Future previews will show targets,
          thresholds, schedules, exceptions and actual enforcement support
          before you activate anything.
        </Paragraph>
      </Panel>
      <Panel title="Access stays yours">
        <Paragraph>
          Emergency access will be immediate. An unsupported Reels surface will
          never silently become a whole-app block.
        </Paragraph>
      </Panel>
    </Screen>
  );
}
