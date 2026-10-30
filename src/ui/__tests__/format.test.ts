import {
  compactCount,
  duration,
  exactCount,
  fullDuration,
  sumCounts,
  countedEncounters,
} from '../format';

test.each([
  [59_000, '59s'],
  [60_000, '1m'],
  [3_599_000, '59m'],
  [3_600_000, '1h 0m'],
  [86_340_000, '23h 59m'],
  [86_400_000, '24h 0m'],
  [2_678_400_000, '31d 0h'],
  [null, '—'],
] as const)(
  'formats time boundaries without losing units',
  (value, expected) => {
    expect(duration(value)).toBe(expected);
  },
);
test('keeps long counts exact during aggregation and abbreviates only the display', () => {
  expect(sumCounts(['9223372036854775807', '1'])).toBe('9223372036854775808');
  expect(exactCount('9223372036854775807')).toBe('9,223,372,036,854,775,807');
  expect(compactCount('9223372036854775807')).toBe('9.2E');
  expect(compactCount('999999')).toBe('999,999');
  expect(compactCount('1000000')).toBe('1M');
  expect(compactCount('10000', 10_000n)).toBe('10K');
  expect(compactCount('999999', 10_000n)).toBe('999.9K');
});
test('expands displayed time into the complete measured value', () => {
  expect(fullDuration(86_459_000)).toBe('24 hours 0 minutes 59 seconds');
  expect(fullDuration(0)).toBe('0 hours 0 minutes 0 seconds');
  expect(fullDuration(null)).toBe(
    'No measured time is available for this period.',
  );
});
test('excludes only recognized ads and preserves exact large totals', () => {
  expect(countedEncounters('10', '3', false)).toBe('7');
  expect(countedEncounters('10', '3', true)).toBe('10');
  expect(countedEncounters('9223372036854775807', '1', false)).toBe(
    '9223372036854775806',
  );
  expect(countedEncounters('0', '1', false)).toBe('0');
});
