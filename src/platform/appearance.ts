import type * as Zod from 'zod';
import NativeDiary from './specs/NativeDiary';
const { z }: typeof Zod = require('zod');
const schema = z.strictObject({
  mode: z.enum(['system', 'light', 'dark']),
  accent: z.enum(['graphite', 'blue', 'teal', 'violet']),
  motion: z.boolean(),
  customColor: z
    .string()
    .regex(/^$|^#[0-9A-Fa-f]{6}$/)
    .default(''),
  corners: z.number().int().min(0).max(32).default(24),
  spacing: z.number().int().min(12).max(24).default(20),
  numberSize: z.number().int().min(24).max(40).default(30),
});
export type AppearancePreference = Zod.infer<typeof schema>;
export async function loadAppearance(): Promise<AppearancePreference> {
  if (!NativeDiary)
    return schema.parse({ mode: 'system', accent: 'graphite', motion: true });
  return schema.parse(JSON.parse(await NativeDiary.getAppearance()));
}
export async function saveAppearance(
  value: AppearancePreference,
): Promise<AppearancePreference> {
  if (!NativeDiary) throw new Error('Appearance unavailable');
  const chosen = schema.parse(value);
  return schema.parse(
    JSON.parse(await NativeDiary.configureAppearance(JSON.stringify(chosen))),
  );
}
