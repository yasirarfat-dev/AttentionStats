import type * as Zod from 'zod';
const { z }: typeof Zod = require('zod');
const socialKeys = [
  'instagram',
  'facebook',
  'youtube',
  'tiktok',
  'snapchat',
] as const;
const count = z
  .string()
  .regex(/^(0|[1-9][0-9]{0,18})$/)
  .refine(value => value.length < 19 || value <= '9223372036854775807');
const app = z.strictObject({
  packageName: z.string().max(256),
  name: z.string().max(100),
  social: z.enum(socialKeys).nullable(),
  appMs: count.nullable(),
  appOpens: count.nullable(),
  reelsMs: count.nullable(),
  reelsCount: count,
  adCount: count,
  photoCount: count,
  revisitCount: count,
  videoCount: count.nullable().default(null),
  measuredVideoCount: count.nullable().default(null),
  playbackMs: count.nullable().default(null),
  watchCount: count.nullable().default(null),
  completionCount: count.nullable().default(null),
});
const schema = z
  .strictObject({
    contractVersion: z.union([z.literal(2), z.literal(3)]),
    revision: count,
    usageEnabled: z.boolean(),
    usageGranted: z.boolean(),
    batteryOptimized: z.boolean(),
    backgroundRestricted: z.boolean().nullable(),
    usageStatus: z.enum([
      'disabled',
      'permission_needed',
      'unavailable',
      'dense_history',
      'interrupted',
      'partial',
    ]),
    effectiveAtMs: count,
    queryThroughMs: count,
    reminderEnabled: z.boolean(),
    reminderCount: z.number().int().min(1).max(1000),
    reminderMinutes: z.number().int().min(1).max(240),
    onboardingSeen: z.boolean(),
    includeRecognizedAds: z.boolean(),
    breakSeconds: z.number().int().min(0).max(120),
    measurePlayback: z.boolean().default(false),
    countPhotos: z.boolean().default(true),
    blockers: z
      .array(
        z.strictObject({
          app: z.enum(socialKeys),
          enabled: z.boolean(),
          dailyLimit: z.number().int().min(0).max(1000),
          available: z.boolean(),
        }),
      )
      .length(socialKeys.length),
    zone: z.string().min(1).max(100),
    days: z
      .array(
        z.strictObject({
          day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
          apps: z.array(app).max(2000),
          hours: z.array(count).length(24),
          interventions: z.record(z.string(), z.number()),
        }),
      )
      .min(1)
      .max(31),
  })
  .refine(
    value =>
      new Set(value.days.map(day => day.day)).size === value.days.length &&
      new Set(value.blockers.map(item => item.app)).size ===
        socialKeys.length &&
      value.days.every(
        day =>
          new Set(day.apps.map(item => item.packageName)).size ===
          day.apps.length,
      ),
  );
export type DiarySnapshot = Zod.infer<typeof schema>;
export type DiaryApp = Zod.infer<typeof app>;
export function parseDiarySnapshot(payload: unknown): DiarySnapshot {
  try {
    if (typeof payload === 'string' && payload.length <= 2_000_000) {
      const result = schema.safeParse(JSON.parse(payload));
      if (result.success) return result.data;
    }
  } catch {
    /* Native data never appears in diagnostic errors. */
  }
  throw new Error('Diary unavailable');
}

const civilDay = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/)
  .refine(value => {
    const date = new Date(value + 'T00:00:00Z');
    return (
      Number.isFinite(date.getTime()) &&
      date.toISOString().slice(0, 10) === value
    );
  });
const shiftDay = (value: string, offset: number) =>
  new Date(new Date(value + 'T00:00:00Z').getTime() + offset * 86_400_000)
    .toISOString()
    .slice(0, 10);
