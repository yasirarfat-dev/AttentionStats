import { parseDiaryHistoryPage, parseDiarySnapshot } from '../diaryStatus';
import { HistoryReadError } from '../historyFailure';

const value = {
  contractVersion: 2,
  revision: '0',
  usageEnabled: false,
  usageGranted: false,
  batteryOptimized: true,
  backgroundRestricted: null,
  usageStatus: 'disabled',
  effectiveAtMs: '0',
  queryThroughMs: '0',
  reminderEnabled: false,
  reminderCount: 15,
  reminderMinutes: 15,
  onboardingSeen: false,
  includeRecognizedAds: true,
  breakSeconds: 10,
  blockers: ['instagram', 'facebook', 'youtube', 'tiktok', 'snapchat'].map(
    app => ({
      app,
      enabled: false,
      dailyLimit: 0,
      available: false,
    }),
  ),
  zone: 'UTC',
  days: [
    {
      day: '2026-10-29',
      apps: [
        {
          packageName: 'synthetic.example',
          name: 'Example',
          social: null,
          appMs: null,
          appOpens: null,
          reelsMs: null,
          reelsCount: '0',
          adCount: '0',
          photoCount: '0',
          revisitCount: '0',
        },
      ],
      hours: Array.from({ length: 24 }, () => '0'),
      interventions: {},
    },
  ],
};

const lifetime = () => ({
  contractVersion: 1,
  firstDay: '2026-10-29',
  lastRecordedDay: '2026-10-29',
  throughDay: '2026-10-29',
  zone: 'UTC',
  dayCount: 1,
  sources: ['instagram', 'facebook', 'youtube', 'tiktok', 'snapchat'].map(
    key => ({ key, videos: '9007199254740993', reelsMs: null }),
  ),
  pageStart: '2026-10-29',
  pageEnd: '2026-10-29',
  olderEnd: null,
  newerEnd: null,
  snapshot: value,
});

test('history errors identify the bounded failure stage without payload or parser issues', () => {
  for (const [payload, code] of [
    [null, 'payload'],
    [' '.repeat(2_000_001), 'payload'],
    ['{"synthetic-private-history":', 'json'],
    [JSON.stringify({ private: 'synthetic-private-history' }), 'validation'],
    [JSON.stringify({ ...lifetime(), olderEnd: '2026-10-01' }), 'validation'],
  ] as const) {
    let failure: unknown;
    try {
      parseDiaryHistoryPage(payload);
    } catch (error) {
      failure = error;
    }
    expect(failure).toBeInstanceOf(HistoryReadError);
    expect(failure).toMatchObject({
      code,
      message: 'History page unavailable',
    });
    expect(JSON.stringify(failure)).not.toMatch(/private|issues|cause/);
    expect(Object.keys(failure as HistoryReadError).sort()).toEqual([
      'code',
      'name',
    ]);
  }
});

