import NativeDiary from '../../platform/specs/NativeDiary';
import {
  parseDiaryHistoryPage,
  type DiaryHistoryPage,
} from '../../platform/diaryStatus';

type Request = [string, string, string, 7 | 30, 0 | 7 | 30];
const cache = new Map<string, DiaryHistoryPage>();
const pending = new Map<string, Promise<DiaryHistoryPage>>();
let generation = 0;
const listeners = new Set<() => void>();
export const historyGeneration = () => generation;
export const subscribeHistory = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};
export const historyKey = (args: Request) => args.join('|');
export const cachedHistory = (args: Request) =>
  cache.get(historyKey(args)) ?? null;
/** Memory only. Invalidate on foreground/data replacement; never revive a pre-erase request. */
export function clearHistoryCache() {
  generation++;
  cache.clear();
  pending.clear();
  listeners.forEach(listener => listener());
}
export function readHistory(args: Request): Promise<DiaryHistoryPage> {
  const key = historyKey(args);
  const existing = pending.get(key);
  if (existing) return existing;
  if (!NativeDiary) return Promise.reject(new Error('History unavailable'));
  const epoch = generation;
  const request = NativeDiary.getHistoryRange(...args)
    .then(raw => {
      const value = parseDiaryHistoryPage(raw);
      if (epoch !== generation) throw new Error('History invalidated');
      if (epoch === generation) {
        if (cache.size >= 8) cache.delete(cache.keys().next().value!);
        cache.set(key, value);
      }
      return value;
    })
    .finally(() => {
      if (pending.get(key) === request) pending.delete(key);
    });
  pending.set(key, request);
  return request;
}
export async function warmHistory() {
  const epoch = generation;
  // Serialize expensive native range queries so launch keeps priority.
  for (const days of [7, 30, 0] as const) {
    if (generation !== epoch) return;
    try {
      await readHistory(['', '', '', days === 7 ? 7 : 30, days]);
    } catch {
      /* visible retry owns errors */
    }
  }
}
