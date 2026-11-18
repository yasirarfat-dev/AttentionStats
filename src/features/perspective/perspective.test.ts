import {
  achievements,
  distancePerspective,
  PERSPECTIVE_ASSUMPTIONS,
  quantity,
} from './perspective';

// Identical inputs and expected strings are asserted in PerspectiveTests.kt.
const sharedSamples: [number, number, string[]][] = [
  [5, 0, ['read 5 pages', 'cycled 1.3 km']],
  [
    30,
    2,
    [
      'read 27 pages',
      'run a full 5 km',
      'done 1.5 20-minute workouts',
      'taken 2 language lessons',
    ],
  ],
  [
    68,
    0,
    [
      'read all of The Little Prince',
      'walked 5.7 km (about 7,400 steps)',
      'cycled 18 km',
      'run 2.3 × 5 km',
    ],
  ],
  [
    160,
    1,
    [
      'read all of The Great Gatsby',
      'cycled 43 km',
      'run 5.3 × 5 km',
      'done 8 20-minute workouts',
    ],
  ],
  [
    480,
    0,
    [
      'read all of Pride and Prejudice',
      'walked 40 km (about 52,000 steps)',
      'cycled 128 km',
      'run 16 × 5 km',
    ],
  ],
  [
    488000,
    7,
    [
      'read 1,000 books the length of Pride and Prejudice',
      'practised an instrument 16,267 times',
      'walked 40,667 km (about 52,866,700 steps)',
      'cycled 130,133 km',
    ],
  ],
];
test.each(sharedSamples)(
  'shared output: %s minutes, seed %s',
  (minutes, seed, expected) => {
    expect(
      achievements(minutes * 60_000, 4, seed).map(item => item.text),
    ).toEqual(expected);
  },
);
const all = (minutes: number) => achievements(minutes * 60_000, 100, 0);
const text = (minutes: number, key: string) =>
  all(minutes).find(item => item.key === key)?.text;

test('book qualification, longest selection and repetition boundaries', () => {
  expect(text(57.799, 'pages')).toBe('read 53 pages');
  expect(text(57.8, 'book')).toBe('read all of The Little Prince');
  expect(text(68, 'book')).toBe('read all of The Little Prince');
  expect(text(78.2, 'book')).toBe('read The Little Prince 1.2 times');
  expect(text(159.799, 'book')).toBe('read all of The Alchemist');
  expect(text(159.8, 'book')).toBe('read all of The Great Gatsby');
  expect(text(160, 'book')).toBe('read all of The Great Gatsby');
  expect(text(1000, 'book')).toBe('read Pride and Prejudice 2 times');
  expect(text(1464, 'book')).toBe(
    'read 3 books the length of Pride and Prejudice',
  );
  expect(text(488000, 'book')).toBe(
    'read 1,000 books the length of Pride and Prejudice',
  );
});

test('eligibility and singular bands use numeric achievement quantities', () => {
  expect(text(5, 'walk')).toBeUndefined();
  expect(text(6, 'walk')).toBe('walked 0.5 km (about 700 steps)');
  expect(text(7, 'walk')).toBe('walked 0.6 km (about 800 steps)');
  expect(text(25.499, 'run')).toBeUndefined();
  expect(text(25.5, 'run')).toBe('run a full 5 km');
  expect(text(34.5, 'run')).toBe('run 1.2 × 5 km');
  expect(text(16.999, 'workout')).toBeUndefined();
  expect(text(17, 'workout')).toBe('done a 20-minute workout');
  expect(text(23, 'workout')).toBe('done 1.2 20-minute workouts');
  expect(text(12.75, 'language')).toBe('taken a language lesson');
  expect(text(51, 'course')).toBe('finished an hour of an online course');
  expect(text(8.5, 'meditate')).toBe('meditated for 10 minutes');
  expect(text(25.5, 'instrument')).toBe(
    'practised an instrument for half an hour',
  );
});

