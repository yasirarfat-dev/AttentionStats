import { Body, Row, SectionTitle } from '../../ui/Kit';
import { focusChoices, type QuickFocusRequest } from './focusChoices';
import { useSchedules } from './useSchedules';

export function FocusQuickActions({
  onSelect,
}: {
  onSelect: (choice: QuickFocusRequest) => void;
}) {
  const { snapshot, apps, busy, error } = useSchedules();
  const choices = snapshot ? focusChoices(snapshot.groups, apps) : [];
  return (
    <>
      <SectionTitle>Focus Now</SectionTitle>
      {snapshot?.focus ? (
        <Row
          icon="clock"
          title="View Active Focus"
          detail="Check remaining time or end your session."
          onPress={() =>
            onSelect({ kind: 'suggested', id: 'active', request: Date.now() })
          }
        />
      ) : (
        <>
          {busy && !snapshot && <Body muted>Loading focus shortcuts…</Body>}
          {error && (
            <Body muted>
              Focus shortcuts could not load. Open Choose a Focus Session to
              retry.
            </Body>
          )}
          {choices.slice(0, 4).map(choice => (
            <Row
              key={choice.kind + choice.id}
              icon="clock"
              title={'Focus With ' + choice.name}
              detail={
                '25 min · ' +
                choice.packages
                  .map(
                    pkg =>
                      apps.find(app => app.packageName === pkg)?.name ?? pkg,
                  )
                  .slice(0, 3)
                  .join(' · ') +
                (choice.packages.length > 3
                  ? ' +' + (choice.packages.length - 3)
                  : '') +
                ' · Review Before Starting'
              }
              onPress={() =>
                onSelect({
                  kind: choice.kind,
                  id: choice.id,
                  request: Date.now(),
                })
              }
            />
          ))}
        </>
      )}
    </>
  );
}
