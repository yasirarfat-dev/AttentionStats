import { useEffect, useRef, useState } from 'react';
import { ScrollView, View } from 'react-native';
import {
  Body,
  Button,
  CheckRow,
  SectionTitle,
  Segmented,
  Stepper,
} from '../../ui/Kit';
import {
  focusChoices,
  visibleFocusChoices,
  type QuickFocusRequest,
} from './focusChoices';
import { useSchedules } from './useSchedules';
import { FocusClock } from './FocusClock';

/** The chooser is itself the review: targets, effect, duration and explicit Start stay together. */
export function FocusQuickActions({
  onSelect,
}: {
  onSelect: (choice: QuickFocusRequest) => void;
}) {
  const schedules = useSchedules();
  const { snapshot, apps, busy, error } = schedules;
  const [selected, setSelected] = useState<string[]>([]);
  const [allowOnly, setAllowOnly] = useState(false);
  const ruleChosen = useRef(false);
  const [minutes, setMinutes] = useState(25);
  const [showApps, setShowApps] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const choices = snapshot ? focusChoices(snapshot.groups, apps) : [];
  const visibleChoices = visibleFocusChoices(
    choices,
    allowOnly,
    showAll,
    selected,
  );
  const packages = [
    ...new Set(
      choices
        .filter(item => selected.includes(item.kind + item.id))
        .flatMap(item => item.packages),
    ),
  ];
  const active = snapshot?.focus;
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => void schedules.refresh(false), 5000);
    return () => clearInterval(timer);
  }, [active?.id, schedules.refresh]);
  return (
    <View style={{ gap: 8, flexShrink: 1 }}>
      <SectionTitle>{active ? 'Active focus' : 'Focus now'}</SectionTitle>
      {error && <Body>{error}</Body>}
      {busy && !snapshot && <Body muted>Loading app groups…</Body>}
      {active ? (
        <>
          <FocusClock
            remainingMs={active.remainingMs}
            durationMs={active.durationMs}
          />
          <Body muted>
            {active.allowOnly ? 'Allow only' : 'Block apps'} ·{' '}
            {active.packages.length} chosen apps
          </Body>
          <Button
            label="End focus now"
            disabled={busy}
            onPress={() => void schedules.focus(null)}
          />
        </>
      ) : (
        <>
          <Segmented
            label="Focus app rule"
            value={allowOnly ? 'allow' : 'block'}
            onChange={value => {
              ruleChosen.current = true;
              if ((value === 'allow') === allowOnly) return;
              setAllowOnly(value === 'allow');
              setSelected([]);
              setShowAll(false);
            }}
            options={[
              { value: 'block', label: 'Block apps' },
              { value: 'allow', label: 'Allow only' },
            ]}
          />
          <ScrollView
            style={{ maxHeight: 216, flexShrink: 1 }}
            contentContainerStyle={{ gap: 0 }}
          >
            {visibleChoices.map(choice => (
              <CheckRow
                compact
                key={choice.kind + choice.id}
                title={`${choice.name} · ${choice.packages.length} apps`}
                value={selected.includes(choice.kind + choice.id)}
                disabled={busy}
                onChange={enabled => {
                  if (
                    !ruleChosen.current &&
                    enabled &&
                    selected.length === 0 &&
                    (choice.id === 'default-productive' ||
                      choice.id === 'default-essentials')
                  )
                    setAllowOnly(true);
                  setSelected(current =>
                    enabled
                      ? [...current, choice.kind + choice.id]
                      : current.filter(id => id !== choice.kind + choice.id),
                  );
                }}
              />
            ))}
            {showApps && (
              <Body muted>
                {packages
                  .map(
                    pkg =>
                      apps.find(app => app.packageName === pkg)?.name ?? pkg,
                  )
                  .join(' · ')}
              </Body>
            )}
          </ScrollView>
          {(choices.length > visibleChoices.length || showAll) && (
            <Button
              label={showAll ? 'Show fewer groups' : 'All groups'}
              variant="text"
              onPress={() => setShowAll(!showAll)}
            />
          )}
          {!busy && visibleChoices.length === 0 && choices.length > 0 && (
            <Body muted>
              Open All groups or customize a list for this mode.
            </Body>
          )}
          {!busy && choices.length === 0 && (
            <Body muted>Create an app group to begin.</Body>
          )}
          <Stepper
            label="Focus duration"
            value={minutes}
            onChange={setMinutes}
            min={5}
            max={180}
            step={5}
            suffix="minutes"
          />
          <Body muted>
            Other schedules still apply. Emergency access stays available.
          </Body>
          {packages.length > 0 && (
            <>
              <Button
                label={`${showApps ? 'Hide' : 'Review'} ${packages.length} chosen apps`}
                variant="text"
                onPress={() => setShowApps(!showApps)}
              />
            </>
          )}
          {packages.length > 100 && (
            <Body>Choose up to 100 apps for this session.</Body>
          )}
          {snapshot && !snapshot.accessibility && (
            <Body>Connect Accessibility in Settings to start focus.</Body>
          )}
          <Button
            label={busy ? 'Starting…' : `Start ${minutes}-minute focus`}
            disabled={
              busy ||
              !snapshot?.accessibility ||
              packages.length === 0 ||
              packages.length > 100
            }
            onPress={() =>
              void schedules.focus(
                packages,
                minutes,
                snapshot?.revision,
                allowOnly,
              )
            }
          />
        </>
      )}
      <Button
        label="Customize focus list"
        variant="text"
        onPress={() =>
          onSelect({ kind: 'suggested', id: 'customize', request: Date.now() })
        }
      />
    </View>
  );
}
