import { fixedRange, shiftDate } from './dateRange';
test('fixed ranges include exactly seven or thirty civil dates across month/year/leap boundaries', () => {
  expect(fixedRange('2024-02-26', 7)).toEqual({
    start: '2024-02-26',
    end: '2024-03-03',
  });
  expect(fixedRange('2026-12-15', 30)).toEqual({
    start: '2026-12-15',
    end: '2027-01-13',
  });
  expect(shiftDate('2026-11-01', -6)).toBe('2026-10-26');
});
