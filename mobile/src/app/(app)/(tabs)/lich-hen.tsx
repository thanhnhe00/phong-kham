import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { T } from '@/components/text';
import { Button, Card, Loading, LoadError, Screen, Segmented, Tag } from '@/components/ui';
import { errorText } from '@/lib/api';
import { useBooking } from '@/lib/booking';
import { relativeDay, shortWeekday, time, vnDate } from '@/lib/format';
import { appointmentStatus } from '@/lib/labels';
import { useProfile } from '@/lib/profile';
import { useAppointments } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { Appointment } from '@/lib/types';

const UPCOMING = new Set(['PENDING_CONFIRMATION', 'CONFIRMED', 'NEEDS_RECONFIRMATION']);

export default function Appointments() {
  const q = useAppointments();
  const { patients } = useProfile();
  const { reset } = useBooking();
  const [tab, setTab] = useState(0);
  const [who, setWho] = useState<number | null>(null);
  const today = vnDate();

  const all = (q.data ?? []).filter((a) => who === null || a.patientId === who);
  const upcoming = all.filter((a) => UPCOMING.has(a.status) && a.date >= today).sort((a, b) => a.start.localeCompare(b.start));
  const past = all.filter((a) => !upcoming.includes(a)).sort((a, b) => b.start.localeCompare(a.start));
  const list = tab === 0 ? upcoming : past;

  return (
    <Screen>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <T size={22} weight={800} accessibilityRole="header">
          Lịch hẹn
        </T>
        <Button
          title="Đặt lịch"
          icon="plus"
          height={40}
          onPress={() => {
            reset();
            router.push('/dat-lich');
          }}
        />
      </View>
      <Segmented items={[`Sắp tới (${upcoming.length})`, 'Đã qua']} value={tab} onChange={setTab} />
      {patients.length > 1 ? (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {[{ id: null as number | null, name: 'Tất cả hồ sơ' }, ...patients.map((p) => ({ id: p.patient.id as number | null, name: p.patient.fullName }))].map((p) => (
            <Pressable
              key={String(p.id)}
              onPress={() => setWho(p.id)}
              accessibilityRole="button"
              accessibilityState={{ selected: who === p.id }}
              style={{ paddingHorizontal: 12, height: 34, borderRadius: 17, justifyContent: 'center', borderWidth: 1, borderColor: who === p.id ? colors.brand : colors.line, backgroundColor: who === p.id ? colors.soft : colors.card }}
            >
              <T size={13} weight={who === p.id ? 600 : 400}>
                {p.name}
              </T>
            </Pressable>
          ))}
        </View>
      ) : null}
      {q.isLoading ? (
        <Loading />
      ) : q.error ? (
        <LoadError message={errorText(q.error)} onRetry={() => q.refetch()} />
      ) : list.length === 0 ? (
        <Card>
          <T weight={600}>{tab === 0 ? 'Chưa có lịch hẹn sắp tới' : 'Chưa có lịch hẹn nào đã qua'}</T>
          <T size={13} color={colors.muted}>
            {tab === 0 ? 'Bấm Đặt lịch để chọn bác sĩ và giờ khám.' : 'Lịch đã khám, đã hủy hoặc bỏ lỡ sẽ nằm ở đây.'}
          </T>
        </Card>
      ) : (
        list.map((a) => <ApptCard key={a.id} a={a} showWho={patients.length > 1} />)
      )}
      {tab === 0 && list.length > 0 ? (
        <T size={12} color={colors.muted}>
          Lịch đã hủy hoặc bỏ lỡ nằm ở mục Đã qua.
        </T>
      ) : null}
    </Screen>
  );
}

function ApptCard({ a, showWho }: { a: Appointment; showWho: boolean }) {
  const st = appointmentStatus[a.status];
  const note =
    a.status === 'PENDING_CONFIRMATION'
      ? 'Lễ tân sẽ gọi cho bạn để xác nhận lịch này'
      : a.status === 'NEEDS_RECONFIRMATION'
        ? 'Lịch có thay đổi bác sĩ, lễ tân sẽ gọi hỏi ý bạn'
        : a.status === 'CANCELLED' && a.cancelReason
          ? `Lý do hủy: ${a.cancelReason}`
          : null;
  return (
    <Pressable
      onPress={() => router.push(`/lich-hen/${a.id}`)}
      accessibilityRole="button"
      style={({ pressed }) => ({ backgroundColor: pressed ? '#F4F8F6' : colors.card, borderRadius: 18, padding: 14, gap: 8 })}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View style={{ width: 50, height: 50, borderRadius: 12, backgroundColor: colors.soft, alignItems: 'center', justifyContent: 'center' }}>
          <T size={11} color={colors.brandDark}>
            {shortWeekday(a.date)}
          </T>
          <T size={17} weight={800} color={colors.brandDark} style={{ lineHeight: 20 }}>
            {a.date.slice(8)}
          </T>
        </View>
        <View style={{ flex: 1 }}>
          <T weight={700}>
            {time(a.start)}, {relativeDay(a.date) === 'Đã qua' ? `ngày ${a.date.slice(8)}/${a.date.slice(5, 7)}` : relativeDay(a.date).toLowerCase()}
          </T>
          <T size={13} color={colors.muted}>
            BS {a.doctorName}, {a.specialtyName}
          </T>
        </View>
        <Tag text={st.text} kind={st.kind} />
      </View>
      {showWho ? (
        <T size={13} color={colors.muted}>
          Người khám: <T size={13} weight={600}>{a.patientName}</T>
        </T>
      ) : null}
      {note ? (
        <T size={12} color={a.status === 'CANCELLED' ? colors.muted : colors.amberInk}>
          {note}
        </T>
      ) : null}
    </Pressable>
  );
}
