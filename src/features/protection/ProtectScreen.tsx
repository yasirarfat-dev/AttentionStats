import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { FocusChooser } from './FocusChooser';
import { FocusClock } from './FocusClock';
import { daysLabel, slotLabel } from './schedulePresentation';
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
  onSchedules: (view?: 'groups' | 'slots') => void;
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
  const [quick, setQuick] = useState(true);
  const [chooser, setChooser] = useState(false);
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
            <Button label="Focus Now" onPress={() => setChooser(true)} />
            <Button
              label="Scheduled Focus"
              variant="tonal"
              onPress={() => onSchedules('slots')}
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
          <FocusClock
            remainingMs={active.remainingMs}
            durationMs={active.durationMs}
          />
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
      <SectionTitle>Your groups & time slots</SectionTitle>
      <Card padded={false}>
        {snapshot?.groups.map(group => (
          <Row
            key={group.id}
            icon="layers"
            title={group.name}
            detail={`${group.packages.length} apps`}
            onPress={() => onSchedules('groups')}
          />
        ))}
        {snapshot?.windows.map(slot => (
          <Row
            key={slot.id}
            icon="clock"
            title={
              snapshot.groups.find(group => group.id === slot.groupId)?.name ??
              'Time slot'
            }
            detail={`${slotLabel(slot)} · ${daysLabel(slot.days)} · ${slot.enabled ? 'On' : 'Off'}`}
            onPress={() => onSchedules('slots')}
          />
        ))}
        <Row
          icon="layers"
          title="Customize focus lists & schedules"
          detail="Edit groups or recurring time slots"
          onPress={() => onSchedules('groups')}
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
      <FocusChooser
        visible={chooser}
        onClose={() => {
          setChooser(false);
          void refresh(false);
        }}
        onCustomize={() => {
          setChooser(false);
          onSchedules('groups');
        }}
      />
    </Screen>
  );
}
