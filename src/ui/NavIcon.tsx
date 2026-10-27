import Svg, { Path } from 'react-native-svg';

export type NavSymbol =
  'today' | 'history' | 'patterns' | 'protect' | 'settings';
const paths: Record<NavSymbol, string> = {
  today:
    'M12 3v2m0 14v2M3 12h2m14 0h2M5.6 5.6 7 7m10 10 1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  history: 'M3 5v5h5M3.7 10a8.5 8.5 0 1 1 .8 6M12 7v5l3 2',
  patterns: 'M4 20V10m8 10V4m8 16v-8M3 20h18',
  protect: 'M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6l-8-3Zm-4 9 3 3 5-6',
  settings:
    'M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3Zm7 9a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
};
export function NavIcon({
  name,
  color,
  size = 24,
}: {
  name: NavSymbol;
  color: string;
  size?: number;
}) {
  return (
    <Svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      importantForAccessibility="no"
    >
      <Path
        d={paths[name]}
        fill="none"
        stroke={color}
        strokeWidth={1.7}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Svg>
  );
}
