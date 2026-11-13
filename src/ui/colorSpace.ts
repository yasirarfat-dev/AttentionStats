/** HSV picker coordinates: hue wraps; saturation/value clamp at the canvas edges. */
export function hsvToHex(
  hue: number,
  saturation: number,
  value: number,
): string {
  const h = (((hue % 360) + 360) % 360) / 60;
  const s = Math.max(0, Math.min(1, saturation));
  const v = Math.max(0, Math.min(1, value));
  const c = v * s,
    x = c * (1 - Math.abs((h % 2) - 1)),
    m = v - c;
  const rgb =
    h < 1
      ? [c, x, 0]
      : h < 2
        ? [x, c, 0]
        : h < 3
          ? [0, c, x]
          : h < 4
            ? [0, x, c]
            : h < 5
              ? [x, 0, c]
              : [c, 0, x];
  return (
    '#' +
    rgb
      .map(n =>
        Math.round((n + m) * 255)
          .toString(16)
          .padStart(2, '0'),
      )
      .join('')
  );
}
export function hexToHsv(hex: string) {
  const [r, g, b] = [1, 3, 5].map(
    i => parseInt(hex.slice(i, i + 2), 16) / 255,
  ) as [number, number, number];
  const max = Math.max(r, g, b),
    min = Math.min(r, g, b),
    d = max - min;
  const hue =
    d === 0
      ? 0
      : max === r
        ? 60 * (((g - b) / d + 6) % 6)
        : max === g
          ? 60 * ((b - r) / d + 2)
          : 60 * ((r - g) / d + 4);
  return { hue, saturation: max === 0 ? 0 : d / max, value: max };
}
