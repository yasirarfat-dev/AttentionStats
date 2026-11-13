import type * as Zod from 'zod';
const { z }: typeof Zod = require('zod');

const id = z.string().regex(/^[A-Za-z0-9-]{1,64}$/);
const group = z.strictObject({
  id,
  name: z.string().trim().min(1).max(60),
  packages: z.array(z.string().min(1).max(256)).min(1).max(100),
});
const window = z.strictObject({
  id,
  groupId: id,
  days: z.number().int().min(1).max(127),
  startMinute: z.number().int().min(0).max(1439),
  endMinute: z.number().int().min(0).max(1439),
  enabled: z.boolean(),
  scope: z.enum(['app', 'short_videos']),
  allDay: z.boolean(),
  allowOnly: z.boolean().optional(),
});
const schema = z
  .strictObject({
    revision: z.string().regex(/^(0|[1-9][0-9]*)$/),
    accessibility: z.boolean(),
    focus: z
      .strictObject({
        id,
        allowOnly: z.boolean().optional(),
        packages: z.array(z.string().min(1).max(256)).min(1).max(100),
        durationMs: z
          .string()
          .regex(/^(0|[1-9][0-9]*)$/)
          .optional(),
        remainingMs: z.string().regex(/^(0|[1-9][0-9]*)$/),
        deadlineMs: z.string().regex(/^(0|[1-9][0-9]*)$/),
      })
      .nullable()
      .optional(),
    groups: z.array(group).max(30),
    windows: z.array(window).max(100),
  })
  .superRefine((value, context) => {
    const groupIds = new Set(value.groups.map(item => item.id));
    if (
      groupIds.size !== value.groups.length ||
      new Set(value.windows.map(item => item.id)).size !== value.windows.length
    )
      context.addIssue({ code: 'custom', message: 'Duplicate protection ID' });
    for (const item of value.groups)
      if (new Set(item.packages).size !== item.packages.length)
        context.addIssue({ code: 'custom', message: 'Duplicate group member' });
    for (const item of value.windows)
      if (
        (item.allowOnly === true && item.scope !== 'app') ||
        !groupIds.has(item.groupId) ||
        (item.allDay
          ? item.startMinute !== 0 || item.endMinute !== 0
          : item.startMinute === item.endMinute)
      )
        context.addIssue({ code: 'custom', message: 'Invalid time slot' });
  });
const apps = z
  .array(
    z.strictObject({
      packageName: z.string().min(1).max(256),
      name: z.string().max(100),
      shortVideos: z.boolean(),
    }),
  )
  .max(500);

export type ProtectionSnapshot = Zod.infer<typeof schema>;
export type AppGroup = Zod.infer<typeof group>;
export type TimeSlot = Zod.infer<typeof window>;
export type ProtectableApp = Zod.infer<typeof apps>[number];
export const parseProtection = (raw: string): ProtectionSnapshot =>
  schema.parse(JSON.parse(raw));
export const parseProtectableApps = (raw: string): ProtectableApp[] =>
  apps.parse(JSON.parse(raw));

/** Configuration identity only, never a security token or behavioral fact ID. Native rejects collisions. */
export const newProtectionId = () =>
  `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`;
