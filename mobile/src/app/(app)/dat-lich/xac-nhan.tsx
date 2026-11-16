import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, View } from 'react-native';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { BackHeader, Button, Card, ErrorNote, Field, KV, Loading, Progress, Screen } from '@/components/ui';
import { api, errorText } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { apptWhen, vnd } from '@/lib/format';
import { useProfile } from '@/lib/profile';
import { useLabServices } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { Appointment } from '@/lib/types';

/** Đặt lịch bước 3: xác nhận, lý do khám, xét nghiệm mong muốn (chỉ để bác sĩ tham khảo) */
export default function BookStep3() {
  const { draft, update } = useBooking();
  const { patients, current, loading } = useProfile();
  const labs = useLabServices();
  const qc = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const who = patients.find((p) => p.patient.id === draft.patientId);

  useEffect(() => {
    // chỉ xong việc đặt dở khi đã hiện đủ người khám (chưa có hồ sơ thì còn phải qua màn tạo hồ sơ rồi quay lại)
    if (draft.resume && who) update({ resume: false });
    if (!who && current) update({ patientId: current.patient.id });
  }, [draft.resume, who, current, update]);

  if (!loading && patients.length === 0) {
    return (
      <Screen>
        <BackHeader title="Xác nhận thông tin" />
        <Button title="Tạo hồ sơ khám trước" onPress={() => router.push('/tao-ho-so')} />
      </Screen>
    );
  }
  if (draft.slot && !who) return <Loading />;
  if (!draft.slot) {
    return (
      <Screen>
        <BackHeader title="Xác nhận thông tin" />
        <Button title="Chọn giờ khám trước" onPress={() => router.replace('/dat-lich/gio')} />
      </Screen>
    );
  }

  const toggle = (id: number) =>
    update({ serviceIds: draft.serviceIds.includes(id) ? draft.serviceIds.filter((x) => x !== id) : [...draft.serviceIds, id] });

  const send = async () => {
    setBusy(true);
    setError(null);
    try {
      const a = await api.post<Appointment>('/me/appointments', {
        patientId: draft.patientId,
        slotId: draft.slot!.id,
        reason: draft.reason.trim() || null,
        serviceIds: draft.serviceIds,
      });
      await qc.invalidateQueries();
      router.replace({ pathname: '/dat-lich/xong', params: { id: String(a.id) } });
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen
      footer={
        <>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
            <T color={colors.muted}>Phí khám, trả sau khi khám</T>
            <T weight={700}>{draft.price ? vnd(draft.price) : 'Theo bảng giá'}</T>
          </View>
          <Button title="Gửi yêu cầu đặt lịch" onPress={send} loading={busy} />
        </>
      }
    >
      <BackHeader title="Xác nhận thông tin" />
      <Progress step={3} total={3} />
      <Card gap={10}>
        <KV k="Người khám" v={who?.patient.fullName ?? ''} />
        <KV k="Bác sĩ" v={`BS ${draft.doctorName}`} />
        <KV k="Chuyên khoa" v={draft.specialtyName} />
        <KV k="Thời gian" v={apptWhen(draft.slot.start)} />
        <KV k="Phòng" v={draft.slot.roomCode} />
      </Card>
      <Field
        label="Bạn bị làm sao? (không bắt buộc)"
        value={draft.reason}
        onChangeText={(reason) => update({ reason })}
        multiline
        maxLength={1000}
        placeholder="Ví dụ: đau họng, sốt nhẹ 3 ngày nay"
      />
      {labs.data && labs.data.length > 0 ? (
        <Card gap={12}>
          <View style={{ gap: 2 }}>
            <T weight={600}>Bạn muốn được xét nghiệm gì? (không bắt buộc)</T>
            <T size={12} color={colors.muted}>
              Đây chỉ là mong muốn để bác sĩ tham khảo. Bác sĩ khám xong mới quyết định có làm hay không, và chỉ tính tiền khi đã làm.
            </T>
          </View>
          {labs.data.map((s) => {
            const on = draft.serviceIds.includes(s.id);
            return (
              <Pressable key={s.id} onPress={() => toggle(s.id)} accessibilityRole="checkbox" accessibilityState={{ checked: on }} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                <View style={{ width: 22, height: 22, borderRadius: 6, alignItems: 'center', justifyContent: 'center', backgroundColor: on ? colors.brand : 'transparent', borderWidth: on ? 0 : 2, borderColor: '#B8CCC6' }}>
                  {on ? <Icon name="check" size={14} stroke={3} color="#FFFFFF" /> : null}
                </View>
                <T weight={500} style={{ flex: 1 }}>
                  {s.name}
                </T>
                <T size={13} color={colors.muted} tabular>
                  khoảng {vnd(s.listPrice)}
                </T>
              </Pressable>
            );
          })}
        </Card>
      ) : null}
      <ErrorNote text={error} />
    </Screen>
  );
}
