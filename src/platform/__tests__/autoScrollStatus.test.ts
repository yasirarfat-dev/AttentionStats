import { parseAutoScrollStatus } from '../autoScrollStatus';

const status = {
  contract: 2,
  sessionOnly: true,
  enabled: false,
  running: false,
  skipStandaloneAds: false,
  reason: 'off',
  sources: ['instagram', 'facebook', 'youtube', 'tiktok', 'snapchat'].map(
    app => ({
      app,
      candidate: false,
    }),
  ),
};
test('accepts a complete four-app session status', () => {
  expect(parseAutoScrollStatus(JSON.stringify(status)).enabled).toBe(false);
});
test.each([
  { ...status, contract: 1 },
  { ...status, skipStandaloneAds: true },
  { ...status, skipStandaloneAds: undefined },
  { ...status, running: true },
  { ...status, sessionOnly: false },
  { ...status, sources: status.sources.slice(1) },
  { ...status, sources: [status.sources[0], ...status.sources.slice(0, 4)] },
  {
    ...status,
    sources: [
      ...status.sources.slice(0, 4),
      { app: 'unknown', candidate: true },
    ],
  },
  {
    ...status,
    sources: status.sources.map(source => ({ ...source, extra: 'unexpected' })),
  },
  { ...status, unexpected: 'field' },
  { ...status, reason: 'unbounded arbitrary content' },
])('rejects inconsistent or expanded action status %#', value => {
  expect(() => parseAutoScrollStatus(JSON.stringify(value))).toThrow();
});
test('accepts a separately allowed ad choice only with convenience enabled', () => {
  expect(
    parseAutoScrollStatus(
      JSON.stringify({ ...status, enabled: true, skipStandaloneAds: true }),
    ).skipStandaloneAds,
  ).toBe(true);
});
test('bounds the response before JSON parsing', () => {
  expect(() => parseAutoScrollStatus(' '.repeat(4097))).toThrow();
});
