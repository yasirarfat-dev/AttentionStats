import { accessibilityReconnectNeeded, countingReady } from '../countingHealth';

const source = {
  enabled: true,
  consentCurrent: true,
  paused: false,
};
const connected = {
  accessibilityEnabled: true,
  accessibilitySelected: true,
  serviceConnected: true,
  sources: [source],
};

test('a saved grant never hides a lost connection', () => {
  expect(countingReady(connected)).toBe(true);
  expect(countingReady({ ...connected, serviceConnected: false })).toBe(false);
  expect(countingReady(null)).toBe(false);
});

test('consent, enabled source and OS grant remain separate requirements', () => {
  expect(countingReady({ ...connected, accessibilityEnabled: false })).toBe(
    false,
  );
  expect(countingReady({ ...connected, sources: [] })).toBe(false);
  for (const change of [
    { enabled: false },
    { consentCurrent: false },
    { paused: true },
  ]) {
    expect(
      countingReady({ ...connected, sources: [{ ...source, ...change }] }),
    ).toBe(false);
  }
});

test('selected but disconnected needs reconnection even when Android master is off', () => {
  for (const accessibilityEnabled of [true, false]) {
    expect(
      accessibilityReconnectNeeded({
        ...connected,
        accessibilityEnabled,
        serviceConnected: false,
      }),
    ).toBe(true);
  }
  expect(accessibilityReconnectNeeded(connected)).toBe(false);
  expect(
    accessibilityReconnectNeeded({
      ...connected,
      accessibilitySelected: false,
      serviceConnected: false,
    }),
  ).toBe(false);
  expect(accessibilityReconnectNeeded(null)).toBe(false);
});
