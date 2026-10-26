// Metro's documented require condition selects Zod's published CommonJS export.
// Its ESM export namespace needs a transform the current RN preset does not provide.
const { z }: typeof import('zod') = require('zod');

const schema = z.strictObject({
  contractVersion: z.literal(1),
  sourceState: z.literal('not_implemented'),
});

export type BootstrapResult =
  | { kind: 'disabled' }
  | {
      kind: 'unavailable';
      reason: 'UNSUPPORTED' | 'INVALID_RESPONSE' | 'NATIVE_FAILURE';
    };

export async function readBootstrapStatus(
  native: { getBootstrapStatus: () => Promise<unknown> } | null,
): Promise<BootstrapResult> {
  if (!native) return { kind: 'unavailable', reason: 'UNSUPPORTED' };
  try {
    const value = await native.getBootstrapStatus();
    return schema.safeParse(value).success
      ? { kind: 'disabled' }
      : { kind: 'unavailable', reason: 'INVALID_RESPONSE' };
  } catch {
    // Native details may contain private context; expose a stable reason only.
    return { kind: 'unavailable', reason: 'NATIVE_FAILURE' };
  }
}
