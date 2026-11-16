import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Icon } from '../../components/icons';
import { Area, Button, Card, Chip, Field, Loading, LoadError, Note, PageHead, Stub, Table, Tabs, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { decimal, dmy, personLine, time, timeDay, vitalsAlerts } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import { visitStatus } from '../../lib/labels';
import type { Emr, PatientDetail, Visit, VisitRecord } from '../../lib/types';
import { stageOf } from '../../lib/work';
import { OrdersTab } from './exam-orders';
import { RxTab } from './exam-rx';

type Tab = 'emr' | 'orders' | 'rx' | 'history';

export function ExamPage() {
  const id = Number(useParams().id);
  const nav = useNavigate();
  const rec = useGet<VisitRecord>(`/visits/${id}/record`, true);
  const v = rec.data?.visit;
  const patient = useGet<PatientDetail>(v ? `/patients/${v.patientId}` : null);
  const history = useGet<Visit[]>(v ? `/patients/${v.patientId}/visits` : null);
  const [tab, setTab] = useState<Tab>('emr');
  const { run, busy } = useRun();

  if (rec.isLoading) return <div className="page"><Loading /></div>;
  if (!rec.data || !v) return <div className="page"><LoadError error={rec.error} retry={() => rec.refetch()} /></div>;
  const r = rec.data;
  const editable = v.status === 'IN_PROGRESS' || v.status === 'AWAITING_RESULTS';
  const items = r.orders.flatMap((o) => o.items);
  const pendingItems = items.filter((i) => !['done', 'refused'].includes(stageOf(i)));
  const draftRx = r.prescriptions.filter((p) => p.status === 'DRAFT');
  const vit = r.vitals[r.vitals.length - 1];
  const others = (history.data ?? []).filter((h) => h.id !== v.id);

  return (
    <div className="page">
      <PageHead title="Khám bệnh" sub={<>Lượt khám {v.code}, {dmy(v.date)}. <Chip tone={visitStatus[v.status][1]}>{visitStatus[v.status][0]}</Chip></>}
        actions={<>
          <Button kind="ghost" onClick={() => nav('/bac-si')}>Về danh sách</Button>
          {v.status === 'CALLED' && <>
            <Button kind="danger" loading={busy === 'left'} onClick={() => window.confirm('Ghi nhận bệnh nhân không có mặt?') && run('left', () => api.post(`/visits/${v.id}/left`), 'Đã ghi nhận không có mặt').then((ok) => ok && nav('/bac-si'))}>Không có mặt</Button>
            <Button icon="check" loading={busy === 'start'} onClick={() => run('start', () => api.post(`/visits/${v.id}/start`), 'Bắt đầu khám')}>Bắt đầu khám</Button>
          </>}
          {v.status === 'AWAITING_RESULTS' && (
            <Button kind="outline" loading={busy === 'cb'} onClick={() => run('cb', () => api.post(`/visits/${v.id}/call-back`), 'Đã gọi lại vào phòng')}>Gọi lại vào phòng</Button>
          )}
        </>} />

      <section className="ticket flat" aria-label="Bệnh nhân">
        <Stub n={v.queueNumber} />
        <div className="t">
          <div className="stack tight" style={{ gap: 4 }}>
            <span style={{ fontSize: 17, fontWeight: 700 }}>{v.patientName}</span>
            <span className="muted small">{personLine(patient.data?.patient.sex, v.patientDateOfBirth)}, {v.patientCode}{v.appointmentId ? '' : ', khám không hẹn'}{v.companionName ? `, đi cùng ${v.companionName}` : ''}</span>
          </div>
          <div className="wrap">{patient.data?.allergies.map((a) => <Chip key={a.id} tone="red">Dị ứng {a.allergen}</Chip>)}</div>
          {vit ? (
            <div className="wrap num" style={{ gap: 22 }}>
              {vit.systolicBp != null && <div className="vit"><span>Huyết áp</span><span>{vit.systolicBp}/{vit.diastolicBp}</span></div>}
              {vit.pulsePerMin != null && <div className="vit"><span>Mạch</span><span>{vit.pulsePerMin}</span></div>}
              {vit.temperatureC != null && <div className="vit"><span>Nhiệt độ</span><span className={vit.temperatureC >= 38 ? 'red' : undefined}>{decimal(vit.temperatureC)} °C</span></div>}
              {vit.spo2Percent != null && <div className="vit"><span>SpO₂</span><span className={vit.spo2Percent < 94 ? 'red' : undefined}>{vit.spo2Percent}%</span></div>}
              {vit.weightKg != null && <div className="vit"><span>Cân nặng</span><span>{decimal(vit.weightKg)} kg</span></div>}
              <span className="muted xs">{vit.recordedByName} đo lúc {time(vit.recordedAt)}</span>
            </div>
          ) : <Chip>Chưa đo sinh hiệu</Chip>}
        </div>
      </section>
      {vitalsAlerts(vit).some((a) => a.severe) && <Note tone="red">{vitalsAlerts(vit).map((a) => a.text).join('; ')}.</Note>}
      {v.status === 'CALLED' && <Note tone="teal" icon="bell">Đã gọi bệnh nhân vào phòng. Bấm “Bắt đầu khám” khi bệnh nhân đã vào.</Note>}

      <Tabs value={tab} onChange={setTab} items={[
        { key: 'emr', label: 'Phiếu khám' },
        { key: 'orders', label: `Chỉ định và kết quả (${items.length})` },
        { key: 'rx', label: `Đơn thuốc (${r.prescriptions.length})` },
        { key: 'history', label: `Lịch sử khám (${others.length})` },
      ]} />
      {/* Phiếu khám và đơn thuốc chỉ ẩn khi đổi tab (không gỡ) để giữ chữ bác sĩ đang gõ dở */}
      <div hidden={tab !== 'emr'}>
        <EmrTab key={r.emr?.id ?? 'new'} rec={r} editable={editable} pendingItems={pendingItems.length} draftRx={draftRx.length}
          onDone={() => nav('/bac-si')} hasOrders={items.length > 0} />
      </div>
      {tab === 'orders' && <OrdersTab rec={r} editable={editable} />}
      <div hidden={tab !== 'rx'}><RxTab rec={r} detail={patient.data} editable={editable} /></div>
      {tab === 'history' && (
        <Card title="Các lần khám khác" pad={false}>
          <Table rows={others} rowKey={(h) => h.id} onRow={(h) => nav(`/bac-si/kham/${h.id}`)} empty="Đây là lần khám đầu tiên."
            cols={[
              { head: 'Ngày', cell: (h) => <b>{dmy(h.date)}</b> },
              { head: 'Bác sĩ', cell: (h) => <Who name={`BS ${h.doctorName}`} sub={h.specialtyName} /> },
              { head: 'Lý do khám', cell: (h) => h.reason ?? '' },
              { head: 'Trạng thái', cell: (h) => <Chip tone={visitStatus[h.status][1]}>{visitStatus[h.status][0]}</Chip> },
            ]} />
        </Card>
      )}
    </div>
  );
}

interface Dx { code: string; name: string }
const dxOf = (e: Emr | null, type: 'PRIMARY' | 'SECONDARY'): Dx => {
  const d = e?.diagnoses.find((x) => x.type === type);
  return { code: d?.icd10Code ?? '', name: d?.name ?? '' };
};

function EmrTab({ rec, editable, pendingItems, draftRx, hasOrders, onDone }: {
  rec: VisitRecord; editable: boolean; pendingItems: number; draftRx: number; hasOrders: boolean; onDone: () => void;
}) {
  const e = rec.emr;
  const v = rec.visit;
  const signed = !!e && e.status !== 'DRAFT';
  const ro = !editable || signed;
  const [f, setF] = useState({
    chiefComplaint: e?.chiefComplaint ?? v.reason ?? '', presentIllness: e?.presentIllness ?? '', organExam: e?.organExam ?? '',
    treatmentPlan: e?.treatmentPlan ?? '', followUpDate: e?.followUpDate ?? '',
  });
  const [p, setP] = useState(dxOf(e, 'PRIMARY'));
  const [s, setS] = useState(dxOf(e, 'SECONDARY'));
  const { run, busy } = useRun();
  const up = (k: keyof typeof f) => (ev: { target: { value: string } }) => setF({ ...f, [k]: ev.target.value });

  const body = () => ({
    ...Object.fromEntries(Object.entries(f).map(([k, val]) => [k, val.trim() || null])),
    conclusion: p.name.trim() || null,
    diagnoses: [
      ...(p.name.trim() ? [{ icd10Code: p.code.trim() || null, name: p.name.trim(), type: 'PRIMARY' }] : []),
      ...(s.name.trim() ? [{ icd10Code: s.code.trim() || null, name: s.name.trim(), type: 'SECONDARY' }] : []),
    ],
  });
  const hasDx = !!f.chiefComplaint.trim() && !!p.name.trim();
  const checks: [boolean, string][] = [
    [hasDx, 'Lý do khám và chẩn đoán chính (luôn bắt buộc)'],
    ...(hasOrders ? [[pendingItems === 0, pendingItems ? `Có chỉ định: còn ${pendingItems} mục chưa có kết quả được duyệt` : 'Có chỉ định: kết quả đã duyệt'] as [boolean, string]] : []),
    ...(rec.prescriptions.length ? [[draftRx === 0, draftRx ? `Có kê đơn: còn ${draftRx} đơn chưa ký` : 'Có kê đơn: đơn đã ký'] as [boolean, string]] : []),
  ];
  const ready = checks.every(([ok]) => ok);

  const finish = () => run('finish', async () => {
    if (!signed) {
      await api.put(`/visits/${v.id}/emr`, body());
      await api.post(`/visits/${v.id}/emr/sign`);
    }
    await api.post(`/visits/${v.id}/complete`);
  }, 'Đã kết thúc khám, hóa đơn chuyển sang thu ngân').then((ok) => ok && onDone());

  return (
    <div className="cols">
      <div className="wide">
        <Card title="Phiếu khám" sub={signed ? `Đã ký lúc ${timeDay(e!.signedAt!)}, ${e!.signedByName}` : undefined}>
          <Field label="Lý do khám" value={f.chiefComplaint} onChange={up('chiefComplaint')} readOnly={ro} />
          <Area label="Bệnh sử" rows={3} value={f.presentIllness} onChange={up('presentIllness')} readOnly={ro} />
          <Area label="Khám" rows={3} value={f.organExam} onChange={up('organExam')} readOnly={ro} />
          <div className="row">
            <Field label="Mã ICD-10" value={p.code} onChange={(ev) => setP({ ...p, code: ev.target.value })} readOnly={ro} placeholder="J03.9" style={{ flexBasis: 80 }} />
            <Field label="Chẩn đoán chính" value={p.name} onChange={(ev) => setP({ ...p, name: ev.target.value })} readOnly={ro} style={{ flexBasis: 240 }} />
          </div>
          <div className="row">
            <Field label="Mã ICD-10" value={s.code} onChange={(ev) => setS({ ...s, code: ev.target.value })} readOnly={ro} placeholder="I10" style={{ flexBasis: 80 }} />
            <Field label="Bệnh kèm theo" value={s.name} onChange={(ev) => setS({ ...s, name: ev.target.value })} readOnly={ro} style={{ flexBasis: 240 }} />
          </div>
          <div className="row">
            <Field label="Hướng điều trị" value={f.treatmentPlan} onChange={up('treatmentPlan')} readOnly={ro} style={{ flexBasis: 260 }} />
            <Field label="Hẹn tái khám" type="date" value={f.followUpDate} onChange={up('followUpDate')} readOnly={ro} />
          </div>
          {editable && !signed && (
            <div className="end">
              <Button kind="ghost" loading={busy === 'save'} onClick={() => run('save', () => api.put(`/visits/${v.id}/emr`, body()), 'Đã lưu nháp')}>Lưu nháp</Button>
              {v.status === 'IN_PROGRESS' && (
                <Button kind="outline" icon="flask" disabled={pendingItems === 0} loading={busy === 'await'}
                  title={pendingItems === 0 ? 'Chưa có chỉ định nào cần làm' : undefined}
                  onClick={() => run('await', async () => { await api.put(`/visits/${v.id}/emr`, body()); await api.post(`/visits/${v.id}/await-results`); }, 'Bệnh nhân đi làm xét nghiệm').then((ok) => ok && onDone())}>
                  Cho đi xét nghiệm
                </Button>
              )}
            </div>
          )}
        </Card>
      </div>
      <div className="narrow">
        {editable && (
          <Card title="Trước khi kết thúc" sheet>
            {checks.map(([ok, text]) => (
              <div key={text} className="wrap" style={{ flexWrap: 'nowrap' }}>
                <span style={{ color: ok ? 'var(--brand)' : 'var(--amber)', display: 'flex' }}><Icon name={ok ? 'check' : 'alert'} stroke={2.5} /></span><span>{text}</span>
              </div>
            ))}
            <span className="muted small">Không chỉ định hay không kê đơn thì bỏ qua mục đó. Kết thúc xong, hệ thống lập hóa đơn và gửi kết quả, đơn thuốc vào ứng dụng của bệnh nhân. Bệnh án đã ký không sửa đè.</span>
            <Button icon="check" big disabled={!ready} loading={busy === 'finish'} onClick={finish}>Ký và kết thúc khám</Button>
          </Card>
        )}
        {rec.invoice && (
          <Card title={`Hóa đơn ${rec.invoice.code}`}>
            <span className="muted">Tổng {rec.invoice.totalAmount.toLocaleString('vi-VN')} đ, {rec.invoice.status === 'PAID' ? 'đã thu đủ' : `còn ${rec.invoice.remaining.toLocaleString('vi-VN')} đ`}.</span>
          </Card>
        )}
      </div>
    </div>
  );
}
