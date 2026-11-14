import { useState } from 'react';
import { TextInput } from 'react-native';
import { useUnistyles } from 'react-native-unistyles';
import { Body, Button, Card, Row, SectionTitle, CheckRow } from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { AppIcon } from '../../ui/Charts';
import { useAppIcons } from '../diary';
import {
  newProtectionId,
  type AppGroup,
  type ProtectableApp,
} from '../../platform/protectionStatus';
import { scheduleStyles as styles } from './schedulePresentation';

export function GroupEditor({
  apps,
  initial,
  busy,
  error,
  onSave,
  onBack,
  shortVideosOnly = false,
}: {
  apps: ProtectableApp[];
  initial?: AppGroup;
  busy: boolean;
  error: string | null;
  onSave: (group: AppGroup) => void;
  onBack: () => void;
  shortVideosOnly?: boolean;
}) {
  const { theme } = useUnistyles();
  const [name, setName] = useState(initial?.name ?? '');
  const [selected, setSelected] = useState(new Set(initial?.packages ?? []));
  const [query, setQuery] = useState('');
  const choices = apps.filter(
    app =>
      (!shortVideosOnly || app.shortVideos) &&
      app.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()),
  );
  const visible = [...choices].sort((a, b) => a.name.localeCompare(b.name));
  const icons = useAppIcons(visible.map(app => app.packageName));
  const valid =
    name.trim().length > 0 &&
    name.trim().length <= 60 &&
    selected.size > 0 &&
    selected.size <= 100;
  return (
    <Screen
      title={initial ? 'Edit app group' : 'Create app group'}
      subtitle="Apps you choose"
    >
      <Button
        label="Back to schedules"
        variant="text"
        onPress={onBack}
        disabled={busy}
      />
      <SectionTitle>Group name</SectionTitle>
      <TextInput
        accessibilityLabel="Group name"
        placeholder="For example, Distracting apps"
        placeholderTextColor={theme.faint}
        value={name}
        editable={!busy}
        maxLength={60}
        onChangeText={setName}
        style={styles.input}
        selectionColor={theme.accent}
      />
      <SectionTitle>Choose apps</SectionTitle>
      <TextInput
        accessibilityLabel="Search apps"
        placeholder="Search installed apps"
        placeholderTextColor={theme.faint}
        value={query}
        editable={!busy}
        onChangeText={value => {
          setQuery(value);
        }}
        style={styles.input}
        selectionColor={theme.accent}
      />
      <Body muted>
        {selected.size} selected · {choices.length} available
      </Body>
      <Card
        title="Installed Apps"
        info={
          shortVideosOnly
            ? 'This group has short-video slots, so only supported apps can be added. Remove those slots first to include other apps. Up to 100 selected apps; Save applies the draft.'
            : 'Choose one app or build a group of up to 100. Apps stay alphabetical while selected. Save applies the draft; Back discards it.'
        }
        padded={false}
      >
        {visible.map(app => (
          <CheckRow
            key={app.packageName}
            title={app.name}
            value={selected.has(app.packageName)}
            disabled={
              busy || (!selected.has(app.packageName) && selected.size >= 100)
            }
            leading={
              <AppIcon
                uri={icons[app.packageName]}
                mark={app.name.slice(0, 1)}
                color={theme.accentFill}
                size={36}
              />
            }
            onChange={enabled =>
              setSelected(current => {
                const next = new Set(current);
                if (enabled) next.add(app.packageName);
                else next.delete(app.packageName);
                return next;
              })
            }
          />
        ))}
        {visible.length === 0 && (
          <Row
            icon="info"
            title="No matching apps"
            detail="Try a different name."
          />
        )}
      </Card>
      {error && <Body>{error}</Body>}
      <Button
        label={busy ? 'Saving…' : 'Save app group'}
        disabled={!valid || busy}
        onPress={() =>
          onSave({
            id: initial?.id ?? newProtectionId(),
            name: name.trim(),
            packages: Array.from(selected),
          })
        }
      />
    </Screen>
  );
}
