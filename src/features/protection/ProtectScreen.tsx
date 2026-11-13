import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import { Body, Button, Card, Row, SectionTitle } from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import type { ProtectionTool } from './ProtectionToolScreen';
import { useSchedules } from './useSchedules';

export function ProtectScreen({
  onFocus,
  onSchedules,
  onTool,
}: {
  onFocus: () => void;
  onSchedules: () => void;
  onTool: (tool: ProtectionTool) => void;
}) {
  const { snapshot, refresh, focus, busy, error } = useSchedules(false);
  const focused = useIsFocused();
  useEffect(() => {
    if (!focused) return;
    void refresh(false);
    const timer = setInterval(() => {
      if (AppState.currentState === 'active') void refresh(false);
    }, 5000);
    return () => clearInterval(timer);
  }, [focused, refresh]);
  const active = snapshot?.focus;
  const [quick, setQuick] = useState(false);
  return (
    <Screen title="Protect" subtitle="Gentle tools you choose">
      <Card tone="soft">
        <Row
          icon="clock"
          title="Focus Sessions"
          detail={active ? 'Session Active' : 'On the Fly or On a Schedule'}
          value={quick ? '−' : '+'}
          onPress={() => setQuick(!quick)}
        />
        {quick && (
          <>
            <Button label="Focus Now" onPress={onFocus} />
            <Button
              label="Scheduled Focus"
              variant="tonal"
              onPress={onSchedules}
            />
          </>
        )}
      </Card>
      {error && (
        <Card tone="warn">
          <Body>{error}</Body>
          <Button
            label="Refresh protection"
            onPress={() => void refresh(false)}
            disabled={busy}
          />
        </Card>
      )}
      {active && (
        <Card tone="soft">
          <Body>
            Focus · {Math.ceil(Number(active.remainingMs) / 60_000)} minutes
            remaining
          </Body>
          <Body muted>
            {snapshot.accessibility
              ? 'Accessibility is connected. Blocking is best effort.'
              : 'Blocking is unavailable while Accessibility is disconnected. The timer continues.'}
          </Body>
          <Button
            label="View focus session"
            variant="tonal"
            onPress={onFocus}
          />
          <Button
            label="End focus now"
            onPress={() => void focus(null)}
            disabled={busy}
          />
        </Card>
      )}
      <SectionTitle>Focus & schedules</SectionTitle>
      <Card padded={false}>
        <Row
          icon="clock"
          title="Focus session"
          detail="Choose an app group and a duration. End whenever you need."
          onPress={onFocus}
        />
        <Row
          icon="layers"
          title="App groups & schedules"
          detail="Choose apps together. Set recurring periods or all-day blocks."
          onPress={onSchedules}
        />
      </Card>
      <SectionTitle>Short-video boundaries</SectionTitle>
      <Card padded={false}>
        <Row
          icon="heart"
          title="Mindful breaks"
          detail="Set a gentle pause after a number of videos or minutes."
          onPress={() => onTool('Breaks')}
        />
        <Row
          icon="block"
          title="Short-video blocks"
          detail="Set daily video limits or block the player in selected apps."
          onPress={() => onTool('Blocks')}
        />
      </Card>
      <SectionTitle>In the moment</SectionTitle>
      <Card padded={false}>
        <Row
          icon="eye"
          title="Live counter"
          detail="Choose when the floating counter appears and what it shows."
          onPress={() => onTool('Counter')}
        />
      </Card>
    </Screen>
  );
}
