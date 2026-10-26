import { readBootstrapStatus } from '../bootstrapStatus';

test('a checked, source-disabled native response stays distinct from unavailability', async () => {
  await expect(
    readBootstrapStatus({
      getBootstrapStatus: async () => ({
        contractVersion: 1,
        sourceState: 'not_implemented',
      }),
    }),
  ).resolves.toEqual({ kind: 'disabled' });
});

test('an unsupported platform cannot masquerade as a successful check', async () => {
  await expect(readBootstrapStatus(null)).resolves.toEqual({
    kind: 'unavailable',
    reason: 'UNSUPPORTED',
  });
});

test.each([
  null,
  {},
  { contractVersion: 2, sourceState: 'not_implemented' },
  { contractVersion: 1, sourceState: 'collecting' },
  { contractVersion: '1', sourceState: 'not_implemented' },
  { contractVersion: 1, sourceState: 'not_implemented', history: [] },
])('rejects malformed or unsupported native data: %p', async value => {
  await expect(
    readBootstrapStatus({ getBootstrapStatus: async () => value }),
  ).resolves.toEqual({ kind: 'unavailable', reason: 'INVALID_RESPONSE' });
});

test('native failure returns only a stable reason without leaking native details', async () => {
  const getBootstrapStatus = async () => {
    throw new Error('synthetic private detail');
  };
  await expect(readBootstrapStatus({ getBootstrapStatus })).resolves.toEqual({
    kind: 'unavailable',
    reason: 'NATIVE_FAILURE',
  });
});
