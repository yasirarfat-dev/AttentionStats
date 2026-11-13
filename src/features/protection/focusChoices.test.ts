import { focusChoices } from './focusChoices';

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
