import { useState } from 'react';
import { Button, Card, Chip, Field, Loading, Note, PageHead, Table, Tabs, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { decimal, personLine, time, timeDay, vitalsAlerts, vitalsShort } from '../../lib/format';
import { useGet, useRun, useTodayQueues } from '../../lib/hooks';
import { queueGroup } from '../../lib/labels';
import type { PatientDetail, Visit, Vitals } from '../../lib/types';

type VitalsRequestShape = Omit<Vitals, 'id' | 'recordedByName' | 'recordedAt'>;

interface Row { visit: Visit; vitals: Vitals | null }

export function VitalsPage() {
  const { queues, loading } = useTodayQueues();
  const [tab, setTab] = useState<'todo' | 'done'>('todo');
  const [sel, setSel] = useState<number | null>(null);
  const all: Row[] = queues.flatMap((q) => q.waiting.map((w) => ({ visit: w.visit, vitals: w.vitals })))
    .sort((a, b) => a.visit.checkedInAt.localeCompare(b.visit.checkedInAt));
  const todo = all.filter((r) => !r.vitals);
  const done = all.filter((r) => r.vitals);
  const rows = tab === 'todo' ? todo : done;
  const cur = rows.find((r) => r.visit.id === sel) ?? rows[0];

  return (
    <div className="page">
      <PageHead title="Đo sinh hiệu" sub="Đo trước khi vào bác sĩ: phát hiện người cần khám sớm và để bác sĩ có sẵn số liệu" />
      <Tabs value={tab} onChange={(t) => { setTab(t); setSel(null); }} items={[{ key: 'todo', label: `Chờ đo (${todo.length})` }, { key: 'done', label: `Đã đo, đang chờ khám (${done.length})` }]} />
      <div className="cols">
        <div className="wide">
          <Card title={tab === 'todo' ? 'Bệnh nhân chờ đo' : 'Đã đo, đang chờ bác sĩ gọi'} sub="Theo giờ check-in" pad={false}>
            {loading ? <Loading /> : (
              <Table rows={rows} rowKey={(r) => r.visit.id} selected={cur?.visit.id} onRow={(r) => setSel(r.visit.id)}
                empty={tab === 'todo' ? 'Không còn ai chờ đo.' : 'Chưa có ai.'}
                cols={[
                  { head: 'Số', cell: (r) => <b>{r.visit.queueNumber}</b> },
                  { head: 'Bệnh nhân', cell: (r) => <Who name={r.visit.patientName} sub={`${personLine(null, r.visit.patientDateOfBirth)}, ${queueGroup[r.visit.queueGroup].toLowerCase()}`} /> },
                  { head: 'Phòng', cell: (r) => r.visit.roomCode },
                  { head: 'Check-in', cell: (r) => time(r.visit.checkedInAt) },
                  { head: tab === 'todo' ? 'Lý do khám' : 'Sinh hiệu', cell: (r) => tab === 'todo' ? (r.visit.reason ?? '') : (
                    <span className={vitalsAlerts(r.vitals).some((a) => a.severe) ? 'red b' : undefined}>{vitalsShort(r.vitals)}</span>
                  ) },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">{cur && <VitalsForm key={cur.visit.id} row={cur} />}</div>
      </div>
    </div>
  );
}

const num = (s: string) => (s.trim() === '' ? null : Number(s.replace(',', '.')));

function VitalsForm({ row }: { row: Row }) {
  const v = row.visit;
  const detail = useGet<PatientDetail>(`/patients/${v.patientId}`);
  const { run, busy } = useRun();
  const [reason, setReason] = useState(v.reason ?? '');
  const [f, setF] = useState({ bp: '', pulse: '', temp: '', spo2: '', rr: '', weight: '', height: '' });
  const [allergen, setAllergen] = useState('');
  const up = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const bp = f.bp.match(/^\s*(\d{2,3})\s*\/\s*(\d{2,3})\s*$/);
  const req: VitalsRequestShape = {
    systolicBp: bp ? Number(bp[1]) : null, diastolicBp: bp ? Number(bp[2]) : null, pulsePerMin: num(f.pulse), temperatureC: num(f.temp),
    spo2Percent: num(f.spo2), respiratoryRate: num(f.rr), weightKg: num(f.weight), heightCm: num(f.height),
  };
  const alerts = vitalsAlerts({ ...req, id: 0, recordedByName: null, recordedAt: '' });
  const severe = alerts.some((a) => a.severe);
  const empty = Object.values(req).every((x) => x == null);
  const bpBad = f.bp.trim() !== '' && !bp;

  const save = () => run('save', async () => {
    if (reason.trim() && reason.trim() !== (v.reason ?? '')) await api.patch(`/visits/${v.id}/reason`, { reason: reason.trim() });
    await api.post(`/visits/${v.id}/vitals`, req);
  }, `Đã lưu sinh hiệu số ${v.queueNumber}`);

  return (
    <Card title={v.patientName} stub={v.queueNumber} sub={`${personLine(detail.data?.patient.sex, v.patientDateOfBirth)}, phòng ${v.roomCode}, BS ${v.doctorName}`} sheet>
      {row.vitals && <Note tone="teal" icon="check">Đã đo lúc {timeDay(row.vitals.recordedAt)} ({row.vitals.recordedByName}): {vitalsShort(row.vitals)}. Lưu thêm là một lần đo mới.</Note>}
      <Field label="Lý do khám" value={reason} onChange={(e) => setReason(e.target.value)} />
      <div className="field"><span>Dị ứng</span>
        <div className="wrap">
          {detail.data?.allergies.length ? detail.data.allergies.map((a) => <Chip key={a.id} tone="red">{a.allergen}</Chip>) : <Chip>Chưa ghi nhận dị ứng</Chip>}
          <span className="input" style={{ width: 170, height: 36 }}><input aria-label="Thêm dị ứng" placeholder="Thêm dị ứng" value={allergen} onChange={(e) => setAllergen(e.target.value)} /></span>
          <Button kind="ghost" sm icon="plus" disabled={!allergen.trim()} loading={busy === 'al'}
            onClick={() => run('al', () => api.post(`/patients/${v.patientId}/allergies`, { allergen: allergen.trim() }), 'Đã thêm dị ứng').then((ok) => ok && setAllergen(''))}>Thêm</Button>
        </div>
      </div>
      <div className="row">
        <Field label="Huyết áp" placeholder="120/80" suffix="mmHg" value={f.bp} onChange={up('bp')} warn={bpBad || (req.systolicBp ?? 0) >= 140} />
        <Field label="Mạch" inputMode="numeric" suffix="lần/phút" value={f.pulse} onChange={up('pulse')} />
      </div>
      <div className="row">
        <Field label="Nhiệt độ" inputMode="decimal" suffix="°C" value={f.temp} onChange={up('temp')} warn={(req.temperatureC ?? 0) >= 38} />
        <Field label="SpO₂" inputMode="numeric" suffix="%" value={f.spo2} onChange={up('spo2')} warn={req.spo2Percent != null && req.spo2Percent < 94} />
      </div>
      <div className="row">
        <Field label="Nhịp thở" inputMode="numeric" suffix="l/ph" value={f.rr} onChange={up('rr')} style={{ flexBasis: 90 }} />
        <Field label="Cân nặng" inputMode="decimal" suffix="kg" value={f.weight} onChange={up('weight')} style={{ flexBasis: 90 }} />
        <Field label="Chiều cao" inputMode="decimal" suffix="cm" value={f.height} onChange={up('height')} style={{ flexBasis: 90 }} />
      </div>
      {bpBad && <Note tone="red">Huyết áp ghi dạng tâm thu/tâm trương, ví dụ 120/80.</Note>}
      {alerts.length > 0 && (
        <Note tone={severe ? 'red' : 'amber'}>
          <b>{alerts.map((a) => a.text).join('; ')}.</b>{' '}
          {severe ? `Báo trực tiếp bác sĩ phòng ${v.roomCode}; danh sách của bác sĩ cũng hiện cảnh báo. ` : 'Bác sĩ sẽ thấy cảnh báo này trong danh sách. '}
          Nếu là cấp cứu (khó thở, đau ngực, co giật) thì không xếp hàng: sơ cứu, báo bác sĩ ngay, gọi 115 hoặc chuyển viện.
        </Note>
      )}
      {req.weightKg != null && req.heightCm != null && req.heightCm > 0 && (
        <span className="muted small">BMI {decimal(Math.round((req.weightKg / (req.heightCm / 100) ** 2) * 10) / 10)}</span>
      )}
      <div className="between">
        <span className="muted xs">Mỗi lần lưu là một lần đo mới, ghi giờ và người đo. Đo lại không xóa lần trước.</span>
        <Button icon="check" disabled={empty || bpBad} loading={busy === 'save'} onClick={save}>Lưu sinh hiệu</Button>
      </div>
    </Card>
  );
}
