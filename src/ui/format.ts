export function duration(value: number | null): string {
  if (value === null || !Number.isFinite(value) || value < 0) return '—';
  if (value < 60_000) return `${Math.floor(value / 1000)}s`;
  const minutes = Math.floor(value / 60_000);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  return hours >= 48
    ? `${Math.floor(hours / 24)}d ${hours % 24}h`
    : `${hours}h ${minutes % 60}m`;
}
export function sumCounts(values: string[]): string {
  return values.reduce((sum, value) => sum + BigInt(value), 0n).toString();
}
export function exactCount(value: string): string {
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}
export function compactCount(value: string, compactAt = 1_000_000n): string {
  const count = BigInt(value);
  if (count < compactAt) return exactCount(value);
  const units = [
    [1_000_000_000_000_000_000n, 'E'],
    [1_000_000_000_000_000n, 'Q'],
    [1_000_000_000_000n, 'T'],
    [1_000_000_000n, 'B'],
    [1_000_000n, 'M'],
    [1_000n, 'K'],
  ] as const;
  for (const [scale, suffix] of units)
    if (count >= scale) {
      const tenth = (count % scale) / (scale / 10n);
      return `${count / scale}${tenth ? '.' + tenth : ''}${suffix}`;
    }
  return exactCount(value);
}

export function fullDuration(value: number | null): string {
  if (value === null) return 'No measured time is available for this period.';
  const seconds = Math.floor(value / 1000);
  return `${Math.floor(seconds / 3600)} hours ${Math.floor(seconds / 60) % 60} minutes ${seconds % 60} seconds`;
}

export function countedEncounters(
  total: string,
  ads: string,
  includeAds: boolean,
): string {
  const value = BigInt(total) - (includeAds ? 0n : BigInt(ads));
  return (value < 0n ? 0n : value).toString();
}

/** "just now", "5 min ago", "3 h ago", "2 d ago". */
export function ago(atMs: number, now = Date.now()): string {
  const minutes = Math.floor((now - atMs) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.floor(hours / 24)} d ago`;
}

/** 0..23 -> "12a", "6a", "12p", "6p" style axis labels. */
export function hourLabel(hour: number): string {
  const suffix = hour < 12 ? 'a' : 'p';
  const value = hour % 12 === 0 ? 12 : hour % 12;
  return `${value}${suffix}`;
}