test('first line stays fixed while eligible candidates rotate daily', () => {
  for (const minutes of [5, 30, 60, 160, 480]) {
    const base = all(minutes);
    const candidates = base.slice(1);
    for (let seed = -10; seed < 20; seed++) {
      const start =
        ((seed % candidates.length) + candidates.length) % candidates.length;
      const result = achievements(minutes * 60_000, 100, seed);
      expect(result).toEqual([
        base[0],
        ...candidates.slice(start),
        ...candidates.slice(0, start),
      ]);
      expect(result).toEqual(achievements(minutes * 60_000, 100, seed));
      expect(achievements(minutes * 60_000, 2, seed)).toEqual(
        result.slice(0, 2),
      );
      expect(new Set(result.map(item => item.key)).size).toBe(result.length);
      expect(
        result.some(item => item.key === 'book') &&
          result.some(item => item.key === 'pages'),
      ).toBe(false);
    }
  }
  for (const ms of [-1, 0, 299999, NaN, Infinity])
    expect(achievements(ms, 4, 0)).toEqual([]);
  expect(achievements(300000, 0, 0)).toEqual([]);
  expect(achievements(300000, -1, 0)).toEqual([]);
});

test('copy motivates without shaming and has exact accessibility labels', () => {
  for (const minutes of [5, 15, 60, 160, 480, 488000])
    for (const item of all(minutes)) {
      expect(item.accessibilityLabel).toBe(
        `In that time you could have ${item.text}.`,
      );
      expect(item.text).not.toMatch(
        /wasted|should|addicted|guilt|film|nap|meal|call/i,
      );
    }
  expect(PERSPECTIVE_ASSUMPTIONS).not.toMatch(/wasted|should|addicted|guilt/i);
});

// Legacy distance formatting remains unchanged.
test.each([
  [0.25, 'a quarter of'],
  [0.4, 'half'],
  [0.6, 'three quarters of'],
  [0.85, '1'],
  [1.15, '1.2'],
  [10.5, '11'],
])('distance quantity %s', (q, expected) => {
  expect(quantity(q as number)).toBe(expected);
});

const distance = (metres: number) => distancePerspective(metres / 0.11);

test('distance threshold and the three phrasing bands', () => {
  expect(distancePerspective(54)).toBeNull();
  expect(distancePerspective(-1)).toBeNull();
  expect(distancePerspective(Infinity)).toBeNull();
  expect(distance(6)?.text).toBe(
    '6 m of thumb scrolling · over half the length of a city bus',
  );
  expect(distance(10.2)?.text).toContain('as long as a city bus');
  expect(distance(13.8)?.text).toContain('times the length of a city bus');
  expect(distancePerspective(380)?.text).toBe(
    '42 m of thumb scrolling · 1.4 times the length of a blue whale',
  );
});

test.each([
  [6, 'bus'],
  [15, 'whale'],
  [46.5, 'liberty'],
  [52.5, 'pitch'],
  [165, 'eiffel'],
  [221.5, 'empire'],
  [414, 'burj'],
  [4424.5, 'everest'],
  [21097.5, 'marathon'],
])('selects the largest eligible landmark at %s metres', (metres, key) => {
  expect(distance(metres)?.key).toBe(key);
});

test('marathon and Everest use their special dimension wording', () => {
  expect(distance(22000)?.text).toContain('over half the length of a marathon');
  expect(distance(42195)?.text).toContain('a whole marathon');
  expect(distance(84390)?.text).toContain('2 times the length of a marathon');
  expect(distance(8849)?.text).toContain('as tall as Mount Everest');
  expect(distance(17698)?.text).toContain(
    '2 times the height of Mount Everest',
  );
});

test('long distances read in kilometres and lengths use long wording', () => {
  expect(distance(8849)?.text).toBe(
    '8.8 km of thumb scrolling · as tall as Mount Everest',
  );
  expect(distance(30)?.text).toContain('as long as a blue whale');
});
