import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { useIsFocused } from '@react-navigation/native';
import {
  NativeReels,
  parseReelsSnapshot,
  type ReelsSnapshot,
} from '../../platform';

export function useCounterAppearance() {
  const [snapshot, setSnapshot] = useState<ReelsSnapshot | null>(null);
  const [error, setError] = useState<string | null>(
    NativeReels ? null : 'Counter display settings are available on Android.',
  );
  const [busy, setBusy] = useState(false);
  const active = useRef(true);
  const pending = useRef(false);
  const request = useRef(0);
  const focused = useIsFocused();
  const refresh = useCallback(async () => {
    if (!NativeReels || pending.current) return;
    const ticket = ++request.current;
    try {
      const next = parseReelsSnapshot(await NativeReels.getSnapshot());
      if (active.current && ticket === request.current) {
        setSnapshot(next);
        setError(null);
      }
    } catch {
      if (active.current && ticket === request.current)
        setError(
          'Display settings are unavailable. Saved history is preserved.',
        );
    }
  }, []);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      request.current++;
    };
  }, []);
  useEffect(() => {
    if (!focused) return;
    refresh();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') refresh();
    });
    return () => {
      subscription.remove();
      request.current++;
    };
  }, [focused, refresh]);

  async function save(showIcons: boolean, allAppsToday: boolean) {
    if (!NativeReels || !snapshot || pending.current) return;
    pending.current = true;
    setBusy(true);
    const ticket = ++request.current;
    try {
      const next = parseReelsSnapshot(
        await NativeReels.setCounterAppearance(
          showIcons,
          allAppsToday,
          snapshot.revision,
        ),
      );
      if (active.current && ticket === request.current) {
        setSnapshot(next);
        setError(null);
      }
    } catch {
      if (active.current && ticket === request.current)
        setError(
          'The change was not saved. Refresh the current settings and try again.',
        );
    } finally {
      pending.current = false;
      if (active.current) setBusy(false);
      if (active.current && ticket !== request.current) refresh();
    }
  }
  return { snapshot, error, busy, refresh, save };
}
