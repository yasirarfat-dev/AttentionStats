import tokens from './paletteTokens.json';

/** Arbitrary accents are adapted to readable semantic roles; status colors retain their meaning. */
export function blend(a: string, b: string, amount: number): string {
  const channels = [1, 3, 5].map(i =>
    Math.round(
      parseInt(a.slice(i, i + 2), 16) * (1 - amount) +
        parseInt(b.slice(i, i + 2), 16) * amount,
    ),
  );
  return '#' + channels.map(n => n.toString(16).padStart(2, '0')).join('');
}
export function luminance(hex: string): number {
  return [1, 3, 5]
    .map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(n => (n <= 0.04045 ? n / 12.92 : ((n + 0.055) / 1.055) ** 2.4))
    .reduce((sum, n, i) => sum + n * [0.2126, 0.7152, 0.0722][i]!, 0);
}
export function contrast(a: string, b: string): number {
  const x = luminance(a),
    y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}
function readable(color: string, against: string | string[], toward: string) {
  for (let i = 0; i <= 100; i++) {
    const candidate = blend(color, toward, i / 100);
    if (
      (Array.isArray(against) ? against : [against]).every(
        background => contrast(candidate, background) >= 4.5,
      )
    )
      return candidate;
  }
  return toward;
}
export function customPalette(color: string, dark: boolean) {
  if (!/^#[0-9a-f]{6}$/i.test(color)) return {};
  const soft = blend(color, dark ? '#141518' : '#ffffff', dark ? 0.83 : 0.92);
  const accent = readable(
    color,
    [
      soft,
      tokens.base[dark ? 'dark' : 'light'].surfaceAlt,
      tokens.base[dark ? 'dark' : 'light'].surface,
    ],
    dark ? '#ffffff' : '#000000',
  );
  const fill = readable(color, '#ffffff', '#000000');
  const hero = blend(fill, '#08090b', 0.45);
  return {
    accent,
    accentFill: fill,
    accentSoft: soft,
    onAccent: '#ffffff',
    hero,
    onHero: '#ffffff',
    heroMuted: '#eeeeee',
    tint: soft,
  };
}
