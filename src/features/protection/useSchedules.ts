import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import NativeDiary from '../../platform/specs/NativeDiary';
import {
  parseProtection,
  parseProtectableApps,
  type ProtectionSnapshot,
  type ProtectableApp,
} from '../../platform/protectionStatus';

export function useSchedules(loadCatalog = true) {
  const [snapshot, setSnapshot] = useState<ProtectionSnapshot | null>(null);
  const [apps, setApps] = useState<ProtectableApp[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const alive = useRef(true);
  const pending = useRef(false);
  const generation = useRef(0);
  const refresh = useCallback(async (catalog = true) => {
    if (!NativeDiary || pending.current) return;
    const request = ++generation.current;
    setBusy(true);
    try {
      const [raw, installed] = await Promise.all([
        NativeDiary.getAppSchedules(),
        catalog ? NativeDiary.getProtectableApps() : Promise.resolve(null),
      ]);
      const value = parseProtection(raw);
      const choices =
        installed === null ? null : parseProtectableApps(installed);
      if (alive.current && request === generation.current) {
        setSnapshot(value);
        if (choices) setApps(choices);
        setError(null);
      }
    } catch {
      if (alive.current && request === generation.current)
        setError('Could not load schedules. Saved rules are unchanged.');
    } finally {
      if (alive.current && request === generation.current) setBusy(false);
    }
  }, []);
  useEffect(() => {
    alive.current = true;
    void refresh(loadCatalog);
    const listener = AppState.addEventListener('change', state => {
      if (state === 'active') void refresh(loadCatalog);
    });
    return () => {
      alive.current = false;
      generation.current++;
      listener.remove();
    };
  }, [loadCatalog, refresh]);
  async function save(
    next: ProtectionSnapshot,
    expectedRevision = next.revision,
  ) {
    if (!NativeDiary || pending.current) return false;
    pending.current = true;
    generation.current++;
    setBusy(true);
    try {
      const document = JSON.stringify({
        groups: next.groups,
        windows: next.windows,
      });
      const value = parseProtection(
        await NativeDiary.saveAppSchedules(document, expectedRevision),
      );
      if (alive.current) {
        setSnapshot(value);
        setError(null);
      }
      return true;
    } catch {
      if (alive.current)
        setError(
          'Could not save. Schedules may have changed. Your draft is kept; return and refresh before trying again.',
        );
      return false;
    } finally {
      pending.current = false;
      if (alive.current) setBusy(false);
    }
  }
  async function focus(
    packages: string[] | null,
    minutes = 25,
    expected = snapshot?.revision,
    allowOnly = false,
  ) {
    if (!NativeDiary || pending.current || !snapshot || !expected) return false;
    pending.current = true;
    generation.current++;
    setBusy(true);
    try {
      const raw = packages
        ? await NativeDiary.startFocusMode(
            packages,
            minutes,
            expected,
            allowOnly,
          )
        : await NativeDiary.stopFocus(snapshot.focus?.id ?? '', expected);
      if (alive.current) {
        setSnapshot(parseProtection(raw));
        setError(null);
      }
      return true;
    } catch {
      if (alive.current)
        setError(
          'Could not change focus. Refresh to check the current session before trying again.',
        );
      return false;
    } finally {
      pending.current = false;
      if (alive.current) setBusy(false);
    }
  }
  return { snapshot, apps, busy, error, refresh, save, focus };
}
