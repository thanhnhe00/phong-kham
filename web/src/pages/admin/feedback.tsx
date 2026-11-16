import { useQueries } from '@tanstack/react-query';
import { useState } from 'react';
import { Icon } from '../../components/icons';
import { Card, Loading, PageHead, Seg, Table, Tabs, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { phone, timeDay } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import type { Feedback, PatientDetail } from '../../lib/types';

type Status = Feedback['status'];
const label: Record<Status, string> = { NEW: 'Mới', IN_REVIEW: 'Đang xử lý', RESOLVED: 'Đã xong', CLOSED: 'Đóng' };

export function FeedbackPage() {
  const all = useGet<Feedback[]>('/feedback', true); // mặc định: mới và đang xử lý
  const resolved = useGet<Feedback[]>('/feedback?status=RESOLVED');
  const closed = useGet<Feedback[]>('/feedback?status=CLOSED');
  const [tab, setTab] = useState<Status>('NEW');
  const [sel, setSel] = useState<number | null>(null);
  const list = [...(all.data ?? []), ...(resolved.data ?? []), ...(closed.data ?? [])];
  const rows = list.filter((f) => f.status === tab);
  const cur = rows.find((f) => f.id === sel) ?? rows[0];
  // shortcut: lấy tên, số điện thoại từng bệnh nhân vì API phản hồi chỉ trả mã hồ sơ; số phản hồi ít nên chấp nhận được
  const ids = [...new Set(rows.map((f) => f.patientId))];
  const people = useQueries({ queries: ids.map((id) => ({ queryKey: [`/patients/${id}`], queryFn: () => api.get<PatientDetail>(`/patients/${id}`) })) });
  const who = new Map(people.map((p) => p.data).filter((p): p is PatientDetail => !!p).map((p) => [p.patient.id, p.patient]));
  const { run, busy } = useRun();

  return (
    <div className="page">
      <PageHead title="Phản hồi" sub="Bệnh nhân gửi riêng từ ứng dụng, chỉ quản lý đọc được" />
      <Tabs value={tab} onChange={(t) => { setTab(t); setSel(null); }} items={(Object.keys(label) as Status[]).map((s) => ({ key: s, label: `${label[s]} (${list.filter((f) => f.status === s).length})` }))} />
      <div className="cols">
        <div className="wide">
          <Card title={`Phản hồi ${label[tab].toLowerCase()}`} pad={false}>
            {all.isLoading ? <Loading /> : (
              <Table rows={rows} rowKey={(f) => f.id} selected={cur?.id} onRow={(f) => setSel(f.id)} empty="Không có phản hồi nào."
                cols={[
                  { head: 'Gửi lúc', cell: (f) => <b>{timeDay(f.createdAt)}</b> },
                  { head: 'Bệnh nhân', cell: (f) => <Who name={who.get(f.patientId)?.fullName ?? '…'} sub={who.get(f.patientId)?.patientCode} /> },
                  { head: 'Chủ đề', cell: (f) => f.category ?? 'Khác' },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow">
          {cur && (
            <Card title={cur.category ?? 'Phản hồi'} sheet>
              <Who name={who.get(cur.patientId)?.fullName ?? ''} sub={`Gửi ${timeDay(cur.createdAt)}`} />
              <p style={{ lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>“{cur.content}”</p>
              {who.get(cur.patientId)?.phone && (
                <a href={`tel:${who.get(cur.patientId)!.phone}`} className="link wrap"><Icon name="phone" />Gọi cho bệnh nhân {phone(who.get(cur.patientId)!.phone)}</a>
              )}
              <div className="field"><span>Trạng thái</span>
                <Seg label="Trạng thái" value={cur.status} items={(Object.keys(label) as Status[]).map((s) => ({ key: s, label: label[s] }))}
                  onChange={(status) => status !== cur.status && run('st', () => api.patch(`/feedback/${cur.id}`, { status }), `Đã chuyển sang “${label[status]}”`)} />
              </div>
              {busy === 'st' && <span className="muted small">Đang lưu…</span>}
              {cur.handledAt && <span className="muted small">Cập nhật lần cuối {timeDay(cur.handledAt)}.</span>}
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
