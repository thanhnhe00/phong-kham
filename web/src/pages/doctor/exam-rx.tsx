import { useState } from 'react';
import { Area, Button, Card, Check, Chip, Field, Note, Seg, Table, useToast } from '../../components/ui';
import { api, errorText } from '../../lib/api';
import { ageText, dmy, personLine, timeDay, vnDate } from '../../lib/format';
import { useRun } from '../../lib/hooks';
import type { Medicine, Page, PatientDetail, Prescription, VisitRecord } from '../../lib/types';

interface Line { medicineId: number; label: string; unit: string; quantity: string; dosePerTime: string; timesPerDay: string; timing: string; days: string }

const toLines = (p?: Prescription): Line[] => (p?.items ?? []).map((i) => ({
  medicineId: i.medicineId, label: i.medicineLabel, unit: i.unit, quantity: String(i.quantity), dosePerTime: stripUnit(i.dosePerTime, i.unit),
  timesPerDay: String(i.timesPerDay), timing: i.timing ?? '', days: String(i.days),
}));

/** Liều lưu kèm đơn vị ("1 viên"); khi sửa thì bỏ đơn vị vì ô nhập đã hiện sẵn */
export const stripUnit = (dose: string, unit: string) => (unit && dose.endsWith(unit) ? dose.slice(0, -unit.length).trim() : dose);
export const withUnit = (dose: string, unit: string) => (unit && !dose.trim().endsWith(unit) ? `${dose.trim()} ${unit}` : dose.trim());

/** Số lượng gợi ý = liều mỗi lần × số lần × số ngày (khi liều là số) */
export function suggestQuantity(l: Pick<Line, 'dosePerTime' | 'timesPerDay' | 'days'>): string {
  const dose = parseFloat(l.dosePerTime.replace(',', '.'));
  const n = dose * Number(l.timesPerDay) * Number(l.days);
  return Number.isFinite(n) && n > 0 ? String(Math.ceil(n)) : '';
}

export function RxTab({ rec, detail, editable }: { rec: VisitRecord; detail?: PatientDetail; editable: boolean }) {
  const list = rec.prescriptions;
  const [sel, setSel] = useState<number | 'new' | null>(null);
  const cur = sel === 'new' ? undefined : list.find((p) => p.id === sel) ?? list.find((p) => p.status === 'DRAFT') ?? list[0];
  const creating = sel === 'new' || (list.length === 0 && editable);

  return (
    <div className="stack" style={{ gap: 20 }}>
      {(list.length > 0 || editable) && (
        <div className="between">
          <Seg label="Đơn của lượt khám" value={creating ? 'new' : cur ? String(cur.id) : null} onChange={(k) => setSel(k === 'new' ? 'new' : Number(k))}
            items={[...list.map((p, i) => ({ key: String(p.id), label: `Đơn ${i + 1}: ${p.status === 'DRAFT' ? 'đang soạn' : p.status === 'SIGNED' ? 'đã ký' : 'đã thay'}` })),
              ...(creating ? [{ key: 'new', label: `Đơn ${list.length + 1}: mới` }] : [])]} />
          {editable && !creating && <Button kind="ghost" sm icon="plus" onClick={() => setSel('new')}>Tạo đơn khác</Button>}
        </div>
      )}
      {list.length === 0 && !editable && <Card title="Không kê đơn"><span className="muted">Lượt khám này không có đơn thuốc.</span></Card>}
      {creating ? <RxEditor key="new" rec={rec} detail={detail} onSaved={(id) => setSel(id)} />
        : cur && (cur.status === 'DRAFT' && editable ? <RxEditor key={cur.id} rec={rec} detail={detail} rx={cur} onSaved={(id) => setSel(id)} /> : <RxView rx={cur} rec={rec} detail={detail} />)}
    </div>
  );
}

