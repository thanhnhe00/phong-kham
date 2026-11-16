// Màu và phông theo thiết kế mẫu A (bản 2): dải xanh rừng, nút xanh lá, mỗi chức năng một màu nhạt.
export const colors = {
  ink: '#11231E',
  muted: '#56685F',
  line: '#D6E2DC',
  brand: '#1F7A5C',
  brandDark: '#0F3B32',
  soft: '#DDF0E4',
  bg: '#EEF4F1',
  card: '#FFFFFF',
  onDark: '#A8D3C2',
  amberBg: '#FFF6E5',
  amberLine: '#F1D9A6',
  amberInk: '#7A4A00',
  amberSoft: '#FBE7C6',
  redBg: '#FDE8E6',
  red: '#A3261B',
  disabled: '#A9BAB2',
};

export const tints = {
  mint: { bg: '#D9EFE2', ink: '#145C42' },
  peach: { bg: '#FBE3DC', ink: '#9A3B22' },
  sky: { bg: '#DCE9F6', ink: '#1E4E80' },
  honey: { bg: '#FAEDC8', ink: '#7A5300' },
} as const;
export type Tint = keyof typeof tints;

export const fonts = {
  400: 'BeVietnamPro_400Regular',
  500: 'BeVietnamPro_500Medium',
  600: 'BeVietnamPro_600SemiBold',
  700: 'BeVietnamPro_700Bold',
  800: 'BeVietnamPro_800ExtraBold',
} as const;
export type Weight = keyof typeof fonts;
