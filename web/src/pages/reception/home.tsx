import { Link, useNavigate } from 'react-router-dom';
import { Button, Card, Chip, Loading, PageHead, Table, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { minutesSince, shortDay, time, vnd, vnDate } from '../../lib/format';
import { useGet, useRun, useTodayQueues } from '../../lib/hooks';
import type { Appointment, AppointmentChange, ConfirmItem, Invoice, Page } from '../../lib/types';

interface Todo { key: string; when: string; sort: number; who: string; sub: string; chip: React.ReactNode; action: React.ReactNode }

export function ReceptionHome() {
  const nav = useNavigate();
  const today = vnDate();
  const { run, busy } = useRun();
  const { queues, sessions, loading } = useTodayQueues();
  const confirm = useGet<ConfirmItem[]>('/appointments/confirmation-queue', true);
  const changes = useGet<AppointmentChange[]>('/appointment-change-requests', true);
  const todayAppts = useGet<Page<Appointment>>(`/appointments?date=${today}&status=CONFIRMED&size=200`, true);
  const unpaid = useGet<Page<Invoice>>('/invoices?status=UNPAID&size=100', true);
  const partial = useGet<Page<Invoice>>('/invoices?status=PARTIALLY_PAID&size=100', true);

  const checkedIn = queues.reduce((n, q) => n + q.current.length + q.waiting.length + q.awaitingResults.length + q.done, 0);
  const waiting = queues.reduce((n, q) => n + q.waiting.length, 0);
  const invoices = [...(unpaid.data?.items ?? []), ...(partial.data?.items ?? [])];
  const owed = invoices.reduce((n, i) => n + i.remaining, 0);

  const todos: Todo[] = [];
  for (const a of todayAppts.data?.items ?? []) {
    const late = minutesSince(a.start);
    if (Date.parse(a.start) > Date.now()) continue;
    todos.push({
      key: `late-${a.id}`, when: time(a.start), sort: Date.parse(a.start), who: a.patientName, sub: `Hẹn ${time(a.start)} hôm nay, BS ${a.doctorName}`,
      chip: <Chip tone="amber">Trễ {late} phút, còn giữ lịch {Math.max(0, 15 - late)} phút</Chip>,
      action: <Button sm loading={busy === `ci-${a.id}`} onClick={() => run(`ci-${a.id}`, () => api.post(`/reception/check-in/appointments/${a.id}`, {}), `Đã check-in ${a.patientName}`)}>Check-in</Button>,
    });
  }
  for (const c of confirm.data ?? []) {
    const a = c.appointment;
    todos.push({
      key: `cf-${a.id}`, when: shortDay(a.date), sort: Date.parse(a.start) + 1e12, who: a.patientName, sub: `Hẹn ${time(a.start)} ${shortDay(a.date).toLowerCase()}, BS ${a.doctorName}`,
      chip: a.status === 'NEEDS_RECONFIRMATION'
        ? <Chip tone="amber">Báo đổi lịch</Chip>
        : <Chip>{c.noAnswerCount ? `Gọi xác nhận, đã gọi ${c.noAnswerCount} lần` : 'Gọi xác nhận'}</Chip>,
      action: <Button kind="outline" sm icon="phone" onClick={() => nav(`/le-tan/lich-hen?tab=${a.status === 'NEEDS_RECONFIRMATION' ? 'recon' : 'confirm'}&id=${a.id}`)}>Gọi</Button>,
    });
  }
  for (const r of (changes.data ?? []).filter((x) => x.status === 'PENDING')) {
    todos.push({
      key: `ch-${r.id}`, when: time(r.createdAt), sort: Date.parse(r.createdAt) + 5e11, who: `Yêu cầu ${r.type === 'CANCEL' ? 'hủy' : 'đổi giờ'} từ ứng dụng`, sub: `Gửi lúc ${time(r.createdAt)}`,
      chip: <Chip tone={r.type === 'CANCEL' ? 'red' : 'blue'}>{r.type === 'CANCEL' ? 'Xin hủy' : 'Xin đổi giờ'}</Chip>,
      action: <Button kind="outline" sm onClick={() => nav('/le-tan/lich-hen?tab=changes')}>Xem</Button>,
    });
  }
  todos.sort((a, b) => a.sort - b.sort);

  return (
    <div className="page">
      <PageHead title="Tổng quan"
        sub={<>Hôm nay đã tiếp đón <b>{checkedIn}</b> người, <b>{waiting}</b> người đang chờ, còn <b>{vnd(owed)}</b> chưa thu.</>}
        actions={<><Button kind="outline" icon="useradd" onClick={() => nav('/le-tan/tiep-don?mode=walkin')}>Đăng ký khám</Button><Button icon="qr" onClick={() => nav('/le-tan/tiep-don?mode=qr')}>Quét mã QR</Button></>} />
      <div className="cols">
        <div className="wide">
          <Card title="Việc cần làm" sub="Việc gấp lên trước: người đang trễ hẹn, rồi gọi xác nhận, yêu cầu từ ứng dụng" pad={false}>
            <Table rows={todos} rowKey={(t) => t.key} empty="Không có việc nào đang chờ."
              cols={[
                { head: 'Lúc', cell: (t) => <b>{t.when}</b>, width: 90 },
                { head: 'Bệnh nhân', cell: (t) => <Who name={t.who} sub={t.sub} /> },
                { head: 'Việc', cell: (t) => t.chip },
                { head: <span className="sr">Thao tác</span>, cell: (t) => t.action, right: true },
              ]} />
          </Card>
        </div>
        <div className="narrow">
          <Card title="Các phòng khám" sheet>
            {loading ? <Loading /> : sessions.length === 0 ? <span className="muted">Hôm nay không có phiên khám.</span> : (
              <div className="stack">
                {sessions.map((s) => {
                  const q = queues.find((x) => x.sessionId === s.id);
                  const cur = q?.current[0];
                  const started = Date.parse(s.startsAt) <= Date.now();
                  const ended = Date.parse(s.endsAt) < Date.now();
                  return (
                    <div className="between" key={s.id}>
                      <Who name={`Phòng ${s.roomCode}, BS ${s.doctorName}`}
                        sub={!started ? `Ca ${time(s.startsAt)} – ${time(s.endsAt)}` : `${cur ? `Đang khám số ${cur.queueNumber}` : 'Chưa gọi ai'}, còn ${q?.waiting.length ?? 0} người chờ`} />
                      {ended ? <Chip>Hết ca</Chip> : !started ? <Chip>Chưa mở</Chip> : cur ? <Chip tone={cur.status === 'CALLED' ? 'amber' : 'teal'}>{cur.status === 'CALLED' ? `Đã gọi số ${cur.queueNumber}` : 'Đang khám'}</Chip> : <Chip tone="teal">Đang mở</Chip>}
                    </div>
                  );
                })}
                <Link to="/le-tan/hang-doi" className="link">Xem hàng đợi</Link>
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
