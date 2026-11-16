import { router } from 'expo-router';
import { useState } from 'react';
import { T } from '@/components/text';
import { BackHeader, Button, ErrorNote, Field, Note, Screen } from '@/components/ui';
import { errorText } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { colors } from '@/lib/theme';

export default function ChangePassword() {
  const { changePassword } = useAuth();
  const [cur, setCur] = useState('');
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const save = async () => {
    setError(null);
    if (pw.length < 8) return setError('Mật khẩu mới cần ít nhất 8 ký tự.');
    if (pw !== pw2) return setError('Hai lần nhập mật khẩu mới chưa giống nhau.');
    setBusy(true);
    try {
      await changePassword(cur, pw);
      setDone(true);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen footer={done ? <Button title="Xong" onPress={() => router.back()} /> : <Button title="Đổi mật khẩu" onPress={save} loading={busy} />}>
      <BackHeader title="Đổi mật khẩu" />
      {done ? (
        <Note>
          <T color={colors.brandDark}>Đã đổi mật khẩu. Các máy khác đang đăng nhập tài khoản này đã bị đăng xuất.</T>
        </Note>
      ) : (
        <>
          <Field label="Mật khẩu hiện tại" value={cur} onChangeText={setCur} secureTextEntry autoComplete="current-password" />
          <Field label="Mật khẩu mới" value={pw} onChangeText={setPw} secureTextEntry autoComplete="new-password" hint="Từ 8 ký tự trở lên." />
          <Field label="Nhập lại mật khẩu mới" value={pw2} onChangeText={setPw2} secureTextEntry autoComplete="new-password" />
        </>
      )}
      <ErrorNote text={error} />
    </Screen>
  );
}
