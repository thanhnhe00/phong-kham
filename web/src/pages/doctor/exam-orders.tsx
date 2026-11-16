import { useState } from 'react';
import { ResultEditor } from '../../components/result-editor';
import { Button, Card, Chip, Field, Note, Who, useToast } from '../../components/ui';
import { Icon } from '../../components/icons';
import { api, errorText, openFile } from '../../lib/api';
import { timeDay, vnd } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import { category } from '../../lib/labels';
import type { MedicalService, OrderItem, VisitRecord } from '../../lib/types';
import { lastPerformance, stageLabel, stageOf } from '../../lib/work';

export function OrdersTab({ rec, editable }: { rec: VisitRecord; editable: boolean }) {
  const items = rec.orders.flatMap((o) => o.items);
  const services = useGet<MedicalService[]>('/services');
  const [q, setQ] = useState('');
  const { run, busy } = useRun();
  const v = rec.visit;
  const choices = (services.data ?? []).filter((s) => s.category !== 'CONSULTATION' && s.isActive !== false)
    .filter((s) => !q.trim() || s.name.toLowerCase().includes(q.trim().toLowerCase()));

  return (
    <div className="cols">
      <div className="wide stack" style={{ gap: 20 }}>
        {items.length === 0 && <Card title="Chưa có chỉ định"><span className="muted">Không chỉ định xét nghiệm hay chẩn đoán hình ảnh thì bỏ qua phần này.</span></Card>}
        {items.map((it) => <ItemCard key={it.id} it={it} editable={editable} />)}
      </div>
      <div className="narrow">
        {editable && (
          <Card title="Chỉ định thêm" sheet>
            <Field label="Tìm xét nghiệm, chẩn đoán hình ảnh" type="search" value={q} onChange={(e) => setQ(e.target.value)} />
            <div className="stack tight" style={{ maxHeight: 360, overflowY: 'auto' }}>
              {choices.map((s) => (
                <div key={s.id} className="between">
                  <Who name={s.name} sub={`${category[s.category]}, ${vnd(s.listPrice)}${s.estimatedMinutes ? `, ${s.estimatedMinutes} phút` : ''}`} />
                  <Button kind="ghost" sm icon="plus" loading={busy === `o${s.id}`}
                    onClick={() => run(`o${s.id}`, () => api.post(`/visits/${v.id}/orders`, { items: [{ serviceId: s.id, quantity: 1 }] }), `Đã chỉ định ${s.name}`)}>Chỉ định</Button>
                </div>
              ))}
            </div>
            <span className="muted small">Giá chốt tại lúc chỉ định. Bệnh nhân từ chối thì ghi nhận, không tính tiền.</span>
          </Card>
        )}
      </div>
    </div>
  );
}

function ItemCard({ it, editable }: { it: OrderItem; editable: boolean }) {
  const stage = stageOf(it);
  const r = lastPerformance(it)?.result ?? null;
  const { run, busy } = useRun();
  const toast = useToast();
  const doctorDoes = it.category !== 'LAB';
  const tone = stage === 'done' ? 'teal' : stage === 'review' ? 'amber' : stage === 'refused' ? 'grey' : 'blue';

  return (
    <Card title={it.serviceName} sub={`${category[it.category]}, ${vnd(it.unitPrice)}`} right={<Chip tone={tone}>{stage === 'review' ? 'Chờ bạn duyệt' : stageLabel[stage]}</Chip>}>
      {stage === 'refused' && <span className="muted">{it.status === 'REFUSED' ? `Bệnh nhân từ chối: ${it.refusalNote ?? ''}` : 'Đã bớt chỉ định.'}</span>}
      {(stage === 'review' || stage === 'done') && r && (
        <>
          <span className="muted small">{r.enteredByName} nhập lúc {timeDay(r.enteredAt)}{r.reviewedByName ? `, ${r.reviewedByName} duyệt lúc ${timeDay(r.reviewedAt!)}` : ''}</span>
          {r.resultText && <p style={{ whiteSpace: 'pre-wrap' }}>{r.resultText}</p>}
          {r.conclusion && <p><span className="muted">Nhận xét: </span><b>{r.conclusion}</b></p>}
          {r.files.map((f) => (
            <div key={f.id} className="between">
              <span className="wrap"><Icon name="doc" />{f.fileName}</span>
              <button type="button" className="link" onClick={() => openFile(f.id).catch((e) => toast(errorText(e), true))}>Mở tệp</button>
            </div>
          ))}
        </>
      )}
      {stage === 'review' && editable && <ReviewButtons resultId={r!.id} />}
      {doctorDoes && editable && ['todo', 'entering', 'returned'].includes(stage) && <ResultEditor item={it} selfReview />}
      {!doctorDoes && ['todo', 'entering', 'returned'].includes(stage) && (
        <span className="muted small">{stage === 'todo' ? 'Chờ điều dưỡng làm xét nghiệm.' : stage === 'returned' ? 'Đã trả lại, điều dưỡng đang sửa.' : 'Điều dưỡng đang nhập kết quả.'}</span>
      )}
      {editable && stage === 'todo' && (
        <div className="end">
          <Button kind="ghost" sm loading={busy === 'cancel'} onClick={() => run('cancel', () => api.post(`/order-items/${it.id}/cancel`), 'Đã bớt chỉ định')}>Bớt chỉ định</Button>
          <Button kind="danger" sm loading={busy === 'refuse'} onClick={() => {
            const note = window.prompt('Lý do bệnh nhân từ chối');
            if (note?.trim()) run('refuse', () => api.post(`/order-items/${it.id}/refuse`, { note: note.trim() }), 'Đã ghi nhận từ chối');
          }}>Bệnh nhân từ chối</Button>
        </div>
      )}
    </Card>
  );
}

/** Duyệt kết quả (bệnh nhân xem được ngay) hoặc trả lại điều dưỡng kèm lý do */
export function ReviewButtons({ resultId }: { resultId: number }) {
  const { run, busy } = useRun();
  const [returning, setReturning] = useState(false);
  const [reason, setReason] = useState('');
  if (returning) {
    return (
      <div className="stack tight">
        <Field label="Lý do trả lại" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Ví dụ: ghi rõ lấy mẫu lúc đói hay sau ăn" autoFocus />
        <div className="end">
          <Button kind="ghost" sm onClick={() => setReturning(false)}>Thôi</Button>
          <Button kind="danger" sm disabled={!reason.trim()} loading={busy === 'ret'}
            onClick={() => run('ret', () => api.post(`/results/${resultId}/return`, { reason: reason.trim() }), 'Đã trả lại cho điều dưỡng')}>Trả lại</Button>
        </div>
      </div>
    );
  }
  return (
    <>
      <div className="end">
        <Button kind="ghost" onClick={() => setReturning(true)}>Trả lại điều dưỡng sửa</Button>
        <Button icon="check" loading={busy === 'rev'} onClick={() => run('rev', () => api.post(`/results/${resultId}/review`), 'Đã duyệt, bệnh nhân xem được kết quả')}>Duyệt kết quả</Button>
      </div>
      <Note tone="teal" icon="bell">Duyệt xong, bệnh nhân nhận thông báo “Đã có kết quả” và xem được kết quả, tệp trên ứng dụng.</Note>
    </>
  );
}
