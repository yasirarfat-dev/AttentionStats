import { parseAutoScrollStatus } from '../autoScrollStatus';

const status = {
  contract: 1,
  sessionOnly: true,
  enabled: false,
  running: false,
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
test('bounds the response before JSON parsing', () => {
  expect(() => parseAutoScrollStatus(' '.repeat(4097))).toThrow();
});
