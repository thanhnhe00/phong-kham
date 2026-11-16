import { Link, router } from 'expo-router';
import { useEffect } from 'react';
import { Pressable, View } from 'react-native';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { Avatar, BackHeader, Button, Loading, LoadError, Progress, Screen } from '@/components/ui';
import { errorText } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useBooking } from '@/lib/booking';
import { vnd } from '@/lib/format';
import { relationshipLabel } from '@/lib/labels';
import { useProfile } from '@/lib/profile';
import { useSpecialties } from '@/lib/queries';
import { colors } from '@/lib/theme';

/** Đặt lịch bước 1: khám cho ai, chuyên khoa nào */
export default function BookStep1() {
  const { user } = useAuth();
  const { draft, update } = useBooking();
  const { patients, current } = useProfile();
  const sp = useSpecialties();

  useEffect(() => {
    if (!draft.patientId && current) update({ patientId: current.patient.id });
  }, [current, draft.patientId, update]);

  const next = () => router.push('/dat-lich/gio');

  return (
    <Screen footer={<Button title="Tiếp tục" onPress={next} disabled={(!!user && !draft.patientId) || !draft.specialtyId} />}>
      {user ? (
        <BackHeader title="Đặt lịch khám" />
      ) : (
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingTop: 8 }}>
          <T size={20} weight={800} accessibilityRole="header">
            Phòng khám An Tâm
          </T>
          <Link href="/dang-nhap" accessibilityRole="link">
            <T weight={600} color={colors.brand}>
              Đăng nhập
            </T>
          </Link>
        </View>
      )}
      <Progress step={1} total={3} />
      {user ? (
      <View style={{ gap: 10 }}>
        <T size={16} weight={700} accessibilityRole="header">
          Khám cho ai?
        </T>
        <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {patients.map((p) => {
            const on = draft.patientId === p.patient.id;
            return (
              <Pressable
                key={p.patient.id}
                accessibilityRole="radio"
                accessibilityState={{ checked: on }}
                onPress={() => update({ patientId: p.patient.id })}
                style={{ width: '31%', alignItems: 'center', gap: 6, padding: 10, borderRadius: 14, borderWidth: 2, borderColor: on ? colors.brand : 'transparent', backgroundColor: colors.card }}
              >
                <Avatar name={p.patient.fullName} size={36} />
                <T size={12} weight={600} center numberOfLines={2}>
                  {p.relationship === 'SELF' ? 'Bản thân' : p.patient.fullName}
                </T>
                {p.relationship !== 'SELF' ? (
                  <T size={11} color={colors.muted}>
                    {relationshipLabel[p.relationship]}
                  </T>
                ) : null}
              </Pressable>
            );
          })}
          <Pressable
            onPress={() => router.push('/tao-ho-so?nguoiThan=1')}
            accessibilityRole="button"
            style={{ width: '31%', alignItems: 'center', justifyContent: 'center', gap: 6, padding: 10, borderRadius: 14, borderWidth: 1, borderStyle: 'dashed', borderColor: '#B8CCC6' }}
          >
            <Icon name="plus" color={colors.brand} size={22} />
            <T size={12} weight={600} color={colors.brand} center>
              Thêm người thân
            </T>
          </Pressable>
        </View>
      </View>
      ) : null}

      <View style={{ gap: 10 }}>
        <T size={16} weight={700} accessibilityRole="header">
          Bạn muốn khám chuyên khoa nào?
        </T>
        {sp.isLoading ? (
          <Loading />
        ) : sp.error ? (
          <LoadError message={errorText(sp.error)} onRetry={() => sp.refetch()} />
        ) : (
          <View accessibilityRole="radiogroup" style={{ gap: 8 }}>
            {(sp.data ?? []).map((s) => {
              const on = draft.specialtyId === s.id;
              return (
                <Pressable
                  key={s.id}
                  accessibilityRole="radio"
                  accessibilityState={{ checked: on }}
                  onPress={() => update({ specialtyId: s.id, specialtyName: s.name, price: s.consultationPrice, doctorId: null, doctorName: '', slot: null })}
                  style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14, borderRadius: 16, backgroundColor: colors.card, borderWidth: 2, borderColor: on ? colors.brand : 'transparent' }}
                >
                  <View style={{ width: 20, height: 20, borderRadius: 10, borderWidth: on ? 6 : 2, borderColor: on ? colors.brand : '#B8CCC6' }} />
                  <View style={{ flex: 1 }}>
                    <T weight={600}>{s.name}</T>
                    {s.description ? (
                      <T size={13} color={colors.muted}>
                        {s.description}
                      </T>
                    ) : null}
                  </View>
                  {s.consultationPrice ? (
                    <T size={13} weight={600}>
                      {vnd(s.consultationPrice)}
                    </T>
                  ) : null}
                </Pressable>
              );
            })}
          </View>
        )}
        <T size={13} color={colors.muted}>
          Chưa biết khám khoa nào? Chọn Nội tổng quát, bác sĩ sẽ chuyển khoa nếu cần.
        </T>
      </View>
    </Screen>
  );
}
