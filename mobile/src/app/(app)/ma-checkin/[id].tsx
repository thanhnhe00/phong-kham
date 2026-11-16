import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { Icon } from '@/components/icon';
import { QrCode } from '@/components/qr-code';
import { T } from '@/components/text';
import { BackHeader, Loading, LoadError, Screen } from '@/components/ui';
import { errorText } from '@/lib/api';
import { apptWhen, dm, vnDate } from '@/lib/format';
import { fetchQr, useAppointment } from '@/lib/queries';
import { colors } from '@/lib/theme';

/** Mã QR check-in: mỗi lần mở backend cấp mã mới, dùng một lần, hết hạn cuối ngày khám. */
export default function CheckinCode() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const a = useAppointment(Number(id));
  const qr = useQuery({ queryKey: ['qr', id], queryFn: () => fetchQr(Number(id)), staleTime: Infinity, gcTime: 0 });

  return (
    <Screen>
      <BackHeader title="Mã check-in" />
      {qr.isLoading || a.isLoading ? (
        <Loading />
      ) : qr.error || !qr.data || !a.data ? (
        <LoadError message={errorText(qr.error ?? a.error)} onRetry={() => qr.refetch()} />
      ) : (
        <>
          <View style={{ backgroundColor: colors.card, borderRadius: 22, padding: 24, alignItems: 'center', gap: 14 }}>
            <T size={17} weight={700}>
              {a.data.patientName}
            </T>
            <T size={13} color={colors.muted} center>
              {apptWhen(a.data.start)}, BS {a.data.doctorName}, phòng {a.data.roomCode}
            </T>
            <View style={{ padding: 10, borderRadius: 14, borderWidth: 1, borderColor: colors.line }} testID="qr" accessibilityLabel={`Mã QR ${a.data.code}`}>
              <QrCode value={qr.data.token} />
            </View>
            <T size={18} weight={700} tabular style={{ letterSpacing: 1.5 }}>
              {a.data.code}
            </T>
            <T size={13} color={colors.muted} center>
              Đưa mã này cho lễ tân quét khi đến phòng khám. Mã chỉ dùng được một lần, hết hạn cuối ngày {dm(vnDate(new Date(Date.parse(qr.data.expiresAt) - 60_000)))}.
            </T>
          </View>
          <View style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
            <Icon name="sun" color={colors.muted} />
            <T size={13} color={colors.muted} style={{ flex: 1 }}>
              Tăng độ sáng màn hình nếu quét không được. Không có mạng thì đọc số điện thoại cho lễ tân.
            </T>
          </View>
        </>
      )}
    </Screen>
  );
}
