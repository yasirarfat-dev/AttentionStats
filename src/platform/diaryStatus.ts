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
});
const schema = z
  .strictObject({
    contractVersion: z.literal(2),
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
    breakSeconds: z.number().int().min(1).max(120),
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
