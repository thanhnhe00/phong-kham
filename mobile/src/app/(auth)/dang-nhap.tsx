import { Link } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { Button, ErrorNote, Field, Screen } from '@/components/ui';
import { errorText } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useBooking } from '@/lib/booking';
import { apptWhen } from '@/lib/format';
import { colors } from '@/lib/theme';

export default function Login() {
  const { login } = useAuth();
  const { draft } = useBooking();
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!/^0\d{9}$/.test(phone.replace(/\s/g, ''))) return setError('Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.');
    if (!password) return setError('Vui lòng nhập mật khẩu.');
    setBusy(true);
    try {
      await login(phone.replace(/\s/g, ''), password);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <View style={{ gap: 14, paddingTop: 40 }}>
        <View style={{ width: 56, height: 56, borderRadius: 16, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="plus" size={30} color="#FFFFFF" stroke={2.4} />
        </View>
        <T size={26} weight={800} accessibilityRole="header">
          Phòng khám An Tâm
        </T>
        <T size={15} color={colors.muted}>
          Đặt lịch khám, xem kết quả và đơn thuốc ngay trên điện thoại.
        </T>
      </View>
      {draft.resume && draft.slot ? (
        <T size={14} color={colors.brandDark} testID="dang-dat-do">
          Đăng nhập hoặc đăng ký để giữ giờ {apptWhen(draft.slot.start)} với BS {draft.doctorName}.
        </T>
      ) : null}
      <Field label="Số điện thoại" value={phone} onChangeText={setPhone} keyboardType="phone-pad" autoComplete="tel" placeholder="0912 345 678" />
      <Field
        label="Mật khẩu"
        value={password}
        onChangeText={setPassword}
        secureTextEntry={!show}
        autoComplete="password"
        onSubmitEditing={submit}
        right={
          <Pressable onPress={() => setShow(!show)} accessibilityRole="button" hitSlop={8}>
            <T size={13} weight={600} color={colors.brand}>
              {show ? 'Ẩn' : 'Hiện'}
            </T>
          </Pressable>
        }
      />
      <Link href="/quen-mat-khau" accessibilityRole="link" style={{ alignSelf: 'flex-end' }}>
        <T size={13} weight={600} color={colors.brand}>
          Quên mật khẩu?
        </T>
      </Link>
      <ErrorNote text={error} />
      <Button title="Đăng nhập" onPress={submit} loading={busy} />
      <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 4 }}>
        <T color={colors.muted}>Chưa có tài khoản?</T>
        <Link href="/tao-tai-khoan" accessibilityRole="link">
          <T weight={600} color={colors.brand}>
            Đăng ký bằng số điện thoại
          </T>
        </Link>
      </View>
      <Link href="/dat-lich" accessibilityRole="link" style={{ alignSelf: 'center' }}>
        <T weight={600} color={colors.brand}>
          Xem bác sĩ và giờ trống
        </T>
      </Link>
    </Screen>
  );
}
