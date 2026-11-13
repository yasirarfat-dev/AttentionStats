import { hexToHsv, hsvToHex } from '../colorSpace';
import { contrast, scopedPalette } from '../customPalette';
import tokens from '../paletteTokens.json';

test('picker covers primary hues, white/black edges and clamps finger positions', () => {
  expect([0, 60, 120, 180, 240, 300, 360].map(h => hsvToHex(h, 1, 1))).toEqual([
    '#ff0000',
    '#ffff00',
    '#00ff00',
    '#00ffff',
    '#0000ff',
    '#ff00ff',
    '#ff0000',
  ]);
  expect(hsvToHex(120, -1, 2)).toBe('#ffffff');
  expect(hsvToHex(240, 2, -1)).toBe('#000000');
  for (const r of [0, 64, 128, 192, 255])
    for (const g of [0, 64, 128, 192, 255])
      for (const b of [0, 64, 128, 192, 255]) {
        const hex =
          '#' + [r, g, b].map(n => n.toString(16).padStart(2, '0')).join('');
        const hsv = hexToHsv(hex);
        expect(hsvToHex(hsv.hue, hsv.saturation, hsv.value)).toBe(hex);
      }
});

test('independent color scopes preserve background and readable ink across difficult choices', () => {
  const colors = [
    '#000000',
    '#ffffff',
    '#ffff00',
    '#00ff00',
    '#0000ff',
    '#757575',
    '#123456',
  ];
  for (const base of [tokens.base.light, tokens.base.dark])
    for (const background of colors)
      for (const foreground of colors)
        for (const accent of ['', '#ff0000', '#000000', '#ffffff']) {
          const result = scopedPalette(base, foreground, background, accent);
          expect(result.canvas).toBe(background);
          for (const panel of [
            result.canvas,
            result.surface,
            result.surfaceAlt,
            result.raised,
          ]) {
            expect(contrast(result.text, panel)).toBeGreaterThanOrEqual(4.5);
            expect(contrast(result.muted, panel)).toBeGreaterThanOrEqual(4.5);
            expect(contrast(result.accent, panel)).toBeGreaterThanOrEqual(4.5);
          }
          expect(result.danger).toBe(base.danger);
          expect(result.warn).toBe(base.warn);
          expect(
            contrast(result.onAccent, result.accentFill),
          ).toBeGreaterThanOrEqual(4.5);
        }
});
