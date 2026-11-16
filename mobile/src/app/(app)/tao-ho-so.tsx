import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { BackHeader, Button, ErrorNote, Field, Intro, Pill, Progress, Screen } from '@/components/ui';
import { api, ApiError, errorText } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useBooking } from '@/lib/booking';
import { age, parseDmy } from '@/lib/format';
import { relationshipChoices } from '@/lib/labels';
import { useProfile } from '@/lib/profile';
import { qk } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { MyPatient, Relationship, Sex } from '@/lib/types';

/** Tạo hồ sơ khám: của chính mình (bước 4 sau đăng ký) hoặc của người thân. */
export default function CreateProfile() {
  const { nguoiThan } = useLocalSearchParams<{ nguoiThan?: string }>();
  const { patients, select } = useProfile();
  const { user } = useAuth();
  const qc = useQueryClient();
  const { draft } = useBooking();
  const forSelf = !nguoiThan && !patients.some((p) => p.relationship === 'SELF');
  const [rel, setRel] = useState<Relationship>(forSelf ? 'SELF' : 'PARENT');
  const [name, setName] = useState('');
  const [dob, setDob] = useState('');
  const [sex, setSex] = useState<Sex>('MALE');
  const [idNo, setIdNo] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  const iso = parseDmy(dob);
  const adult = iso ? age(iso) >= 18 : false;

  const save = async () => {
    setError(null);
    setFields({});
    if (!name.trim()) return setFields({ fullName: 'Vui lòng nhập họ tên.' });
    if (!iso) return setFields({ dateOfBirth: 'Nhập ngày sinh theo dạng ngày/tháng/năm, ví dụ 15/03/1985.' });
    if (idNo && !/^\d{12}$/.test(idNo)) return setFields({ identityNumber: 'Số CCCD gồm đúng 12 chữ số.' });
    setBusy(true);
    try {
      const created = await api.post<MyPatient>('/me/patients', {
        relationship: forSelf ? 'SELF' : rel,
        profile: {
          fullName: name.trim(),
          dateOfBirth: iso,
          sex,
          identityType: idNo ? 'CCCD' : 'NOT_PROVIDED',
          identityNumber: idNo || null,
          phone: forSelf ? user?.phone : null,
        },
      });
      await qc.invalidateQueries({ queryKey: qk.patients });
      if (created?.patient?.id) select(created.patient.id);
      // khách chọn giờ trước rồi mới đăng ký: tạo hồ sơ xong đi thẳng tới bước xác nhận
      if (draft.resume && draft.slot) router.replace('/dat-lich/xac-nhan');
      else if (router.canGoBack()) router.back();
      else router.replace('/');
    } catch (e) {
      setError(errorText(e));
      if (e instanceof ApiError) setFields(Object.fromEntries(Object.entries(e.fieldErrors).map(([k, v]) => [k.replace('profile.', ''), v])));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen footer={<Button title="Lưu hồ sơ" onPress={save} loading={busy} />}>
      {forSelf ? <Progress step={4} total={4} /> : <BackHeader title="Thêm hồ sơ người thân" />}
      <Intro
        title={forSelf ? 'Thông tin người khám' : 'Hồ sơ người thân'}
        sub={forSelf ? 'Điền đúng như trên CCCD để lễ tân đối chiếu khi bạn đến khám.' : 'Bạn sẽ đặt lịch và xem kết quả khám thay cho người này.'}
      />
      {!forSelf ? (
        <View style={{ gap: 8 }}>
          <T weight={600}>Người này là ai của bạn?</T>
          <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {relationshipChoices.map((c) => (
              <View key={c.value} style={{ width: '48%' }}>
                <Pill text={c.label} on={rel === c.value} onPress={() => setRel(c.value)} />
              </View>
            ))}
          </View>
        </View>
      ) : null}
      <Field label="Họ và tên" value={name} onChangeText={setName} autoComplete="name" error={fields.fullName} />
      <Field label="Ngày sinh" value={dob} onChangeText={setDob} placeholder="ngày/tháng/năm" keyboardType="numbers-and-punctuation" error={fields.dateOfBirth} />
      <View style={{ gap: 8 }}>
        <T weight={600}>Giới tính</T>
        <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', gap: 8 }}>
          <Pill text="Nam" on={sex === 'MALE'} onPress={() => setSex('MALE')} />
          <Pill text="Nữ" on={sex === 'FEMALE'} onPress={() => setSex('FEMALE')} />
        </View>
      </View>
      <Field
        label="Số CCCD"
        value={idNo}
        onChangeText={(t) => setIdNo(t.replace(/\D/g, '').slice(0, 12))}
        keyboardType="number-pad"
        placeholder="12 số"
        error={fields.identityNumber}
        hint={adult ? 'Cần có với người từ 18 tuổi để bác sĩ kê đơn thuốc.' : 'Trẻ em chưa có CCCD thì để trống.'}
      />
      {forSelf ? (
        <Pressable onPress={() => router.push('/lien-ket')} accessibilityRole="link" style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
          <Icon name="link" size={18} color={colors.brand} />
          <T size={13} weight={600} color={colors.brand}>
            Đã từng khám ở đây? Liên kết hồ sơ cũ
          </T>
        </Pressable>
      ) : null}
      <ErrorNote text={error} />
    </Screen>
  );
}
