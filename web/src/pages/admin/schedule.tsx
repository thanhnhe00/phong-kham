import { useState } from 'react';
import { Button, Card, Chip, Field, Loading, Note, PageHead, Select, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { addDays, dayLabel, dm, mondayOf, shortWeekday, time, timeDay, vnDate } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import type { DoctorCard, Room, ScheduleChange, Session, Specialty } from '../../lib/types';
import { changeStatus, changeType } from '../../lib/labels';
import { WeekNav } from '../../components/week-nav';

export function SchedulePage() {
  const [week, setWeek] = useState(mondayOf(vnDate()));
  const sessions = useGet<Session[]>(`/schedule/sessions?from=${week}&to=${addDays(week, 6)}`);
  const changes = useGet<ScheduleChange[]>('/schedule/change-requests?status=PENDING_MANAGER', true);
  const { run, busy } = useRun();
  const [adding, setAdding] = useState(false);
  const days = [0, 1, 2, 3, 4, 5, 6].map((i) => addDays(week, i));
  const list = sessions.data ?? [];
  const doctors = [...new Map(list.map((s) => [s.doctorId, s])).values()].sort((a, b) => a.specialtyName.localeCompare(b.specialtyName));
  const pending = changes.data ?? [];
  const hasPending = (s: Session) => pending.some((c) => c.sessionId === s.id);

  return (
    <div className="page">
      <PageHead title="Lịch làm việc" actions={<>
        <WeekNav week={week} setWeek={setWeek} />
        <Button kind="outline" loading={busy === 'gen'} onClick={() => run('gen', () => api.post('/schedule/sessions/generate', { from: vnDate(), to: addDays(vnDate(), 28) }), 'Đã tạo phiên khám 4 tuần tới từ lịch cố định')}>Tạo phiên 4 tuần tới</Button>
        <Button icon="plus" onClick={() => setAdding(true)}>Thêm ca khám bù</Button>
      </>} />
      <div className="cols">
        <div className="wide" style={{ flex: '5 1 0' }}>
          <Card title="Lịch theo tuần" sub="Ô vàng là buổi đang có yêu cầu thay đổi, ô gạch là buổi đã hủy" pad={false}>
            {sessions.isLoading ? <Loading /> : doctors.length === 0 ? <div className="empty">Tuần này chưa có phiên khám. Bấm “Tạo phiên 4 tuần tới”.</div> : (
              <div className="tbl-wrap">
                <table className="tbl week">
                  <thead><tr><th>Bác sĩ</th>{days.map((d) => <th key={d}>{shortWeekday(d)} {dm(d)}</th>)}</tr></thead>
                  <tbody>
                    {doctors.map((doc) => (
                      <tr key={doc.doctorId}>
                        <td><Who name={`BS ${doc.doctorName}`} sub={doc.specialtyName} /></td>
                        {days.map((d) => {
                          const ss = list.filter((s) => s.doctorId === doc.doctorId && s.date === d);
                          return (
                            <td key={d}>
                              {ss.length === 0 ? <span className="muted">—</span> : ss.map((s) => (
                                <span key={s.id} className={`shift${s.status === 'CANCELLED' ? ' off' : hasPending(s) ? ' warn' : ''}`} title={`${time(s.startsAt)} – ${time(s.endsAt)}, ${s.totalSlots - s.availableSlots}/${s.totalSlots} giờ đã đặt`}>
                                  {s.shiftName} {s.roomCode}
                                </span>
                              ))}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
        <div className="narrow">
          {adding && <MakeupForm onDone={() => setAdding(false)} />}
          <Card title="Chờ duyệt" sub="Bác sĩ gửi trước ít nhất 24 giờ" sheet={!adding} pad={false}>
            {pending.length === 0 ? <div className="empty">Không có yêu cầu nào.</div> : pending.map((c) => (
              <div key={c.id} className="card-b" style={{ borderTop: '1px solid var(--line-2)' }}>
                <div className="between"><b>BS {c.requesterName}: {changeType[c.type].toLowerCase()} {c.shiftName.toLowerCase()} {dayLabel(c.sessionDate)}</b><Chip tone={changeStatus[c.status][1]}>{changeStatus[c.status][0]}</Chip></div>
                <span className="muted small">Lý do: {c.reason}. {c.replacementName ? `BS ${c.replacementName} đã đồng ý nhận.` : 'Duyệt thì buổi khám bị hủy.'} Lịch hẹn bị ảnh hưởng chuyển sang “Cần báo đổi lịch” để lễ tân gọi. Gửi {timeDay(c.createdAt)}.</span>
                <div className="end">
                  <Button kind="danger" sm loading={busy === `n${c.id}`} onClick={() => {
                    const note = window.prompt('Lý do từ chối (bác sĩ sẽ đọc được)') ?? '';
                    run(`n${c.id}`, () => api.post(`/schedule/change-requests/${c.id}/decide`, { approve: false, note }), 'Đã từ chối');
                  }}>Từ chối</Button>
                  <Button sm loading={busy === `y${c.id}`} onClick={() => run(`y${c.id}`, () => api.post(`/schedule/change-requests/${c.id}/decide`, { approve: true }), 'Đã duyệt')}>Duyệt</Button>
                </div>
              </div>
            ))}
          </Card>
          <Note tone="teal" icon="cal">Lịch cố định hằng tuần do quản lý soạn, bác sĩ xác nhận rồi quản lý duyệt. Màn này tạo phiên khám từ lịch đã duyệt và xử lý nghỉ, đổi ca.</Note>
        </div>
      </div>
    </div>
  );
}

function MakeupForm({ onDone }: { onDone: () => void }) {
  const specs = useGet<Specialty[]>('/specialties');
  const rooms = useGet<Room[]>('/rooms');
  const [spec, setSpec] = useState<number | null>(null);
  const docs = useGet<DoctorCard[]>(spec ? `/doctors?specialtyId=${spec}` : null);
  const [f, setF] = useState({ doctorId: '', roomId: '', date: addDays(vnDate(), 1), shiftName: 'Khám bù', startTime: '17:30', endTime: '19:30', walkInCapacity: '2' });
  const { run, busy } = useRun();
  const up = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  return (
    <Card title="Thêm ca khám bù" sheet>
      <div className="row">
        <Select label="Chuyên khoa" value={spec ?? ''} onChange={(e) => setSpec(Number(e.target.value) || null)}>
          <option value="">Chọn</option>{specs.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </Select>
        <Select label="Bác sĩ" value={f.doctorId} onChange={up('doctorId')}>
          <option value="">Chọn</option>{docs.data?.map((d) => <option key={d.id} value={d.id}>{d.fullName}</option>)}
        </Select>
      </div>
      <div className="row">
        <Field label="Ngày" type="date" value={f.date} onChange={up('date')} />
        <Select label="Phòng" value={f.roomId} onChange={up('roomId')}>
          <option value="">Chọn</option>{rooms.data?.filter((r) => r.isActive).map((r) => <option key={r.id} value={r.id}>{r.code}, {r.name}</option>)}
        </Select>
      </div>
      <div className="row">
        <Field label="Tên buổi" value={f.shiftName} onChange={up('shiftName')} />
        <Field label="Bắt đầu" type="time" value={f.startTime} onChange={up('startTime')} />
        <Field label="Kết thúc" type="time" value={f.endTime} onChange={up('endTime')} />
        <Field label="Chỗ không hẹn" inputMode="numeric" value={f.walkInCapacity} onChange={up('walkInCapacity')} />
      </div>
      <div className="end">
        <Button kind="ghost" onClick={onDone}>Thôi</Button>
        <Button disabled={!spec || !f.doctorId || !f.roomId} loading={busy === 'add'}
          onClick={() => run('add', () => api.post('/schedule/sessions', {
            doctorId: Number(f.doctorId), specialtyId: spec, roomId: Number(f.roomId), date: f.date, shiftName: f.shiftName,
            startTime: f.startTime, endTime: f.endTime, walkInCapacity: Number(f.walkInCapacity) || 0,
          }), 'Đã thêm ca khám bù').then((ok) => ok && onDone())}>Thêm ca</Button>
      </div>
    </Card>
  );
}
