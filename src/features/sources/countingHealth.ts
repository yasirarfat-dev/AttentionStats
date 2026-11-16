import type { ReelsSnapshot } from '../../platform';

type Health = Pick<
  ReelsSnapshot,
  'accessibilityEnabled' | 'accessibilitySelected' | 'serviceConnected'
> & {
  sources: ReadonlyArray<
    Pick<
      ReelsSnapshot['sources'][number],
      'enabled' | 'consentCurrent' | 'paused'
    >
  >;
};

/** A saved Android selection can survive a disconnected or crashed service. */
export function accessibilityReconnectNeeded(snapshot: Health | null): boolean {
  return (
    !!snapshot && snapshot.accessibilitySelected && !snapshot.serviceConnected
  );
}

/** Setup readiness requires the actual connection, separately from the saved grant. */
export function countingReady(snapshot: Health | null): boolean {
  return (
    !!snapshot &&
    snapshot.accessibilityEnabled &&
    snapshot.serviceConnected &&
    snapshot.sources.some(
      source => source.enabled && source.consentCurrent && !source.paused,
    )
  );
}
