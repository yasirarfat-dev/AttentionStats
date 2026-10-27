import type * as Zod from 'zod';
// Metro supports the package's published CJS export; RN's preset currently cannot
// transform Zod's ESM namespace re-exports. Keep the same reviewed latest package.
const { z }: typeof Zod = require('zod');

const count = z
  .string()
  .regex(/^(0|[1-9][0-9]{0,18})$/)
  .refine(
    value =>
      value.length < 19 ||
      (value.length === 19 && value <= '9223372036854775807'),
  );
const source = z.strictObject({
  app: z.enum(['instagram', 'facebook']),
  enabled: z.boolean(),
  paused: z.boolean(),
  installed: z.boolean(),
  version: z.string().max(100),
  supported: z.boolean(),
  today: count,
  visit: count.nullable(),
});
const schema = z
  .strictObject({
    contractVersion: z.literal(1),
    revision: z.string().regex(/^[0-9a-f-]{36}:[0-9]{1,19}$/),
    historyRevision: z.string().regex(/^[0-9a-f-]{36}:[0-9]{1,19}$/),
    generation: z.string().uuid(),
    storage: z.enum(['starting', 'ready', 'storage_unavailable', 'busy']),
    accessibilityEnabled: z.boolean(),
    accessibilitySelected: z.boolean(),
    serviceConnected: z.boolean(),
    overlayEnabled: z.boolean(),
    calibrationEnabled: z.boolean(),
    day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
    zone: z.string().min(1).max(100),
    sources: z.array(source).length(2),
  })
  .refine(
    value =>
      new Set(value.sources.map(item => item.app)).size === 2 &&
      value.revision.startsWith(value.generation + ':') &&
      value.historyRevision.startsWith(value.generation + ':'),
  );

export type ReelsSnapshot = Zod.infer<typeof schema>;
export type ReelsApp = ReelsSnapshot['sources'][number]['app'];
export function parseReelsSnapshot(payload: unknown): ReelsSnapshot {
  if (typeof payload !== 'string' || payload.length > 8192)
    throw new Error('Counter unavailable');
  try {
    const result = schema.safeParse(JSON.parse(payload));
    if (result.success) return result.data;
  } catch {
    /* Never expose malformed native payloads in an error. */
  }
  throw new Error('Counter unavailable');
}
