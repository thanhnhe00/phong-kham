import { useState } from 'react';
import { Area, Button, Card, Chip, Loading, Note, PageHead, Seg, Select, Table, Who } from '../../components/ui';
import { WeekNav } from '../../components/week-nav';
import { changeStatus, changeType } from '../../lib/labels';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { addDays, dayLabel, mondayOf, shortWeekday, dm, time, timeDay, vnDate } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import type { DoctorCard, ScheduleChange, Session } from '../../lib/types';

export function DoctorSchedulePage() {
  const { me } = useAuth();
  const [week, setWeek] = useState(mondayOf(vnDate()));
  const sessions = useGet<Session[]>(`/schedule/sessions/mine?from=${week}&to=${addDays(week, 6)}`);
  const upcoming = useGet<Session[]>(`/schedule/sessions/mine?from=${vnDate()}&to=${addDays(vnDate(), 30)}`);
  const changes = useGet<ScheduleChange[]>('/schedule/change-requests', true);
  const { run, busy } = useRun();
  const days = [0, 1, 2, 3, 4, 5, 6].map((i) => addDays(week, i));
  const shifts = [...new Set((sessions.data ?? []).map((s) => s.shiftName))];
  const mine = (changes.data ?? []).filter((c) => c.requestedBy === me?.employeeId);
  const asked = (changes.data ?? []).filter((c) => c.replacementDoctorId === me?.employeeId && c.status === 'PENDING_COLLEAGUE');
  const pendingFor = (s: Session) => mine.find((c) => c.sessionId === s.id && ['PENDING_COLLEAGUE', 'PENDING_MANAGER'].includes(c.status));

  return (
    <div className="page">
      <PageHead title="Lịch làm việc" actions={<WeekNav week={week} setWeek={setWeek} />} />
      <div className="cols">
        <div className="wide stack" style={{ gap: 20 }}>
          <Card title="Lịch của tôi" sub="Quản lý xếp lịch cố định thứ Hai đến thứ Sáu và tạo phiên khám" pad={false}>
            {sessions.isLoading ? <Loading /> : shifts.length === 0 ? <div className="empty">Tuần này bạn không có phiên khám.</div> : (
              <div className="tbl-wrap">
                <table className="tbl week">
                  <thead><tr><th>Buổi</th>{days.map((d) => <th key={d}>{shortWeekday(d)} {dm(d)}</th>)}</tr></thead>
                  <tbody>
                    {shifts.map((sh) => (
                      <tr key={sh}>
                        <td><b>{sh}</b></td>
                        {days.map((d) => (
                          <td key={d}>
                            {(sessions.data ?? []).filter((s) => s.date === d && s.shiftName === sh).map((s) => (
                              <span key={s.id} className={`shift${s.status === 'CANCELLED' ? ' off' : pendingFor(s) ? ' warn' : ''}`}>
                                Phòng {s.roomCode}, {s.totalSlots - s.availableSlots} hẹn{pendingFor(s) ? ', đang xin đổi' : ''}
                              </span>
                            ))}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
          <Card title="Yêu cầu đã gửi" pad={false}>
            <Table rows={mine} rowKey={(c) => c.id} empty="Chưa gửi yêu cầu nào."
              cols={[
                { head: 'Buổi', cell: (c) => <Who name={`${c.shiftName} ${dayLabel(c.sessionDate)}`} sub={`Gửi ${timeDay(c.createdAt)}`} /> },
                { head: 'Loại', cell: (c) => `${changeType[c.type]}${c.replacementName ? `: BS ${c.replacementName}` : ''}` },
                { head: 'Trạng thái', cell: (c) => <Chip tone={changeStatus[c.status][1]}>{changeStatus[c.status][0]}</Chip> },
                { head: <span className="sr">Thao tác</span>, right: true, cell: (c) => ['PENDING_COLLEAGUE', 'PENDING_MANAGER'].includes(c.status) && (
                  <Button kind="ghost" sm loading={busy === `w${c.id}`} onClick={() => run(`w${c.id}`, () => api.post(`/schedule/change-requests/${c.id}/withdraw`), 'Đã rút yêu cầu')}>Rút</Button>
                ) },
              ]} />
          </Card>
        </div>
        <div className="narrow">
          {asked.map((c) => (
            <Card key={c.id} title={`BS ${c.requesterName} nhờ bạn`} sheet>
              <span>{changeType[c.type]} {c.shiftName.toLowerCase()} {dayLabel(c.sessionDate)}. Lý do: {c.reason}</span>
              <div className="end">
                <Button kind="danger" sm loading={busy === `n${c.id}`} onClick={() => run(`n${c.id}`, () => api.post(`/schedule/change-requests/${c.id}/respond`, { accept: false }), 'Đã từ chối')}>Từ chối</Button>
                <Button sm loading={busy === `y${c.id}`} onClick={() => run(`y${c.id}`, () => api.post(`/schedule/change-requests/${c.id}/respond`, { accept: true }), 'Đã đồng ý, chờ quản lý duyệt')}>Đồng ý</Button>
              </div>
            </Card>
          ))}
          <RequestForm sessions={(upcoming.data ?? []).filter((s) => s.status === 'PUBLISHED' && Date.parse(s.startsAt) - Date.now() >= 24 * 3600_000 && !pendingFor(s))} />
        </div>
      </div>
    </div>
  );
}

function RequestForm({ sessions }: { sessions: Session[] }) {
  const [type, setType] = useState<'LEAVE' | 'REPLACEMENT' | 'SWAP'>('LEAVE');
  const [sid, setSid] = useState<number | null>(null);
  const [doctor, setDoctor] = useState<number | null>(null);
  const [swap, setSwap] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const { me } = useAuth();
  const { run, busy } = useRun();
  const s = sessions.find((x) => x.id === sid);
  const docs = useGet<DoctorCard[]>(s ? `/doctors?specialtyId=${s.specialtyId}` : null);
  const colleagues = (docs.data ?? []).filter((d) => d.id !== me?.employeeId);
  const theirs = useGet<Session[]>(type === 'SWAP' && doctor ? `/schedule/sessions?doctorId=${doctor}&from=${vnDate()}&to=${addDays(vnDate(), 30)}` : null);
  const booked = s ? s.totalSlots - s.availableSlots : 0;

  return (
    <Card title="Xin nghỉ hoặc nhờ người khám thay" sheet={false}>
      <Seg label="Loại yêu cầu" value={type} onChange={(t) => { setType(t); setDoctor(null); setSwap(null); }}
        items={[{ key: 'LEAVE', label: 'Xin nghỉ' }, { key: 'REPLACEMENT', label: 'Nhờ bác sĩ khác khám thay' }, { key: 'SWAP', label: 'Đổi ca' }]} />
      <Select label="Buổi" value={sid ?? ''} onChange={(e) => { setSid(Number(e.target.value) || null); setDoctor(null); }}>
        <option value="">Chọn buổi khám (trước ít nhất 24 giờ)</option>
        {sessions.map((x) => <option key={x.id} value={x.id}>{x.shiftName} {dayLabel(x.date)}, phòng {x.roomCode} ({x.totalSlots - x.availableSlots} lịch hẹn)</option>)}
      </Select>
      {type !== 'LEAVE' && s && (colleagues.length === 0
        ? <Note>Không có bác sĩ {s.specialtyName} nào khác. Chọn “Xin nghỉ” để gửi quản lý duyệt.</Note>
        : <Select label="Bác sĩ khám thay" value={doctor ?? ''} onChange={(e) => setDoctor(Number(e.target.value) || null)}>
          <option value="">Chọn bác sĩ cùng chuyên khoa</option>
          {colleagues.map((d) => <option key={d.id} value={d.id}>{d.academicTitle ?? 'BS'} {d.fullName}</option>)}
        </Select>)}
      {type === 'SWAP' && doctor && (
        <Select label="Đổi lấy buổi của bác sĩ đó" value={swap ?? ''} onChange={(e) => setSwap(Number(e.target.value) || null)}>
          <option value="">Chọn buổi</option>
          {(theirs.data ?? []).filter((x) => x.status === 'PUBLISHED').map((x) => <option key={x.id} value={x.id}>{x.shiftName} {dayLabel(x.date)} {time(x.startsAt)}</option>)}
        </Select>
      )}
      <Area label="Lý do" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
      {s && booked > 0 && <Note>Buổi này có {booked} lịch hẹn. Duyệt xong, lễ tân sẽ gọi báo từng bệnh nhân.</Note>}
      <div className="end">
        <Button disabled={!sid || !reason.trim() || (type !== 'LEAVE' && !doctor) || (type === 'SWAP' && !swap)} loading={busy === 'send'}
          onClick={() => run('send', () => api.post('/schedule/change-requests', { sessionId: sid, type, replacementDoctorId: doctor, swapSessionId: swap, reason: reason.trim() }), 'Đã gửi yêu cầu')
            .then((ok) => ok && (setSid(null), setReason('')))}>Gửi yêu cầu</Button>
      </div>
      <span className="muted small">Người được nhờ đồng ý trước, quản lý duyệt sau. Người khám thay phải cùng chuyên khoa và rảnh buổi đó.</span>
    </Card>
  );
}
