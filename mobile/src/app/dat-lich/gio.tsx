import { router } from 'expo-router';
import { useEffect } from 'react';
import { Pressable, View } from 'react-native';
import { SlotPicker } from '@/components/slot-picker';
import { T } from '@/components/text';
import { BackHeader, Button, Loading, LoadError, Progress, Screen } from '@/components/ui';
import { errorText } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useBooking } from '@/lib/booking';
import { apptWhen, initials } from '@/lib/format';
import { useDoctors, useSpecialties } from '@/lib/queries';
import { useProfile } from '@/lib/profile';
import { colors, tints, type Tint } from '@/lib/theme';

const DOC_TINTS: Tint[] = ['mint', 'sky', 'peach', 'honey'];

/** Đặt lịch bước 2: bác sĩ, ngày, giờ */
export default function BookStep2() {
  const { user } = useAuth();
  const { draft, update } = useBooking();
  const docs = useDoctors(draft.specialtyId);
  const specialties = useSpecialties();
  const { current } = useProfile();

  // Vào thẳng bước này (đặt lịch tái khám từ trang chủ): điền người khám và chuyên khoa còn thiếu
  useEffect(() => {
    if (!draft.patientId && current) update({ patientId: current.patient.id });
    const sp = specialties.data?.find((x) => x.id === draft.specialtyId);
    if (sp && !draft.specialtyName) update({ specialtyName: sp.name, price: sp.consultationPrice });
  }, [current, draft.patientId, draft.specialtyId, draft.specialtyName, specialties.data, update]);

  // Chưa chọn bác sĩ thì chọn sẵn người đầu tiên của chuyên khoa
  useEffect(() => {
    const first = docs.data?.[0];
    if (!draft.doctorId && first) update({ doctorId: first.id, doctorName: first.fullName });
  }, [docs.data, draft.doctorId, update]);

  if (!draft.specialtyId) {
    return (
      <Screen>
        <BackHeader title="Chọn bác sĩ và giờ" />
        <Button title="Chọn chuyên khoa trước" onPress={() => router.replace('/dat-lich')} />
      </Screen>
    );
  }

  return (
    <Screen
      footer={
        <>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <T size={13} color={colors.muted}>
              Đã chọn
            </T>
            <T size={13} weight={700}>
              {draft.slot ? `${apptWhen(draft.slot.start)}, BS ${draft.doctorName.split(' ').pop()}` : 'Chưa chọn giờ'}
            </T>
          </View>
          <Button
            title={user ? 'Tiếp tục' : 'Đăng nhập để đặt giờ này'}
            disabled={!draft.slot}
            onPress={() => {
              if (user) return router.push('/dat-lich/xac-nhan');
              update({ resume: true });
              router.push('/dang-nhap');
            }}
          />
        </>
      }
    >
      <BackHeader title="Chọn bác sĩ và giờ" sub={draft.specialtyName || undefined} />
      <Progress step={2} total={3} />
      {docs.isLoading ? (
        <Loading />
      ) : docs.error ? (
        <LoadError message={errorText(docs.error)} onRetry={() => docs.refetch()} />
      ) : (
        <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {(docs.data ?? []).map((d, i) => {
            const on = d.id === draft.doctorId;
            const tint = tints[DOC_TINTS[i % DOC_TINTS.length]];
            return (
              <Pressable
                key={d.id}
                accessibilityRole="radio"
                accessibilityState={{ checked: on }}
                onPress={() => update({ doctorId: d.id, doctorName: d.fullName, slot: null })}
                style={{ width: '48.5%', flexDirection: 'row', gap: 10, alignItems: 'center', padding: 10, borderRadius: 18, backgroundColor: colors.card, borderWidth: 2, borderColor: on ? colors.brand : 'transparent' }}
              >
                <View style={{ width: 46, height: 52, borderRadius: 12, backgroundColor: tint.bg, alignItems: 'center', justifyContent: 'center' }}>
                  <T weight={800} size={16} color={tint.ink}>
                    {initials(d.fullName)}
                  </T>
                </View>
                <View style={{ flex: 1 }}>
                  <T size={13} weight={700}>
                    {d.academicTitle ? `${d.academicTitle} ` : 'BS '}
                    {d.fullName}
                  </T>
                  {d.practiceSince ? (
                    <T size={12} color={colors.muted}>
                      Hành nghề từ {d.practiceSince}
                    </T>
                  ) : null}
                </View>
              </Pressable>
            );
          })}
        </View>
      )}
      {draft.doctorId ? (
        <SlotPicker key={draft.doctorId} doctorId={draft.doctorId} value={draft.slot} onChange={(slot) => update({ slot })} preferDate={draft.followUpDate} />
      ) : null}
    </Screen>
  );
}