test('zero-second continuation is a valid saved choice; negative delays are rejected', () => {
  expect(
    parseDiarySnapshot(JSON.stringify({ ...value, breakSeconds: 0 }))
      .breakSeconds,
  ).toBe(0);
  expect(() =>
    parseDiarySnapshot(JSON.stringify({ ...value, breakSeconds: -1 })),
  ).toThrow();
});
test('lifetime contract preserves exact counts, nullable time and empty-history scope', () => {
  expect(
    parseDiaryHistoryPage(JSON.stringify(lifetime())).sources[0]?.videos,
  ).toBe('9007199254740993');
  const empty = {
    ...lifetime(),
    firstDay: null,
    lastRecordedDay: null,
    dayCount: 0,
  };
  expect(parseDiaryHistoryPage(JSON.stringify(empty)).firstDay).toBeNull();
});
test('lifetime contract validates leap-day contiguous pages and true calendar span', () => {
  const days = ['2024-02-28', '2024-02-29', '2024-03-01'].map(day => ({
    ...value.days[0],
    day,
  }));
  const payload = {
    ...lifetime(),
    firstDay: '2024-02-28',
    lastRecordedDay: '2024-03-01',
    throughDay: '2024-03-01',
    dayCount: 3,
    pageStart: '2024-02-28',
    pageEnd: '2024-03-01',
    snapshot: { ...value, days },
  };
  expect(
    parseDiaryHistoryPage(JSON.stringify(payload)).snapshot.days,
  ).toHaveLength(3);
  expect(() =>
    parseDiaryHistoryPage(
      JSON.stringify({
        ...payload,
        snapshot: { ...value, days: [days[0], days[2]] },
      }),
    ),
  ).toThrow('History page unavailable');
});
test('lifetime contract rejects contradictory bounds, paging, source identities and dates', () => {
  const payload = lifetime();
  for (const change of [
    { dayCount: 30 },
    { throughDay: '2026-02-30' },
    { pageEnd: '2026-10-30' },
    { olderEnd: '2026-10-28' },
    { newerEnd: '2026-10-30' },
    { lastRecordedDay: null },
    { sources: payload.sources.map(() => payload.sources[0]) },
    { snapshot: { ...value, zone: 'Asia/Dhaka' } },
  ])
    expect(() =>
      parseDiaryHistoryPage(JSON.stringify({ ...payload, ...change })),
    ).toThrow('History page unavailable');
});
test('preserves unavailable timing separately from recorded zero', () => {
  expect(
    parseDiarySnapshot(JSON.stringify(value)).days[0]?.apps[0]?.appMs,
  ).toBeNull();
  const payload = JSON.parse(JSON.stringify(value));
  payload.days[0].apps[0].appMs = '0';
  expect(
    parseDiarySnapshot(JSON.stringify(payload)).days[0]?.apps[0]?.appMs,
  ).toBe('0');
});
test.each([null, '0', '1', '9223372036854775807'])(
  'preserves observed app opens including unknown and exact counts: %s',
  appOpens => {
    const payload = JSON.parse(JSON.stringify(value));
    payload.days[0].apps[0].appOpens = appOpens;
    expect(
      parseDiarySnapshot(JSON.stringify(payload)).days[0]?.apps[0]?.appOpens,
    ).toBe(appOpens);
  },
);
test.each(['-1', '9223372036854775808', 1, undefined])(
  'rejects invalid or missing app-open counts: %s',
  appOpens => {
    const payload = JSON.parse(JSON.stringify(value));
    payload.days[0].apps[0].appOpens = appOpens;
    expect(() => parseDiarySnapshot(JSON.stringify(payload))).toThrow(
      'Diary unavailable',
    );
  },
);
test.each([
  { ...value, contractVersion: 1 },
  { ...value, revision: '-1' },
  { ...value, reminderCount: 0 },
  { ...value, reminderMinutes: 241 },
  { ...value, reminderEnabled: 'false' },
  { ...value, batteryOptimized: 'true' },
  { ...value, backgroundRestricted: 'unknown' },
  { ...value, days: [...value.days, ...value.days] },
  { ...value, extra: 'content' },
])('rejects malformed or ambiguous diary data', malformed => {
  expect(() => parseDiarySnapshot(JSON.stringify(malformed))).toThrow(
    'Diary unavailable',
  );
});

test('ranged history validates bounded pages independently of retained-history bounds', () => {
  const payload = {
    ...lifetime(),
    contractVersion: 2,
    windowDays: 0,
    rangeStart: '2026-10-01',
    rangeEnd: '2026-10-29',
    earliestDay: '2026-09-29',
    installDay: '2026-09-29',
    pageSize: 7,
    pageStart: '2026-10-23',
    olderEnd: '2026-10-22',
    snapshot: {
      ...value,
      days: Array.from({ length: 7 }, (_, i) => ({
        ...value.days[0],
        day: `2026-10-${23 + i}`,
      })),
    },
  };
  expect(
    parseDiaryHistoryPage(JSON.stringify(payload)).snapshot.days,
  ).toHaveLength(7);
  for (const changes of [
    { pageSize: 31 },
    { rangeStart: '2026-10-30' },
    { rangeEnd: '2026-10-30' },
    { olderEnd: null },
    { pageStart: '2026-10-22' },
    { earliestDay: '2026-10-15' },
  ]) {
    expect(() =>
      parseDiaryHistoryPage(JSON.stringify({ ...payload, ...changes })),
    ).toThrow('History page unavailable');
  }
});

test('fixed periods can end after today while detail pages stop at observed dates', () => {
  const payload = {
    ...lifetime(),
    contractVersion: 2,
    windowDays: 7,
    rangeStart: '2026-10-29',
    rangeEnd: '2026-11-04',
    earliestDay: '2026-10-29',
    installDay: '2026-10-29',
    pageSize: 7,
  };
  expect(
    parseDiaryHistoryPage(JSON.stringify(payload)).snapshot.days,
  ).toHaveLength(1);
  expect(() =>
    parseDiaryHistoryPage(
      JSON.stringify({ ...payload, rangeEnd: '2026-11-03' }),
    ),
  ).toThrow('History page unavailable');
  expect(() =>
    parseDiaryHistoryPage(
      JSON.stringify({ ...payload, installDay: '2026-10-30' }),
    ),
  ).toThrow('History page unavailable');
});
