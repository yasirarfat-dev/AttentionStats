import { useEffect, useState } from 'react';
import { BackHandler, Text, View } from 'react-native';
import { useUnistyles } from 'react-native-unistyles';
import {
  Body,
  Button,
  Card,
  Row,
  SectionTitle,
  Title,
  ToggleRow,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { AppIcon } from '../../ui/Charts';
import { useAppIcons } from '../diary';
import {
  newProtectionId,
  type AppGroup,
  type ProtectionSnapshot,
  type TimeSlot,
} from '../../platform/protectionStatus';
import { useSchedules } from './useSchedules';
import { GroupEditor } from './GroupEditor';
import { TimeSlotEditor } from './TimeSlotEditor';
import {
  daysLabel,
  scopeLabel,
  slotLabel,
  scheduleStyles as styles,
} from './schedulePresentation';

type Draft = { base: ProtectionSnapshot } & (
  | { kind: 'group'; group?: AppGroup }
  | { kind: 'slot'; slot?: TimeSlot; group?: AppGroup }
  | { kind: 'enable' | 'delete-slot'; slot: TimeSlot }
  | { kind: 'delete-group'; group: AppGroup }
);

export function SchedulesScreen({
  packageName,
  onBack,
}: {
  packageName?: string;
  onBack: () => void;
}) {
  const schedules = useSchedules();
  const { snapshot, apps, busy, error } = schedules;
  const { theme } = useUnistyles();
  const [draft, setDraft] = useState<Draft | null>(null);
  const icons = useAppIcons(
    packageName ? [packageName] : apps.slice(0, 80).map(app => app.packageName),
  );
  useEffect(() => {
    const listener = BackHandler.addEventListener('hardwareBackPress', () => {
      if (busy) return true;
      if (draft) {
        setDraft(null);
        return true;
      }
      onBack();
      return true;
    });
    return () => listener.remove();
  }, [draft, busy, onBack]);
  async function commit(next: ProtectionSnapshot) {
    if (await schedules.save(next)) setDraft(null);
  }
  if (draft?.kind === 'group')
    return (
      <GroupEditor
        initial={draft.group}
        shortVideosOnly={draft.base.windows.some(
          slot =>
            slot.groupId === draft.group?.id && slot.scope === 'short_videos',
        )}
        apps={apps}
        busy={busy}
        error={error}
        onBack={() => setDraft(null)}
        onSave={group =>
          void commit({
            ...draft.base,
            groups: draft.base.groups
              .filter(item => item.id !== group.id)
              .concat(group),
          })
        }
      />
    );
  if (draft?.kind === 'slot') {
    const groups = draft.group
      ? draft.base.groups
          .filter(group => group.id !== draft.group?.id)
          .concat(draft.group)
      : draft.base.groups;
    return (
      <TimeSlotEditor
        groups={packageName && draft.group ? [draft.group] : groups}
        apps={apps}
        initial={draft.slot}
        initialGroup={draft.group?.id}
        busy={busy}
        error={error}
        onBack={() => setDraft(null)}
        onSave={slot =>
          void commit({
            ...draft.base,
            groups,
            windows: draft.base.windows
              .filter(item => item.id !== slot.id)
              .concat(slot),
          })
        }
      />
    );
  }
  if (draft) {
    const group =
      draft.kind === 'delete-group'
        ? draft.group
        : draft.base.groups.find(item => item.id === draft.slot.groupId);
    const slot = draft.kind === 'delete-group' ? null : draft.slot;
    return (
      <Screen
        title={
          draft.kind === 'enable'
            ? 'Ready for a little space?'
            : 'Remove this rule?'
        }
        subtitle="Your choice, always"
      >
        <Button
          label="Back to schedules"
          variant="text"
          onPress={() => setDraft(null)}
          disabled={busy}
        />
        <Card tone="soft">
          <Title>{group?.name ?? 'Selected apps'}</Title>
          <Body>
            {group?.packages
              .map(
                pkg =>
                  apps.find(app => app.packageName === pkg)?.name ??
                  'Previously selected app',
              )
              .join(' · ')}
          </Body>
          {slot && (
            <>
              <Text style={styles.time}>{slotLabel(slot)}</Text>
              <Body>
                {daysLabel(slot.days)} · {scopeLabel(slot)}
              </Body>
            </>
          )}
          <Body muted>
            {draft.kind === 'enable'
              ? 'This slot will block the selected apps during these times. You can always allow 5 minutes, use system navigation or change this rule.'
              : draft.kind === 'delete-group'
                ? 'This also removes all time slots for the group. Your recorded history is preserved.'
                : 'This removes the time slot. Your recorded history is preserved.'}
          </Body>
        </Card>
        {error && <Body>{error}</Body>}
        <Button
          label={
            busy
              ? 'Saving…'
              : draft.kind === 'enable'
                ? 'Enable time slot'
                : 'Remove'
          }
          variant={draft.kind === 'enable' ? 'filled' : 'danger'}
          disabled={busy}
          onPress={() => {
            if (draft.kind === 'delete-group')
              void commit({
                ...draft.base,
                groups: draft.base.groups.filter(
                  item => item.id !== draft.group.id,
                ),
                windows: draft.base.windows.filter(
                  item => item.groupId !== draft.group.id,
                ),
              });
            else if (draft.kind === 'delete-slot')
              void commit({
                ...draft.base,
                windows: draft.base.windows.filter(
                  item => item.id !== draft.slot.id,
                ),
              });
            else
              void commit({
                ...draft.base,
                windows: draft.base.windows.map(item =>
                  item.id === draft.slot.id ? { ...item, enabled: true } : item,
                ),
              });
          }}
        />
      </Screen>
    );
  }
  const focused = packageName
    ? snapshot?.groups.filter(
        group =>
          group.packages.length === 1 && group.packages[0] === packageName,
      )
    : snapshot?.groups;
  const windows =
    snapshot?.windows.filter(slot =>
      focused?.some(group => group.id === slot.groupId),
    ) ?? [];
  const app = apps.find(item => item.packageName === packageName);
  function addSlot() {
    if (!snapshot) return;
    const group = packageName
      ? (focused?.[0] ?? {
          id: newProtectionId(),
          name: app?.name || 'App',
          packages: [packageName],
        })
      : undefined;
    setDraft({ kind: 'slot', base: snapshot, group });
  }
  return (
    <Screen
      title={packageName ? app?.name || 'App time slots' : 'Space for your day'}
      subtitle="App schedules"
    >
      <Button label="Back to Protect" variant="text" onPress={onBack} />
      <Card tone="hero">
        <Text style={styles.heroHeading}>Less distraction. More room.</Text>
        <Text style={styles.heroBody}>
          Set aside time for what you want to do. Choose one app or a group,
          then make a schedule that fits your day.
        </Text>
        <Button
          label="+ Add time slot"
          variant="tonal"
          disabled={
            !snapshot || busy || (!packageName && !snapshot.groups.length)
          }
          onPress={addSlot}
        />
      </Card>
      {snapshot && !snapshot.accessibility && (
        <Card tone="warn">
          <Body>
            Accessibility is disconnected. Saved schedules are not enforcing.
            Reconnect it in Settings.
          </Body>
        </Card>
      )}
      {error && (
        <Card>
          <Body>{error}</Body>
          <Button
            label="Refresh schedules"
            variant="tonal"
            onPress={() => void schedules.refresh()}
            disabled={busy}
          />
        </Card>
      )}
      {!snapshot && !error && <Body muted>Loading your schedules…</Body>}
      <SectionTitle>Your time slots</SectionTitle>
      {windows.length === 0 && snapshot && (
        <Card>
          <View style={styles.empty}>
            <Title>A quieter routine starts here</Title>
            <Body muted>
              {packageName
                ? 'Try 5–11 AM for mornings, then add another slot for your evenings. New slots stay off until you enable them.'
                : 'Make an app group below, then add morning, evening or overnight slots. New slots stay off until you enable them.'}
            </Body>
          </View>
        </Card>
      )}
      {windows.map(slot => {
        const group = snapshot?.groups.find(item => item.id === slot.groupId);
        return (
          <Card key={slot.id}>
            <ToggleRow
              icon="clock"
              title={group?.name ?? 'App group'}
              detail={`${daysLabel(slot.days)} · ${scopeLabel(slot)}`}
              value={slot.enabled}
              disabled={busy}
              onChange={enabled => {
                if (!snapshot) return;
                if (enabled) setDraft({ kind: 'enable', base: snapshot, slot });
                else
                  void schedules.save({
                    ...snapshot,
                    windows: snapshot.windows.map(item =>
                      item.id === slot.id ? { ...item, enabled: false } : item,
                    ),
                  });
              }}
            />
            <Text style={styles.time}>{slotLabel(slot)}</Text>
            <View style={styles.row}>
              <View style={styles.grow}>
                <Button
                  label="Edit slot"
                  variant="tonal"
                  disabled={busy}
                  onPress={() =>
                    snapshot &&
                    setDraft({
                      kind: 'slot',
                      base: snapshot,
                      slot,
                      group: packageName ? group : undefined,
                    })
                  }
                />
              </View>
              <View style={styles.grow}>
                <Button
                  label="Remove"
                  variant="text"
                  disabled={busy}
                  onPress={() =>
                    snapshot &&
                    setDraft({ kind: 'delete-slot', base: snapshot, slot })
                  }
                />
              </View>
            </View>
          </Card>
        );
      })}
      {!packageName && (
        <>
          <SectionTitle>App groups</SectionTitle>
          {snapshot?.groups.map(group => (
            <Card key={group.id} padded={false}>
              <Row
                leading={
                  <AppIcon
                    uri={icons[group.packages[0] ?? '']}
                    mark={group.name.slice(0, 1)}
                    color={theme.accentFill}
                    size={40}
                  />
                }
                title={group.name}
                detail={`${group.packages.length} selected ${group.packages.length === 1 ? 'app' : 'apps'}`}
                onPress={() =>
                  setDraft({ kind: 'group', base: snapshot, group })
                }
              />
              <Button
                label={`Remove ${group.name}`}
                variant="text"
                disabled={busy}
                onPress={() =>
                  setDraft({ kind: 'delete-group', base: snapshot, group })
                }
              />
            </Card>
          ))}
          <Button
            label="+ Create app group"
            variant="tonal"
            disabled={!snapshot || busy}
            onPress={() =>
              snapshot && setDraft({ kind: 'group', base: snapshot })
            }
          />
        </>
      )}
      <Card tone="soft">
        <Body muted>
          Times follow your phone’s local clock. Slots block during the chosen
          periods, independently of daily video limits. Essential recovery apps
          stay available; Android may interrupt enforcement.
        </Body>
      </Card>
    </Screen>
  );
}
