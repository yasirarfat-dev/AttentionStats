import React from 'react';
import { AppSelection } from './AppSelection';
jest.mock('../diary', () => ({ useAppIcons: () => ({}) }));
jest.mock('react', () => ({
  ...jest.requireActual('react'),
  useState: (value: unknown) => [value, jest.fn()],
}));
jest.mock('react-native-unistyles', () => ({
  useUnistyles: () => ({ theme: {} }),
  StyleSheet: { create: () => ({}) },
}));
jest.mock('../../ui/Kit', () => ({
  Body: 'Body',
  Card: 'Card',
  Divider: 'Divider',
  SectionTitle: 'SectionTitle',
  CheckRow: 'CheckRow',
}));
type Element = React.ReactElement<Record<string, any>>;
function elements(value: unknown): Element[] {
  if (Array.isArray(value)) return value.flatMap(elements);
  if (!React.isValidElement(value)) return [];
  const node = value as Element;
  return [node, ...elements(node.props.children)];
}
const apps = [
  { packageName: 'example.zebra', name: 'Zebra', shortVideos: false },
  { packageName: 'example.alpha', name: 'Alpha', shortVideos: false },
  ...Array.from({ length: 45 }, (_, i) => ({
    packageName: `example.app${i}`,
    name: `App ${String(i).padStart(2, '0')}`,
    shortVideos: false,
  })),
];
const groups = [
  {
    id: 'group',
    name: 'My group',
    packages: ['example.alpha', 'example.zebra'],
  },
];
test('collapsed groups retain all individual apps in a stable alphabetical catalog', () => {
  for (const selected of [[], ['example.zebra']]) {
    const rows = elements(
      AppSelection({ groups, apps, selected, onChange: jest.fn() }),
    ).filter(node => node.type === 'CheckRow');
    expect(rows).toHaveLength(apps.length);
    expect(rows.map(node => node.props.title)).toEqual(
      apps.map(app => app.name).sort((a, b) => a.localeCompare(b)),
    );
  }
});
test('individual exclusion after bulk group selection updates the shared package set and group state', () => {
  let selected = ['example.alpha', 'example.zebra'];
  const onChange = (next: string[]) => {
    selected = next;
  };
  let nodes = elements(AppSelection({ groups, apps, selected, onChange }));
  nodes
    .find(node => node.type === 'CheckRow' && node.props.title === 'Alpha')!
    .props.onChange(false);
  expect(selected).toEqual(['example.zebra']);
  nodes = elements(AppSelection({ groups, apps, selected, onChange }));
  expect(
    nodes.find(
      node => node.props.accessibilityLabel === 'Select All in My group',
    )!.props.value,
  ).toBe(false);
  expect(
    nodes.find(
      node => node.type === 'CheckRow' && node.props.title === 'Zebra',
    )!.props.value,
  ).toBe(true);
});
