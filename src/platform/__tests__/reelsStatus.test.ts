import { parseReelsSnapshot } from '../reelsStatus';

const generation = '11111111-1111-4111-8111-111111111111';
function fixture() {
  return {
    contractVersion: 6,
    revision: generation + ':1',
    historyRevision: generation + ':1',
    generation,
    storage: 'ready',
    noticeRevision: 6,
    accessibilityEnabled: false,
    accessibilitySelected: false,
    serviceConnected: false,
    overlayEnabled: false,
    calibrationEnabled: false,
    diagnosticsAvailable: false,
    counterAppearance: { showIcons: true, allAppsToday: true },
    storageBytes: '4096',
    session: { count: '0' },
    device: {
      model: 'Fixture',
      manufacturer: 'Synthetic',
      sdk: 36,
      release: '16',
      appVersion: '1.0.0',
    },
    day: '2026-10-27',
    zone: 'UTC',
    sources: ['instagram', 'facebook', 'youtube', 'tiktok', 'snapchat'].map(
      app => ({
        app,
        enabled: false,
        consentCurrent: false,
        paused: false,
        installed: true,
        version: 'fixture-only',
        supported: false,
        lastRecognizedMs: null as string | null,
        today: '0',
        visit: null,
        counts: {
          reel: '0',
          photo: '0',
          post: null,
          ad: '0',
          unclassified: '0',
        },
      }),
    ),
  };
}
function sourceAt(value: ReturnType<typeof fixture>, index: number) {
  const selected = value.sources[index];
  if (!selected) throw new Error('Missing synthetic source');
  return selected;
}
test('accepts bounded native status without conflating grant, consent and support', () => {
  const value = fixture();
  value.accessibilityEnabled = true;
  const status = parseReelsSnapshot(JSON.stringify(value));
  expect(status.accessibilityEnabled).toBe(true);
  expect(status.sources.every(item => !item.enabled && !item.supported)).toBe(
    true,
  );
});
test('keeps 64-bit counts as decimal strings', () => {
  const value = fixture();
  sourceAt(value, 0).today = '9223372036854775807';
  expect(parseReelsSnapshot(JSON.stringify(value)).sources[0]?.today).toBe(
    '9223372036854775807',
  );
});
test.each(['-1', '1.5', '00', '1e6', '9223372036854775808', 'content-marker'])(
  'rejects invalid count %s',
  count => {
    const value = fixture();
    sourceAt(value, 0).today = count;
    expect(() => parseReelsSnapshot(JSON.stringify(value))).toThrow(
      'Counter unavailable',
    );
  },
);
test('rejects duplicate/unknown apps and mismatched generation', () => {
  const duplicate = fixture();
  sourceAt(duplicate, 1).app = 'instagram';
  const unknown = fixture();
  sourceAt(unknown, 1).app = 'unknown';
  const mismatch = fixture();
  mismatch.revision = '22222222-2222-4222-8222-222222222222:1';
  const historyMismatch = fixture();
  historyMismatch.historyRevision = '22222222-2222-4222-8222-222222222222:1';
  for (const value of [duplicate, unknown, mismatch, historyMismatch])
    expect(() => parseReelsSnapshot(JSON.stringify(value))).toThrow();
});
test('rejects extra fields, bad version/types and oversized/malformed transport', () => {
  for (const raw of [
    JSON.stringify({ ...fixture(), extra: true }),
    JSON.stringify({ ...fixture(), contractVersion: 1 }),
    JSON.stringify({ ...fixture(), accessibilityEnabled: 'yes' }),
    'x'.repeat(8193),
    '{"private-marker":',
    null,
  ]) {
    expect(() => parseReelsSnapshot(raw)).toThrow('Counter unavailable');
  }
});

test('keeps updated consent independent of old enablement and supported layouts', () => {
  const value = fixture();
  const source = sourceAt(value, 0);
  source.enabled = true;
  source.supported = true;
  const result = parseReelsSnapshot(JSON.stringify(value));
  expect(result.sources[0]?.consentCurrent).toBe(false);
  expect(result.sources[0]?.counts.post).toBeNull();
});

test.each(['reel', 'photo', 'ad', 'unclassified'] as const)(
  'rejects invalid or missing category %s',
  category => {
    for (const bad of ['9223372036854775808', '-1', '00', null, 1, undefined]) {
      const value = fixture();
      Object.assign(sourceAt(value, 0).counts, { [category]: bad });
      expect(() => parseReelsSnapshot(JSON.stringify(value))).toThrow(
        'Counter unavailable',
      );
    }
  },
);

test('rejects normal-feed post counts while that capability is unavailable', () => {
  const value = fixture();
  Object.assign(sourceAt(value, 0).counts, { post: '0' });
  expect(() => parseReelsSnapshot(JSON.stringify(value))).toThrow();
});

test('rejects a diagnostic session when the build does not allow diagnostics', () => {
  const value = fixture();
  value.calibrationEnabled = true;
  expect(() => parseReelsSnapshot(JSON.stringify(value))).toThrow();
  value.diagnosticsAvailable = true;
  expect(parseReelsSnapshot(JSON.stringify(value)).calibrationEnabled).toBe(
    true,
  );
});

test('validates display preferences without changing source consent', () => {
  const value = fixture();
  value.counterAppearance = { showIcons: false, allAppsToday: false };
  const result = parseReelsSnapshot(JSON.stringify(value));
  expect(result.counterAppearance).toEqual(value.counterAppearance);
  expect(result.sources.every(source => !source.enabled)).toBe(true);
  for (const bad of [
    undefined,
    { showIcons: true },
    { showIcons: true, allAppsToday: 'yes' },
  ]) {
    expect(() =>
      parseReelsSnapshot(JSON.stringify({ ...value, counterAppearance: bad })),
    ).toThrow();
  }
});
