import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import NativeDiary from '../../platform/specs/NativeDiary';
import {
  parseDiaryHistoryPage,
  type DiaryHistoryPage,
} from '../../platform/diaryStatus';

/** Lifetime aggregates are read only on entry, paging, explicit retry or foreground return. */
export function useHistoryPage(enabled: boolean, endDay: string) {
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
  const current = useRef({ enabled, endDay });
  current.current = { enabled, endDay };
  const refresh = useCallback(async () => {
    if (!enabled || !NativeDiary || AppState.currentState !== 'active') return;
    const request = ++serial.current;
    setBusy(true);
    try {
      const value = parseDiaryHistoryPage(
        await NativeDiary.getHistoryPage(endDay),
      );
      if (
        serial.current === request &&
        current.current.enabled &&
        current.current.endDay === endDay
      ) {
        setLoaded({ key: endDay, value });
        setFailure(null);
      }
    } catch {
      if (
        serial.current === request &&
        current.current.enabled &&
        current.current.endDay === endDay
      )
        setFailure({
          key: endDay,
          message: 'All-time history is unavailable. Saved data is preserved.',
        });
    } finally {
      if (serial.current === request) setBusy(false);
    }
  }, [enabled, endDay]);
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
    history: enabled && loaded?.key === endDay ? loaded.value : null,
    error: enabled && failure?.key === endDay ? failure.message : null,
    busy,
    refresh,
  };
}
