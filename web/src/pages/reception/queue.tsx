import { useState } from 'react';
import { Button, Card, Chip, Kv, Loading, PageHead, Table, Tabs, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { personLine, time } from '../../lib/format';
import { useRun, useTodayQueues } from '../../lib/hooks';
import { queueGroup, visitStatus } from '../../lib/labels';
import type { QueueView, Visit, Vitals } from '../../lib/types';

interface Row { visit: Visit; position: number | null; vitals: Vitals | null | undefined }

/** Gộp người đang khám, đang chờ (theo thứ tự gọi), đi xét nghiệm của một phiên */
export function queueRows(q: QueueView): Row[] {
  return [
    ...q.current.map((visit) => ({ visit, position: null, vitals: undefined })),
    ...q.waiting.map((w) => ({ visit: w.visit, position: w.position, vitals: w.vitals })),
    ...q.awaitingResults.map((visit) => ({ visit, position: null, vitals: undefined })),
  ];
}

export function QueuePage() {
  const { sessions, queues, loading } = useTodayQueues();
  const [sid, setSid] = useState<number | null>(null);
  const [vid, setVid] = useState<number | null>(null);
  const { run, busy } = useRun();
  const cur = sessions.find((s) => s.id === sid) ?? sessions[0];
  const q = queues.find((x) => x.sessionId === cur?.id);
  const rows = q ? queueRows(q) : [];
  const sel = rows.find((r) => r.visit.id === vid) ?? null;

  return (
    <div className="page">
      <PageHead title="Hàng đợi khám" sub="Thứ tự do hệ thống xếp: đúng giờ trước, đến trễ sau, cứ 3 người có hẹn thì xen 1 người không hẹn" />
      {loading ? <Loading /> : sessions.length === 0 ? <div className="empty">Hôm nay không có phiên khám.</div> : (
        <>
          <Tabs value={String(cur.id)} onChange={(k) => { setSid(Number(k)); setVid(null); }}
            items={sessions.map((s) => ({ key: String(s.id), label: `${s.roomCode}, BS ${s.doctorName}, ${time(s.startsAt)} (${queues.find((x) => x.sessionId === s.id)?.waiting.length ?? 0})` }))} />
          <div className="cols">
            <div className="wide">
              <Card title={`Phòng ${cur.roomCode}, BS ${cur.doctorName}`} sub={`${cur.shiftName} ${time(cur.startsAt)} – ${time(cur.endsAt)}. Đã khám xong ${q?.done ?? 0} người`} pad={false}>
                <Table rows={rows} rowKey={(r) => r.visit.id} selected={sel?.visit.id} onRow={(r) => setVid(r.visit.id)} empty="Chưa có ai trong hàng đợi."
                  cols={[
                    { head: 'Số', cell: (r) => <b>{r.visit.queueNumber}</b> },
                    { head: 'Bệnh nhân', cell: (r) => <Who name={r.visit.patientName} sub={personLine(null, r.visit.patientDateOfBirth)} /> },
                    { head: 'Loại', cell: (r) => queueGroup[r.visit.queueGroup] },
                    { head: 'Check-in', cell: (r) => time(r.visit.checkedInAt) },
                    { head: 'Sinh hiệu', cell: (r) => r.vitals === undefined ? '' : r.vitals ? <Chip tone="teal">Đã đo</Chip> : <Chip tone="amber">Chưa đo</Chip> },
                    { head: 'Trạng thái', cell: (r) => r.position ? `Chờ, thứ ${r.position}` : <Chip tone={visitStatus[r.visit.status][1]}>{visitStatus[r.visit.status][0]}</Chip> },
                  ]} />
              </Card>
            </div>
            <div className="narrow">
              {sel && (
                <Card title={sel.visit.patientName} stub={sel.visit.queueNumber} sub={queueGroup[sel.visit.queueGroup]} sheet>
                  <Kv k="Check-in" v={time(sel.visit.checkedInAt)} />
                  {sel.position && <Kv k="Phía trước" v={sel.position === 1 ? 'Người tiếp theo' : `${sel.position - 1} người đang chờ`} />}
                  {sel.visit.reason && <Kv k="Lý do khám" v={sel.visit.reason} />}
                  {sel.visit.companionName && <Kv k="Người đi cùng" v={sel.visit.companionName} />}
                  {['WAITING', 'CALLED'].includes(sel.visit.status) && (
                    <div className="end">
                      <Button kind="danger" sm loading={busy === 'left'} onClick={() => {
                        if (window.confirm(`Ghi nhận ${sel.visit.patientName} đã bỏ về?`)) run('left', () => api.post(`/visits/${sel.visit.id}/left`), 'Đã ghi nhận bỏ về');
                      }}>Báo bệnh nhân bỏ về</Button>
                    </div>
                  )}
                </Card>
              )}
              <Card title="Cách xếp thứ tự">
                <ol className="muted small" style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <li>Người có hẹn đến đúng giờ, theo giờ check-in.</li>
                  <li>Người có hẹn đến trễ (vẫn trong 15 phút).</li>
                  <li>Cứ 3 người có hẹn thì xen 1 người không hẹn.</li>
                </ol>
                <span className="muted small">Lễ tân không sửa thứ tự để công bằng cho mọi người. Ca cấp cứu không xếp hàng: báo bác sĩ ngay.</span>
              </Card>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
