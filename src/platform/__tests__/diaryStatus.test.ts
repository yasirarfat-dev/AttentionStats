import { parseDiarySnapshot } from '../diaryStatus';

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
