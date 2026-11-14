/** Calendar-day arithmetic in UTC avoids DST duration errors. */
export function shiftDate(day: string, offset: number): string {
  const date = new Date(day + 'T12:00:00Z');
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}
export function localToday(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}
export function fixedRange(start: string, days: 7 | 30) {
  return { start, end: shiftDate(start, days - 1) };
}

/** Center a selected civil day in a bounded 30-day detail page, clipped by range end. */
export function dayPageEnd(day: string, through: string): string {
  const centeredEnd = shiftDate(day, 14);
  return centeredEnd > through ? through : centeredEnd;
}
