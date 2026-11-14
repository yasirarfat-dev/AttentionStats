import { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { useUnistyles } from 'react-native-unistyles';
import {
  Body,
  Button,
  Card,
  SectionTitle,
  Segmented,
  Title,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import {
  newProtectionId,
  type AppGroup,
  type ProtectableApp,
  type TimeSlot,
} from '../../platform/protectionStatus';
import {
  scheduleStyles as styles,
  weekdays,
  daysLabel,
  scopeLabel,
  slotLabel,
} from './schedulePresentation';

import { AppSelection } from './AppSelection';
import { clockDraft, toMinute, type ClockDraft } from './scheduleTime';
function ClockInput({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string;
  value: ClockDraft;
  onChange: (value: ClockDraft) => void;
  disabled: boolean;
}) {
  const { theme } = useUnistyles();
  return (
    <Card
      title={label}
      info="Enter hours 1–12 and minutes 00–59, then choose AM or PM. Overnight slots end on the following date; weekdays refer to their start."
    >
      <View style={styles.row}>
        <TextInput
          accessibilityLabel={`${label} hour`}
          value={value.hour}
          editable={!disabled}
          onChangeText={hour => onChange({ ...value, hour })}
          keyboardType="number-pad"
          maxLength={2}
          selectTextOnFocus
          style={styles.timeField}
          selectionColor={theme.accent}
        />
        <Text style={styles.time}>:</Text>
        <TextInput
          accessibilityLabel={`${label} minute`}
          value={value.minute}
          editable={!disabled}
          onChangeText={minute => onChange({ ...value, minute })}
          keyboardType="number-pad"
          maxLength={2}
          selectTextOnFocus
          style={styles.timeField}
          selectionColor={theme.accent}
        />
      </View>
      <Segmented
        label={`${label} period`}
        disabled={disabled}
        value={value.period}
        options={[
          { value: 'am', label: 'AM' },
          { value: 'pm', label: 'PM' },
        ]}
        onChange={period => onChange({ ...value, period })}
      />
    </Card>
  );
}

export function TimeSlotEditor({
  groups,
  apps,
  initial,
  initialGroup,
  busy,
  error,
  onSave,
  onBack,
}: {
  groups: AppGroup[];
  apps: ProtectableApp[];
  initial?: TimeSlot;
  initialGroup?: string;
  busy: boolean;
  error: string | null;
  onSave: (slot: TimeSlot, group?: AppGroup) => void;
  onBack: () => void;
}) {
  const initialSelected = groups.find(
    group => group.id === (initial?.groupId ?? initialGroup),
  );
  const [packages, setPackages] = useState<string[]>(
    initialSelected?.packages ?? [],
  );
  const [customId] = useState(newProtectionId);
  const [customName, setCustomName] = useState('Selected Apps');
  const existingGroup = [...groups]
    .sort(
      (a, b) =>
        Number(b.id === initialSelected?.id) -
        Number(a.id === initialSelected?.id),
    )
    .find(
      group =>
        group.packages.length === packages.length &&
        group.packages.every(pkg => packages.includes(pkg)),
    );
  const groupId = existingGroup?.id ?? customId;
  const [scope, setScope] = useState<TimeSlot['scope']>(
    initial?.scope ?? 'app',
  );
  const [allowOnly, setAllowOnly] = useState(initial?.allowOnly ?? false);
  const [allDay, setAllDay] = useState(initial?.allDay ?? false);
  const [days, setDays] = useState(initial?.days ?? 127);
  const [start, setStart] = useState(
    clockDraft(initial?.allDay ? 300 : (initial?.startMinute ?? 300)),
  );
  const [end, setEnd] = useState(
    clockDraft(initial?.allDay ? 660 : (initial?.endMinute ?? 660)),
  );
  const selected =
    existingGroup ??
    (packages.length > 0
      ? { id: customId, name: customName.trim(), packages }
      : undefined);
  const supported =
    selected?.packages.every(pkg =>
      apps.some(app => app.packageName === pkg && app.shortVideos),
    ) ?? false;
  const canSelectShortVideos =
    supported ||
    (initial?.scope === 'short_videos' && groupId === initial.groupId);
  const from = allDay ? 0 : toMinute(start),
    to = allDay ? 0 : toMinute(end);
  const valid =
    (!allowOnly || scope === 'app') &&
    !!selected &&
    selected.name.length > 0 &&
    (existingGroup !== undefined || groups.length < 30) &&
    (scope === 'app' || canSelectShortVideos) &&
    days > 0 &&
    from !== null &&
    to !== null &&
    (allDay || from !== to);
  const slot: TimeSlot = {
    id: initial?.id ?? newProtectionId(),
    groupId,
    allowOnly,
    scope,
    allDay,
    days,
    startMinute: from ?? 0,
    endMinute: to ?? 0,
    enabled: false,
  };
  return (
    <Screen
      title={initial ? 'Edit time slot' : 'A little time away'}
      subtitle="Make room for what matters"
    >
      <Button
        label="Back to schedules"
        variant="text"
        onPress={onBack}
        disabled={busy}
      />
      <Segmented
        label="Time Slot App Rule"
        value={allowOnly ? 'allow' : 'block'}
        disabled={busy}
        options={[
          { value: 'block', label: 'Block Apps' },
          { value: 'allow', label: 'Allow Only' },
        ]}
        onChange={value => {
          setAllowOnly(value === 'allow');
          if (value === 'allow') setScope('app');
        }}
      />
      <AppSelection
        groups={groups}
        apps={apps}
        selected={packages}
        onChange={setPackages}
        disabled={busy}
      />
      {!existingGroup && packages.length > 0 && (
        <>
          <SectionTitle>Save Selection As</SectionTitle>
          <TextInput
            accessibilityLabel="Selection Group Name"
            value={customName}
            onChangeText={setCustomName}
            maxLength={60}
            style={styles.input}
            editable={!busy}
          />
          {groups.length >= 30 && (
            <Body>
              Group limit reached. Choose an existing group or remove one first.
            </Body>
          )}
        </>
      )}
      <Card
        title="What to Block"
        info={
          slot.allowOnly
            ? 'Other installed launchable apps are blocked. The selected apps and essential recovery controls remain available; other rules still apply.'
            : slot.scope === 'app'
              ? 'Every screen in the selected apps. Their content is not read for this rule. Other saved limits and schedules apply independently.'
              : 'Only recognized short-video viewers. Messages and other screens remain available. Source tracking must be enabled.'
        }
      >
        <Segmented
          label="Blocking scope"
          disabled={busy}
          value={slot.scope}
          options={
            !allowOnly && (canSelectShortVideos || scope === 'short_videos')
              ? [
                  { value: 'app', label: 'Whole app' },
                  { value: 'short_videos', label: 'Short videos' },
                ]
              : [{ value: 'app', label: 'Whole app' }]
          }
          onChange={setScope}
        />
        {scope === 'short_videos' && !canSelectShortVideos && (
          <Body muted>
            Short-video blocking is unavailable for this group. Choose a
            supported group or explicitly select Whole app before saving.
          </Body>
        )}
      </Card>
      <Card
        title="When"
        info="Times follow your phone's local clock. Choose the weekdays on which the slot starts. Always covers each chosen day; a slot that crosses midnight ends the next day."
      >
        <Segmented
          label="Slot timing"
          disabled={busy}
          value={allDay ? 'always' : 'custom'}
          options={[
            { value: 'custom', label: 'Time slot' },
            { value: 'always', label: 'Always' },
          ]}
          onChange={value => setAllDay(value === 'always')}
        />
        <View style={styles.row}>
          {weekdays.map((day, index) => (
            <Pressable
              key={day}
              accessibilityRole="checkbox"
              disabled={busy}
              accessibilityLabel={day}
              accessibilityState={{ checked: !!(days & (1 << index)) }}
              onPress={() => setDays(days ^ (1 << index))}
              style={[styles.day, !!(days & (1 << index)) && styles.dayOn]}
            >
              <Text
                style={[
                  styles.dayText,
                  !!(days & (1 << index)) && styles.dayTextOn,
                ]}
              >
                {day}
              </Text>
            </Pressable>
          ))}
        </View>
        <Body muted>{days ? daysLabel(days) : 'Choose at least one day.'}</Body>
      </Card>
      {!allDay && (
        <>
          <ClockInput
            label="From"
            value={start}
            onChange={setStart}
            disabled={busy}
          />
          <ClockInput
            label="Until"
            value={end}
            onChange={setEnd}
            disabled={busy}
          />
        </>
      )}
      <Card
        tone="soft"
        title="Review Time Slot"
        info="New slots save Off so you can review them before enabling. A five-minute allowance and essential recovery controls remain available. Overnight days refer to the date the slot starts."
      >
        <Title>{valid ? slotLabel(slot) : 'Check your time slot'}</Title>
        <Body>
          {selected?.name ?? 'Choose apps'} · {scopeLabel(slot)}
        </Body>
        <Body muted>
          {valid
            ? `${daysLabel(days)} · Saves Off`
            : 'Use hours 1–12 and minutes 00–59, choose days, and use different start and end times.'}
        </Body>
        {!allDay && from !== null && to !== null && from > to && (
          <Body muted>Ends Next Day</Body>
        )}
      </Card>
      {error && <Body>{error}</Body>}
      <Button
        label={busy ? 'Saving…' : 'Save time slot · off'}
        disabled={!valid || busy}
        onPress={() => onSave(slot, existingGroup ? undefined : selected)}
      />
    </Screen>
  );
}
