import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { SlotPicker, type PickedSlot } from '@/components/slot-picker';
import { T } from '@/components/text';
import { BackHeader, Button, ErrorNote, Loading, LoadError, Note, Screen } from '@/components/ui';
import { api, errorText } from '@/lib/api';
import { apptWhen } from '@/lib/format';
import { useAppointment, useChangeRequests } from '@/lib/queries';
import { colors } from '@/lib/theme';

/** Xin đổi giờ: chọn giờ mới cùng bác sĩ, gửi yêu cầu cho lễ tân duyệt. */
export default function Reschedule() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const q = useAppointment(Number(id));
  const reqs = useChangeRequests(Number(id));
  const qc = useQueryClient();
  const [slot, setSlot] = useState<PickedSlot | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  if (q.isLoading) return <Loading />;
  if (!q.data) return <LoadError message={errorText(q.error)} onRetry={() => q.refetch()} />;
  const a = q.data;
  const pending = reqs.data?.some((r) => r.status === 'PENDING');

  const send = async () => {
    if (!slot) return;
    setBusy(true);
    setError(null);
    try {
      await api.post(`/me/appointments/${a.id}/change-requests`, { type: 'RESCHEDULE', requestedSlotId: slot.id, reason: 'Bệnh nhân xin đổi giờ trên ứng dụng' });
      await qc.invalidateQueries();
      setDone(true);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen
      footer={
        done || pending ? (
          <Button title="Về lịch hẹn" onPress={() => router.back()} />
        ) : (
          <>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <T size={13} color={colors.muted}>
                Giờ mới
              </T>
              <T size={13} weight={700}>
                {slot ? apptWhen(slot.start) : 'Chưa chọn'}
              </T>
            </View>
            <Button title="Gửi yêu cầu đổi giờ" onPress={send} disabled={!slot} loading={busy} />
          </>
        )
      }
    >
      <BackHeader title="Xin đổi giờ khám" sub={`Đang hẹn ${apptWhen(a.start)}`} />
      {done ? (
        <Note>
          <T weight={700} color={colors.brandDark}>
            Đã gửi yêu cầu đổi giờ
          </T>
          <T size={13} color={colors.brandDark}>
            Lễ tân sẽ xử lý và báo lại cho bạn. Trong lúc chờ, lịch cũ vẫn giữ nguyên.
          </T>
        </Note>
      ) : pending ? (
        <Note kind="amber">
          <T size={13} color={colors.amberInk}>
            Lịch này đã có một yêu cầu đổi giờ hoặc hủy đang chờ lễ tân duyệt. Chờ lễ tân xử lý xong rồi gửi yêu cầu mới.
          </T>
        </Note>
      ) : (
        <>
          <T size={13} color={colors.muted}>
            Chọn giờ mới với BS {a.doctorName}. Muốn khám bác sĩ khác thì xin hủy lịch này rồi đặt lịch mới.
          </T>
          <SlotPicker doctorId={a.doctorId} value={slot} onChange={setSlot} />
        </>
      )}
      <ErrorNote text={error} />
    </Screen>
  );
}
