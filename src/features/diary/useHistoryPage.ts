import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import NativeDiary from '../../platform/specs/NativeDiary';
import type { DiaryHistoryPage } from '../../platform/diaryStatus';
import {
  cachedHistory,
  readHistory,
  historyGeneration,
  subscribeHistory,
} from './historyCache';

/** Lifetime aggregates are read only on entry, paging, explicit retry or foreground return. */
export function useHistoryPage(
  enabled: boolean,
  endDay: string,
  startDay = '',
  throughDay = '',
  pageSize: 7 | 30 = 30,
  windowDays: 0 | 7 | 30 = 0,
) {
  const epoch = useSyncExternalStore(subscribeHistory, historyGeneration);
  const key = `${epoch}|${startDay}|${throughDay}|${endDay}|${pageSize}|${windowDays}`;
  const focused = useIsFocused();
  const [loaded, setLoaded] = useState<{
    key: string;
    value: DiaryHistoryPage;
  } | null>(null);
  const [failure, setFailure] = useState<{
    key: string;
    message: string;
  } | null>(null);
  const [busy, setBusy] = useState(false);
  const serial = useRef(0);
  const current = useRef({ enabled, key });
  current.current = { enabled, key };
  const refresh = useCallback(async () => {
    if (!enabled || !NativeDiary || AppState.currentState !== 'active') return;
    const request = ++serial.current;
    setBusy(true);
    try {
      const value = await readHistory([
        startDay,
        throughDay,
        endDay,
        pageSize,
        windowDays,
      ]);
      if (
        serial.current === request &&
        current.current.enabled &&
        current.current.key === key
      ) {
        setLoaded({ key, value });
        setFailure(null);
      }
    } catch {
      if (
        serial.current === request &&
        current.current.enabled &&
        current.current.key === key
      )
        setFailure({
          key,
          message: 'History is unavailable. Saved data is preserved.',
        });
    } finally {
      if (serial.current === request) setBusy(false);
    }
  }, [enabled, endDay, startDay, throughDay, pageSize, windowDays, key]);
  useEffect(() => {
    if (!focused || !enabled) return;
    void refresh();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') void refresh();
    });
    return () => {
      serial.current++;
      subscription.remove();
    };
  }, [enabled, focused, refresh]);
  return {
    history: enabled
      ? loaded?.key === key
        ? loaded.value
        : cachedHistory([startDay, throughDay, endDay, pageSize, windowDays])
      : null,
    error: enabled && failure?.key === key ? failure.message : null,
    busy,
    refresh,
  };
}
