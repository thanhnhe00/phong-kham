import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Chip, Loading, PageHead, Stub, Table, Tabs, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { minutesSince, personLine, time, vitalsAlerts, vitalsShort } from '../../lib/format';
import { useGet, useRun, useTodayQueues } from '../../lib/hooks';
import { queueGroup } from '../../lib/labels';
import type { Session, Visit, WorkItem } from '../../lib/types';
import { stageOf } from '../../lib/work';

/** Phiên đang diễn ra, nếu không có thì phiên sắp tới gần nhất, nếu không thì phiên cuối */
export function pickSession(list: Session[], now = Date.now()): Session | undefined {
  const sorted = [...list].sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  return sorted.find((s) => Date.parse(s.startsAt) - 30 * 60_000 <= now && now <= Date.parse(s.endsAt))
    ?? sorted.find((s) => Date.parse(s.startsAt) > now) ?? sorted[sorted.length - 1];
}

export function DoctorTodayPage() {
  const nav = useNavigate();
  const { me } = useAuth();
  const { sessions, queues, loading } = useTodayQueues(true);
  const work = useGet<WorkItem[]>('/order-items/worklist', true);
  const [sid, setSid] = useState<number | null>(null);
  const { run, busy } = useRun();
  const s = sessions.find((x) => x.id === sid) ?? pickSession(sessions);
  const q = queues.find((x) => x.sessionId === s?.id);

  if (loading) return <div className="page"><Loading /></div>;
  if (!s) return <div className="page"><PageHead title="Danh sách khám" sub="Hôm nay bạn không có phiên khám nào." /></div>;

  const next = q?.waiting[0];
  const current = q?.current ?? [];
  const waitingRest = q?.waiting.slice(1) ?? [];
  const longest = q?.waiting.length ? Math.max(...q.waiting.map((w) => minutesSince(w.visit.checkedInAt))) : 0;
  const pendingLab = (v: Visit) => (work.data ?? []).filter((w) => w.visitId === v.id && w.doctorId === me?.employeeId);

  const callNext = () => {
    let called: Visit | null = null;
    run('call', async () => { called = await api.post<Visit>(`/queue/sessions/${s.id}/call-next`); }, 'Đã gọi vào phòng')
      .then((ok) => ok && called && nav(`/bac-si/kham/${(called as Visit).id}`));
  };

  return (
    <div className="page">
      <PageHead title="Danh sách khám"
        sub={<>Phòng {s.roomCode}, {s.shiftName.toLowerCase()} {time(s.startsAt)} – {time(s.endsAt)}. Đã khám <b>{q?.done ?? 0}</b> người, còn <b>{q?.waiting.length ?? 0}</b> người chờ{longest ? `, chờ lâu nhất ${longest} phút` : ''}.</>} />
      {sessions.length > 1 && (
        <Tabs value={String(s.id)} onChange={(k) => setSid(Number(k))} items={sessions.map((x) => ({ key: String(x.id), label: `${x.shiftName} ${time(x.startsAt)}` }))} />
      )}
      {next ? (
        <section className="ticket" aria-label="Người tiếp theo">
          <Stub n={next.visit.queueNumber} />
          <div className="t">
            <div className="stack tight" style={{ gap: 4 }}>
              <span className="muted small">Người tiếp theo</span>
              <span style={{ fontSize: 22, fontWeight: 700 }}>{next.visit.patientName}</span>
              <span className="muted">{[personLine(null, next.visit.patientDateOfBirth), queueGroup[next.visit.queueGroup].toLowerCase(), next.visit.reason, vitalsShort(next.vitals)].filter(Boolean).join('. ')}</span>
              {vitalsAlerts(next.vitals).length > 0 && <div className="wrap">{vitalsAlerts(next.vitals).map((a) => <Chip key={a.text} tone={a.severe ? 'red' : 'amber'}>{a.text}</Chip>)}</div>}
            </div>
            <Button icon="bell" big disabled={current.length > 0} loading={busy === 'call'} onClick={callNext}
              title={current.length ? 'Kết thúc hoặc cho đi xét nghiệm người đang khám trước' : undefined}>Gọi vào phòng</Button>
          </div>
        </section>
      ) : (
        <div className="ticket flat"><div className="t"><span className="muted">Không còn ai đang chờ.</span></div></div>
      )}
      <div className="cols">
        <div className="wide">
          <Card title="Đang chờ" sub="Hệ thống xếp thứ tự. Người có sinh hiệu bất thường được tô màu để bác sĩ cân nhắc" pad={false}>
            <Table rows={waitingRest} rowKey={(w) => w.visit.id} empty="Không còn ai sau người tiếp theo."
              cols={[
                { head: 'Số', cell: (w) => <b>{w.visit.queueNumber}</b> },
                { head: 'Bệnh nhân', cell: (w) => <Who name={w.visit.patientName} sub={`${personLine(null, w.visit.patientDateOfBirth)}, ${queueGroup[w.visit.queueGroup].toLowerCase()}`} /> },
                { head: 'Lý do khám', cell: (w) => w.visit.reason ?? '' },
                { head: 'Sinh hiệu', cell: (w) => w.vitals ? vitalsShort(w.vitals) : <Chip>Chưa đo</Chip> },
                { head: <span className="sr">Cảnh báo</span>, right: true, cell: (w) => <div className="wrap" style={{ justifyContent: 'flex-end' }}>{vitalsAlerts(w.vitals).map((a) => <Chip key={a.text} tone={a.severe ? 'red' : 'amber'}>{a.text}</Chip>)}</div> },
              ]} />
          </Card>
        </div>
        <div className="narrow">
          <Card title="Đang khám" sheet>
            {current.length === 0 ? <span className="muted">Chưa có ai trong phòng.</span> : current.map((v) => (
              <div className="between" key={v.id}>
                <Who name={`Số ${v.queueNumber}: ${v.patientName}`} sub={v.status === 'CALLED' ? `Đã gọi lúc ${time(v.calledAt!)}` : `Bắt đầu ${time(v.startedAt!)}`} />
                <Button kind="outline" sm onClick={() => nav(`/bac-si/kham/${v.id}`)}>Mở phiếu khám</Button>
              </div>
            ))}
          </Card>
          <Card title="Đi xét nghiệm">
            {(q?.awaitingResults ?? []).length === 0 ? <span className="muted">Không có ai.</span> : q!.awaitingResults.map((v) => {
              const items = pendingLab(v);
              const working = items.some((w) => ['todo', 'entering', 'returned'].includes(stageOf(w.item)));
              const toReview = items.some((w) => stageOf(w.item) === 'review');
              return (
                <div className="stack tight" key={v.id}>
                  <div className="between">
                    <Who name={`Số ${v.queueNumber}: ${v.patientName}`} sub={items.map((w) => w.item.serviceName).join(', ') || 'Kết quả đã duyệt'} />
                    <Chip tone={working ? 'amber' : 'teal'}>{working ? 'Đang làm' : toReview ? 'Có kết quả chờ duyệt' : 'Đã có kết quả'}</Chip>
                  </div>
                  <div className="end">
                    <Button kind="ghost" sm onClick={() => nav(`/bac-si/kham/${v.id}`)}>Xem</Button>
                    <Button kind="outline" sm disabled={current.length > 0} loading={busy === `cb${v.id}`}
                      onClick={() => run(`cb${v.id}`, () => api.post(`/visits/${v.id}/call-back`), 'Đã gọi lại vào phòng').then((ok) => ok && nav(`/bac-si/kham/${v.id}`))}>Gọi lại vào phòng</Button>
                  </div>
                </div>
              );
            })}
            <span className="muted small">Gọi lại thì ứng dụng báo bệnh nhân quay về phòng {s.roomCode}.</span>
          </Card>
        </div>
      </div>
    </div>
  );
}