function RxEditor({ rec, detail, rx, onSaved }: { rec: VisitRecord; detail?: PatientDetail; rx?: Prescription; onSaved: (id: number) => void }) {
  const [lines, setLines] = useState<Line[]>(toLines(rx));
  const [advice, setAdvice] = useState(rx?.advice ?? '');
  const [q, setQ] = useState('');
  const [found, setFound] = useState<Medicine[]>([]);
  const [ack, setAck] = useState(false);
  const [ackNote, setAckNote] = useState('');
  const { run, busy } = useRun();
  const toast = useToast();
  const allergies = detail?.allergies ?? [];

  const search = async (text: string) => {
    setQ(text);
    if (text.trim().length < 2) return setFound([]);
    try {
      setFound((await api.get<Page<Medicine>>(`/medicines?q=${encodeURIComponent(text.trim())}&size=8`)).items);
    } catch (e) {
      toast(errorText(e), true);
    }
  };
  const add = (m: Medicine) => {
    const l: Line = { medicineId: m.id, label: m.label, unit: m.unit, dosePerTime: '1', timesPerDay: '2', days: '5', timing: 'Sau ăn', quantity: '' };
    l.quantity = suggestQuantity(l);
    setLines([...lines, l]);
    setQ('');
    setFound([]);
  };
  const upd = (i: number, k: keyof Line, val: string) => setLines(lines.map((l, j) => {
    if (j !== i) return l;
    const n = { ...l, [k]: val };
    if (k !== 'quantity' && k !== 'timing' && suggestQuantity(l) === l.quantity) n.quantity = suggestQuantity(n);
    return n;
  }));

  const body = () => ({
    advice: advice.trim() || null,
    items: lines.map((l) => ({
      medicineId: l.medicineId, quantity: Number(l.quantity.replace(',', '.')), dosePerTime: withUnit(l.dosePerTime, l.unit),
      timesPerDay: Number(l.timesPerDay), timing: l.timing || null, days: Number(l.days), route: null,
    })),
  });
  const save = async () => {
    const saved = rx ? await api.put<Prescription>(`/prescriptions/${rx.id}`, body()) : await api.post<Prescription>(`/visits/${rec.visit.id}/prescriptions`, body());
    return saved;
  };
  const valid = lines.length > 0 && lines.every((l) => Number(l.quantity) > 0 && Number(l.timesPerDay) > 0 && Number(l.days) > 0 && Number(l.days) <= 30 && l.dosePerTime.trim());

  return (
    <div className="cols">
      <div className="wide">
        <Card title={rx ? 'Sửa đơn nháp' : 'Đơn thuốc mới'} sub="Bệnh nhân mua tại nhà thuốc bên ngoài, phòng khám không bán thuốc">
          <div style={{ position: 'relative' }}>
            <Field label="Thêm thuốc" type="search" placeholder="Gõ tên thuốc hoặc hoạt chất" value={q} onChange={(e) => search(e.target.value)} />
            {found.length > 0 && (
              <div className="card" style={{ position: 'absolute', zIndex: 5, left: 0, right: 0, top: '100%', marginTop: 4, boxShadow: 'var(--sheet-shadow)' }}>
                {found.map((m) => (
                  <button key={m.id} type="button" className="between" onClick={() => add(m)}
                    style={{ padding: '10px 14px', border: 0, borderBottom: '1px solid var(--line-2)', background: '#fff', textAlign: 'left', cursor: 'pointer' }}>
                    <span>{m.label}</span>{m.drugClass !== 'NORMAL' && <Chip tone="amber">{m.drugClass === 'ANTIBIOTIC' ? 'Kháng sinh' : 'Thuốc kiểm soát'}</Chip>}
                  </button>
                ))}
              </div>
            )}
          </div>
          <div style={{ margin: '0 -20px' }}>
            <Table rows={lines.map((l, i) => ({ ...l, i }))} rowKey={(l) => `${l.medicineId}-${l.i}`} empty="Chưa có thuốc nào."
              cols={[
                { head: 'Thuốc', cell: (l) => <b>{l.label}</b> },
                { head: `Mỗi lần`, cell: (l) => <span className="input" style={{ width: 90 }}><input aria-label="Mỗi lần" value={l.dosePerTime} onChange={(e) => upd(l.i, 'dosePerTime', e.target.value)} /><span className="sfx">{l.unit}</span></span> },
                { head: 'Lần/ngày', cell: (l) => <span className="input" style={{ width: 56 }}><input aria-label="Lần mỗi ngày" inputMode="numeric" value={l.timesPerDay} onChange={(e) => upd(l.i, 'timesPerDay', e.target.value)} /></span> },
                { head: 'Số ngày', cell: (l) => <span className="input" style={{ width: 56 }}><input aria-label="Số ngày" inputMode="numeric" value={l.days} onChange={(e) => upd(l.i, 'days', e.target.value)} /></span> },
                { head: 'Cách dùng', cell: (l) => <span className="input" style={{ width: 110 }}><input aria-label="Cách dùng" value={l.timing} onChange={(e) => upd(l.i, 'timing', e.target.value)} /></span> },
                { head: 'Số lượng', cell: (l) => <span className="input" style={{ width: 80 }}><input aria-label="Số lượng" inputMode="numeric" value={l.quantity} onChange={(e) => upd(l.i, 'quantity', e.target.value)} /></span> },
                { head: <span className="sr">Xóa</span>, cell: (l) => <button type="button" className="link" aria-label={`Xóa ${l.label}`} onClick={() => setLines(lines.filter((_, j) => j !== l.i))}>Xóa</button> },
              ]} />
          </div>
          <Area label="Lời dặn" rows={2} value={advice} onChange={(e) => setAdvice(e.target.value)} placeholder="Ví dụ: uống nhiều nước, tái khám nếu sốt trên 39 °C" />
          <div className="end">
            {rx && <Button kind="danger" loading={busy === 'del'} onClick={() => window.confirm('Xóa đơn nháp này?') && run('del', () => api.del(`/prescriptions/${rx.id}`), 'Đã xóa đơn nháp')}>Xóa đơn nháp</Button>}
            <Button kind="ghost" disabled={!valid} loading={busy === 'save'} onClick={() => run('save', async () => onSaved((await save()).id), 'Đã lưu đơn nháp')}>Lưu nháp</Button>
          </div>
        </Card>
      </div>
      <div className="narrow">
        <Card title="Kiểm tra trước khi ký" sheet>
          {allergies.length > 0 ? (
            <>
              <Note tone="red">Bệnh nhân dị ứng <b>{allergies.map((a) => a.allergen).join(', ')}</b>. Đối chiếu từng thuốc trong đơn trước khi ký.</Note>
              <Check label="Tôi đã đối chiếu dị ứng với các thuốc trong đơn" checked={ack} onChange={setAck} />
              <Field label="Ghi chú (nếu vẫn kê thuốc có nguy cơ)" value={ackNote} onChange={(e) => setAckNote(e.target.value)} />
            </>
          ) : <span className="muted">Chưa ghi nhận dị ứng.</span>}
          <span className="muted small">Mỗi thuốc tối đa 30 ngày. Người lớn cần số CCCD hoặc mã định danh; trẻ dưới 6 tuổi cần cân nặng và người đưa đến khám. Thuốc gây nghiện, hướng thần kê đơn riêng.</span>
          <Button icon="check" disabled={!valid || (allergies.length > 0 && !ack)} loading={busy === 'sign'}
            onClick={() => run('sign', async () => {
              const saved = await save();
              await api.post(`/prescriptions/${saved.id}/sign`, { allergyChecked: ack || undefined, allergyNote: ackNote.trim() || null });
              onSaved(saved.id);
            }, 'Đã ký đơn, đơn hiện trong ứng dụng của bệnh nhân')}>Ký đơn</Button>
        </Card>
      </div>
    </div>
  );
}

