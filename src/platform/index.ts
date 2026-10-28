import NativeBootstrap from './specs/NativeBootstrap';
import { readBootstrapStatus } from './bootstrapStatus';

export type { BootstrapResult } from './bootstrapStatus';
export const getBootstrapStatus = () =>
  readBootstrapStatus(NativeBootstrap ?? null);

export { default as NativeReels } from './specs/NativeReels';
export { reelsSources, reelsSourceKeys } from './reelsSources';
export {
  parseReelsSnapshot,
  type ReelsSnapshot,
  type ReelsApp,
} from './reelsStatus';
