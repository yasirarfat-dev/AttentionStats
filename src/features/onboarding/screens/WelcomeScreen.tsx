import { useEffect, useState } from 'react';
import { getBootstrapStatus, type BootstrapResult } from '../../../platform';
import { Action } from '../../../ui/Action';
import { Heading, Paragraph } from '../../../ui/Reading';
import { Screen } from '../../../ui/Screen';

export function WelcomeScreen({ onPrivacy }: { onPrivacy: () => void }) {
  const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState<BootstrapResult | null>(null);
  useEffect(() => {
    let active = true;
    setStatus(null);
    getBootstrapStatus().then(result => {
      if (active) setStatus(result);
    });
    return () => {
      active = false;
    };
  }, [attempt]);
  return (
    <Screen>
      <Heading>Notice your patterns. Choose what comes next.</Heading>
      <Paragraph>
        {status === null
          ? 'Checking availability…'
          : status.kind === 'disabled'
            ? 'Nothing is being tracked.'
            : 'Source availability could not be checked.'}
      </Paragraph>
      {status?.kind === 'unavailable' && (
        <Action
          label="Check again"
          onPress={() => setAttempt(value => value + 1)}
        />
      )}
      <Paragraph>
        No observations yet. Tracking sources are not available in this early
        build. There is no recorded app time or activity to show.
      </Paragraph>
      <Paragraph>
        Attention Stats is being built to help you understand your day and
        choose boundaries that work for you. Observation will always be your
        choice.
      </Paragraph>
      <Action label="About your data" onPress={onPrivacy} />
    </Screen>
  );
}
