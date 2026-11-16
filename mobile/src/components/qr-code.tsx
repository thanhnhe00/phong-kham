import { useMemo } from 'react';
import Svg, { Rect } from 'react-native-svg';
import { qrMatrix } from '@/lib/qr';
import { colors } from '@/lib/theme';

export function QrCode({ value, size = 220 }: { value: string; size?: number }) {
  const m = useMemo(() => qrMatrix(value), [value]);
  const n = m.length;
  return (
    <Svg width={size} height={size} viewBox={`-2 -2 ${n + 4} ${n + 4}`} accessibilityLabel="Mã QR check-in">
      <Rect x={-2} y={-2} width={n + 4} height={n + 4} fill="#FFFFFF" />
      {m.flatMap((row, r) => row.map((on, c) => (on ? <Rect key={`${r}-${c}`} x={c} y={r} width={1} height={1} fill={colors.ink} /> : null)))}
    </Svg>
  );
}
