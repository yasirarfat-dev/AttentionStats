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
    <Card>
      <Body muted>{label}</Body>
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
  onSave: (slot: TimeSlot) => void;
  onBack: () => void;
}) {
  const [groupId, setGroupId] = useState(
    initial?.groupId ?? initialGroup ?? groups[0]?.id ?? '',
  );
  const [scope, setScope] = useState<TimeSlot['scope']>(
    initial?.scope ?? 'app',
  );
  const [allDay, setAllDay] = useState(initial?.allDay ?? false);
  const [days, setDays] = useState(initial?.days ?? 127);
  const [start, setStart] = useState(
    clockDraft(initial?.allDay ? 300 : (initial?.startMinute ?? 300)),
  );
  const [end, setEnd] = useState(
    clockDraft(initial?.allDay ? 660 : (initial?.endMinute ?? 660)),
  );
  const selected = groups.find(group => group.id === groupId);
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
    !!selected &&
    days > 0 &&
    from !== null &&
    to !== null &&
    (allDay || from !== to);
  const slot: TimeSlot = {
    id: initial?.id ?? newProtectionId(),
    groupId,
    scope: canSelectShortVideos ? scope : 'app',
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
      <SectionTitle>Apps to pause</SectionTitle>
      <Card padded={false}>
        {groups.map(group => (
          <Pressable
            key={group.id}
            accessibilityRole="radio"
            accessibilityState={{ selected: groupId === group.id }}
            disabled={busy}
            onPress={() => {
              setGroupId(group.id);
              if (
                !group.packages.every(pkg =>
                  apps.some(app => app.packageName === pkg && app.shortVideos),
                )
              )
                setScope('app');
            }}
          >
            <View style={[styles.row, { padding: 16 }]}>
              <View style={styles.grow}>
                <Text style={styles.heading}>{group.name}</Text>
                <Body muted>
                  {group.packages.length}{' '}
                  {group.packages.length === 1 ? 'app' : 'apps'}
                </Body>
              </View>
              <Body>{group.id === groupId ? 'Selected' : 'Choose'}</Body>
            </View>
          </Pressable>
        ))}
      </Card>
      <SectionTitle>What to block</SectionTitle>
      <Card>
        <Segmented
          label="Blocking scope"
          disabled={busy}
          value={slot.scope}
          options={
            canSelectShortVideos
              ? [
                  { value: 'app', label: 'Whole app' },
                  { value: 'short_videos', label: 'Short videos' },
                ]
              : [{ value: 'app', label: 'Whole app' }]
          }
          onChange={setScope}
        />
        <Body muted>
          {slot.scope === 'app'
            ? 'Every screen in the selected apps. Their content is never read for this rule.'
            : 'Only recognized short-video viewers. Messages and other screens stay available. Source tracking must be enabled.'}
        </Body>
      </Card>
      <SectionTitle>When</SectionTitle>
      <Card>
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
      <Card tone="soft">
        <Title>{valid ? slotLabel(slot) : 'Check your time slot'}</Title>
        <Body>
          {selected?.name ?? 'Choose apps'} · {scopeLabel(slot)}
        </Body>
        <Body muted>
          {valid
            ? `${daysLabel(days)}. Saved off so you can review before enabling. You can always allow a 5-minute visit.`
            : 'Use hours 1–12 and minutes 00–59, choose days, and use different start and end times.'}
        </Body>
        {!allDay && from !== null && to !== null && from > to && (
          <Body muted>
            This slot ends the next day. Days refer to when it starts.
          </Body>
        )}
      </Card>
      {error && <Body>{error}</Body>}
      <Button
        label={busy ? 'Saving…' : 'Save time slot · off'}
        disabled={!valid || busy}
        onPress={() => onSave(slot)}
      />
    </Screen>
  );
}
