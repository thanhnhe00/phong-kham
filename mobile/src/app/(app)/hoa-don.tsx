import { router } from 'expo-router';
import { T } from '@/components/text';
import { BackHeader, Card, Loading, RowList, Screen } from '@/components/ui';
import { dmy, vnd } from '@/lib/format';
import { useProfile } from '@/lib/profile';
import { useRecentRecords, useVisits } from '@/lib/queries';
import { colors } from '@/lib/theme';

/** Hóa đơn các lượt khám gần đây của hồ sơ đang xem: chưa trả xếp trước. */
export default function Invoices() {
  const { current } = useProfile();
  const visits = useVisits(current?.patient.id ?? null);
  const { records, loading } = useRecentRecords(visits.data, 10);
  const list = records
    .filter((r) => r.invoice)
    .sort((a, b) => Number(b.invoice!.remaining > 0) - Number(a.invoice!.remaining > 0) || b.visit.date.localeCompare(a.visit.date));

  return (
    <Screen>
      <BackHeader title="Hóa đơn" sub={current?.patient.fullName} />
      {visits.isLoading || loading ? (
        <Loading />
      ) : list.length === 0 ? (
        <Card>
          <T weight={600}>Chưa có hóa đơn nào</T>
          <T size={13} color={colors.muted}>
            Hóa đơn được lập khi bác sĩ hoàn tất lượt khám, gồm phí khám và các dịch vụ đã làm.
          </T>
        </Card>
      ) : (
        <RowList
          rows={list.map((r) => {
            const inv = r.invoice!;
            const due = inv.remaining > 0;
            return {
              icon: 'card',
              tint: 'honey',
              title: due ? `Còn phải trả ${vnd(inv.remaining)}` : `Đã trả ${vnd(inv.paidAmount)}`,
              sub: `${inv.code}, lần khám ${dmy(r.visit.date)}`,
              tag: due ? { text: 'Cần thanh toán', kind: 'amber' } : { text: 'Đã thanh toán', kind: 'teal' },
              action: due ? 'Thanh toán' : undefined,
              onPress: () => router.push(`/luot-kham/${r.visit.id}`),
            };
          })}
        />
      )}
      <T size={12} color={colors.muted}>
        Phòng khám chỉ kê đơn, không bán thuốc nên hóa đơn không có tiền thuốc.
      </T>
    </Screen>
  );
}
