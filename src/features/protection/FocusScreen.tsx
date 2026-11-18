import { useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import {
  Body,
  Button,
  Card,
  Segmented,
  Explanation,
  SectionTitle,
  Stepper,
  Title,
} from '../../ui/Kit';
import { Screen } from '../../ui/Screen';
import { FocusClock } from './FocusClock';
import { AppSelection } from './AppSelection';
import { focusChoices, type QuickFocusRequest } from './focusChoices';
import { useSchedules } from './useSchedules';
import { AppIdentityList } from '../diary';

export function FocusScreen({
  onBack,
  onSchedules,
  quickStart,
}: {
  onBack: () => void;
  onSchedules: () => void;
  quickStart?: QuickFocusRequest;
}) {
  const schedules = useSchedules();
  const { snapshot, apps, busy, error, refresh } = schedules;
  const focused = useIsFocused();
  const [packages, setPackages] = useState<string[]>([]);
  const [allowOnly, setAllowOnly] = useState(false);
  const [minutes, setMinutes] = useState(25);
  const [review, setReview] = useState<{
    packages: string[];
    revision: string;
    minutes: number;
    allowOnly: boolean;
  } | null>(null);

  const applied = useRef<number | null>(null);
  const [shortcutNotice, setShortcutNotice] = useState<string | null>(null);
  useEffect(() => {
    if (
      !focused ||
      !quickStart ||
      !snapshot ||
      busy ||
      applied.current === quickStart.request
    )
      return;
    applied.current = quickStart.request;
    if (snapshot.focus) return;
    const choice = focusChoices(snapshot.groups, apps).find(
      item => item.kind === quickStart.kind && item.id === quickStart.id,
    );
    if (!choice) {
      setShortcutNotice(
        'This group is no longer available. Choose apps to continue.',
      );
      setReview(null);
      return;
    }
    setShortcutNotice(null);
    setPackages(choice.packages);
    setMinutes(25);
    setAllowOnly(false);
    setReview({
      packages: [...choice.packages],
      minutes: 25,
      revision: snapshot.revision,
      allowOnly: false,
    });
  }, [focused, quickStart, snapshot, apps, busy]);
  const active = snapshot?.focus;
  useEffect(() => {
    if (!focused) return;
    void refresh();
    const timer = setInterval(() => {
      if (AppState.currentState === 'active') void refresh(false);
    }, 5000);
    return () => clearInterval(timer);
  }, [focused, refresh]);

  return (
    <Screen
      title="Focus session"
      subtitle="A little space for what you choose"
      back={{ label: 'Back to Protect', onPress: onBack }}
    >
      {shortcutNotice && <Body muted>{shortcutNotice}</Body>}
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
          <Card
            tone="soft"
            title="Active focus"
            info="The selected app rule lasts until this session ends. You can end early or allow a five-minute visit from the cover. Ending focus leaves other schedules and video limits unchanged. Closing this screen does not end the session."
          >
            <FocusClock
              remainingMs={active.remainingMs}
              durationMs={active.durationMs}
            />
            <Body>{active.allowOnly ? 'Allow only' : 'Block apps'}</Body>
            <AppIdentityList packages={active.packages} apps={apps} />
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
        </>
      ) : review ? (
        <>
          <Card
            tone="soft"
            title="Review focus"
            info="Existing schedules and limits still apply. Settings, emergency access and system navigation remain available. You can end at any time or allow a five-minute visit. Blocking depends on Accessibility; closing this screen does not end the session."
          >
            <Title>{review.minutes} minutes for yourself</Title>
            <Body>{review.allowOnly ? 'Allow only' : 'Block apps'}</Body>
            <AppIdentityList packages={review.packages} apps={apps} />
            <Body>
              {review.allowOnly
                ? 'Other apps blocked'
                : 'Selected apps blocked'}
            </Body>
          </Card>
          {!snapshot.accessibility && (
            <Body>
              Connect accessibility in Settings before starting focus.
            </Body>
          )}
          <Button
            label={busy ? 'Starting…' : 'Start focus'}
            disabled={busy || !snapshot.accessibility}
            onPress={() =>
              void schedules
                .focus(
                  review.packages,
                  review.minutes,
                  review.revision,
                  review.allowOnly,
                )
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
          <Segmented
            label="Focus timing"
            value="now"
            options={[
              { value: 'now', label: 'Focus now' },
              { value: 'slots', label: 'By time slots' },
            ]}
            onChange={value => {
              if (value === 'slots') onSchedules();
            }}
          />
          <Explanation title="Focus session">
            Choose app groups or individual apps, then review before starting.
            Selected apps are frozen for this session; later group edits do not
            change it. End whenever you need. Block apps targets the selected
            apps. Allow only blocks other installed launchable apps; essential
            recovery, Settings and system navigation remain available. Other
            rules still apply.
          </Explanation>
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
          <Segmented
            label="Focus app rule"
            value={allowOnly ? 'allow' : 'block'}
            onChange={value => setAllowOnly(value === 'allow')}
            options={[
              { value: 'block', label: 'Block apps' },
              { value: 'allow', label: 'Allow only' },
            ]}
          />

          <AppSelection
            groups={snapshot.groups}
            apps={apps}
            selected={packages}
            onChange={setPackages}
            disabled={busy}
          />
          <Button
            label="Review focus"
            disabled={busy || packages.length === 0}
            onPress={() => {
              if (packages.length)
                setReview({
                  packages: [...packages],
                  revision: snapshot.revision,
                  minutes,
                  allowOnly,
                });
            }}
          />
          <Button
            label="Manage app groups"
            variant="text"
            onPress={onSchedules}
          />
        </>
      )}
    </Screen>
  );
}
