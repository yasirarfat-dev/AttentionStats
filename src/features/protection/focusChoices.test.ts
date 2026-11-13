import { focusChoices, visibleFocusChoices } from './focusChoices';

test('suggested focus includes only installed native-approved Reels targets without saving groups', () => {
  const apps = [
    { packageName: 'com.example.reels', name: 'Reels', shortVideos: true },
    { packageName: 'com.example.utility', name: 'Utility', shortVideos: false },
  ];
  expect(focusChoices([], apps)).toEqual([
    {
      kind: 'suggested',
      id: 'reels-apps',
      name: 'Reels Apps',
      packages: ['com.example.reels'],
    },
  ]);
});
test('saved shortcuts exclude unavailable targets and omit empty groups', () => {
  const groups = [
    {
      id: 'a',
      name: 'My Group',
      packages: ['com.example.available', 'com.example.removed'],
    },
    { id: 'b', name: 'Removed', packages: ['com.example.removed'] },
  ];
  const apps = [
    {
      packageName: 'com.example.available',
      name: 'Available',
      shortVideos: false,
    },
  ];
  expect(focusChoices(groups, apps)).toEqual([
    {
      kind: 'saved',
      id: 'a',
      name: 'My Group',
      packages: ['com.example.available'],
    },
  ]);
  expect(groups[0]?.packages).toHaveLength(2);
});
test('no installed suggested targets produces no default focus shortcut', () => {
  expect(focusChoices([], [])).toEqual([]);
});

const starters = [
  'social',
  'distracting',
  'games',
  'productive',
  'essentials',
  'reels',
].map(id => ({
  kind: 'saved' as const,
  id: `default-${id}`,
  name: id,
  packages: ['example.app'],
}));
test('focus modes show only relevant starters until the full catalog is requested', () => {
  const choices = [
    ...starters,
    {
      kind: 'saved' as const,
      id: 'custom',
      name: 'My apps',
      packages: ['example.app'],
    },
  ];
  expect(visibleFocusChoices(choices, false, false, []).map(c => c.id)).toEqual(
    ['default-social', 'default-distracting', 'default-games'],
  );
  expect(visibleFocusChoices(choices, true, false, []).map(c => c.id)).toEqual([
    'default-productive',
    'default-essentials',
  ]);
  expect(visibleFocusChoices(choices, false, true, [])).toEqual(choices);
});
test('collapsing the catalog keeps selected targets visible for review', () => {
  expect(
    visibleFocusChoices(starters, false, false, [
      'saveddefault-essentials',
    ]).map(c => c.id),
  ).toContain('default-essentials');
});

test('an existing Games group can supply the starter when native seeding respected its name', () => {
  const games = {
    kind: 'saved' as const,
    id: 'mine',
    name: 'Games',
    packages: ['example.game'],
  };
  expect(visibleFocusChoices([games], false, false, [])).toEqual([games]);
  expect(visibleFocusChoices([games], true, false, [])).toEqual([]);
});
