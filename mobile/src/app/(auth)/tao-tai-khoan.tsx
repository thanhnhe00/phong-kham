import { Link } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { Icon } from '@/components/icon';
import { SmsVerify } from '@/components/sms-verify';
import { T } from '@/components/text';
import { BackHeader, Button, ErrorNote, Field, Intro, Progress, Screen } from '@/components/ui';
import { ApiError, errorText } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { cleanPhone, PHONE_RE, usePhoneCode } from '@/lib/phone-code';
import { colors } from '@/lib/theme';

/**
 * Đăng ký 3 bước: số điện thoại, nhắn tin xác minh tới phòng khám, mật khẩu.
 * Bước 4 (hồ sơ khám) ở màn tạo hồ sơ sau khi đăng nhập.
 */
export default function Register() {
  const { register } = useAuth();
  const sms = usePhoneCode('REGISTER');
  const [picked, setStep] = useState(1);
  const [phone, setPhone] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Phòng khám nhận được tin nhắn thì tự sang bước đặt mật khẩu
  const step = picked === 2 && sms.verified ? 3 : picked;

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

  const sendCode = () =>
    run(async () => {
      if (!PHONE_RE.test(cleanPhone(phone))) throw new ApiError('Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.', 400);
      await sms.send(cleanPhone(phone));
      setStep(2);
    });

  const pwOk = pw.length >= 8;
  const create = () =>
    run(async () => {
      if (!pwOk) throw new ApiError('Mật khẩu cần ít nhất 8 ký tự.', 400);
      if (pw !== pw2) throw new ApiError('Hai lần nhập mật khẩu chưa giống nhau.', 400);
      await register(sms.ticket!.phone, sms.ticket!.code, pw);
    });

  const show_ = (
    <Pressable onPress={() => setShow(!show)} accessibilityRole="button" hitSlop={8}>
      <T size={13} weight={600} color={colors.brand}>
        {show ? 'Ẩn' : 'Hiện'}
      </T>
    </Pressable>
  );

  return (
    <Screen
      footer={
        step === 1 ? (
          <Button title="Tiếp tục" onPress={sendCode} loading={busy} />
        ) : step === 2 ? null : (
          <Button title="Tạo tài khoản" onPress={create} loading={busy} />
        )
      }
    >
      <BackHeader title="Tạo tài khoản" />
      <Progress step={step} total={4} />
      {step === 1 ? (
        <>
          <Intro
            title="Số điện thoại của bạn"
            sub="Bạn sẽ nhắn một tin xác minh từ số này tới phòng khám. Số này cũng là số lễ tân gọi để xác nhận lịch khám."
          />
          <Field label="Số điện thoại" value={phone} onChangeText={setPhone} keyboardType="phone-pad" autoComplete="tel" autoFocus placeholder="0912 345 678" />
          <View style={{ flexDirection: 'row', gap: 4 }}>
            <T size={13} color={colors.muted}>
              Đã có tài khoản?
            </T>
            <Link href="/dang-nhap" accessibilityRole="link">
              <T size={13} weight={600} color={colors.brand}>
                Đăng nhập
              </T>
            </Link>
          </View>
        </>
      ) : step === 2 ? (
        <>
          <Intro title="Nhắn tin để xác minh" sub="Nhờ tin nhắn từ chính SIM của bạn, phòng khám biết số điện thoại này đúng là của bạn." />
          <SmsVerify sms={sms} onResend={sendCode} />
          <Pressable onPress={() => setStep(1)} accessibilityRole="button">
            <T size={13} weight={600} color={colors.brand}>
              Đổi số điện thoại
            </T>
          </Pressable>
        </>
      ) : (
        <>
          <Intro title="Đặt mật khẩu" sub="Dùng mật khẩu này cùng số điện thoại để đăng nhập." />
          <Field label="Mật khẩu" value={pw} onChangeText={setPw} secureTextEntry={!show} autoComplete="new-password" right={show_} autoFocus />
          <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
            <Icon name="check" size={16} stroke={3} color={pwOk ? colors.brand : colors.disabled} />
            <T size={13} color={pwOk ? colors.brand : colors.muted}>
              Từ 8 ký tự trở lên
            </T>
          </View>
          <Field label="Nhập lại mật khẩu" value={pw2} onChangeText={setPw2} secureTextEntry={!show} autoComplete="new-password" onSubmitEditing={create} />
        </>
      )}
      <ErrorNote text={error} />
    </Screen>
  );
}
