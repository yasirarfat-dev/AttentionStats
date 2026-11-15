import type { DiarySnapshot } from '../../platform/diaryStatus';

/** Source facts repeat on regional package rows. Sum each source once per day. */
export function viewingSummary(days: DiarySnapshot['days']) {
  const apps = days.flatMap(day => {
    const seen = new Set<string>();
    return day.apps.filter(app => {
      if (app.social === null || seen.has(app.social)) return false;
      seen.add(app.social);
      return true;
    });
  });
  const sum = (
    key:
      | 'photoCount'
      | 'adCount'
      | 'watchCount'
      | 'completionCount'
      | 'measuredVideoCount'
      | 'playbackMs',
  ): string | null => {
    const known = apps.map(app => app[key]).filter(value => value !== null);
    return known.length
      ? known.reduce((total, value) => total + BigInt(value), 0n).toString()
      : null;
  };
  return {
    photos: sum('photoCount'),
    ads: sum('adCount'),
    watches: sum('watchCount'),
    completions: sum('completionCount'),
    visits: sum('measuredVideoCount'),
    playbackMs: sum('playbackMs'),
  };
}
