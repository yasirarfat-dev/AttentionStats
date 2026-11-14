import { dayPageEnd, fixedRange, shiftDate } from './dateRange';
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

test('calendar jumps use a bounded page around the day across years and leap days', () => {
  expect(dayPageEnd('2020-02-29', '2026-11-14')).toBe('2020-03-14');
  expect(dayPageEnd('2026-11-10', '2026-11-14')).toBe('2026-11-14');
  expect(dayPageEnd('2026-12-31', '2027-03-01')).toBe('2027-01-14');
  for (const day of ['2020-02-29', '2021-12-31', '2026-11-14']) {
    const end = dayPageEnd(day, '2026-11-14');
    expect(end >= day).toBe(true);
    expect(shiftDate(end, -29) <= day).toBe(true);
  }
});