function RxView({ rx, rec, detail }: { rx: Prescription; rec: VisitRecord; detail?: PatientDetail }) {
  return (
    <div className="cols">
      <div className="wide">
        <Card title={rx.code ? `Đơn thuốc ${rx.code}` : 'Đơn thuốc'} sub={rx.signedAt ? `${rx.signedByName} ký lúc ${timeDay(rx.signedAt)}` : undefined}
          right={rx.status === 'SIGNED' && <Button kind="ghost" sm icon="print" onClick={() => printRx(rx, rec, detail)}>In đơn</Button>}>
          {rx.diagnosisText && <p><span className="muted">Chẩn đoán: </span>{rx.diagnosisText}</p>}
          <div style={{ margin: '0 -20px' }}>
            <Table rows={rx.items} rowKey={(i) => i.id}
              cols={[
                { head: 'Thuốc', cell: (i) => <b>{i.medicineLabel}</b> },
                { head: 'Cách dùng', cell: (i) => `${i.dosePerTime} × ${i.timesPerDay} lần/ngày${i.timing ? `, ${i.timing.toLowerCase()}` : ''}, ${i.days} ngày` },
                { head: 'Số lượng', right: true, cell: (i) => `${i.quantity} ${i.unit}` },
              ]} />
          </div>
          {rx.advice && <p><span className="muted">Lời dặn: </span>{rx.advice}</p>}
          {rx.allergyAck && <span className="muted small">Đối chiếu dị ứng: {rx.allergyAck}</span>}
        </Card>
      </div>
      <div className="narrow">
        <Card title="Sau khi ký">
          <span className="muted small">Đơn hiện trong ứng dụng của bệnh nhân. Bấm “In đơn” cho người không dùng ứng dụng. Sửa đơn đã ký thì lập đơn thay thế.</span>
        </Card>
      </div>
    </div>
  );
}

