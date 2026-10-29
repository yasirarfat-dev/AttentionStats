import { reelsSourceKeys } from './reelsSources';
type ReelsSourceKey = (typeof reelsSourceKeys)[number];

export type AutoScrollStatus = {
  enabled: boolean;
  running: boolean;
  reason: string;
  sources: { app: ReelsSourceKey; candidate: boolean }[];
};
export function parseAutoScrollStatus(raw: string): AutoScrollStatus {
  if (raw.length > 4096) throw new Error('Auto-scroll status unavailable');
  const value = JSON.parse(raw);
  if (
    value?.contract !== 1 ||
    Object.keys(value).length !== 6 ||
    value.sessionOnly !== true ||
    typeof value.enabled !== 'boolean' ||
    typeof value.running !== 'boolean' ||
    (value.running && !value.enabled) ||
    typeof value.reason !== 'string' ||
    !/^[a-z_]{1,48}$/.test(value.reason) ||
    !Array.isArray(value.sources) ||
    value.sources.length !== reelsSourceKeys.length ||
    reelsSourceKeys.some(
      key =>
        value.sources.filter((source: { app?: unknown }) => source?.app === key)
          .length !== 1,
    ) ||
    value.sources.some(
      (source: { candidate?: unknown }) =>
        typeof source?.candidate !== 'boolean' ||
        Object.keys(source).length !== 2,
    )
  ) {
    throw new Error('Auto-scroll status unavailable');
  }
  return value;
}
