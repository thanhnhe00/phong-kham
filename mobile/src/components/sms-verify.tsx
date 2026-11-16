import { ActivityIndicator, Linking, Platform, Pressable, View } from 'react-native';
import type { usePhoneCode } from '@/lib/phone-code';
import { smsUrl } from '@/lib/sms';
import { colors } from '@/lib/theme';
import { Icon } from './icon';
import { T } from './text';
import { Button, Card, ErrorNote, Note } from './ui';

/** Bước "nhắn tin để xác minh": hiện nội dung cần nhắn, nút mở sẵn màn soạn tin, trạng thái chờ phòng khám nhận tin. */
export function SmsVerify({ sms, onResend }: { sms: ReturnType<typeof usePhoneCode>; onResend: () => void }) {
  const t = sms.ticket;
  if (!t) return null;
  if (!t.smsTo) {
    return <Note kind="amber">Phòng khám chưa cài đặt số nhận tin nhắn xác minh. Vui lòng gọi phòng khám để được hỗ trợ.</Note>;
  }
  return (
    <View style={{ gap: 14 }}>
      <Card gap={10}>
        <T size={13} color={colors.muted}>
          Từ số {t.phone}, nhắn tin
        </T>
        <T size={28} weight={800} tabular testID="sms-body" selectable>
          {t.smsBody}
        </T>
        <T size={13} color={colors.muted}>
          tới số của phòng khám
        </T>
        <T size={20} weight={700} tabular testID="sms-to" selectable>
          {t.smsTo}
        </T>
      </Card>
      {sms.verified ? null : (
        <Button title="Mở tin nhắn đã soạn sẵn" icon="phone" onPress={() => Linking.openURL(smsUrl(t.smsTo!, t.smsBody, Platform.OS)).catch(() => undefined)} />
      )}
      {sms.verified ? (
        <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }} testID="sms-verified">
          <Icon name="check" size={18} stroke={3} color={colors.brand} />
          <T weight={600} color={colors.brand}>
            Phòng khám đã nhận tin nhắn, số điện thoại đã được xác minh.
          </T>
        </View>
      ) : sms.pollError ? (
        <ErrorNote text={sms.pollError} />
      ) : (
        <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
          <ActivityIndicator color={colors.brand} />
          <T size={13} color={colors.muted} style={{ flex: 1 }}>
            Đang chờ tin nhắn của bạn. Phí tin nhắn theo giá nhà mạng; tin phải gửi từ đúng số {t.phone}.
          </T>
        </View>
      )}
      {sms.verified ? null : (
        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <T size={13} color={colors.muted}>
            Mã có hiệu lực 10 phút.
          </T>
          {sms.wait > 0 ? (
            <T size={13} color={colors.muted}>
              Lấy mã mới sau {sms.wait} giây
            </T>
          ) : (
            <Pressable onPress={onResend} accessibilityRole="button">
              <T size={13} weight={600} color={colors.brand}>
                Lấy mã mới
              </T>
            </Pressable>
          )}
        </View>
      )}
    </View>
  );
}
