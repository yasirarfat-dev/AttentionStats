import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import {
  NativeReels,
  NOTICE_REVISION,
  parseReelsSnapshot,
  type ReelsSnapshot,
  type ReelsSourceKey,
} from '../../platform';

/** Live counting state plus every counting-related action, revision-checked natively. */
export function useReels(poll = 5_000) {
  const [snapshot, setSnapshot] = useState<ReelsSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const live = useRef(true);
  const focused = useIsFocused();

  const refresh = useCallback(async () => {
    if (!NativeReels) return;
    try {
      const value = parseReelsSnapshot(await NativeReels.getSnapshot());
      if (live.current) {
        setSnapshot(value);
        setError(null);
      }
    } catch {
      if (live.current) setError('Counting status is unavailable right now.');
    }
  }, []);

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
    const timer = setInterval(refresh, poll);
    return () => {
      subscription.remove();
      clearInterval(timer);
    };
  }, [focused, refresh, poll]);

  const run = useCallback(
    async (operation: (revision: string) => Promise<string>) => {
      if (!NativeReels) return false;
      setBusy(true);
      try {
        const current = parseReelsSnapshot(await NativeReels.getSnapshot());
        const value = parseReelsSnapshot(await operation(current.revision));
        if (live.current) {
          setSnapshot(value);
          setError(null);
        }
        return true;
      } catch {
        if (live.current)
          setError('Could not save that change. Please try again.');
        return false;
      } finally {
        if (live.current) setBusy(false);
      }
    },
    [],
  );

  const native = NativeReels;
  return {
    snapshot,
    error,
    busy,
    refresh,
    enableAll: (enabled: boolean) =>
      run(revision => native!.configureAll(enabled, NOTICE_REVISION, revision)),
    setSource: (app: ReelsSourceKey, enabled: boolean) =>
      run(revision =>
        native!.configure(app, enabled, NOTICE_REVISION, revision),
      ),
    setOverlay: (enabled: boolean) =>
      run(revision => native!.setOverlay(enabled, revision)),
    setAppearance: (showIcons: boolean, allAppsToday: boolean) =>
      run(revision =>
        native!.setCounterAppearance(showIcons, allAppsToday, revision),
      ),
    setPaused: (paused: boolean) =>
      run(revision => native!.setPaused(paused, revision)),
    deleteAll: () => run(revision => native!.deleteHistory(revision)),
    openAccessibility: () => native?.openAccessibilitySettings(),
    openApp: (app: ReelsSourceKey) => native?.openTarget(app),
  };
}

/** True when counting is fully working: a source on, accessibility granted and connected. */
export function countingReady(snapshot: ReelsSnapshot | null): boolean {
  return (
    !!snapshot &&
    snapshot.accessibilityEnabled &&
    snapshot.sources.some(
      source => source.enabled && source.consentCurrent && !source.paused,
    )
  );
}
