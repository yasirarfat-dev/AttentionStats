import { StyleSheet } from 'react-native-unistyles';
import type { TimeSlot } from '../../platform/protectionStatus';

export const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
export function timeLabel(minute: number) {
  const hour = Math.floor(minute / 60);
  return `${hour % 12 || 12}:${String(minute % 60).padStart(2, '0')} ${hour < 12 ? 'AM' : 'PM'}`;
}
export const daysLabel = (days: number) =>
  days === 127
    ? 'Every day'
    : days === 31
      ? 'Weekdays'
      : weekdays.filter((_, i) => days & (1 << i)).join(', ');
export const slotLabel = (slot: TimeSlot) =>
  slot.allDay
    ? 'All day'
    : `${timeLabel(slot.startMinute)} – ${timeLabel(slot.endMinute)}${slot.startMinute > slot.endMinute ? ' · next day' : ''}`;
export const scopeLabel = (slot: TimeSlot) =>
  slot.allowOnly
    ? 'Allow Only · Other Apps Blocked'
    : slot.scope === 'app'
      ? 'Whole App'
      : 'Short Videos Only';

export const scheduleStyles = StyleSheet.create(theme => ({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flexWrap: 'wrap',
  },
  grow: { flex: 1, minWidth: 100 },
  input: {
    backgroundColor: theme.surfaceAlt,
    color: theme.text,
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 14,
    minHeight: 52,
    fontSize: 16,
  },
  heroHeading: {
    color: theme.onHero,
    fontSize: 26,
    fontWeight: '800',
    lineHeight: 32,
  },
  heroBody: { color: theme.heroMuted, fontSize: 15, lineHeight: 22 },
  heading: { color: theme.text, fontSize: 20, fontWeight: '700' },
  time: {
    color: theme.text,
    fontSize: 28,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  timeField: {
    flex: 1,
    minWidth: 64,
    backgroundColor: theme.surfaceAlt,
    color: theme.text,
    fontSize: 30,
    fontWeight: '700',
    textAlign: 'center',
    borderRadius: 16,
    minHeight: 64,
    padding: 12,
  },
  day: {
    minWidth: 44,
    minHeight: 48,
    paddingHorizontal: 8,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: theme.border,
    backgroundColor: theme.surfaceAlt,
  },
  dayOn: { backgroundColor: theme.accentSoft, borderColor: theme.accent },
  dayText: { color: theme.muted, fontSize: 13, fontWeight: '600' },
  dayTextOn: { color: theme.text },
  empty: { paddingVertical: 12, gap: 14 },
}));
