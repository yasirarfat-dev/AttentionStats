import { clockDraft, toMinute } from './scheduleTime';

test.each([0, 300, 660, 720, 1080, 1380, 1439])(
  '12-hour editor round-trips %i minutes',
  minute => {
    expect(toMinute(clockDraft(minute))).toBe(minute);
  },
);
test.each([
  { hour: '0', minute: '00', period: 'am' as const },
  { hour: '13', minute: '00', period: 'pm' as const },
  { hour: '05', minute: '60', period: 'am' as const },
  { hour: '', minute: '00', period: 'am' as const },
  { hour: '5a', minute: '00', period: 'am' as const },
  { hour: '05', minute: '-1', period: 'am' as const },
])('invalid time drafts remain unsaveable: %j', clock =>
  expect(toMinute(clock)).toBeNull(),
);
