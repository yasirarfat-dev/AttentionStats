import type { DiaryApp } from '../../platform/diaryStatus';
import { appHistories, filterAppHistories } from './appHistory';
import {
  historySummary,
  orderedDays,
  resolveDay,
  type HistoryDay,
} from './historySummary';

const app = (overrides: Partial<DiaryApp> = {}): DiaryApp => ({
  packageName: 'com.example.player',
  name: 'Example',
  social: 'instagram',
  appMs: null,
  appOpens: null,
  reelsMs: null,
  reelsCount: '0',
  videoCount: '0',
  adCount: '0',
  photoCount: '0',
  revisitCount: '0',
  measuredVideoCount: null,
  playbackMs: null,
  watchCount: null,
  completionCount: null,
  ...overrides,
});
const day = (key: string, apps: DiaryApp[] = []): HistoryDay => ({
  day: key,
  apps,
  hours: Array(24).fill('0'),
  interventions: {},
});

test('counts source facts once but retains each package’s app time', () => {
  const result = historySummary([
    day('2026-11-12', [
      app({ videoCount: '8', reelsMs: '60000', appMs: '90000' }),
      app({
        packageName: 'com.example.variant',
        videoCount: '8',
        reelsMs: '60000',
        appMs: '30000',
      }),
    ]),
  ]);
  expect(result.videos).toBe('8');
  expect(result.reelsMs).toBe('60000');
  expect(result.appMs).toBe('120000');
});

test('preserves exact counts above JavaScript integer precision across days', () => {
  const result = historySummary([
    day('2026-11-11', [app({ videoCount: '9007199254740993' })]),
    day('2026-11-12', [app({ videoCount: '2' })]),
  ]);
  expect(result.videos).toBe('9007199254740995');
});

test('distinguishes missing duration, covered zero and partial known duration', () => {
  expect(historySummary([day('2026-11-12', [app()])]).appMs).toBeNull();
  expect(
    historySummary([day('2026-11-12', [app({ appMs: '0', reelsMs: '0' })])]),
  ).toMatchObject({ appMs: '0', reelsMs: '0' });
  expect(
    historySummary([
      day('2026-11-12', [
        app(),
        app({ packageName: 'com.example.other', appMs: '1234' }),
      ]),
    ]).appMs,
  ).toBe('1234');
});

test('falls back to legacy encounter counts only when video counts are absent', () => {
  expect(
    historySummary([
      day('2026-11-12', [app({ videoCount: null, reelsCount: '7' })]),
    ]).videos,
  ).toBe('7');
  expect(
    historySummary([
      day('2026-11-12', [app({ videoCount: '0', reelsCount: '7' })]),
    ]).videos,
  ).toBe('0');
});

test('sorts calendar keys without mutating the snapshot and resolves stale dates safely', () => {
  const days = [day('2026-11-12'), day('2026-11-10'), day('2026-11-11')];
  const ordered = orderedDays(days);
  expect(days[0]?.day).toBe('2026-11-12');
  expect(ordered.map(item => item.day)).toEqual([
    '2026-11-10',
    '2026-11-11',
    '2026-11-12',
  ]);
  expect(resolveDay(ordered, '2026-11-11')?.day).toBe('2026-11-11');
  expect(resolveDay(ordered, '2026-10-01')?.day).toBe('2026-11-12');
  expect(resolveDay([], null)).toBeUndefined();
});

test('app history retains missing days and independently available time and opens', () => {
  const [result] = appHistories([
    day('2026-11-10', [app({ appMs: '0', appOpens: null })]),
    day('2026-11-11'),
    day('2026-11-12', [app({ appMs: null, appOpens: '3' })]),
  ]);
  expect(result).toMatchObject({
    appMs: '0',
    appOpens: '3',
    timeDays: 1,
    opensDays: 1,
  });
  expect(result?.daily[1]).toEqual({
    day: '2026-11-11',
    appMs: null,
    appOpens: null,
  });
  expect(appHistories([day('2026-11-12', [app()])])[0]).toMatchObject({
    appMs: null,
    appOpens: null,
  });
});

test('package histories never assign repeated source media facts to package usage', () => {
  const result = appHistories([
    day('2026-11-11', [
      app({ appMs: '9007199254740993', appOpens: '2', videoCount: '8' }),
    ]),
    day('2026-11-12', [
      app({ name: 'Renamed', appMs: '2', appOpens: '3', videoCount: '8' }),
      app({ packageName: 'com.example.variant', appMs: '9', videoCount: '8' }),
    ]),
  ]);
  expect(result[0]).toMatchObject({
    name: 'Renamed',
    appMs: '9007199254740995',
    appOpens: '5',
  });
  expect(result[1]).toMatchObject({ appMs: '9', appOpens: null, timeDays: 1 });
  expect(result[0]).not.toHaveProperty('videoCount');
});

test('app search matches names and packages; exact sort keeps unknown below zero', () => {
  const apps = appHistories([
    day('2026-11-12', [
      app({ packageName: 'com.example.unknown', name: 'Alpha' }),
      app({
        packageName: 'com.example.zero',
        name: 'Zero',
        appMs: '0',
        appOpens: '9',
      }),
      app({
        packageName: 'com.example.big',
        name: 'Big',
        appMs: '9007199254740993',
        appOpens: '0',
      }),
      app({
        packageName: 'com.example.small',
        name: 'Small',
        appMs: '9007199254740992',
        appOpens: '2',
      }),
    ]),
  ]);
  expect(filterAppHistories(apps, '', 'time').map(row => row.name)).toEqual([
    'Big',
    'Small',
    'Zero',
    'Alpha',
  ]);
  expect(filterAppHistories(apps, '', 'opens').map(row => row.name)).toEqual([
    'Zero',
    'Small',
    'Big',
    'Alpha',
  ]);
  expect(filterAppHistories(apps, '', 'name').map(row => row.name)).toEqual([
    'Alpha',
    'Big',
    'Small',
    'Zero',
  ]);
  expect(filterAppHistories(apps, '  BIG ', 'time')).toHaveLength(1);
  expect(filterAppHistories(apps, 'example.zero', 'time')[0]?.name).toBe(
    'Zero',
  );
  expect(filterAppHistories(apps, 'no-match', 'time')).toEqual([]);
  expect(apps[0]?.name).toBe('Alpha');
});
