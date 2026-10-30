import Svg, { Circle, Path, Rect } from 'react-native-svg';

export type IconName =
  | 'shield'
  | 'lock'
  | 'code'
  | 'phone'
  | 'noads'
  | 'cloudoff'
  | 'check'
  | 'chevron'
  | 'clock'
  | 'play'
  | 'pause'
  | 'bell'
  | 'block'
  | 'eye'
  | 'battery'
  | 'chart'
  | 'bug'
  | 'heart'
  | 'trash'
  | 'info'
  | 'spark'
  | 'layers';

/** Simple 24px line icons; colour comes from the caller so they follow the theme. */
export function Icon({
  name,
  size = 22,
  color,
}: {
  name: IconName;
  size?: number;
  color: string;
}) {
  const stroke = {
    stroke: color,
    strokeWidth: 1.8,
    fill: 'none',
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  };
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessible={false}>
      {name === 'shield' && (
        <Path
          d="M12 3l7 3v5c0 4.6-3 8.4-7 10-4-1.6-7-5.4-7-10V6l7-3z"
          {...stroke}
        />
      )}
      {name === 'lock' && (
        <>
          <Rect x={5} y={10.5} width={14} height={10} rx={2.5} {...stroke} />
          <Path d="M8 10.5V8a4 4 0 018 0v2.5" {...stroke} />
        </>
      )}
      {name === 'code' && <Path d="M9 7l-5 5 5 5M15 7l5 5-5 5" {...stroke} />}
      {name === 'phone' && (
        <>
          <Rect x={7} y={2.5} width={10} height={19} rx={2.5} {...stroke} />
          <Path d="M11 18.5h2" {...stroke} />
        </>
      )}
      {name === 'noads' && (
        <>
          <Circle cx={12} cy={12} r={8.5} {...stroke} />
          <Path d="M6 6l12 12" {...stroke} />
        </>
      )}
      {name === 'cloudoff' && (
        <Path
          d="M7.5 18h9a4 4 0 00.9-7.9A6 6 0 006.2 9.3 4.4 4.4 0 007.5 18zM4 4l16 16"
          {...stroke}
        />
      )}
      {name === 'check' && <Path d="M5 12.5l4.5 4.5L19 7.5" {...stroke} />}
      {name === 'chevron' && <Path d="M9.5 6l6 6-6 6" {...stroke} />}
      {name === 'clock' && (
        <>
          <Circle cx={12} cy={12} r={8.5} {...stroke} />
          <Path d="M12 7.5V12l3 2" {...stroke} />
        </>
      )}
      {name === 'play' && <Path d="M8 5.5v13l10.5-6.5L8 5.5z" {...stroke} />}
      {name === 'pause' && <Path d="M9 6v12M15 6v12" {...stroke} />}
      {name === 'bell' && (
        <Path
          d="M6 16.5V11a6 6 0 0112 0v5.5l1.5 1.5h-15L6 16.5zM10 20.5h4"
          {...stroke}
        />
      )}
      {name === 'block' && (
        <>
          <Rect x={4} y={4} width={16} height={16} rx={4} {...stroke} />
          <Path d="M8 8l8 8" {...stroke} />
        </>
      )}
      {name === 'eye' && (
        <>
          <Path
            d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"
            {...stroke}
          />
          <Circle cx={12} cy={12} r={3} {...stroke} />
        </>
      )}
      {name === 'battery' && (
        <>
          <Rect x={3} y={7.5} width={16} height={9} rx={2} {...stroke} />
          <Path d="M21 10.5v3M6.5 10.5v3M9.5 10.5v3" {...stroke} />
        </>
      )}
      {name === 'chart' && (
        <Path d="M5 19V11M10 19V6M15 19v-5M20 19V9" {...stroke} />
      )}
      {name === 'bug' && (
        <>
          <Rect x={8} y={7} width={8} height={12} rx={4} {...stroke} />
          <Path
            d="M12 7V5M5 10h3M16 10h3M5 15h3M16 15h3M9.5 4.5L8 3M14.5 4.5L16 3"
            {...stroke}
          />
        </>
      )}
      {name === 'heart' && (
        <Path
          d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0112 7.3 4.3 4.3 0 0119.5 10c0 5.4-7.5 10-7.5 10z"
          {...stroke}
        />
      )}
      {name === 'trash' && (
        <Path d="M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13" {...stroke} />
      )}
      {name === 'info' && (
        <>
          <Circle cx={12} cy={12} r={8.5} {...stroke} />
          <Path d="M12 11v5.5M12 7.8v.2" {...stroke} />
        </>
      )}
      {name === 'spark' && (
        <Path
          d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M18 6l-2.5 2.5M8.5 15.5L6 18"
          {...stroke}
        />
      )}
      {name === 'layers' && (
        <Path
          d="M12 4l8.5 4.5L12 13 3.5 8.5 12 4zM3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5"
          {...stroke}
        />
      )}
    </Svg>
  );
}
