import Constants from 'expo-constants';
import { router } from 'expo-router';
import { Alert, Linking, Platform, Pressable, View } from 'react-native';
import { Icon, type IconName } from '@/components/icon';
import { T } from '@/components/text';
import { Avatar, Screen } from '@/components/ui';
import { useAuth } from '@/lib/auth';
import { age, phone } from '@/lib/format';
import { relationshipLabel } from '@/lib/labels';
import { useProfile } from '@/lib/profile';
import { colors } from '@/lib/theme';

const CLINIC_PHONE = (Constants.expoConfig?.extra?.clinicPhone as string | undefined) ?? '02838123456';

export default function Account() {
  const { user, logout } = useAuth();
  const { patients, select } = useProfile();
  const self = patients.find((p) => p.relationship === 'SELF');

  const confirmLogout = () => {
    if (Platform.OS === 'web') return void logout();
    Alert.alert('Đăng xuất', 'Bạn muốn đăng xuất khỏi máy này?', [
      { text: 'Ở lại', style: 'cancel' },
      { text: 'Đăng xuất', style: 'destructive', onPress: () => void logout() },
    ]);
  };

  return (
    <Screen>
      <T size={22} weight={800} accessibilityRole="header">
        Tài khoản
      </T>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
        <Avatar name={self?.patient.fullName ?? user?.phone ?? ''} size={56} />
        <View>
          <T size={17} weight={700}>
            {self?.patient.fullName ?? 'Chưa có hồ sơ'}
          </T>
          <T color={colors.muted}>{phone(user?.phone)}</T>
        </View>
      </View>

      <Group title="Hồ sơ bệnh nhân">
        {patients.map((p) => (
          <Row
            key={p.patient.id}
            avatar={p.patient.fullName}
            text={p.patient.fullName}
            sub={`${relationshipLabel[p.relationship]}, ${age(p.patient.dateOfBirth)} tuổi, ${p.patient.patientCode}`}
            onPress={() => {
              select(p.patient.id);
              router.push('/ho-so');
            }}
          />
        ))}
        <Row icon="plus" text="Thêm hồ sơ người thân" onPress={() => router.push('/tao-ho-so?nguoiThan=1')} />
        <Row icon="link" text="Liên kết hồ sơ đã có ở phòng khám" sub="Dùng mã do lễ tân cấp" onPress={() => router.push('/lien-ket')} />
      </Group>

      <Group title="Cài đặt">
        <Row icon="bell" text="Thông báo" onPress={() => router.push('/thong-bao')} />
        <Row icon="lock" text="Đổi mật khẩu" onPress={() => router.push('/doi-mat-khau')} />
        <Row icon="phone" text="Gọi phòng khám" sub={phone(CLINIC_PHONE)} onPress={() => Linking.openURL(`tel:${CLINIC_PHONE}`)} />
        <Row icon="logout" text="Đăng xuất" danger onPress={confirmLogout} />
      </Group>
    </Screen>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={{ backgroundColor: colors.card, borderRadius: 18, overflow: 'hidden' }}>
      <T size={13} weight={600} color={colors.muted} style={{ paddingHorizontal: 16, paddingTop: 12, paddingBottom: 4 }}>
        {title}
      </T>
      {children}
    </View>
  );
}

function Row({ icon, avatar, text, sub, danger, onPress }: { icon?: IconName; avatar?: string; text: string; sub?: string; danger?: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 13, borderTopWidth: 1, borderTopColor: '#EEF2F1', backgroundColor: pressed ? '#F4F8F6' : colors.card })}
    >
      {avatar ? <Avatar name={avatar} size={36} /> : icon ? <Icon name={icon} color={danger ? colors.red : colors.brand} /> : null}
      <View style={{ flex: 1 }}>
        <T weight={avatar ? 600 : 500} color={danger ? colors.red : colors.ink}>
          {text}
        </T>
        {sub ? (
          <T size={12} color={colors.muted}>
            {sub}
          </T>
        ) : null}
      </View>
      {danger ? null : <Icon name="right" size={18} color={colors.muted} />}
    </Pressable>
  );
}
