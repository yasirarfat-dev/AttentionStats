import React, { useRef, useState } from 'react';
import { FocusQuickActions } from './FocusQuickActions';
jest.mock('../diary', () => ({ AppIdentityList: 'AppIdentityList' }));
jest.mock('react', () => ({
  ...jest.requireActual('react'),
  useState: jest.fn(),
  useRef: jest.fn(),
  useEffect: jest.fn(),
}));
jest.mock('../../ui/Kit', () => ({
  Body: 'Body',
  Button: 'Button',
  CheckRow: 'CheckRow',
  SectionTitle: 'SectionTitle',
  Segmented: 'Segmented',
  Stepper: 'Stepper',
}));
jest.mock('./FocusClock', () => ({ FocusClock: 'FocusClock' }));
const mockFocus = jest.fn().mockResolvedValue(true);
jest.mock('./useSchedules', () => ({
  useSchedules: () => ({
    snapshot: {
      revision: '1',
      accessibility: true,
      focus: null,
      groups: [
        {
          id: 'default-essentials',
          name: 'Essentials',
          packages: ['example.utility'],
        },
      ],
      windows: [],
    },
    apps: [
      { packageName: 'example.utility', name: 'Utility', shortVideos: false },
    ],
    busy: false,
    error: null,
    refresh: jest.fn(),
    focus: mockFocus,
  }),
}));
type Element = React.ReactElement<Record<string, any>>;
function nodes(value: unknown): Element[] {
  if (Array.isArray(value)) return value.flatMap(nodes);
  if (!React.isValidElement(value)) return [];
  const node = value as Element;
  return [node, ...nodes(node.props.children)];
}
function chooser() {
  const states: unknown[] = [],
    refs: { current: unknown }[] = [];
  let stateIndex = 0,
    refIndex = 0;
  (useState as jest.Mock).mockImplementation((initial: unknown) => {
    const index = stateIndex++;
    if (!(index in states)) states[index] = initial;
    return [
      states[index],
      (next: unknown) => {
        states[index] = typeof next === 'function' ? next(states[index]) : next;
      },
    ];
  });
  (useRef as jest.Mock).mockImplementation((initial: unknown) => {
    const index = refIndex++;
    if (!refs[index]) refs[index] = { current: initial };
    return refs[index];
  });
  return () => {
    stateIndex = 0;
    refIndex = 0;
    return nodes(FocusQuickActions({ onSelect: jest.fn() }));
  };
}
beforeEach(() => mockFocus.mockClear());
test('an explicit Block Apps choice survives selecting an allow-oriented group and requires Start', () => {
  const render = chooser();
  let tree = render();
  tree.find(n => n.type === 'Segmented')!.props.onChange('block');
  tree = render();
  tree
    .find(n => n.type === 'Button' && n.props.label === 'All groups')!
    .props.onPress();
  tree = render();
  tree.find(n => n.type === 'CheckRow')!.props.onChange(true);
  tree = render();
  expect(tree.find(n => n.type === 'Segmented')!.props.value).toBe('block');
  expect(mockFocus).not.toHaveBeenCalled();
  tree.find(n => n.type === 'Segmented')!.props.onChange('block');
  tree = render();
  expect(tree.find(n => n.type === 'CheckRow')!.props.value).toBe(true);
  tree
    .find(
      n => n.type === 'Button' && n.props.label === 'Start 25-minute focus',
    )!
    .props.onPress();
  expect(mockFocus).toHaveBeenCalledWith(['example.utility'], 25, '1', false);
});
test('an untouched chooser suggests Allow Only for Essentials and still permits changing it', () => {
  const render = chooser();
  let tree = render();
  tree
    .find(n => n.type === 'Button' && n.props.label === 'All groups')!
    .props.onPress();
  tree = render();
  tree.find(n => n.type === 'CheckRow')!.props.onChange(true);
  tree = render();
  expect(tree.find(n => n.type === 'Segmented')!.props.value).toBe('allow');
  tree.find(n => n.type === 'Segmented')!.props.onChange('block');
  tree = render();
  expect(tree.find(n => n.type === 'Segmented')!.props.value).toBe('block');
  expect(
    tree.find(
      n => n.type === 'Button' && n.props.label === 'Start 25-minute focus',
    )!.props.disabled,
  ).toBe(true);
  expect(mockFocus).not.toHaveBeenCalled();
});
