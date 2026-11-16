import { router } from 'expo-router';
import { useState, type ReactNode } from 'react';
import { ActivityIndicator, Platform, Pressable, ScrollView, StyleSheet, TextInput, View, type TextInputProps, type ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { initials } from '@/lib/format';
import type { TagKind } from '@/lib/labels';
import { colors, fonts, tints, type Tint } from '@/lib/theme';
import { Icon, type IconName } from './icon';
import { T } from './text';

/** Màn có cuộn, nền xanh nhạt; footer là vùng nút cố định ở đáy (bước đặt lịch, biểu mẫu). */
export function Screen({ children, footer, padTop = true }: { children: ReactNode; footer?: ReactNode; padTop?: boolean }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView
        contentContainerStyle={{ padding: 20, paddingTop: padTop ? insets.top + 16 : 0, gap: 18, paddingBottom: 28 }}
        keyboardShouldPersistTaps="handled"
      >
        {children}
      </ScrollView>
      {footer ? <View style={[s.footer, { paddingBottom: insets.bottom + 14 }]}>{footer}</View> : null}
    </View>
  );
}

export function BackHeader({ title, sub, right }: { title: string; sub?: string; right?: ReactNode }) {
  return (
    <View style={s.row}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Quay lại"
        onPress={() => (router.canGoBack() ? router.back() : router.replace('/'))}
        style={s.backBtn}
      >
        <Icon name="left" />
      </Pressable>
      <View style={{ flex: 1, marginLeft: 12 }}>
        <T size={18} weight={700}>
          {title}
        </T>
        {sub ? <T size={13} color={colors.muted}>{sub}</T> : null}
      </View>
      {right}
    </View>
  );
}

export function Intro({ title, sub }: { title: string; sub: string }) {
  return (
    <View style={{ gap: 6 }}>
      <T size={24} weight={800} accessibilityRole="header">
        {title}
      </T>
      <T size={14} color={colors.muted}>
        {sub}
      </T>
    </View>
  );
}

export function Progress({ step, total }: { step: number; total: number }) {
  return (
    <View style={{ gap: 6 }} accessibilityLabel={`Bước ${step} trên ${total}`}>
      <View style={{ flexDirection: 'row', gap: 6 }}>
        {Array.from({ length: total }, (_, i) => (
          <View key={i} style={{ flex: 1, height: 4, borderRadius: 2, backgroundColor: i < step ? colors.brand : colors.line }} />
        ))}
      </View>
      <T size={12} color={colors.muted}>
        Bước {step} / {total}
      </T>
    </View>
  );
}

type BtnKind = 'primary' | 'ghost' | 'danger';
export function Button({
  title,
  onPress,
  kind = 'primary',
  icon,
  loading,
  disabled,
  style,
  height = 50,
}: {
  title: string;
  onPress?: () => void;
  kind?: BtnKind;
  icon?: IconName;
  loading?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
  height?: number;
}) {
  const off = disabled || loading;
  const bg = kind === 'primary' ? (off ? '#9CC4B5' : colors.brand) : colors.card;
  const fg = kind === 'primary' ? '#FFFFFF' : kind === 'danger' ? colors.red : colors.brandDark;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!off, busy: !!loading }}
      disabled={off}
      onPress={onPress}
      style={({ pressed }) => [
        s.btn,
        { height, backgroundColor: bg, opacity: pressed ? 0.85 : 1 },
        kind !== 'primary' && { borderWidth: 1, borderColor: kind === 'danger' ? '#E8B4AE' : colors.line },
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={fg} /> : icon ? <Icon name={icon} color={fg} size={20} /> : null}
      <T weight={700} size={15} color={fg}>
        {title}
      </T>
    </Pressable>
  );
}

const TAG: Record<TagKind, [string, string]> = {
  teal: [colors.soft, colors.brandDark],
  amber: [colors.amberSoft, colors.amberInk],
  red: [colors.redBg, colors.red],
  grey: ['#E7EEEA', colors.muted],
  sky: [tints.sky.bg, tints.sky.ink],
};
export function Tag({ text, kind }: { text: string; kind: TagKind }) {
  return (
    <View style={{ backgroundColor: TAG[kind][0], borderRadius: 999, paddingHorizontal: 10, paddingVertical: 3, alignSelf: 'flex-start' }}>
      <T size={12} weight={600} color={TAG[kind][1]}>
        {text}
      </T>
    </View>
  );
}

