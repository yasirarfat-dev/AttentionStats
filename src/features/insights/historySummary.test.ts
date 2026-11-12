import type { DiaryApp } from '../../platform/diaryStatus';
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
