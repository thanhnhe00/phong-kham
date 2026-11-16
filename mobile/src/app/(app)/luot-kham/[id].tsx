import { useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { Icon } from '@/components/icon';
import { T } from '@/components/text';
import { BackHeader, Button, Card, ErrorNote, Field, Loading, LoadError, Note, Screen, Tag } from '@/components/ui';
import { api, errorText } from '@/lib/api';
import { dmy, vnd } from '@/lib/format';
import { openResultFile } from '@/lib/open-file';
import { useRecord } from '@/lib/queries';
import { colors } from '@/lib/theme';
import type { ResultFile } from '@/lib/types';

const fileSize = (b: number) => (b >= 1_048_576 ? `${(b / 1_048_576).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);

/** Chi tiết một lượt khám: chỉ bệnh án đã ký, kết quả đã duyệt, đơn đã ký (backend đã lọc), hóa đơn và góp ý. */
export default function VisitDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const q = useRecord(Number(id));
  const qc = useQueryClient();
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState('');
  const [sentFeedback, setSentFeedback] = useState(false);
  const [opening, setOpening] = useState<number | null>(null);

  const openFile = async (f: ResultFile) => {
    setOpening(f.id);
    setError(null);
    try {
      await openResultFile(f);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setOpening(null);
    }
  };

  if (q.isLoading) return <Loading />;
  if (q.error || !q.data)
    return (
      <Screen>
        <BackHeader title="Lượt khám" />
        <LoadError message={errorText(q.error)} onRetry={() => q.refetch()} />
      </Screen>
    );
  const { visit: v, emr, orders, prescriptions, invoice, vitals } = q.data;
  const primary = emr?.diagnoses.find((d) => d.type === 'PRIMARY');
  const others = emr?.diagnoses.filter((d) => d.type === 'SECONDARY') ?? [];
  const results = orders.flatMap((o) => o.items).filter((i) => i.performances.some((p) => p.result));
  const rx = prescriptions.filter((p) => p.status !== 'REPLACED');
  const vit = vitals[0];

  const pay = async () => {
    if (!invoice) return;
    setPaying(true);
    setError(null);
    try {
      await api.post(`/me/invoices/${invoice.id}/pay`);
      await qc.invalidateQueries();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setPaying(false);
    }
  };
  const sendFeedback = async () => {
    if (!feedback.trim()) return;
    setError(null);
    try {
      await api.post('/me/feedback', { patientId: v.patientId, visitId: v.id, category: 'Lượt khám', content: feedback.trim() });
      setSentFeedback(true);
    } catch (e) {
      setError(errorText(e));
    }
  };

  return (
    <Screen>
      <BackHeader title={`Lượt khám ${dmy(v.date)}`} sub={`BS ${v.doctorName}, ${v.specialtyName}`} />

      {v.status !== 'COMPLETED' ? (
        <Note kind="amber">
          <T size={13} color={colors.amberInk}>
            Lượt khám đang diễn ra. Kết luận, kết quả và đơn thuốc sẽ hiện khi bác sĩ hoàn tất.
          </T>
        </Note>
      ) : null}

      {emr ? (
        <Card gap={6}>
          <T size={13} color={colors.muted}>
            Bác sĩ kết luận
          </T>
          <T size={18} weight={800}>
            {primary?.name ?? emr.conclusion ?? 'Chưa có kết luận'}
          </T>
          {others.length ? (
            <T size={13} color={colors.muted}>
              Kèm theo: {others.map((d) => d.name).join(', ')}
            </T>
          ) : null}
          {emr.treatmentPlan ? <T size={13}>{emr.treatmentPlan}</T> : null}
          {emr.advice ? (
            <T size={13} color={colors.muted}>
              Lời dặn: {emr.advice}
            </T>
          ) : null}
          {emr.followUpDate ? (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingTop: 8, borderTopWidth: 1, borderTopColor: '#EEF2F1', marginTop: 4 }}>
              <T size={13}>Hẹn tái khám</T>
              <T size={13} weight={700} color={colors.brand}>
                {dmy(emr.followUpDate)}
              </T>
            </View>
          ) : null}
        </Card>
      ) : null}

      {vit ? (
        <Card gap={8}>
          <T weight={700}>Chỉ số lúc khám</T>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', rowGap: 8 }}>
            {[
              ['Huyết áp', vit.systolicBp && vit.diastolicBp ? `${vit.systolicBp}/${vit.diastolicBp}` : null],
              ['Mạch', vit.pulsePerMin ? `${vit.pulsePerMin} lần/phút` : null],
              ['Nhiệt độ', vit.temperatureC ? `${String(vit.temperatureC).replace('.', ',')} °C` : null],
              ['Cân nặng', vit.weightKg ? `${String(vit.weightKg).replace('.', ',')} kg` : null],
            ]
              .filter(([, x]) => x)
              .map(([k, x]) => (
                <View key={k} style={{ width: '50%' }}>
                  <T size={12} color={colors.muted}>
                    {k}
                  </T>
                  <T weight={600}>{x}</T>
                </View>
              ))}
          </View>
        </Card>
      ) : null}

      {results.length ? (
        <Card gap={10}>
          <T weight={700}>Kết quả xét nghiệm</T>
          {results.map((i) => {
            const r = i.performances.find((p) => p.result)!.result!;
            return (
              <View key={i.id} style={{ gap: 2 }}>
                <T weight={600}>{i.serviceName}</T>
                {r.conclusion ? <T size={13}>{r.conclusion}</T> : null}
                {r.resultText ? (
                  <T size={13} color={colors.muted}>
                    {r.resultText}
                  </T>
                ) : null}
                {r.files.map((f) => (
                  <Pressable
                    key={f.id}
                    onPress={() => openFile(f)}
                    disabled={opening === f.id}
                    accessibilityRole="button"
                    accessibilityLabel={`Xem tệp ${f.fileName}`}
                    style={({ pressed }) => ({ flexDirection: 'row', gap: 10, alignItems: 'center', marginTop: 6, padding: 10, borderRadius: 12, backgroundColor: pressed ? '#E4EFEA' : colors.bg })}
                  >
                    <Icon name="file" size={18} color={colors.brand} />
                    <View style={{ flex: 1 }}>
                      <T size={13} weight={600} numberOfLines={1}>
                        {f.fileName}
                      </T>
                      <T size={12} color={colors.muted}>
                        {f.mimeType === 'application/pdf' ? 'PDF' : 'Ảnh'}, {fileSize(f.sizeBytes)}
                      </T>
                    </View>
                    {opening === f.id ? (
                      <ActivityIndicator color={colors.brand} />
                    ) : (
                      <T size={13} weight={700} color={colors.brand}>
                        Xem
                      </T>
                    )}
                  </Pressable>
                ))}
              </View>
            );
          })}
        </Card>
      ) : null}

      {rx.map((p) => (
        <Card key={p.id} gap={10}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <T weight={700}>Đơn thuốc</T>
            {p.code ? (
              <T size={12} color={colors.muted}>
                {p.code}
              </T>
            ) : null}
          </View>
          {p.items.map((it) => (
            <View key={it.lineNo} style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
              <View style={{ flex: 1 }}>
                <T weight={500}>{it.medicineLabel}</T>
                <T size={12} color={colors.muted}>
                  {it.dosePerTime} mỗi lần, {it.timesPerDay} lần/ngày{it.timing ? `, ${it.timing}` : ''}, {it.days} ngày
                </T>
              </View>
              <T color={colors.muted} tabular>
                {it.quantity} {it.unit}
              </T>
            </View>
          ))}
          {p.advice ? (
            <T size={13} color={colors.muted}>
              Lời dặn: {p.advice}
            </T>
          ) : null}
          <T size={12} color={colors.muted}>
            Mua thuốc tại nhà thuốc, mang theo đơn này.
          </T>
        </Card>
      ))}

      {invoice ? (
        <View style={{ backgroundColor: invoice.remaining > 0 ? colors.amberBg : colors.card, borderRadius: 18, padding: 16, gap: 8 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <T weight={700}>Hóa đơn {invoice.code}</T>
            <Tag text={invoice.status === 'PAID' ? 'Đã thanh toán' : invoice.remaining > 0 ? 'Chưa thanh toán' : 'Đã xử lý'} kind={invoice.status === 'PAID' ? 'teal' : 'amber'} />
          </View>
          {invoice.items.map((it) => (
            <View key={it.id} style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
              <T color={colors.muted} style={{ flex: 1 }}>
                {it.description}
              </T>
              <T tabular>{vnd(it.lineTotal)}</T>
            </View>
          ))}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingTop: 6 }}>
            <T weight={800} size={16}>
              {invoice.remaining > 0 ? 'Còn phải trả' : 'Tổng cộng'}
            </T>
            <T weight={800} size={16} tabular>
              {vnd(invoice.remaining > 0 ? invoice.remaining : invoice.totalAmount)}
            </T>
          </View>
          {invoice.remaining > 0 ? <Button title="Thanh toán trên app" onPress={pay} loading={paying} height={46} /> : null}
        </View>
      ) : null}

      {v.status === 'COMPLETED' ? (
        <Card gap={10}>
          <T weight={700}>Góp ý cho lần khám này</T>
          {sentFeedback ? (
            <T size={13} color={colors.brand}>
              Đã gửi góp ý. Cảm ơn bạn, phòng khám sẽ xem và phản hồi nếu cần.
            </T>
          ) : (
            <>
              <Field label="Nội dung" value={feedback} onChangeText={setFeedback} multiline maxLength={4000} placeholder="Bạn hài lòng điều gì, phòng khám cần cải thiện điều gì?" />
              <Button title="Gửi góp ý" kind="ghost" height={44} onPress={sendFeedback} disabled={!feedback.trim()} />
            </>
          )}
        </Card>
      ) : null}
      <ErrorNote text={error} />
    </Screen>
  );
}