const esc = (s: string) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);

/** In đơn khổ A5 trong cửa sổ riêng */
export function printRx(rx: Prescription, rec: VisitRecord, detail?: PatientDetail) {
  const p = detail?.patient;
  const v = rec.visit;
  const rows = rx.items.map((i, n) => `<tr><td>${n + 1}</td><td><b>${esc(i.medicineLabel)}</b><br><small>${esc(`${i.dosePerTime} × ${i.timesPerDay} lần/ngày${i.timing ? `, ${i.timing.toLowerCase()}` : ''}, ${i.days} ngày`)}</small></td><td style="text-align:right">${i.quantity} ${esc(i.unit)}</td></tr>`).join('');
  const html = `<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Đơn thuốc ${esc(rx.code ?? '')}</title>
<style>@page{size:A5;margin:12mm}body{font-family:'Be Vietnam Pro',Arial,sans-serif;font-size:13px;color:#12302B}h1{font-size:18px;text-align:center;margin:8px 0}table{width:100%;border-collapse:collapse}td{padding:6px 4px;border-bottom:1px solid #ddd;vertical-align:top}.r{display:flex;justify-content:space-between}small{color:#5B726D}</style></head><body>
<div class="r"><b>PHÒNG KHÁM AN TÂM</b><span>Mã đơn: ${esc(rx.code ?? '')}</span></div>
<h1>ĐƠN THUỐC</h1>
<p>Họ tên: <b>${esc(v.patientName)}</b> &nbsp; ${esc(personLine(p?.sex, v.patientDateOfBirth))}${p && p.ageInMonths < 72 ? ` (${ageText(v.patientDateOfBirth)})` : ''}<br>
Mã BN: ${esc(v.patientCode)}${p?.identityNumber ? ` &nbsp; Số giấy tờ: ${esc(p.identityNumber)}` : ''}<br>
Chẩn đoán: ${esc(rx.diagnosisText ?? '')}</p>
<table>${rows}</table>
${rx.advice ? `<p>Lời dặn: ${esc(rx.advice)}</p>` : ''}
<div class="r" style="margin-top:24px"><span>Ngày ${dmy(vnDate(rx.signedAt ?? new Date()))}</span><span style="text-align:center">Bác sĩ khám bệnh<br><br><b>${esc(rx.signedByName ?? '')}</b><br><small>${esc(rx.prescriberLicenseNo ?? '')}</small></span></div>
<script>window.onload=()=>{window.print()}</script></body></html>`;
  const w = window.open('', '_blank');
  if (!w) return;
  w.document.write(html);
  w.document.close();
}
