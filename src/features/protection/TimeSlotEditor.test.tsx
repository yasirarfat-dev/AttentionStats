import { useState } from 'react';
import type {
  AppGroup,
  ProtectableApp,
  TimeSlot,
} from '../../platform/protectionStatus';
import { TimeSlotEditor } from './TimeSlotEditor';

jest.mock('react', () => ({
  ...jest.requireActual('react'),
  useState: jest.fn(),
}));
jest.mock('react-native-unistyles', () => ({
  useUnistyles: () => ({ theme: {} }),
  StyleSheet: { create: () => ({}) },
}));
jest.mock('../../ui/Kit', () => ({
  Body: 'Body',
  Button: 'Button',
  Card: 'Card',
  SectionTitle: 'SectionTitle',
  Segmented: 'Segmented',
  Title: 'Title',
}));
jest.mock('../../ui/Screen', () => ({ Screen: 'Screen' }));

const social: AppGroup = {
  id: 'social',
  name: 'Social',
  packages: ['com.example.social'],
};
const other: AppGroup = {
  id: 'other',
  name: 'Other',
  packages: ['com.example.other'],
};
const apps: ProtectableApp[] = [
  { packageName: social.packages[0]!, name: 'Social', shortVideos: true },
  { packageName: other.packages[0]!, name: 'Other', shortVideos: false },
];

// Exercise editor choices without a native renderer. State persists between
// renders, including the same draft receiving a refreshed installed-app list.
function editor(initial?: TimeSlot, initialGroup?: string) {
  const states: unknown[] = [];
  let cursor = 0;
  (useState as jest.Mock).mockImplementation((value: unknown) => {
    const index = cursor++;
    if (!(index in states)) states[index] = value;
    return [states[index], (next: unknown) => (states[index] = next)];
  });
  const onSave = jest.fn();
  const onBack = jest.fn();
  function render(installed = apps) {
    cursor = 0;
    const tree = TimeSlotEditor({
      groups: [social, other],
      apps: installed,
      initial,
      initialGroup,
      busy: false,
      error: null,
      onSave,
      onBack,
    });
    const props: Record<string, any>[] = [];
    function visit(node: any) {
      if (Array.isArray(node)) node.forEach(visit);
      else if (node?.props) {
        props.push(node.props);
        visit(node.props.children);
      }
    }
    visit(tree);
    const control = (label: string) => {
      const found = props.find(item => item.label === label);
      if (!found) throw new Error(`Missing editor control: ${label}`);
      return found;
    };
    return { props, control };
  }
  return { render, onSave, onBack };
}

test('catalog loss preserves a short-video draft and prevents whole-app fallback', () => {
  const draft = editor();
  draft.render().control('Blocking scope').onChange('short_videos');
  const refreshed = draft.render([]);
  expect(refreshed.control('Blocking scope').value).toBe('short_videos');
  expect(refreshed.control('Save time slot · off').disabled).toBe(true);
  expect(draft.onSave).not.toHaveBeenCalled();
});

test('changing to an unsupported group requires an explicit whole-app choice', () => {
  const draft = editor();
  draft.render().control('Blocking scope').onChange('short_videos');
  const groupChoices = draft
    .render()
    .props.filter(item => item.accessibilityRole === 'radio');
  groupChoices[1]!.onPress();
  const unsupported = draft.render();
  expect(unsupported.control('Blocking scope').value).toBe('short_videos');
  expect(unsupported.control('Save time slot · off').disabled).toBe(true);
  unsupported.control('Blocking scope').onChange('app');
  const explicit = draft.render();
  expect(explicit.control('Save time slot · off').disabled).toBe(false);
  explicit.control('Save time slot · off').onPress();
  expect(draft.onSave).toHaveBeenCalledWith(
    expect.objectContaining({
      groupId: other.id,
      scope: 'app',
      enabled: false,
    }),
  );
});

test('returning to a supported group retains the chosen short-video scope', () => {
  const draft = editor();
  draft.render().control('Blocking scope').onChange('short_videos');
  draft
    .render()
    .props.filter(item => item.accessibilityRole === 'radio')[1]!
    .onPress();
  expect(draft.render().control('Save time slot · off').disabled).toBe(true);
  draft
    .render()
    .props.filter(item => item.accessibilityRole === 'radio')[0]!
    .onPress();
  const supported = draft.render();
  expect(supported.control('Blocking scope').value).toBe('short_videos');
  expect(supported.control('Save time slot · off').disabled).toBe(false);
  supported.control('Save time slot · off').onPress();
  expect(draft.onSave).toHaveBeenCalledWith(
    expect.objectContaining({
      groupId: social.id,
      scope: 'short_videos',
      enabled: false,
    }),
  );
});

test('a per-app shortcut saves the requested group instead of the first group', () => {
  const draft = editor(undefined, other.id);
  const shortcut = draft.render();
  expect(shortcut.control('Blocking scope').options).toEqual([
    { value: 'app', label: 'Whole app' },
  ]);
  expect(shortcut.control('Save time slot · off').disabled).toBe(false);
  shortcut.control('Save time slot · off').onPress();
  expect(draft.onSave).toHaveBeenCalledWith(
    expect.objectContaining({
      groupId: other.id,
      scope: 'app',
      enabled: false,
    }),
  );
});

test('an existing selective slot stays selective when its app is unavailable', () => {
  const draft = editor({
    id: 'slot',
    groupId: social.id,
    scope: 'short_videos',
    allDay: false,
    days: 127,
    startMinute: 300,
    endMinute: 660,
    enabled: true,
  });
  const refreshed = draft.render([]);
  expect(refreshed.control('Save time slot · off').disabled).toBe(false);
  refreshed.control('Save time slot · off').onPress();
  expect(draft.onSave).toHaveBeenCalledWith(
    expect.objectContaining({
      id: 'slot',
      scope: 'short_videos',
      enabled: false,
    }),
  );
});

test('leaving an edited draft does not save a schedule', () => {
  const draft = editor();
  draft.render().control('Blocking scope').onChange('short_videos');
  draft.render().control('Back to schedules').onPress();
  expect(draft.onBack).toHaveBeenCalledTimes(1);
  expect(draft.onSave).not.toHaveBeenCalled();
});

test('equal custom times cannot save, while an explicit all-day choice uses canonical times', () => {
  const draft = editor({
    id: 'slot',
    groupId: social.id,
    scope: 'app',
    allDay: false,
    days: 1,
    startMinute: 300,
    endMinute: 300,
    enabled: false,
  });
  expect(draft.render().control('Save time slot · off').disabled).toBe(true);
  draft.render().control('Slot timing').onChange('always');
  const allDay = draft.render();
  expect(allDay.control('Save time slot · off').disabled).toBe(false);
  allDay.control('Save time slot · off').onPress();
  expect(draft.onSave).toHaveBeenCalledWith(
    expect.objectContaining({
      days: 1,
      startMinute: 0,
      endMinute: 0,
      allDay: true,
      enabled: false,
    }),
  );
});

test('editing an overnight slot preserves start-day boundaries and requires reactivation', () => {
  const draft = editor({
    id: 'overnight',
    groupId: social.id,
    scope: 'app',
    allDay: false,
    days: 64,
    startMinute: 1380,
    endMinute: 60,
    enabled: true,
  });
  const edited = draft.render();
  expect(edited.control('Save time slot · off').disabled).toBe(false);
  edited.control('Save time slot · off').onPress();
  expect(draft.onSave).toHaveBeenCalledWith(
    expect.objectContaining({
      id: 'overnight',
      days: 64,
      startMinute: 1380,
      endMinute: 60,
      enabled: false,
    }),
  );
});
