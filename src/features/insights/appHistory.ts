import type { HistoryDay } from './historySummary';
import { sumCounts } from '../../ui/format';

export type AppHistorySort = 'time' | 'opens' | 'name';

/** Package usage is independent of the source-level media totals on the same row. */
export function appHistories(days: HistoryDay[]) {
  const packages = new Map<string, string>();
  const byDay = days.map(
    day => new Map(day.apps.map(app => [app.packageName, app])),
  );
  for (const day of days) {
    for (const app of day.apps) packages.set(app.packageName, app.name);
  }
  return [...packages].map(([packageName, name]) => {
    const daily = days.map((day, index) => {
      const app = byDay[index]?.get(packageName);
      return {
        day: day.day,
        appMs: app?.appMs ?? null,
        appOpens: app?.appOpens ?? null,
      };
    });
    const times = daily.flatMap(row => (row.appMs === null ? [] : [row.appMs]));
    const opens = daily.flatMap(row =>
      row.appOpens === null ? [] : [row.appOpens],
    );
    return {
      packageName,
      name,
      daily,
      appMs: times.length ? sumCounts(times) : null,
      appOpens: opens.length ? sumCounts(opens) : null,
      timeDays: times.length,
      opensDays: opens.length,
    };
  });
}

export type AppHistory = ReturnType<typeof appHistories>[number];

export function filterAppHistories(
  apps: AppHistory[],
  query: string,
  sort: AppHistorySort,
) {
  const search = query.trim().toLocaleLowerCase();
  return apps
    .filter(app =>
      `${app.name}\n${app.packageName}`.toLocaleLowerCase().includes(search),
    )
    .sort((a, b) => {
      if (sort !== 'name') {
        const left = sort === 'time' ? a.appMs : a.appOpens;
        const right = sort === 'time' ? b.appMs : b.appOpens;
        if (left === null && right !== null) return 1;
        if (right === null && left !== null) return -1;
        if (left !== null && right !== null && BigInt(left) !== BigInt(right)) {
          return BigInt(left) > BigInt(right) ? -1 : 1;
        }
      }
      return (
        a.name.localeCompare(b.name) ||
        a.packageName.localeCompare(b.packageName)
      );
    });
}
