import type { AppGroup, ProtectableApp } from '../../platform/protectionStatus';

export type QuickFocusRequest = {
  kind: 'suggested' | 'saved';
  id: string;
  request: number;
};
export type FocusChoice = Omit<QuickFocusRequest, 'request'> & {
  name: string;
  packages: string[];
};

/** Suggestions are display-only and contain only installed, native-approved targets. */
export function focusChoices(
  groups: AppGroup[],
  apps: ProtectableApp[],
): FocusChoice[] {
  const available = new Set(apps.map(app => app.packageName));
  const reels = apps.filter(app => app.shortVideos).map(app => app.packageName);
  const saved = groups
    .map(group => ({
      kind: 'saved' as const,
      id: group.id,
      name: group.name,
      packages: group.packages.filter(pkg => available.has(pkg)),
    }))
    .filter(group => group.packages.length > 0);
  return [
    ...(reels.length && !groups.some(group => group.id === 'default-reels')
      ? [
          {
            kind: 'suggested' as const,
            id: 'reels-apps',
            name: 'Reels Apps',
            packages: reels.slice(0, 100),
          },
        ]
      : []),
    ...saved,
  ];
}

/** Keep relevant starters and user choices visible; the complete catalog stays available. */
export function visibleFocusChoices(
  choices: FocusChoice[],
  allowOnly: boolean,
  showAll: boolean,
  selected: string[],
): FocusChoice[] {
  const starterNames = allowOnly
    ? [
        ['default-essentials', 'essentials'],
        ['default-productive', 'productive apps'],
      ]
    : [
        ['default-social', 'social apps'],
        ['default-games', 'games'],
        ['default-distracting', 'distracting apps'],
      ];
  const starters = new Set(
    starterNames.map(
      ([id, name]) =>
        choices.find(c => c.id === id)?.id ??
        choices.find(c => c.name.toLowerCase() === name)?.id,
    ),
  );

  return choices.filter(
    choice =>
      showAll ||
      starters.has(choice.id) ||
      selected.includes(choice.kind + choice.id),
  );
}