const historyPageSchema = z
  .strictObject({
    contractVersion: z.union([z.literal(1), z.literal(2)]),
    rangeStart: civilDay.optional(),
    rangeEnd: civilDay.optional(),
    earliestDay: civilDay.optional(),
    installDay: civilDay.optional(),
    windowDays: z.union([z.literal(0), z.literal(7), z.literal(30)]).optional(),
    pageSize: z.union([z.literal(7), z.literal(30)]).optional(),
    firstDay: civilDay.nullable(),
    lastRecordedDay: civilDay.nullable(),
    throughDay: civilDay,
    zone: z.string().min(1).max(100),
    dayCount: z.number().int().min(0).max(3_000_000),
    sources: z
      .array(
        z.strictObject({
          key: z.enum(socialKeys),
          videos: count,
          reelsMs: count.nullable(),
        }),
      )
      .length(5),
    pageStart: civilDay,
    pageEnd: civilDay,
    olderEnd: civilDay.nullable(),
    newerEnd: civilDay.nullable(),
    snapshot: schema,
  })
  .refine(value => {
    const ranged = value.contractVersion === 2;
    if (
      ranged &&
      (!value.rangeStart ||
        !value.rangeEnd ||
        !value.earliestDay ||
        !value.installDay ||
        value.windowDays === undefined ||
        !value.pageSize)
    )
      return false;
    if (
      !ranged &&
      [
        value.rangeStart,
        value.rangeEnd,
        value.earliestDay,
        value.installDay,
        value.pageSize,
        value.windowDays,
      ].some(item => item !== undefined)
    )
      return false;
    const first = ranged
      ? value.rangeStart!
      : (value.firstDay ?? value.throughDay);
    const last =
      ranged && value.rangeEnd! < value.throughDay
        ? value.rangeEnd!
        : value.throughDay;
    const selectedEnd = ranged ? value.rangeEnd! : value.throughDay;
    const size = ranged ? value.pageSize! : 30;
    const days = value.snapshot.days;
    return (
      new Set(value.sources.map(source => source.key)).size === 5 &&
      value.zone === value.snapshot.zone &&
      days.length <= size &&
      first <= last &&
      last <= value.throughDay &&
      first <= value.throughDay &&
      (!ranged ||
        (value.earliestDay === value.installDay &&
          (value.windowDays === 0
            ? selectedEnd <= value.throughDay
            : selectedEnd === shiftDay(first, value.windowDays! - 1)))) &&
      (!ranged ||
        (value.earliestDay! <= first &&
          value.installDay! >= value.earliestDay!)) &&
      first <= value.pageStart &&
      value.pageStart <= value.pageEnd &&
      value.pageEnd <= last &&
      value.dayCount ===
        (value.firstDay === null
          ? 0
          : Math.round(
              (Date.parse(value.throughDay) - Date.parse(value.firstDay!)) /
                86_400_000,
            ) + 1) &&
      (value.firstDay === null
        ? value.lastRecordedDay === null
        : value.lastRecordedDay !== null &&
          value.firstDay! <= value.lastRecordedDay &&
          value.lastRecordedDay <= value.throughDay) &&
      value.pageStart ===
        (shiftDay(value.pageEnd, 1 - size) < first
          ? first
          : shiftDay(value.pageEnd, 1 - size)) &&
      value.olderEnd ===
        (value.pageStart > first ? shiftDay(value.pageStart, -1) : null) &&
      value.newerEnd ===
        (value.pageEnd < last
          ? shiftDay(value.pageEnd, size) > last
            ? last
            : shiftDay(value.pageEnd, size)
          : null) &&
      days[0]?.day === value.pageStart &&
      days[days.length - 1]?.day === value.pageEnd &&
      days.every((day, index) => day.day === shiftDay(value.pageStart, index))
    );
  });
export type DiaryHistoryPage = Zod.infer<typeof historyPageSchema>;
export function parseDiaryHistoryPage(payload: unknown): DiaryHistoryPage {
  try {
    if (typeof payload === 'string' && payload.length <= 2_000_000) {
      const result = historyPageSchema.safeParse(JSON.parse(payload));
      if (result.success) return result.data;
    }
  } catch {
    /* Never include native history in errors. */
  }
  throw new Error('History page unavailable');
}
