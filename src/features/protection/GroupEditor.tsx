import { useState } from 'react';
import { TextInput } from 'react-native';
import { useUnistyles } from 'react-native-unistyles';
import {
  Body,
  Button,
  Card,
  Row,
  SectionTitle,
  ToggleRow,
  Title,
} from '../../ui/Kit';
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
  const [shown, setShown] = useState(40);
  const [review, setReview] = useState(false);
  const choices = apps.filter(
    app =>
      (!shortVideosOnly || app.shortVideos) &&
      app.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()),
  );
  const visible = choices.slice(0, shown);
  const icons = useAppIcons(visible.map(app => app.packageName));
  const valid =
    name.trim().length > 0 &&
    name.trim().length <= 60 &&
    selected.size > 0 &&
    selected.size <= 100;
  return (
    <Screen
      title={
        review
          ? 'Your app group'
          : initial
            ? 'Edit app group'
            : 'Less distraction, together'
      }
      subtitle="Apps you choose"
    >
      <Button
        label={review ? 'Keep editing' : 'Back to schedules'}
        variant="text"
        onPress={() => (review ? setReview(false) : onBack())}
        disabled={busy}
      />
      {review ? (
        <>
          <Card tone="soft">
            <Title>{name.trim()}</Title>
            <Body>
              {selected.size} selected {selected.size === 1 ? 'app' : 'apps'}
            </Body>
            <Body muted>
              Enabled slots for this group will apply to these apps. Emergency,
              keyboard and system recovery apps stay available.
            </Body>
          </Card>
          <Card padded={false}>
            {Array.from(selected).map(pkg => (
              <Row
                key={pkg}
                icon="layers"
                title={
                  apps.find(app => app.packageName === pkg)?.name ??
                  'Previously selected app'
                }
              />
            ))}
          </Card>
          {error && <Body>{error}</Body>}
          <Button
            label={busy ? 'Saving…' : 'Save app group'}
            disabled={busy}
            onPress={() =>
              onSave({
                id: initial?.id ?? newProtectionId(),
                name: name.trim(),
                packages: Array.from(selected),
              })
            }
          />
        </>
      ) : (
        <>
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
              setShown(40);
            }}
            style={styles.input}
            selectionColor={theme.accent}
          />
          <Body muted>
            {selected.size} selected · {choices.length} available. Pick one app
            or build your own group.
          </Body>
          {shortVideosOnly && (
            <Body muted>
              This group has short-video slots, so only supported apps can be
              added. Remove those slots first to include other apps.
            </Body>
          )}
          <Card padded={false}>
            {visible.map(app => (
              <ToggleRow
                key={app.packageName}
                title={app.name}
                value={selected.has(app.packageName)}
                disabled={
                  busy ||
                  (!selected.has(app.packageName) && selected.size >= 100)
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
          {shown < choices.length && (
            <Button
              label="Show more apps"
              variant="tonal"
              onPress={() => setShown(shown + 40)}
            />
          )}
          <Button
            label="Review group"
            disabled={!valid || busy}
            onPress={() => setReview(true)}
          />
        </>
      )}
    </Screen>
  );
}
