import type { DiaryApp, DiarySnapshot } from '../../platform/diaryStatus';
import { viewingSummary } from './viewingSummary';

const app = (overrides: Partial<DiaryApp> = {}): DiaryApp => ({
  packageName: 'com.example.player',
  name: 'Synthetic player',
  social: 'instagram',
  appMs: null,
  appOpens: null,
  reelsMs: null,
  reelsCount: '1',
  videoCount: '1',
  adCount: '0',
  photoCount: '0',
  revisitCount: '0',
  measuredVideoCount: null,
  playbackMs: null,
  watchCount: null,
  completionCount: null,
  ...overrides,
});
const day = (apps: DiaryApp[]): DiarySnapshot['days'][number] => ({
  day: '2026-11-15',
  apps,
  hours: Array(24).fill('0'),
  interventions: {},
});

test('no observation stays unknown, independently for each playback metric', () => {
  expect(viewingSummary([])).toEqual({
    photos: null,
    ads: null,
    watches: null,
    completions: null,
    visits: null,
    playbackMs: null,
  });
  expect(
    viewingSummary([day([app({ measuredVideoCount: '1', playbackMs: '0' })])]),
  ).toMatchObject({
    photos: '0',
    ads: '0',
    watches: null,
    completions: null,
    visits: '1',
    playbackMs: '0',
  });
});

test('native measured zero remains zero and recorded watches/completions remain exact', () => {
  expect(
    viewingSummary([
      day([
        app({
          measuredVideoCount: '1',
          playbackMs: '0',
          watchCount: '0',
          completionCount: '0',
        }),
      ]),
    ]),
  ).toMatchObject({ watches: '0', completions: '0', playbackMs: '0' });
  const measured = app({
    measuredVideoCount: '2',
    playbackMs: '12345',
    watchCount: '1',
    completionCount: '1',
  });
  expect(viewingSummary([day([measured]), day([measured])])).toMatchObject({
    visits: '4',
    playbackMs: '24690',
    watches: '2',
    completions: '2',
  });
});

test('source metrics count once per day across regional packages and ignore non-social rows', () => {
  const measured = app({
    photoCount: '2',
    adCount: '1',
    measuredVideoCount: '1',
    playbackMs: '4000',
    watchCount: '1',
    completionCount: '1',
  });
  expect(
    viewingSummary([
      day([
        measured,
        { ...measured, packageName: 'com.example.regional' },
        app({ social: null, watchCount: '99' }),
      ]),
    ]),
  ).toEqual({
    photos: '2',
    ads: '1',
    watches: '1',
    completions: '1',
    visits: '1',
    playbackMs: '4000',
  });
});

test('known partial playback is preserved alongside missing sources without inventing zero', () => {
  expect(
    viewingSummary([
      day([
        app({ watchCount: '9007199254740993' }),
        app({
          social: 'youtube',
          packageName: 'com.example.other',
          watchCount: '2',
        }),
        app({ social: 'facebook', packageName: 'com.example.unknown' }),
      ]),
    ]),
  ).toMatchObject({
    watches: '9007199254740995',
    completions: null,
    playbackMs: null,
  });
});
