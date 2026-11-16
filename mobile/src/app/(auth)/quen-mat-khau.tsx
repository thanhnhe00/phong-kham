import { useState } from 'react';
import { Pressable } from 'react-native';
import { SmsVerify } from '@/components/sms-verify';
import { T } from '@/components/text';
import { BackHeader, Button, ErrorNote, Field, Intro, Screen } from '@/components/ui';
import { ApiError, errorText } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { cleanPhone, PHONE_RE, usePhoneCode } from '@/lib/phone-code';
import { colors } from '@/lib/theme';

/** Quên mật khẩu: nhắn tin xác minh từ số đã đăng ký tới phòng khám, rồi đặt mật khẩu mới. */
export default function ForgotPassword() {
  const { resetPassword } = useAuth();
  const sms = usePhoneCode('RESET_PASSWORD');
  const [phone, setPhone] = useState('');
  const [pw, setPw] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sent = !!sms.ticket;

  const run = async (fn: () => Promise<void>) => {
    setError(null);
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  const send = () =>
    run(async () => {
      if (!PHONE_RE.test(cleanPhone(phone))) throw new ApiError('Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.', 400);
      await sms.send(cleanPhone(phone));
    });

  const save = () =>
    run(async () => {
      if (pw.length < 8) throw new ApiError('Mật khẩu mới cần ít nhất 8 ký tự.', 400);
      await resetPassword(sms.ticket!.phone, sms.ticket!.code, pw);
    });

  return (
    <Screen
      footer={
        !sent ? (
          <Button title="Tiếp tục" onPress={send} loading={busy} />
        ) : sms.verified ? (
          <Button title="Đổi mật khẩu" onPress={save} loading={busy} />
        ) : null
      }
    >
      <BackHeader title="Quên mật khẩu" />
      <Intro title="Đặt lại mật khẩu" sub="Nhắn một tin xác minh từ số đã đăng ký tới phòng khám, rồi đặt mật khẩu mới." />
      <Field
        label="Số điện thoại"
        value={phone}
        onChangeText={setPhone}
        keyboardType="phone-pad"
        editable={!sent}
      />
      {sent ? <SmsVerify sms={sms} onResend={send} /> : null}
      {sms.verified ? (
        <>
          <Field
            label="Mật khẩu mới"
            value={pw}
            onChangeText={setPw}
            secureTextEntry={!show}
            autoComplete="new-password"
            autoFocus
            hint={pw.length >= 8 ? 'Mật khẩu đủ độ dài.' : `Cần ít nhất 8 ký tự, còn thiếu ${8 - pw.length}.`}
            right={
              <Pressable onPress={() => setShow(!show)} accessibilityRole="button" hitSlop={8}>
                <T size={13} weight={600} color={colors.brand}>
                  {show ? 'Ẩn' : 'Hiện'}
                </T>
              </Pressable>
            }
          />
          <T size={12} color={colors.muted}>
            Sau khi đổi, các máy đang đăng nhập tài khoản này sẽ bị đăng xuất.
          </T>
        </>
      ) : null}
      <ErrorNote text={error} />
    </Screen>
  );
}
