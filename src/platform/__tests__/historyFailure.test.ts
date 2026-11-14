import {
  HistoryReadError,
  historyFailureCode,
  nativeHistoryFailure,
} from '../historyFailure';

test.each([
  ['BUSY', 'native_busy'],
  ['INTERRUPTED', 'native_interrupted'],
  ['STORAGE_UNAVAILABLE', 'native_storage_unavailable'],
  ['INVALID_ARGUMENT', 'native_invalid_argument'],
  ['private-unrecognized-code', 'native_unknown'],
])('only admitted native codes survive normalization: %s', (code, expected) => {
  const result = nativeHistoryFailure({
    code,
    message: 'synthetic-private-history',
    cause: { payload: 'synthetic-private-history' },
  });
  expect(historyFailureCode(result)).toBe(expected);
  expect(result.message).toBe('History page unavailable');
  expect(JSON.stringify(result)).not.toMatch(/private|payload|cause/);
  expect(result.stack).not.toContain('synthetic-private-history');
  expect(Object.keys(result).sort()).toEqual(['code', 'name']);
});

test('unknown rejection shapes and throwing accessors cannot escape redaction', () => {
  for (const input of [
    null,
    undefined,
    'synthetic-private-history',
    new Error('synthetic-private-history'),
    {
      get code(): string {
        throw new Error('synthetic-private-history');
      },
    },
  ]) {
    expect(nativeHistoryFailure(input).code).toBe('native_unknown');
    expect(historyFailureCode(input)).toBe('unexpected');
  }
  expect(historyFailureCode(new HistoryReadError('invalidated'))).toBe(
    'invalidated',
  );
});
