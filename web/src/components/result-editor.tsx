import { useRef, useState } from 'react';
import { api, errorText, openFile } from '../lib/api';
import { timeDay } from '../lib/format';
import { useRun } from '../lib/hooks';
import type { OrderItem, Result } from '../lib/types';
import { lastPerformance, stageOf } from '../lib/work';
import { Icon } from './icons';
import { Area, Button, Field, Note, useToast } from './ui';

/** Làm chỉ định, nhập kết quả, đính kèm phiếu, gửi bác sĩ duyệt (điều dưỡng; bác sĩ dùng cho siêu âm, X-quang) */
export function ResultEditor({ item, selfReview }: { item: OrderItem; selfReview?: boolean }) {
  const stage = stageOf(item);
  const perf = lastPerformance(item);
  const r = perf?.result ?? null;
  const [text, setText] = useState(r?.resultText ?? '');
  const [conclusion, setConclusion] = useState(r?.conclusion ?? '');
  const { run, busy } = useRun();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  if (stage === 'todo') {
    return (
      <div className="stack tight">
        <span className="muted small">Lấy mẫu hoặc làm xong thì bấm để ghi nhận, sau đó nhập kết quả.</span>
        <div className="end"><Button icon="check" loading={busy === 'perform'} onClick={() => run('perform', () => api.post(`/order-items/${item.id}/perform`, {}), 'Đã ghi nhận thực hiện')}>Đã làm, nhập kết quả</Button></div>
      </div>
    );
  }
  if (!perf) return null;

  const save = (submit: boolean) => run(submit ? 'submit' : 'draft', () => api.post<Result>(`/performances/${perf.id}/result`, { resultText: text, conclusion, submit }),
    submit ? (selfReview ? 'Đã lưu kết quả' : 'Đã gửi bác sĩ duyệt') : 'Đã lưu nháp');

  const upload = async (file: File) => {
    setUploading(true);
    try {
      let id = r?.id;
      if (!id) id = (await api.post<Result>(`/performances/${perf.id}/result`, { resultText: text, conclusion, submit: false })).id;
      const fd = new FormData();
      fd.append('file', file);
      await api.post(`/results/${id}/files`, fd);
      await run('noop', async () => {}, `Đã tải lên ${file.name}`);
    } catch (e) {
      toast(errorText(e), true);
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <div className="stack">
      {perf.performedByName && <span className="muted small">Thực hiện lúc {timeDay(perf.performedAt)}, {perf.performedByName}</span>}
      {stage === 'returned' && r?.returnNote && <Note><b>Bác sĩ trả lại:</b> {r.returnNote}</Note>}
      {stage === 'review' && !selfReview && <Note tone="teal" icon="check">Đã gửi bác sĩ duyệt lúc {timeDay(r!.enteredAt)}. Vẫn sửa được cho tới khi bác sĩ duyệt.</Note>}
      <Area label="Kết quả" rows={3} value={text} onChange={(e) => setText(e.target.value)} placeholder="Các chỉ số, mô tả" />
      <Field label="Nhận xét, kết luận" value={conclusion} onChange={(e) => setConclusion(e.target.value)} />
      <input ref={fileRef} type="file" accept="image/jpeg,image/png,application/pdf" hidden aria-label="Chọn tệp kết quả"
        onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
      <button type="button" className="drop" onClick={() => fileRef.current?.click()} disabled={uploading}>
        <Icon name="upload" size={22} />
        <span><b style={{ color: 'var(--brand)' }}>{uploading ? 'Đang tải lên…' : 'Chọn tệp'}</b> phiếu kết quả từ máy</span>
        <span className="xs">Ảnh JPG, PNG hoặc PDF, tối đa 10 MB</span>
      </button>
      {r?.files.map((f) => (
        <div key={f.id} className="between">
          <span className="wrap"><Icon name="doc" />{f.fileName}</span>
          <button type="button" className="link" onClick={() => openFile(f.id).catch((e) => toast(errorText(e), true))}>Mở tệp ({Math.ceil(f.sizeBytes / 1024)} KB)</button>
        </div>
      ))}
      <div className="end">
        <Button kind="ghost" loading={busy === 'draft'} onClick={() => save(false)}>Lưu nháp</Button>
        <Button icon="check" disabled={!text.trim() && !conclusion.trim() && !r?.files.length} loading={busy === 'submit'} onClick={() => save(true)}>
          {selfReview ? 'Lưu kết quả' : 'Gửi bác sĩ duyệt'}
        </Button>
      </div>
      {!selfReview && <span className="muted small">Bệnh nhân chỉ xem được kết quả trên ứng dụng sau khi bác sĩ duyệt.</span>}
    </div>
  );
}
