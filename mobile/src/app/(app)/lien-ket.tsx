import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { T } from '@/components/text';
import { BackHeader, Button, ErrorNote, Field, Note, Pill, Screen } from '@/components/ui';
import { api, errorText } from '@/lib/api';
import { relationshipChoices } from '@/lib/labels';
import { useProfile } from '@/lib/profile';
import { qk } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { MyPatient, Relationship } from '@/lib/types';

/** Liên kết hồ sơ đã có ở phòng khám bằng mã 8 ký tự lễ tân cấp (hạn 24 giờ, dùng một lần). */
export default function LinkProfile() {
  const { patients, select } = useProfile();
  const qc = useQueryClient();
  const hasSelf = patients.some((p) => p.relationship === 'SELF');
  const choices = [...(hasSelf ? [] : [{ value: 'SELF' as Relationship, label: 'Bản thân tôi' }]), ...relationshipChoices];
  const [code, setCode] = useState('');
  const [rel, setRel] = useState<Relationship>(choices[0].value);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const linked = await api.post<MyPatient>('/me/patients/link', { code: code.replace(/\s/g, '').toUpperCase(), relationship: rel });
      await qc.invalidateQueries({ queryKey: qk.patients });
      if (linked?.patient?.id) select(linked.patient.id);
      router.replace('/');
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen footer={<Button title="Liên kết hồ sơ" onPress={submit} loading={busy} disabled={code.replace(/\s/g, '').length < 8} />}>
      <BackHeader title="Liên kết hồ sơ" />
      <T size={14} color={colors.muted}>
        Nếu bạn hoặc người thân đã từng khám tại phòng khám, hãy xin lễ tân mã liên kết để xem lại lịch sử khám, kết quả và đơn thuốc cũ trên ứng dụng.
      </T>
      <Field label="Mã liên kết" value={code} onChangeText={setCode} autoCapitalize="characters" maxLength={9} placeholder="8 ký tự" hint="Mã có hạn 24 giờ và chỉ dùng được một lần." />
      <View style={{ gap: 8 }}>
        <T weight={600}>Người có hồ sơ là ai của bạn?</T>
        <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {choices.map((c) => (
            <View key={c.value} style={{ width: '48%' }}>
              <Pill text={c.label} on={rel === c.value} onPress={() => setRel(c.value)} />
            </View>
          ))}
        </View>
      </View>
      <Note>
        <T size={13} color={colors.brandDark}>
          Vì bảo mật, mỗi hồ sơ chỉ liên kết được khi có mã do phòng khám cấp sau khi đối chiếu giấy tờ.
        </T>
      </Note>
      <ErrorNote text={error} />
    </Screen>
  );
}
