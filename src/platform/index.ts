import NativeBootstrap from './specs/NativeBootstrap';
import { readBootstrapStatus } from './bootstrapStatus';

export type { BootstrapResult } from './bootstrapStatus';
export const getBootstrapStatus = () =>
  readBootstrapStatus(NativeBootstrap ?? null);