export function Avatar({ name, size = 44, tint = 'mint' }: { name: string; size?: number; tint?: Tint }) {
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: tints[tint].bg, alignItems: 'center', justifyContent: 'center' }}>
      <T weight={700} size={Math.round(size * 0.34)} color={tints[tint].ink}>
        {initials(name)}
      </T>
    </View>
  );
}

export function SectionTitle({ title, link, onLink }: { title: string; link?: string; onLink?: () => void }) {
  return (
    <View style={[s.row, { justifyContent: 'space-between' }]}>
      <T size={17} weight={700} accessibilityRole="header">
        {title}
      </T>
      {link ? (
        <Pressable onPress={onLink} accessibilityRole="link" hitSlop={10}>
          <T size={13} weight={600} color={colors.brand}>
            {link}
          </T>
        </Pressable>
      ) : null}
    </View>
  );
}

/** Phiếu hẹn: hai nửa ngăn bằng đường cắt nét đứt, khuyết tròn hai bên (điểm nhấn của thiết kế). */
export function Ticket({ top, bottom, edge, onPress, style }: { top: ReactNode; bottom?: ReactNode; edge?: string; onPress?: () => void; style?: ViewStyle }) {
  const body = (
    <View style={[s.ticket, edge ? { borderWidth: 1.5, borderColor: edge } : null, style]}>
      <View style={{ padding: 18, paddingBottom: 16, gap: 12 }}>{top}</View>
      {bottom ? (
        <>
          <View style={s.cutRow}>
            <View style={[s.notch, { left: -11 }]} />
            <View style={s.dash} />
            <View style={[s.notch, { right: -11 }]} />
          </View>
          <View style={{ padding: 18, paddingTop: 14, gap: 10 }}>{bottom}</View>
        </>
      ) : null}
    </View>
  );
  return onPress ? (
    <Pressable onPress={onPress} accessibilityRole="button">
      {body}
    </Pressable>
  ) : (
    body
  );
}

/** Khối trắng bo góc */
export function Card({ children, style, gap = 12 }: { children: ReactNode; style?: ViewStyle; gap?: number }) {
  return <View style={[{ backgroundColor: colors.card, borderRadius: 18, padding: 16, gap }, style]}>{children}</View>;
}

export interface ListRowProps {
  icon: IconName;
  tint: Tint;
  title: string;
  sub?: string;
  tag?: { text: string; kind: TagKind };
  action?: string;
  onPress?: () => void;
}

/** Danh sách các dòng ngăn bằng đường kẻ, dùng cho "Việc cần làm", kết quả lượt khám... */
export function RowList({ rows }: { rows: ListRowProps[] }) {
  return (
    <View style={{ backgroundColor: colors.card, borderRadius: 18, overflow: 'hidden' }}>
      {rows.map((r, i) => (
        <Pressable
          key={i}
          onPress={r.onPress}
          accessibilityRole="button"
          style={({ pressed }) => [s.listRow, i > 0 && { borderTopWidth: 1, borderTopColor: '#E6EEEA' }, pressed && { backgroundColor: '#F4F8F6' }]}
        >
          <View style={[s.iconBox, { backgroundColor: tints[r.tint].bg }]}>
            <Icon name={r.icon} color={tints[r.tint].ink} />
          </View>
          <View style={{ flex: 1, gap: 2 }}>
            <T weight={600}>{r.title}</T>
            {r.sub ? (
              <T size={13} color={colors.muted}>
                {r.sub}
              </T>
            ) : null}
            {r.tag ? (
              <View style={{ marginTop: 3 }}>
                <Tag text={r.tag.text} kind={r.tag.kind} />
              </View>
            ) : null}
          </View>
          {r.action ? (
            <T size={13} weight={700} color={colors.brand}>
              {r.action}
            </T>
          ) : (
            <Icon name="right" size={18} color={colors.muted} />
          )}
        </Pressable>
      ))}
    </View>
  );
}

