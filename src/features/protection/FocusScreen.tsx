import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import {
  Body,
  Button,
  Card,
  Row,
  SectionTitle,
  Stepper,
  Title,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { useSchedules } from './useSchedules';

export function FocusScreen({
  onBack,
  onSchedules,
}: {
  onBack: () => void;
  onSchedules: () => void;
}) {
  const schedules = useSchedules();
  const { snapshot, apps, busy, error, refresh } = schedules;
  const focused = useIsFocused();
  const [groupId, setGroupId] = useState('');
  const [minutes, setMinutes] = useState(25);
  const [review, setReview] = useState<{
    packages: string[];
    revision: string;
    minutes: number;
  } | null>(null);
  const group = snapshot?.groups.find(item => item.id === groupId);
  const active = snapshot?.focus;
  useEffect(() => {
    if (!focused) return;
    void refresh();
    const timer = setInterval(() => {
      if (AppState.currentState === 'active') void refresh(false);
    }, 5000);
    return () => clearInterval(timer);
  }, [focused, refresh]);
  const names = (packages: string[]) =>
    packages
      .map(pkg => apps.find(app => app.packageName === pkg)?.name ?? pkg)
      .join(' · ');

  return (
    <Screen title="Focus session" subtitle="A little space for what you choose">
      <Button label="Back to Protect" variant="text" onPress={onBack} />
      {error && (
        <Card tone="warn">
          <Body>{error}</Body>
          <Button
            label="Refresh focus"
            onPress={() => void refresh()}
            disabled={busy}
          />
        </Card>
      )}
      {!snapshot ? (
        <Body>
          {busy
            ? 'Loading focus…'
            : 'Focus is unavailable. Your saved rules are unchanged.'}
        </Body>
      ) : active ? (
        <>
          <Card tone="soft">
            <Title>
              {Math.ceil(Number(active.remainingMs) / 60_000)} minutes remaining
            </Title>
            <Body>{names(active.packages)}</Body>
            <Body>
              Whole-app blocking until this session ends. You can end early or
              allow a five-minute visit from the cover.
            </Body>
            <Body muted>
              {snapshot.accessibility
                ? 'Accessibility is connected. Blocking is best effort.'
                : 'Blocking is unavailable while Accessibility is disconnected. The timer continues.'}
            </Body>
          </Card>
          <Button
            label={busy ? 'Ending…' : 'End focus now'}
            disabled={busy}
            onPress={() => void schedules.focus(null)}
          />
          <Body muted>
            Ending focus leaves your other schedules and video limits unchanged.
          </Body>
        </>
      ) : review ? (
        <>
          <Card tone="soft">
            <Title>{review.minutes} minutes for yourself</Title>
            <Body>{names(review.packages)}</Body>
            <Body>
              These whole apps will be blocked while the session is active.
              Existing schedules and limits still apply. Settings, emergency
              access and system navigation stay available.
            </Body>
            <Body muted>
              You can end focus at any time, or allow a five-minute visit.
              Blocking depends on Accessibility remaining connected. Closing
              this screen does not end the session.
            </Body>
          </Card>
          {!snapshot.accessibility && (
            <Body>
              Connect Accessibility in Settings before starting focus.
            </Body>
          )}
          <Button
            label={busy ? 'Starting…' : 'Start focus'}
            disabled={busy || !snapshot.accessibility}
            onPress={() =>
              void schedules
                .focus(review.packages, review.minutes, review.revision)
                .then(ok => {
                  if (ok) setReview(null);
                })
            }
          />
          <Button
            label="Keep editing focus"
            variant="text"
            disabled={busy}
            onPress={() => setReview(null)}
          />
        </>
      ) : (
        <>
          <Card tone="soft">
            <Title>Make room for a moment</Title>
            <Body>
              Choose an app group and a duration, then review before starting.
              There is no penalty for ending early.
            </Body>
          </Card>
          <SectionTitle>Duration</SectionTitle>
          <Stepper
            label="Focus minutes"
            value={minutes}
            suffix="min"
            min={5}
            max={180}
            step={5}
            onChange={setMinutes}
          />
          <SectionTitle>App group</SectionTitle>
          {snapshot.groups.length === 0 ? (
            <Body>
              Create an app group in schedules, then return here to choose it.
            </Body>
          ) : (
            <Card padded={false}>
              {snapshot.groups.map(item => (
                <Row
                  key={item.id}
                  title={item.name}
                  detail={names(item.packages)}
                  value={groupId === item.id ? 'Selected' : undefined}
                  onPress={() => setGroupId(item.id)}
                />
              ))}
            </Card>
          )}
          <Button
            label="Review focus"
            disabled={busy || !group}
            onPress={() => {
              if (group)
                setReview({
                  packages: [...group.packages],
                  revision: snapshot.revision,
                  minutes,
                });
            }}
          />
          <Button
            label="Manage app groups"
            variant="text"
            onPress={onSchedules}
          />
          <Body muted>
            Focus uses a snapshot of the selected apps. Later group edits do not
            change an active session.
          </Body>
        </>
      )}
    </Screen>
  );
}
