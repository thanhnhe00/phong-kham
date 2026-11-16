import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Platform, View } from 'react-native';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { BackHeader, Button, Card, ErrorNote, KV, Loading, LoadError, Note, Screen, Tag } from '@/components/ui';
import { api, errorText } from '@/lib/api';
import { apptWhen, dayLabel, relativeDay, time, vnDate } from '@/lib/format';
import { appointmentStatus } from '@/lib/labels';
import { useAppointment, useChangeRequests } from '@/lib/queries';
import { colors } from '@/lib/theme';

const CHANGEABLE = new Set(['PENDING_CONFIRMATION', 'CONFIRMED', 'NEEDS_RECONFIRMATION']);

export default function AppointmentDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const q = useAppointment(Number(id));
  const reqs = useChangeRequests(Number(id));
  const qc = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (q.isLoading) return <Loading />;
  if (q.error || !q.data)
    return (
      <Screen>
        <BackHeader title="Chi tiết lịch hẹn" />
        <LoadError message={errorText(q.error)} onRetry={() => q.refetch()} />
      </Screen>
    );
  const a = q.data;
  const st = appointmentStatus[a.status];
  const canChange = CHANGEABLE.has(a.status) && a.date >= vnDate();
  // Yêu cầu đổi/hủy đã gửi: đang chờ thì không cho gửi thêm; vừa bị từ chối thì báo để bệnh nhân biết
  const pending = reqs.data?.find((r) => r.status === 'PENDING');
  const rejected = !pending && reqs.data?.[0]?.status === 'REJECTED' ? reqs.data[0] : null;
  const what = (t: 'RESCHEDULE' | 'CANCEL', slot: string | null) => (t === 'CANCEL' ? 'hủy lịch' : `đổi sang ${slot ? apptWhen(slot) : 'giờ khác'}`);

  const cancel = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.post(`/me/appointments/${a.id}/change-requests`, { type: 'CANCEL', reason: 'Bệnh nhân xin hủy trên ứng dụng' });
      await qc.invalidateQueries();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };
  const askCancel = () => {
    if (Platform.OS === 'web') return void cancel();
    Alert.alert('Xin hủy lịch', `Gửi yêu cầu hủy lịch ${apptWhen(a.start)}?`, [
      { text: 'Giữ lịch', style: 'cancel' },
      { text: 'Gửi yêu cầu hủy', style: 'destructive', onPress: () => void cancel() },
    ]);
  };

  const steps = [
    { t: 'Đã gửi yêu cầu', sub: `${time(a.createdAt)}, ${dayLabel(vnDate(a.createdAt))}`, done: true },
    a.status === 'CANCELLED'
      ? { t: 'Lịch đã hủy', sub: a.cancelReason ?? '', done: true }
      : a.status === 'NO_SHOW'
        ? { t: 'Bạn không đến khám', sub: 'Lịch chuyển thành bỏ lỡ sau 15 phút quá giờ hẹn', done: true }
        : {
            t: a.confirmedAt ? 'Lễ tân đã xác nhận' : 'Chờ lễ tân gọi xác nhận',
            sub: a.confirmedAt ? `${time(a.confirmedAt)}, ${dayLabel(vnDate(a.confirmedAt))}` : 'Lễ tân sẽ gọi trong giờ làm việc',
            done: !!a.confirmedAt,
          },
    ...(a.status === 'CANCELLED' || a.status === 'NO_SHOW'
      ? []
      : [{ t: a.status === 'CHECKED_IN' ? 'Đã check-in' : 'Đến khám và check-in', sub: `Trước ${time(a.start)}, ${dayLabel(a.date)}`, done: a.status === 'CHECKED_IN' }]),
  ];

  return (
    <Screen
      footer={
        a.status === 'CONFIRMED' && a.date === vnDate() ? (
          <Button title="Mở mã check-in" icon="qr" onPress={() => router.push(`/ma-checkin/${a.id}`)} />
        ) : a.status === 'CONFIRMED' ? (
          <Button title="Mở mã check-in" icon="qr" kind="ghost" onPress={() => router.push(`/ma-checkin/${a.id}`)} />
        ) : undefined
      }
    >
      <BackHeader title="Chi tiết lịch hẹn" sub={`Mã ${a.code}`} />
      <Card gap={12}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <View style={{ flex: 1 }}>
            <T size={20} weight={800}>
              {apptWhen(a.start)}
            </T>
            <T size={13} color={colors.muted}>
              {relativeDay(a.date)}
            </T>
          </View>
          <Tag text={st.text} kind={st.kind} />
        </View>
        <View style={{ height: 1, backgroundColor: '#EEF2F1' }} />
        <KV k="Người khám" v={a.patientName} />
        <KV k="Bác sĩ" v={`BS ${a.doctorName}`} />
        <KV k="Chuyên khoa" v={a.specialtyName} />
        <KV k="Phòng" v={a.roomCode} />
        {a.reason ? <KV k="Lý do khám" v={a.reason} /> : null}
        {a.services.length ? <KV k="Muốn xét nghiệm" v={a.services.map((s) => s.name).join(', ')} /> : null}
      </Card>

      {a.status === 'NEEDS_RECONFIRMATION' ? (
        <Note kind="amber">
          <T weight={700} color={colors.amberInk}>
            Lịch khám có thay đổi bác sĩ
          </T>
          <T size={13} color={colors.amberInk}>
            Lễ tân sẽ gọi để hỏi ý bạn. Đồng ý thì giữ nguyên giờ và khám với BS {a.doctorName}; không đồng ý thì lễ tân xếp ngày khác.
          </T>
        </Note>
      ) : null}

      <Card gap={12}>
        <T size={15} weight={700}>
          Tình trạng
        </T>
        {steps.map((s) => (
          <View key={s.t} style={{ flexDirection: 'row', gap: 12 }}>
            {s.done ? (
              <View style={{ width: 22, height: 22, borderRadius: 11, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="check" size={12} stroke={3} color="#FFFFFF" />
              </View>
            ) : (
              <View style={{ width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: colors.line }} />
            )}
            <View style={{ flex: 1 }}>
              <T weight={s.done ? 600 : 500} color={s.done ? colors.ink : colors.muted}>
                {s.t}
              </T>
              <T size={12} color={colors.muted}>
                {s.sub}
              </T>
            </View>
          </View>
        ))}
      </Card>

      {pending && canChange ? (
        <Note>
          <T weight={700} color={colors.brandDark}>
            Đã gửi yêu cầu {what(pending.type, pending.requestedSlotStart)}
          </T>
          <T size={13} color={colors.brandDark}>
            Đang chờ lễ tân duyệt, gửi lúc {time(pending.createdAt)} {dayLabel(vnDate(pending.createdAt))}. Trong lúc chờ, lịch hiện tại vẫn giữ nguyên.
          </T>
        </Note>
      ) : canChange ? (
        <>
          {rejected ? (
            <Note kind="amber">
              <T size={13} color={colors.amberInk}>
                Yêu cầu {what(rejected.type, rejected.requestedSlotStart)} của bạn chưa được duyệt. Bạn có thể gửi yêu cầu khác hoặc gọi phòng khám.
              </T>
            </Note>
          ) : null}
          <View style={{ flexDirection: 'row', gap: 10 }}>
            <Button title="Xin đổi giờ" kind="ghost" style={{ flex: 1 }} onPress={() => router.push(`/doi-lich/${a.id}`)} />
            <Button title="Xin hủy lịch" kind="danger" style={{ flex: 1 }} onPress={askCancel} loading={busy} />
          </View>
          <T size={12} color={colors.muted}>
            Yêu cầu đổi giờ hoặc hủy lịch sẽ được lễ tân xử lý và báo lại cho bạn.
          </T>
        </>
      ) : null}
      <ErrorNote text={error} />
    </Screen>
  );
}
