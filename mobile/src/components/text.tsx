import { Text as RNText, type TextProps, type TextStyle } from 'react-native';
import { colors, fonts, type Weight } from '@/lib/theme';

interface Props extends TextProps {
  size?: number;
  weight?: Weight;
  color?: string;
  center?: boolean;
  /** Số dùng chữ số cùng độ rộng (giờ, tiền) */
  tabular?: boolean;
}

/** Chữ Be Vietnam Pro: chọn đúng tệp phông theo độ đậm (RN không tự tổng hợp đậm cho phông nạp ngoài). */
export function T({ size = 14, weight = 400, color = colors.ink, center, tabular, style, ...rest }: Props) {
  const base: TextStyle = {
    fontFamily: fonts[weight],
    fontSize: size,
    lineHeight: Math.round(size * 1.45),
    color,
    textAlign: center ? 'center' : undefined,
    fontVariant: tabular ? ['tabular-nums'] : undefined,
  };
  return <RNText {...rest} style={[base, style]} />;
}
