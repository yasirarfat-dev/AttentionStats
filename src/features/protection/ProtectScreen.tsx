import { useEffect, useState } from 'react';
import { Alert, AppState } from 'react-native';
import NativeDiary from '../../platform/specs/NativeDiary';
import { FocusChooser } from './FocusChooser';
import { FocusClock } from './FocusClock';
import { daysLabel, slotLabel } from './schedulePresentation';
import { useIsFocused } from '@react-navigation/native';
import { Body, Button, Card, Divider, Row } from '../../ui/Kit';
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
      <Card
        tone="soft"
        title="Focus"
        info="Start a voluntary timed session or set recurring time slots. Opening the chooser does not activate protection; review the selected apps and mode before Start."
      >
        <Row
          icon="clock"
          title="Focus sessions"
          detail={active ? 'Session active' : 'On the fly or on a schedule'}
          expanded={quick}
          onPress={() => setQuick(!quick)}
        />
        {quick && (
          <>
            <Button label="Focus now" onPress={() => setChooser(true)} />
            <Button
              label="Scheduled focus"
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
        <Card
          tone="soft"
          title="Active focus"
          info="The timer is native-authoritative. Blocking is best effort; system navigation and emergency recovery remain available. Ending early requires parent authorization when Protection Lock is on."
        >
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
      <Card
        title="Your groups & time slots"
        info="Groups organize selected apps. Time slots apply recurring rules to those groups. Editing a group can affect its saved slots; changes require their existing review and Save flow."
        padded={false}
      >
        {snapshot?.groups.map(group => (
          <Row
            key={group.id}
            icon="layers"
            title={group.name}
            detail={`${group.packages.length} ${group.packages.length === 1 ? 'app' : 'apps'}`}
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
          title="Edit groups & time slots"
          detail="App lists and recurring schedules"
          onPress={() => onSchedules('groups')}
        />
      </Card>
      <Card
        title="Short-video boundaries"
        info="Mindful Breaks offers a gentle pause after your chosen video count or minutes. Short-video blocks sets daily video limits or blocks supported players; other app screens remain available."
        padded={false}
      >
        <Row
          icon="heart"
          title="Mindful Breaks"
          detail="A gentle pause at your limit"
          onPress={() => onTool('Breaks')}
        />
        <Divider />
        <Row
          icon="block"
          title="Short-video blocks"
          detail="Daily limits or a full block"
          onPress={() => onTool('Blocks')}
        />
      </Card>
      <Card
        title="Protection Lock"
        info="Optional. A parent approves extra time and protection changes in one of two ways: the phone's fingerprint (one touch, if a child's fingerprint is not saved on this phone) or an app PIN or pattern that works only in this app. Android Settings stay available."
        padded={false}
      >
        <Row
          icon="lock"
          title="Parent approval"
          detail="Phone fingerprint or app PIN"
          onPress={async () => {
            if (!(await NativeDiary?.openProtectionLock()))
              Alert.alert('Protection Lock unavailable', 'Please try again.');
          }}
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
