export type ClockDraft = { hour: string; minute: string; period: 'am' | 'pm' };
export function clockDraft(minute: number): ClockDraft {
  const hour = Math.floor(minute / 60);
  return {
    hour: String(hour % 12 || 12).padStart(2, '0'),
    minute: String(minute % 60).padStart(2, '0'),
    period: hour < 12 ? 'am' : 'pm',
  };
}
export function toMinute(clock: ClockDraft): number | null {
  if (!/^\d{1,2}$/.test(clock.hour) || !/^\d{1,2}$/.test(clock.minute))
    return null;
  const hour = Number(clock.hour),
    minute = Number(clock.minute);
  if (hour < 1 || hour > 12 || minute < 0 || minute > 59) return null;
  return ((hour % 12) + (clock.period === 'pm' ? 12 : 0)) * 60 + minute;
}
