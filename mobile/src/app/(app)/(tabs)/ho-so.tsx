import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { ProfilePicker } from '@/components/home';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { Avatar, Card, Loading, LoadError, RowList, Screen, Segmented, Tag, type ListRowProps } from '@/components/ui';
import { errorText } from '@/lib/api';
import { age, dm, dmy, maskId, monthTitle } from '@/lib/format';
import { relationshipLabel, sexLabel } from '@/lib/labels';
import { useProfile } from '@/lib/profile';
import { usePatientDetail, useRecentRecords, useVisits } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { Visit } from '@/lib/types';

const TABS = ['Lần khám', 'Kết quả', 'Đơn thuốc'];

export default function HealthRecord() {
  const params = useLocalSearchParams<{ xem?: string }>();
  const { current } = useProfile();
  const id = current?.patient.id ?? null;
  const detail = usePatientDetail(id);
  const visits = useVisits(id);
  const [tab, setTab] = useState(params.xem === 'don-thuoc' ? 2 : params.xem === 'ket-qua' ? 1 : 0);
  const [picking, setPicking] = useState(false);
  // Kết quả và đơn thuốc nằm trong hồ sơ từng lượt khám: chỉ tải khi mở các mục đó
  const { records, loading } = useRecentRecords(tab === 0 ? [] : visits.data, 10);

  if (!current) return <Loading />;
  const p = detail.data?.patient ?? current.patient;
  const done = (visits.data ?? []).filter((v) => v.status === 'COMPLETED');

  return (
    <Screen>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Pressable onPress={() => setPicking(true)} accessibilityRole="button" accessibilityLabel={`Đổi hồ sơ, đang xem ${p.fullName}`} style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <Avatar name={p.fullName} />
          <View>
            <T size={13} color={colors.muted}>
              {relationshipLabel[current.relationship]}
            </T>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
              <T size={18} weight={700}>
                {p.fullName}
              </T>
              <Icon name="down" size={18} color={colors.muted} />
            </View>
          </View>
        </Pressable>
      </View>
      <ProfilePicker visible={picking} onClose={() => setPicking(false)} />

      <Card gap={8}>
        <T weight={600}>Mã bệnh nhân {p.patientCode}</T>
        <T size={13} color={colors.muted}>
          {p.sex ? sexLabel[p.sex] + ', ' : ''}sinh {dmy(p.dateOfBirth)} ({age(p.dateOfBirth)} tuổi)
        </T>
        <T size={13} color={colors.muted}>
          Giấy tờ: {maskId(p.identityNumber)}
        </T>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
          <T size={13} weight={600}>
            Dị ứng:
          </T>
          {detail.data?.allergies.length ? (
            detail.data.allergies.map((a) => <Tag key={a.id} text={a.allergen} kind="red" />)
          ) : (
            <T size={13} color={colors.muted}>
              Chưa ghi nhận
            </T>
          )}
        </View>
      </Card>

      <Segmented items={TABS} value={tab} onChange={setTab} />

      {visits.isLoading ? (
        <Loading />
      ) : visits.error ? (
        <LoadError message={errorText(visits.error)} onRetry={() => visits.refetch()} />
      ) : done.length === 0 ? (
        <Card>
          <T weight={600}>Chưa có lần khám nào</T>
          <T size={13} color={colors.muted}>
            Sau mỗi lần khám, kết luận, kết quả xét nghiệm và đơn thuốc sẽ được lưu ở đây.
          </T>
        </Card>
      ) : tab === 0 ? (
        <VisitList visits={done} />
      ) : loading ? (
        <Loading />
      ) : tab === 1 ? (
        <ItemList
          empty="Chưa có kết quả xét nghiệm nào được bác sĩ duyệt."
          rows={records.flatMap((r) =>
            r.orders.flatMap((o) =>
              o.items
                .filter((i) => i.performances.some((pf) => pf.result))
                .map<ListRowProps>((i) => {
                  const res = i.performances.find((pf) => pf.result)?.result;
                  return {
                    icon: 'flask',
                    tint: 'peach',
                    title: i.serviceName,
                    sub: `${dm(r.visit.date)}${res?.conclusion ? `, ${res.conclusion}` : ''}`,
                    onPress: () => router.push(`/luot-kham/${r.visit.id}`),
                  };
                }),
            ),
          )}
        />
      ) : (
        <ItemList
          empty="Chưa có đơn thuốc nào."
          rows={records.flatMap((r) =>
            r.prescriptions
              .filter((x) => x.status !== 'REPLACED')
              .map<ListRowProps>((x) => ({
                icon: 'rx',
                tint: 'sky',
                title: `Đơn ${x.items.length} loại thuốc`,
                sub: `${dm(r.visit.date)}, ${x.items.map((i) => i.medicineLabel).slice(0, 2).join(', ')}`,
                onPress: () => router.push(`/luot-kham/${r.visit.id}`),
              })),
          )}
        />
      )}
    </Screen>
  );
}

function VisitList({ visits }: { visits: Visit[] }) {
  const groups = new Map<string, Visit[]>();
  for (const v of visits) groups.set(v.date.slice(0, 7), [...(groups.get(v.date.slice(0, 7)) ?? []), v]);
  return (
    <View style={{ gap: 4 }}>
      {[...groups.entries()].map(([month, list]) => (
        <View key={month}>
          <T size={12} weight={600} color={colors.muted} style={{ paddingTop: 10, paddingBottom: 4 }}>
            {monthTitle(month + '-01')}
          </T>
          {list.map((v) => (
            <Pressable
              key={v.id}
              onPress={() => router.push(`/luot-kham/${v.id}`)}
              accessibilityRole="button"
              style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, borderTopWidth: 1, borderTopColor: '#E3EBE7' }}
            >
              <View style={{ width: 44, alignItems: 'center' }}>
                <T size={16} weight={700}>
                  {v.date.slice(8)}
                </T>
                <T size={12} color={colors.muted}>
                  Th{Number(v.date.slice(5, 7))}
                </T>
              </View>
              <View style={{ flex: 1 }}>
                <T weight={600}>{v.specialtyName}</T>
                <T size={13} color={colors.muted}>
                  BS {v.doctorName}
                </T>
              </View>
              <Icon name="right" size={18} color={colors.muted} />
            </Pressable>
          ))}
        </View>
      ))}
    </View>
  );
}

function ItemList({ rows, empty }: { rows: ListRowProps[]; empty: string }) {
  if (rows.length === 0)
    return (
      <Card>
        <T size={13} color={colors.muted}>
          {empty}
        </T>
      </Card>
    );
  return <RowList rows={rows} />;
}
