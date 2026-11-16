import { useState } from 'react';
import { Card, Field, Loading, PageHead, Seg, Table, Who } from '../../components/ui';
import { addDays, dm, mondayOf, vnd, vnDate, weekdayOf } from '../../lib/format';
import { useGet } from '../../lib/hooks';
import type { Summary } from '../../lib/types';

type Range = 'today' | 'week' | 'month' | 'custom';

export function ReportPage() {
  const today = vnDate();
  const [range, setRange] = useState<Range>('month');
  const [from, setFrom] = useState(today.slice(0, 8) + '01');
  const [to, setTo] = useState(today);
  const span: [string, string] = range === 'today' ? [today, today] : range === 'week' ? [mondayOf(today), today] : range === 'month' ? [today.slice(0, 8) + '01', today] : [from, to];
  const q = useGet<Summary>(`/reports/summary?from=${span[0]}&to=${span[1]}`);
  const s = q.data;
  const [table, setTable] = useState(false);

  // Mọi ngày trong khoảng (kể cả ngày không có lượt khám) để trục thời gian liền mạch
  const days: { date: string; visits: number }[] = [];
  for (let d = span[0]; d <= span[1] && days.length < 62; d = addDays(d, 1)) {
    days.push({ date: d, visits: s?.byDay.find((x) => x.date === d)?.visits ?? 0 });
  }
  const max = Math.max(1, ...days.map((d) => d.visits));
  const booked = s ? s.visits - s.walkIns : 0;

  return (
    <div className="page">
      <PageHead title="Báo cáo" actions={<>
        <Seg label="Khoảng thời gian" value={range} onChange={setRange} items={[{ key: 'today', label: 'Hôm nay' }, { key: 'week', label: 'Tuần này' }, { key: 'month', label: 'Tháng này' }, { key: 'custom', label: 'Chọn ngày' }]} />
        {range === 'custom' && <><Field label="Từ ngày" type="date" value={from} onChange={(e) => e.target.value && setFrom(e.target.value)} /><Field label="Đến ngày" type="date" value={to} onChange={(e) => e.target.value && setTo(e.target.value)} /></>}
      </>} />
      {q.isLoading || !s ? <Loading /> : (
        <>
          <div className="row" style={{ gap: 16 }}>
            {[
              ['Lượt khám', String(s.visits), `${booked} có hẹn, ${s.walkIns} không hẹn`, ''],
              ['Vắng mặt', String(s.noShows), `${s.cancelledAppointments} lịch bị hủy`, s.noShows ? 'amber' : ''],
              ['Thực thu', vnd(s.collected), s.refunded ? `Đã trừ ${vnd(s.refunded)} hoàn tiền` : `Phát sinh ${vnd(s.billed)}`, ''],
              ['Chưa thu', vnd(s.unpaid), 'Hóa đơn còn nợ', s.unpaid ? 'amber' : ''],
            ].map(([k, v, sub, tone]) => (
              <div key={k} className="card" style={{ padding: '14px 18px', gap: 4 }}>
                <span className="muted small">{k}</span>
                <span className={`num ${tone}`} style={{ fontSize: 26, fontWeight: 700, letterSpacing: '-0.01em' }}>{v}</span>
                <span className="muted xs">{sub}</span>
              </div>
            ))}
          </div>
          <div className="cols">
            <div className="wide">
              <Card title="Lượt khám mỗi ngày" right={<button type="button" className="link" onClick={() => setTable(!table)}>{table ? 'Xem biểu đồ' : 'Xem dạng bảng'}</button>}>
                {table ? (
                  <Table rows={days} rowKey={(d) => d.date} cols={[{ head: 'Ngày', cell: (d) => dm(d.date) }, { head: 'Lượt khám', right: true, cell: (d) => d.visits }]} />
                ) : (
                  <div className="bars" role="img" aria-label={`Lượt khám mỗi ngày, tổng ${s.visits}`}>
                    {days.map((d) => (
                      <div key={d.date} title={`${dm(d.date)}: ${d.visits} lượt`}>
                        {d.visits > 0 ? <span className="num">{d.visits}</span> : weekdayOf(d.date) % 6 === 0 ? <span>nghỉ</span> : null}
                        <span className="bar" style={{ height: `${(d.visits / max) * 150}px`, opacity: d.visits ? 1 : 0 }} />
                        <span className="num">{d.date.slice(8)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            </div>
            <div className="narrow">
              <Card title="Theo bác sĩ" pad={false}>
                <Table rows={s.byDoctor} rowKey={(d) => d.doctorId} empty="Chưa có lượt khám."
                  cols={[
                    { head: 'Bác sĩ', cell: (d) => <Who name={`BS ${d.doctorName}`} /> },
                    { head: 'Lượt', right: true, cell: (d) => <b>{d.visits}</b> },
                    { head: 'Xong', right: true, cell: (d) => d.completed },
                    { head: 'Phát sinh', right: true, cell: (d) => vnd(d.billed) },
                  ]} />
              </Card>
            </div>
          </div>
          <Card title="Theo dịch vụ" pad={false}>
            <Table rows={s.byService} rowKey={(x) => x.serviceId} empty="Chưa có dịch vụ nào."
              cols={[
                { head: 'Dịch vụ', cell: (x) => x.serviceName },
                { head: 'Số lần', right: true, cell: (x) => x.quantity },
                { head: 'Thành tiền', right: true, cell: (x) => vnd(x.amount) },
              ]} />
          </Card>
        </>
      )}
    </div>
  );
}
