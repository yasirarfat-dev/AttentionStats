import { parseProtection } from '../protectionStatus';

const slot = {
  id: 'morning',
  groupId: 'social',
  days: 127,
  startMinute: 300,
  endMinute: 660,
  enabled: false,
  scope: 'app',
  allDay: false,
};
const value = {
  revision: '3',
  accessibility: true,
  groups: [{ id: 'social', name: 'Social', packages: ['com.example.social'] }],
  windows: [slot],
};
const parse = (next: unknown) => parseProtection(JSON.stringify(next));

test('multiple independent periods retain scope, boundaries and saved activation', () => {
  const snapshot = parse({
    ...value,
    windows: [
      slot,
      {
        ...slot,
        id: 'evening',
        startMinute: 1080,
        endMinute: 1380,
        scope: 'short_videos',
        enabled: true,
      },
    ],
  });
  expect(
    snapshot.windows.map(item => [
      item.startMinute,
      item.endMinute,
      item.scope,
      item.enabled,
    ]),
  ).toEqual([
    [300, 660, 'app', false],
    [1080, 1380, 'short_videos', true],
  ]);
});
test.each([
  { ...slot, groupId: 'missing' },
  { ...slot, days: 0 },
  { ...slot, days: 128 },
  { ...slot, startMinute: 300.5 },
  { ...slot, endMinute: 1440 },
  { ...slot, endMinute: 300 },
  { ...slot, allDay: true },
  { ...slot, scope: 'unknown' },
  { ...slot, enabled: 'true' },
])('invalid rule cannot be presented as a saved slot: %j', invalid => {
  expect(() => parse({ ...value, windows: [invalid] })).toThrow();
});
test('all-day canonical midnight differs from an invalid equal-time custom period', () => {
  expect(
    parse({
      ...value,
      windows: [{ ...slot, startMinute: 0, endMinute: 0, allDay: true }],
    }).windows[0]?.allDay,
  ).toBe(true);
  expect(() =>
    parse({ ...value, windows: [{ ...slot, startMinute: 0, endMinute: 0 }] }),
  ).toThrow();
});
test('duplicate rules, groups and members are rejected', () => {
  expect(() => parse({ ...value, windows: [slot, slot] })).toThrow();
  expect(() =>
    parse({ ...value, groups: [...value.groups, ...value.groups] }),
  ).toThrow();
  expect(() =>
    parse({
      ...value,
      groups: [
        {
          ...value.groups[0],
          packages: ['com.example.social', 'com.example.social'],
        },
      ],
    }),
  ).toThrow();
});
