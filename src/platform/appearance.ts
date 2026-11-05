import type * as Zod from 'zod';
import NativeDiary from './specs/NativeDiary';
const { z }: typeof Zod = require('zod');
const schema = z.strictObject({
  mode: z.enum(['system', 'light', 'dark']),
  accent: z.enum(['graphite', 'blue', 'teal', 'violet']),
  motion: z.boolean(),
});
export type AppearancePreference = Zod.infer<typeof schema>;
export async function loadAppearance(): Promise<AppearancePreference> {
  if (!NativeDiary) return { mode: 'system', accent: 'graphite', motion: true };
  return schema.parse(JSON.parse(await NativeDiary.getAppearance()));
}
export async function saveAppearance(
  value: AppearancePreference,
): Promise<AppearancePreference> {
  if (!NativeDiary) throw new Error('Appearance unavailable');
  const chosen = schema.parse(value);
  return schema.parse(
    JSON.parse(
      await NativeDiary.setAppearance(
        chosen.mode,
        chosen.accent,
        chosen.motion,
      ),
    ),
  );
}
