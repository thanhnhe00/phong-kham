import { router } from 'expo-router';
import { useState } from 'react';
import { Modal, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { DayStep } from '@/lib/home-state';
import { relationshipLabel } from '@/lib/labels';
import { useNotifications } from '@/lib/queries';
import { useProfile } from '@/lib/profile';
import { colors, tints, type Tint } from '@/lib/theme';
import { Icon, type IconName } from './icon';
import { T } from './text';
import { Avatar } from './ui';

/** Dải xanh đầu trang chủ: bộ chọn hồ sơ, chuông, tên khu vực đang xem. Phần tử ngay sau đè lên mép dưới. */
export function Band({ label, link, onLink, note }: { label: string; link?: string; onLink?: () => void; note?: string | null }) {
  const insets = useSafeAreaInsets();
  const { current } = useProfile();
  const unread = (useNotifications().data?.items ?? []).some((n) => !n.readAt);
  const [picking, setPicking] = useState(false);
  const who = current ? `${current.patient.fullName}${current.relationship === 'SELF' ? '' : ` (${relationshipLabel[current.relationship].toLowerCase()})`}` : '';
  return (
    <View style={{ marginHorizontal: -20, marginTop: -(insets.top + 16), marginBottom: -38, paddingTop: insets.top + 16, paddingHorizontal: 20, paddingBottom: 56, backgroundColor: colors.brandDark, borderBottomLeftRadius: 28, borderBottomRightRadius: 28, gap: 18 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Đổi hồ sơ, đang xem ${who}`}
          onPress={() => setPicking(true)}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: 'rgba(255,255,255,0.1)', borderRadius: 999, paddingVertical: 4, paddingLeft: 4, paddingRight: 12, flexShrink: 1 }}
        >
          <Avatar name={current?.patient.fullName ?? ''} size={32} />
          <T weight={600} color="#FFFFFF" numberOfLines={1} style={{ flexShrink: 1 }}>
            {who}
          </T>
          <Icon name="down" size={16} color="#CFE9DC" />
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={unread ? 'Thông báo, có tin chưa đọc' : 'Thông báo'}
          onPress={() => router.push('/thong-bao')}
          style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.12)', alignItems: 'center', justifyContent: 'center' }}
        >
          <Icon name="bell" color="#FFFFFF" />
          {unread ? <View style={{ position: 'absolute', top: 10, right: 11, width: 8, height: 8, borderRadius: 4, backgroundColor: '#F2A33A' }} /> : null}
        </Pressable>
      </View>
      <View style={{ gap: 2 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <T size={17} weight={700} color="#FFFFFF" accessibilityRole="header">
            {label}
          </T>
          {link ? (
            <Pressable onPress={onLink} accessibilityRole="link" hitSlop={10}>
              <T size={13} weight={600} color={colors.onDark}>
                {link}
              </T>
            </Pressable>
          ) : null}
        </View>
        {note ? (
          <T size={13} color={colors.onDark}>
            {note}
          </T>
        ) : null}
      </View>
      <ProfilePicker visible={picking} onClose={() => setPicking(false)} />
    </View>
  );
}

/** Chọn hồ sơ đang xem: bản thân hoặc người thân tài khoản đang quản lý */
export function ProfilePicker({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const { patients, current, select } = useProfile();
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={{ flex: 1, backgroundColor: 'rgba(17,35,30,0.4)' }} onPress={onClose} accessibilityLabel="Đóng" />
      <View style={{ backgroundColor: colors.card, borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, paddingBottom: insets.bottom + 20, gap: 8 }}>
        <T size={17} weight={700}>
          Xem hồ sơ của
        </T>
        {patients.map((p) => {
          const on = p.patient.id === current?.patient.id;
          return (
            <Pressable
              key={p.patient.id}
              accessibilityRole="radio"
              accessibilityState={{ checked: on }}
              onPress={() => {
                select(p.patient.id);
                onClose();
              }}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, borderRadius: 14, borderWidth: 2, borderColor: on ? colors.brand : 'transparent', backgroundColor: on ? '#F1F8F4' : colors.bg }}
            >
              <Avatar name={p.patient.fullName} size={40} />
              <View style={{ flex: 1 }}>
                <T weight={700}>{p.patient.fullName}</T>
                <T size={13} color={colors.muted}>
                  {relationshipLabel[p.relationship]}, mã {p.patient.patientCode}
                </T>
              </View>
              {on ? <Icon name="check" color={colors.brand} /> : null}
            </Pressable>
          );
        })}
        <Pressable
          onPress={() => {
            onClose();
            router.push('/tao-ho-so?nguoiThan=1');
          }}
          accessibilityRole="button"
          style={{ flexDirection: 'row', alignItems: 'center', gap: 8, padding: 12 }}
        >
          <Icon name="plus" color={colors.brand} />
          <T weight={600} color={colors.brand}>
            Thêm hồ sơ người thân
          </T>
        </Pressable>
      </View>
    </Modal>
  );
}

const TILES: { icon: IconName; tint: Tint; title: string; go: () => void }[] = [
  { icon: 'calplus', tint: 'mint', title: 'Đặt lịch khám', go: () => router.push('/dat-lich') },
  { icon: 'flask', tint: 'peach', title: 'Kết quả khám', go: () => router.push('/ho-so') },
  { icon: 'rx', tint: 'sky', title: 'Đơn thuốc', go: () => router.push('/ho-so?xem=don-thuoc') },
  { icon: 'card', tint: 'honey', title: 'Hóa đơn', go: () => router.push('/hoa-don') },
];

/** Bốn lối tắt cố định: chỉ biểu tượng và tên, số liệu nằm ở màn nghiệp vụ tương ứng. */
export function Tiles() {
  return (
    <View accessibilityRole="menu" style={{ flexDirection: 'row', gap: 8, paddingTop: 4 }}>
      {TILES.map((t) => (
        <Pressable key={t.title} onPress={t.go} accessibilityRole="button" style={{ flex: 1, alignItems: 'center', gap: 6 }}>
          <View style={{ width: 54, height: 54, borderRadius: 16, backgroundColor: tints[t.tint].bg, alignItems: 'center', justifyContent: 'center' }}>
            <Icon name={t.icon} size={24} color={tints[t.tint].ink} />
          </View>
          <T size={12} weight={600} center>
            {t.title}
          </T>
        </Pressable>
      ))}
    </View>
  );
}

/** Các bước trong ngày khám, có đường nối giữa các bước */
export function Steps({ steps, title = 'Các bước hôm nay' }: { steps: DayStep[]; title?: string }) {
  return (
    <View style={{ backgroundColor: colors.card, borderRadius: 18, padding: 16, paddingHorizontal: 18, gap: 14 }}>
      <T size={16} weight={700}>
        {title}
      </T>
      {steps.map((st, i) => (
        <View key={st.label} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }} accessibilityLabel={`${st.label}, ${st.state === 'done' ? 'đã xong' : st.state === 'now' ? 'đang ở bước này' : 'chưa tới'}`}>
          {i < steps.length - 1 ? (
            <View style={{ position: 'absolute', left: 10, top: 24, height: 28, width: 2, backgroundColor: st.state === 'done' ? colors.brand : colors.line }} />
          ) : null}
          {st.state === 'done' ? (
            <View style={{ width: 22, height: 22, borderRadius: 11, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="check" size={12} stroke={3} color="#FFFFFF" />
            </View>
          ) : (
            <View style={{ width: 22, height: 22, borderRadius: 11, borderWidth: st.state === 'now' ? 6 : 2, borderColor: st.state === 'now' ? colors.brand : colors.line, backgroundColor: colors.card }} />
          )}
          <T style={{ flex: 1 }} weight={st.state === 'now' ? 700 : 400} color={st.state === 'todo' ? colors.muted : colors.ink}>
            {st.label}
          </T>
          <T size={13} weight={st.state === 'now' ? 700 : 400} color={st.state === 'now' ? colors.brand : colors.muted}>
            {st.note}
          </T>
        </View>
      ))}
    </View>
  );
}
