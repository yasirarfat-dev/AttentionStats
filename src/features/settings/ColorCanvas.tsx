import { useEffect, useId, useRef, useState, type ComponentRef } from 'react';
import { View, type GestureResponderEvent } from 'react-native';
import Svg, {
  Defs,
  LinearGradient,
  Stop,
  Rect,
  Circle,
} from 'react-native-svg';
import { hsvToHex, hexToHsv } from '../../ui/colorSpace';

/** Page coordinates and a fresh measured surface avoid child-relative SVG touch coordinates. */
function useCanvasDrag(
  select: (x: number, y: number) => void,
  onDragChange: (dragging: boolean) => void,
) {
  const ref = useRef<ComponentRef<typeof View>>(null);
  const origin = useRef<{
    x: number;
    y: number;
    width: number;
    height: number;
  } | null>(null);
  const last = useRef({ x: 0, y: 0 });
  const choose = useRef(select);
  choose.current = select;
  const move = (event: GestureResponderEvent) => {
    last.current = { x: event.nativeEvent.pageX, y: event.nativeEvent.pageY };
    const bounds = origin.current;
    if (bounds)
      choose.current(
        (last.current.x - bounds.x) / bounds.width,
        (last.current.y - bounds.y) / bounds.height,
      );
  };
  return {
    ref,
    onStartShouldSetResponder: () => true,
    onMoveShouldSetResponder: () => true,
    onResponderTerminationRequest: () => false,
    onResponderGrant: (event: GestureResponderEvent) => {
      onDragChange(true);
      origin.current = null;
      last.current = { x: event.nativeEvent.pageX, y: event.nativeEvent.pageY };
      ref.current?.measure((_x, _y, width, height, pageX, pageY) => {
        if (width <= 0 || height <= 0) return;
        origin.current = { x: pageX, y: pageY, width, height };
        choose.current(
          (last.current.x - pageX) / width,
          (last.current.y - pageY) / height,
        );
      });
    },
    onResponderMove: move,
    onResponderRelease: (event: GestureResponderEvent) => {
      move(event);
      onDragChange(false);
    },
    onResponderTerminate: () => onDragChange(false),
    onTouchStart: () => onDragChange(true),
    onTouchEnd: () => onDragChange(false),
    onTouchCancel: () => onDragChange(false),
  };
}

/** Touch surfaces retain the drag within the canvas rather than scrolling Settings. */
export function ColorCanvas({
  color,
  onChange,
  onDragChange,
}: {
  color: string;
  onChange: (color: string) => void;
  onDragChange: (dragging: boolean) => void;
}) {
  const id = useId().replace(/[^a-z0-9]/gi, '');
  const hsv = hexToHsv(color);
  // Keep the chosen hue at black/white so the hue rail remains usable at either edge.
  const [heldHue, setHeldHue] = useState(hsv.hue);
  const hue = hsv.saturation > 0 && hsv.value > 0 ? hsv.hue : heldHue;
  const [width, setWidth] = useState(1);
  const [railWidth, setRailWidth] = useState(1);
  const height = 190;
  useEffect(() => () => onDragChange(false), [onDragChange]);
  const canvasDrag = useCanvasDrag(
    (x, y) => onChange(hsvToHex(hue, x, 1 - y)),
    onDragChange,
  );
  const hueDrag = useCanvasDrag(x => {
    const next = Math.max(0, Math.min(359.99, x * 360));
    setHeldHue(next);
    onChange(hsvToHex(next, hsv.saturation, hsv.value));
  }, onDragChange);
  return (
    <View style={{ gap: 12 }}>
      <View
        {...canvasDrag}
        accessible
        accessibilityLabel="Color canvas. Drag to select saturation and brightness. Hex and RGB controls follow."
        onLayout={event => setWidth(event.nativeEvent.layout.width)}
        style={{ height, borderRadius: 16, overflow: 'hidden' }}
      >
        <Svg width="100%" height="100%" pointerEvents="none">
          <Defs>
            <LinearGradient id={`${id}white`} x1="0%" y1="0%" x2="100%" y2="0%">
              <Stop offset="0" stopColor="#fff" />
              <Stop offset="1" stopColor="#fff" stopOpacity="0" />
            </LinearGradient>
            <LinearGradient id={`${id}black`} x1="0%" y1="0%" x2="0%" y2="100%">
              <Stop offset="0" stopColor="#000" stopOpacity="0" />
              <Stop offset="1" stopColor="#000" />
            </LinearGradient>
          </Defs>
          <Rect width="100%" height="100%" fill={hsvToHex(hue, 1, 1)} />
          <Rect width="100%" height="100%" fill={`url(#${id}white)`} />
          <Rect width="100%" height="100%" fill={`url(#${id}black)`} />
          <Circle
            cx={Math.max(9, Math.min(width - 9, hsv.saturation * width))}
            cy={Math.max(9, Math.min(height - 9, (1 - hsv.value) * height))}
            r={8}
            stroke="#000"
            strokeWidth={4}
            fill={color}
          />
          <Circle
            cx={Math.max(9, Math.min(width - 9, hsv.saturation * width))}
            cy={Math.max(9, Math.min(height - 9, (1 - hsv.value) * height))}
            r={8}
            stroke="#fff"
            strokeWidth={2}
            fill="none"
          />
        </Svg>
      </View>
      <View
        {...hueDrag}
        accessibilityRole="adjustable"
        accessibilityLabel="Hue"
        accessibilityValue={{ min: 0, max: 359, now: Math.round(hue) }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={event => {
          const next =
            (hue + (event.nativeEvent.actionName === 'increment' ? 5 : 355)) %
            360;
          setHeldHue(next);
          onChange(hsvToHex(next, hsv.saturation, hsv.value));
        }}
        onLayout={event => setRailWidth(event.nativeEvent.layout.width)}
        style={{ height: 48, justifyContent: 'center' }}
      >
        <Svg width="100%" height="32" pointerEvents="none">
          <Defs>
            <LinearGradient id={`${id}hue`} x1="0%" y1="0%" x2="100%" y2="0%">
              {[0, 60, 120, 180, 240, 300, 360].map(h => (
                <Stop key={h} offset={h / 360} stopColor={hsvToHex(h, 1, 1)} />
              ))}
            </LinearGradient>
          </Defs>
          <Rect width="100%" height="32" rx={16} fill={`url(#${id}hue)`} />
          <Circle
            cx={Math.max(10, Math.min(railWidth - 10, (hue / 360) * railWidth))}
            cy={16}
            r={10}
            fill={hsvToHex(hue, 1, 1)}
            stroke="#fff"
            strokeWidth={3}
          />
        </Svg>
      </View>
    </View>
  );
}
