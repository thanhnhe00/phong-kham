import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { Button, Card, Loading, Screen, Tag } from '@/components/ui';
import { useAuth } from '@/lib/auth';
import { addDays, apptWhen, dayLabel, daysUntil, phone, time } from '@/lib/format';
import { appointmentStatus } from '@/lib/labels';
import { useAppointment } from '@/lib/queries';
import { colors } from '@/lib/theme';

/** Đặt lịch xong: đã gửi yêu cầu, chờ lễ tân gọi xác nhận */
export default function BookDone() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { user } = useAuth();
  const q = useAppointment(Number(id));
  const a = q.data;

  return (
    <Screen
      footer={
        <>
          <Button title="Xem lịch hẹn" onPress={() => router.replace(`/lich-hen/${id}`)} />
          <Button title="Về trang chủ" kind="ghost" onPress={() => router.replace('/')} />
        </>
      }
    >
      <View style={{ alignItems: 'center', gap: 12, paddingTop: 24 }}>
        <View style={{ width: 72, height: 72, borderRadius: 36, backgroundColor: colors.soft, alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="check" size={36} stroke={2.5} color={colors.brand} />
        </View>
        <T size={22} weight={800} center accessibilityRole="header">
          Đã gửi yêu cầu đặt lịch
        </T>
        <T size={14} color={colors.muted} center>
          Lễ tân sẽ gọi số {phone(user?.phone)} để xác nhận, trong giờ làm việc 7:00 – 17:00.
        </T>
      </View>
      {!a ? (
        <Loading />
      ) : (
        <>
          <Card gap={6}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <T size={17} weight={700}>
                {apptWhen(a.start)}
              </T>
              <Tag text={appointmentStatus[a.status].text} kind={appointmentStatus[a.status].kind} />
            </View>
            <T size={13} color={colors.muted}>
              BS {a.doctorName}, {a.specialtyName}, phòng {a.roomCode}
            </T>
          </Card>
          <Card gap={12}>
            <T size={15} weight={700}>
              Tiếp theo
            </T>
            <Next title="Lễ tân gọi xác nhận" sub="Bạn sẽ nhận thông báo khi lịch được xác nhận" />
            {daysUntil(a.date) >= 2 ? <Next title="Nhắc lịch trước 1 ngày" sub={`Gửi thông báo lúc ${time(a.start)} ${dayLabel(addDays(a.date, -1))}`} /> : null}
            <Next title="Đến khám, mở mã check-in" sub="Đưa mã cho lễ tân quét để lấy số" />
          </Card>
        </>
      )}
    </Screen>
  );
}

function Next({ title, sub }: { title: string; sub: string }) {
  return (
    <View style={{ flexDirection: 'row', gap: 12 }}>
      <View style={{ width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: colors.line, marginTop: 1 }} />
      <View style={{ flex: 1 }}>
        <T weight={500}>{title}</T>
        <T size={12} color={colors.muted}>
          {sub}
        </T>
      </View>
    </View>
  );
}
