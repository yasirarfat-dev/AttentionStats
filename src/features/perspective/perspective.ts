import type { IconName } from '../../ui/Icon';

export type Perspective = {
  key: string;
  icon: IconName;
  text: string;
  accessibilityLabel: string;
};

// Keep the books, assumptions and selection aligned with Perspective.kt.
const BOOKS: readonly [string, number][] = [
  ['The Little Prince', 17000],
  ['The Old Man and the Sea', 27000],
  ['Animal Farm', 30000],
  ['The Alchemist', 39000],
  ['The Great Gatsby', 47000],
  ["Harry Potter and the Philosopher's Stone", 77000],
  ['Nineteen Eighty-Four', 89000],
  ['The Hobbit', 95000],
  ['To Kill a Mockingbird', 100000],
  ['Pride and Prejudice', 122000],
];

const LANDMARKS: readonly [string, number, string][] = [
  ['bus', 12, 'a city bus'],
  ['whale', 30, 'a blue whale'],
  ['liberty', 93, 'the Statue of Liberty'],
  ['pitch', 105, 'a football pitch'],
  ['eiffel', 330, 'the Eiffel Tower'],
  ['empire', 443, 'the Empire State Building'],
  ['burj', 828, 'the Burj Khalifa'],
  ['marathon', 42195, 'a marathon'],
  ['everest', 8849, 'Mount Everest'],
];

/** Landmarks compared by length rather than height. */
const LENGTHS = new Set(['bus', 'whale', 'pitch', 'marathon']);

export const PERSPECTIVE_ASSUMPTIONS =
  "These are estimates of possibilities, not measured activities: reading at 250 words per minute, 275 words per book page (1.1 min per page). Approximate book word counts: The Little Prince 17,000; The Old Man and the Sea 27,000; Animal Farm 30,000; The Alchemist 39,000; The Great Gatsby 47,000; Harry Potter and the Philosopher's Stone 77,000; Nineteen Eighty-Four 89,000; The Hobbit 95,000; To Kill a Mockingbird 100,000; Pride and Prejudice 122,000. Reading minutes = words / 250. Walking at 5 km/h (12 min per km), 1,300 steps per km; cycling at 16 km/h (3.75 min per km); 30 min per 5 km run; 20 min per workout; 15 min per language lesson; 60 min per hour of online course; 10 min per meditation; 30 min per instrument practice. Thumb travel assumes one 11 cm swipe per counted short video; actual swipes vary. Landmark estimates: city bus 12 m, blue whale 30 m, Statue of Liberty 93 m, football pitch 105 m, Eiffel Tower 330 m, Empire State Building 443 m, Burj Khalifa 828 m, marathon 42,195 m and Mount Everest 8,849 m. Recorded short-video time is on-screen time, not confirmed watching.";

export function quantity(q: number): string {
  if (q < 0.3) return 'a quarter of';
  if (q < 0.4) return 'a third of';
  if (q < 0.6) return 'half';
  if (q < 0.85) return 'three quarters of';
  if (q < 1.15) return '1';
  if (q < 10) return String(Math.round(q * 10) / 10);
  return Math.round(q).toLocaleString('en-US');
}

// Achievement quantities are numeric; distance retains its existing fraction wording.
function achievementQuantity(q: number): string {
  return q < 10 ? String(Math.round(q * 10) / 10) : integer(Math.round(q));
}

function integer(n: number): string {
  return n.toLocaleString('en-US', { maximumFractionDigits: 0 });
}

