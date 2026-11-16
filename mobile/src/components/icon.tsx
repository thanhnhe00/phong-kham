import Svg, { Circle, Path, Rect } from 'react-native-svg';

// Biểu tượng nét 2px, cùng bộ hình với bản thiết kế.
const shapes = {
  cal: (
    <>
      <Rect x={3} y={5} width={18} height={16} rx={2} />
      <Path d="M3 10h18M8 3v4M16 3v4" />
    </>
  ),
  calplus: (
    <>
      <Rect x={3} y={5} width={18} height={16} rx={2} />
      <Path d="M3 10h18M8 3v4M16 3v4M12 13v5M9.5 15.5h5" />
    </>
  ),
  doc: (
    <>
      <Path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <Path d="M14 3v5h5M9 13h6M9 17h4" />
    </>
  ),
  rx: <Path d="M7 3h6a3.5 3.5 0 0 1 0 7H7zM7 10v11M11 10l8 11M19 14l-6 7" />,
  card: (
    <>
      <Rect x={3} y={6} width={18} height={13} rx={2} />
      <Path d="M3 10h18M7 15h4" />
    </>
  ),
  flask: <Path d="M9 3h6M10 3v6l-5 9a2 2 0 0 0 1.8 3h10.4a2 2 0 0 0 1.8-3l-5-9V3" />,
  clock: (
    <>
      <Circle cx={12} cy={12} r={9} />
      <Path d="M12 7v5l3 2" />
    </>
  ),
  bell: (
    <>
      <Path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <Path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" />
    </>
  ),
  home: <Path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" />,
  user: (
    <>
      <Circle cx={12} cy={8} r={4} />
      <Path d="M4 21a8 8 0 0 1 16 0" />
    </>
  ),
  down: <Path d="M6 9l6 6 6-6" />,
  right: <Path d="M9 6l6 6-6 6" />,
  left: <Path d="M15 18l-6-6 6-6" />,
  check: <Path d="M5 12l5 5 9-10" />,
  plus: <Path d="M12 5v14M5 12h14" />,
  x: <Path d="M6 6l12 12M18 6L6 18" />,
  qr: (
    <>
      <Rect x={3} y={3} width={7} height={7} rx={1} />
      <Rect x={14} y={3} width={7} height={7} rx={1} />
      <Rect x={3} y={14} width={7} height={7} rx={1} />
      <Path d="M14 14h3v3M21 14v7h-7M17 21v-1" />
    </>
  ),
  phone: <Path d="M5 3h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 5a2 2 0 0 1 2-2" />,
  alert: (
    <>
      <Path d="M12 3l9.5 17h-19z" />
      <Path d="M12 10v4M12 17.5v.01" />
    </>
  ),
  link: <Path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" />,
  lock: (
    <>
      <Rect x={4} y={10} width={16} height={11} rx={2} />
      <Path d="M8 10V7a4 4 0 0 1 8 0v3" />
    </>
  ),
  logout: <Path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11" />,
  sun: (
    <>
      <Circle cx={12} cy={12} r={4} />
      <Path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  pin: (
    <>
      <Path d="M12 21s7-6.5 7-12a7 7 0 0 0-14 0c0 5.5 7 12 7 12z" />
      <Circle cx={12} cy={9} r={2.5} />
    </>
  ),
  file: (
    <>
      <Path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <Path d="M14 3v5h5" />
    </>
  ),
};

export type IconName = keyof typeof shapes;

export function Icon({ name, size = 20, color = '#11231E', stroke = 2 }: { name: IconName; size?: number; color?: string; stroke?: number }) {
  return (
    <Svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={stroke}
      strokeLinecap="round"
      strokeLinejoin="round"
      accessible={false}
    >
      {shapes[name]}
    </Svg>
  );
}
