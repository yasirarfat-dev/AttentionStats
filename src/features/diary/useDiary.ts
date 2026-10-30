import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import NativeDiary from '../../platform/specs/NativeDiary';
import type { ReelsSourceKey } from '../../platform/reelsSources';
import {
  parseDiarySnapshot,
  type DiarySnapshot,
} from '../../platform/diaryStatus';

export function useDiary(days: 1 | 7 | 31 = 1) {
  const [snapshot, setSnapshot] = useState<DiarySnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const live = useRef(true);
  const focused = useIsFocused();
  const latestDays = useRef(days);
  latestDays.current = days;
  const refreshAgain = useRef(false);
  const latestRefresh = useRef<() => Promise<void>>(async () => {});
  const refresh = useCallback(async () => {
    if (!NativeDiary || AppState.currentState !== 'active') return;
    if (pending.current) {
      refreshAgain.current = true;
      return;
    }
    pending.current = true;
    setBusy(true);
    try {
      const value = parseDiarySnapshot(await NativeDiary.getSnapshot(days));
      if (live.current && latestDays.current === days) {
        setSnapshot(value);
        setError(null);
      }
    } catch {
      if (live.current && latestDays.current === days)
        setError('History is unavailable. Saved data is preserved.');
    } finally {
      pending.current = false;
      if (live.current) setBusy(false);
      if (live.current && refreshAgain.current) {
        refreshAgain.current = false;
        void latestRefresh.current();
      }
    }
  }, [days]);
  latestRefresh.current = refresh;
  useEffect(() => {
    setSnapshot(null);
    setError(null);
  }, [days]);
  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
    };
  }, []);
  useEffect(() => {
    if (!focused) return;
    refresh();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') refresh();
    });
    const timer = setInterval(refresh, 10_000);
    return () => {
      subscription.remove();
      clearInterval(timer);
    };
  }, [focused, refresh]);
  async function configure(
    action:
      | 'usage'
      | 'reminder'
      | 'onboarding'
      | 'ads'
      | 'break'
      | `block.${ReelsSourceKey}`,
    enabled: boolean,
    count = snapshot?.reminderCount ?? 15,
    minutes = snapshot?.reminderMinutes ?? 15,
  ) {
    if (!NativeDiary || !snapshot) return false;
    if (pending.current) {
      setError('Refreshing usage. Try your change again in a moment.');
      return false;
    }
    pending.current = true;
    setBusy(true);
    try {
      // Separate Settings cards can hold different revisions. Read a fresh base
      // for independent switches; reminder drafts retain their conflict check.
      const current =
        action === 'reminder'
          ? snapshot
          : parseDiarySnapshot(await NativeDiary.getSnapshot(1));
      const saved = parseDiarySnapshot(
        await NativeDiary.configure(
          action,
          enabled,
          count,
          minutes,
          current.revision,
        ),
      );
      const value =
        days === 1
          ? saved
          : parseDiarySnapshot(await NativeDiary.getSnapshot(days));
      if (live.current) {
        setSnapshot(value);
        setError(null);
      }
      return true;
    } catch {
      if (live.current) setError('Could not save. Refresh and try again.');
      return false;
    } finally {
      pending.current = false;
      if (live.current) setBusy(false);
      if (live.current && refreshAgain.current) {
        refreshAgain.current = false;
        void latestRefresh.current();
      }
    }
  }
  return { snapshot, error, busy, refresh, configure, native: NativeDiary };
}