export function achievements(
  ms: number,
  count: number,
  seed: number,
): Perspective[] {
  if (!Number.isFinite(ms) || ms < 300_000 || count < 1) return [];
  const minutes = ms / 60_000;
  const line = (key: string, icon: IconName, text: string): Perspective => ({
    key,
    icon,
    text,
    accessibilityLabel: `In that time you could have ${text}.`,
  });
  const book =
    minutes >= 30
      ? BOOKS.filter(([, words]) => minutes / (words / 250) >= 0.85).slice(
          -1,
        )[0]
      : undefined;
  let first: Perspective;
  if (book) {
    const [title, words] = book;
    const ratio = minutes / (words / 250);
    first = line(
      'book',
      'layers',
      ratio < 1.15
        ? `read all of ${title}`
        : ratio < 3
          ? `read ${title} ${achievementQuantity(ratio)} times`
          : `read ${integer(Math.floor(ratio))} books the length of ${title}`,
    );
  } else {
    first = line(
      'pages',
      'layers',
      `read ${integer(Math.round(minutes / 1.1))} pages`,
    );
  }
  const candidates: Perspective[] = [];
  const km = minutes / 12;
  if (km >= 0.5)
    candidates.push(
      line(
        'walk',
        'sun',
        `walked ${achievementQuantity(km)} km (about ${integer(Math.round((km * 1300) / 100) * 100)} steps)`,
      ),
    );
  const cycleKm = minutes / 3.75;
  if (cycleKm >= 1)
    candidates.push(
      line('cycle', 'bolt', `cycled ${achievementQuantity(cycleKm)} km`),
    );
  const add = (
    key: string,
    icon: IconName,
    unitMinutes: number,
    singular: string,
    plural: (q: string) => string,
  ) => {
    const n = minutes / unitMinutes;
    if (n >= 0.85)
      candidates.push(
        line(key, icon, n < 1.15 ? singular : plural(achievementQuantity(n))),
      );
  };
  add('run', 'bolt', 30, 'run a full 5 km', q => `run ${q} × 5 km`);
  add(
    'workout',
    'heart',
    20,
    'done a 20-minute workout',
    q => `done ${q} 20-minute workouts`,
  );
  add(
    'language',
    'code',
    15,
    'taken a language lesson',
    q => `taken ${q} language lessons`,
  );
  add(
    'course',
    'chart',
    60,
    'finished an hour of an online course',
    q => `finished ${q} hours of an online course`,
  );
  add(
    'meditate',
    'spark',
    10,
    'meditated for 10 minutes',
    q => `meditated ${q} times for 10 minutes`,
  );
  add(
    'instrument',
    'play',
    30,
    'practised an instrument for half an hour',
    q => `practised an instrument ${q} times`,
  );
  // Pages are already first when no book qualifies; never repeat them or pair them with a book.
  const size = candidates.length;
  const start = Number.isFinite(seed)
    ? ((Math.trunc(seed) % size) + size) % size
    : 0;
  return [
    first,
    ...candidates.slice(start),
    ...candidates.slice(0, start),
  ].slice(0, Math.floor(count));
}

export function distancePerspective(videos: number): Perspective | null {
  const metres = videos * 0.11;
  if (!Number.isFinite(metres) || metres < 6) return null;
  const landmark = LANDMARKS.filter(([, size]) => metres >= 0.5 * size).sort(
    (a, b) => b[1] - a[1],
  )[0];
  if (!landmark) return null;
  const [key, size, phrase] = landmark;
  const ratio = metres / size;
  const long = LENGTHS.has(key);
  const dimension = long ? 'length' : 'height';
  const comparison =
    ratio >= 1.15
      ? `${quantity(ratio)} times the ${dimension} of ${phrase}`
      : ratio >= 0.85
        ? key === 'marathon'
          ? 'a whole marathon'
          : `as ${long ? 'long' : 'tall'} as ${phrase}`
        : `over half the ${dimension} of ${phrase}`;
  const travelled =
    metres >= 1000 ? `${quantity(metres / 1000)} km` : `${quantity(metres)} m`;
  const text = `${travelled} of thumb scrolling · ${comparison}`;
  return {
    key,
    icon: 'phone',
    text,
    accessibilityLabel: `Estimated thumb travel: ${text}.`,
  };
}