export function Field({ label, hint, error, right, ...input }: TextInputProps & { label: string; hint?: string; error?: string; right?: ReactNode }) {
  const [focused, setFocused] = useState(false);
  return (
    <View style={{ gap: 6 }}>
      <T weight={600}>{label}</T>
      <View style={[s.input, focused ? { borderColor: colors.brand, borderWidth: 2 } : null, error ? { borderColor: colors.red, borderWidth: 1.5 } : null]}>
        <TextInput
          placeholderTextColor="#93A6A1"
          accessibilityLabel={label}
          {...input}
          onFocus={(e) => {
            setFocused(true);
            input.onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            input.onBlur?.(e);
          }}
          style={[
            { flex: 1, fontFamily: fonts[400], fontSize: 15, color: colors.ink, paddingVertical: 12 },
            Platform.OS === 'web' ? ({ outlineStyle: 'none' } as object) : null,
            input.multiline && { minHeight: 64, textAlignVertical: 'top' },
          ]}
        />
        {right}
      </View>
      {error ? (
        <T size={12} color={colors.red}>
          {error}
        </T>
      ) : hint ? (
        <T size={12} color={colors.muted}>
          {hint}
        </T>
      ) : null}
    </View>
  );
}

export function ErrorNote({ text }: { text: string | null | undefined }) {
  if (!text) return null;
  return (
    <View accessibilityRole="alert" style={{ backgroundColor: colors.redBg, borderRadius: 12, padding: 12 }}>
      <T size={13} color={colors.red}>
        {text}
      </T>
    </View>
  );
}

export function Note({ children, kind = 'teal' }: { children: ReactNode; kind?: 'teal' | 'amber' }) {
  return (
    <View style={{ backgroundColor: kind === 'amber' ? colors.amberBg : colors.soft, borderRadius: 14, padding: 14, gap: 6 }}>{children}</View>
  );
}

export function Loading() {
  return (
    <View style={{ padding: 40, alignItems: 'center' }}>
      <ActivityIndicator color={colors.brand} />
    </View>
  );
}

/** Lỗi tải dữ liệu, kèm nút thử lại */
export function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Card>
      <T>{message}</T>
      <Button title="Thử lại" kind="ghost" onPress={onRetry} height={44} />
    </Card>
  );
}

export function Pill({ text, on, onPress, disabled }: { text: string; on?: boolean; onPress?: () => void; disabled?: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: !!on, disabled: !!disabled }}
      disabled={disabled}
      onPress={onPress}
      style={[
        s.pill,
        on ? { backgroundColor: colors.brand, borderColor: colors.brand } : disabled ? { backgroundColor: '#E7EEEA', borderColor: '#E7EEEA' } : null,
      ]}
    >
      <T weight={600} tabular color={on ? '#FFFFFF' : disabled ? colors.disabled : colors.ink}>
        {text}
      </T>
    </Pressable>
  );
}

export function Segmented({ items, value, onChange }: { items: string[]; value: number; onChange: (i: number) => void }) {
  return (
    <View accessibilityRole="tablist" style={{ flexDirection: 'row', backgroundColor: '#E1EAE5', borderRadius: 12, padding: 3 }}>
      {items.map((t, i) => (
        <Pressable
          key={t}
          accessibilityRole="tab"
          accessibilityState={{ selected: i === value }}
          onPress={() => onChange(i)}
          style={[{ flex: 1, height: 36, borderRadius: 9, alignItems: 'center', justifyContent: 'center' }, i === value && { backgroundColor: colors.card }]}
        >
          <T weight={600} size={13} color={i === value ? colors.ink : colors.muted}>
            {t}
          </T>
        </Pressable>
      ))}
    </View>
  );
}

export function KV({ k, v }: { k: string; v: string }) {
  return (
    <View style={[s.row, { justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }]}>
      <T color={colors.muted}>{k}</T>
      <T weight={500} style={{ flexShrink: 1, textAlign: 'right' }}>
        {v}
      </T>
    </View>
  );
}

export const s = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
  backBtn: { width: 44, height: 44, borderRadius: 12, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.line, alignItems: 'center', justifyContent: 'center' },
  btn: { borderRadius: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingHorizontal: 16 },
  ticket: {
    backgroundColor: colors.card,
    borderRadius: 20,
    shadowColor: colors.brandDark,
    shadowOpacity: 0.14,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 4,
  },
  cutRow: { height: 2, marginHorizontal: 16, justifyContent: 'center' },
  dash: { borderTopWidth: 2, borderColor: colors.line, borderStyle: 'dashed' },
  notch: { position: 'absolute', top: -11, width: 22, height: 22, borderRadius: 11, backgroundColor: colors.bg },
  listRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 14 },
  iconBox: { width: 40, height: 40, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  footer: { backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.line, paddingHorizontal: 20, paddingTop: 12, gap: 10 },
  input: { borderWidth: 1, borderColor: '#CBD9D2', borderRadius: 12, backgroundColor: colors.card, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 8 },
  pill: { minHeight: 44, borderRadius: 10, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.card, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6, flex: 1 },
});
