import tokens from '../paletteTokens.json';
import { customPalette, contrast } from '../customPalette';

test('arbitrary RGB colors retain readable accent, button and hero roles in both themes', () => {
  for (const red of [0, 64, 128, 192, 255])
    for (const green of [0, 64, 128, 192, 255])
      for (const blue of [0, 64, 128, 192, 255]) {
        const color =
          '#' +
          [red, green, blue].map(n => n.toString(16).padStart(2, '0')).join('');
        for (const dark of [false, true]) {
          const roles = customPalette(color, dark);
          const base = tokens.base[dark ? 'dark' : 'light'];
          for (const background of [
            base.surface,
            base.surfaceAlt,
            roles.accentSoft!,
          ])
            expect(contrast(roles.accent!, background)).toBeGreaterThanOrEqual(
              4.5,
            );
          expect(
            contrast(roles.onAccent!, roles.accentFill!),
          ).toBeGreaterThanOrEqual(4.5);
          expect(contrast(roles.onHero!, roles.hero!)).toBeGreaterThanOrEqual(
            4.5,
          );
          expect(
            contrast(roles.heroMuted!, roles.hero!),
          ).toBeGreaterThanOrEqual(4.5);
        }
      }
});
test('invalid custom colors preserve the selected starting palette', () => {
  for (const value of ['', '#fff', 'red', '#zzzzzz'])
    expect(customPalette(value, false)).toEqual({});
});
