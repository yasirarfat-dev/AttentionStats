import type { DiarySnapshot } from '../../platform/diaryStatus';
import { reelsSourceKeys } from '../../platform/reelsSources';
import { sumCounts } from '../../ui/format';

export type HistoryDay = DiarySnapshot['days'][number];

/** Social facts are source-level; app duration and opens are package-level. */
export function historySummary(days: HistoryDay[]) {
  const sources = reelsSourceKeys.map(key => {
    // Native snapshots can repeat source facts on regional package rows.
    const apps = days.flatMap(day => {
      const app = day.apps.find(item => item.social === key);
      return app ? [app] : [];
    });
    return {
      key,
      videos: sumCounts(apps.map(app => app.videoCount ?? app.reelsCount)),
      reelsMs: nullableSum(apps.map(app => app.reelsMs)),
    };
  });
  return {
    sources,
    videos: sumCounts(sources.map(source => source.videos)),
    reelsMs: nullableSum(sources.map(source => source.reelsMs)),
    appMs: nullableSum(days.flatMap(day => day.apps.map(app => app.appMs))),
  };
}

function nullableSum(values: (string | null)[]): string | null {
  const known = values.filter(value => value !== null);
  return known.length ? sumCounts(known) : null;
}

export function orderedDays(days: HistoryDay[]): HistoryDay[] {
  return [...days].sort((a, b) => a.day.localeCompare(b.day));
}

export function resolveDay(days: HistoryDay[], selected: string | null) {
  return days.find(day => day.day === selected) ?? days[days.length - 1];
}

export function usageExplanation(status: DiarySnapshot['usageStatus']) {
  switch (status) {
    case 'disabled':
      return 'App-time collection is off. Saved observations remain available.';
    case 'permission_needed':
      return 'Android usage access is off. App-time history may be incomplete.';
    case 'unavailable':
      return 'Android app-time history is unavailable. Saved video encounters remain available.';
    case 'dense_history':
      return 'This period exceeds the current app-time query limit. Some time is unavailable.';
    case 'interrupted':
      return 'App-time collection was interrupted. Some time is unavailable.';
    case 'partial':
      return 'Recorded observations only. Android app time can be delayed or incomplete.';
  }
}
