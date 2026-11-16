import { useQueries } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Icon } from '../../components/icons';
import { PatientPicker } from '../../components/patient-form';
import { DoctorPicker, SlotPicker, type PickedSlot } from '../../components/slot-picker';
import { Area, Button, Card, Chip, Field, Kv, Loading, Note, PageHead, Seg, Table, Tabs, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { apptWhen, dmy, phone, shortDay, time, timeDay, vnDate } from '../../lib/format';
import { useGet, useRun } from '../../lib/hooks';
import { apptStatus } from '../../lib/labels';
import type { Appointment, AppointmentChange, ConfirmItem, Contact, Page, Patient } from '../../lib/types';

type Tab = 'confirm' | 'changes' | 'recon' | 'all' | 'book';

const contactResult: Record<string, string> = {
  REACHED: 'Đã nghe máy', NO_ANSWER: 'Không nghe máy', PATIENT_AGREED: 'Đồng ý', PATIENT_DECLINED: 'Từ chối',
  CALLBACK_REQUESTED: 'Hẹn gọi lại', FAILED: 'Không gọi được', OTHER: 'Khác',
};

export function AppointmentsPage() {
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') as Tab) ?? 'confirm';
  const selected = Number(params.get('id')) || null;
  const setTab = (t: Tab) => setParams({ tab: t });
  const select = (id: number) => setParams({ tab, id: String(id) });

  const queue = useGet<ConfirmItem[]>('/appointments/confirmation-queue', true);
  const changes = useGet<AppointmentChange[]>('/appointment-change-requests', true);
  const pendingConfirm = (queue.data ?? []).filter((c) => c.appointment.status === 'PENDING_CONFIRMATION');
  const recon = (queue.data ?? []).filter((c) => c.appointment.status === 'NEEDS_RECONFIRMATION');
  const pendingChanges = (changes.data ?? []).filter((c) => c.status === 'PENDING');

  return (
    <div className="page">
      <PageHead title="Lịch hẹn" sub="Bệnh nhân đặt trên ứng dụng, lễ tân gọi điện xác nhận"
        actions={<Button icon="plus" onClick={() => setTab('book')}>Đặt lịch cho bệnh nhân</Button>} />
      <Tabs value={tab} onChange={setTab} items={[
        { key: 'confirm', label: `Chờ xác nhận (${pendingConfirm.length})` },
        { key: 'changes', label: `Xin đổi / hủy (${pendingChanges.length})` },
        { key: 'recon', label: `Cần báo đổi lịch (${recon.length})` },
        { key: 'all', label: 'Tất cả' },
        ...(tab === 'book' ? [{ key: 'book' as const, label: 'Đặt lịch mới' }] : []),
      ]} />
      {tab === 'confirm' && <CallList items={pendingConfirm} loading={queue.isLoading} selected={selected} onSelect={select} recon={false} />}
      {tab === 'recon' && <CallList items={recon} loading={queue.isLoading} selected={selected} onSelect={select} recon />}
      {tab === 'changes' && <ChangeList items={pendingChanges} loading={changes.isLoading} selected={selected} onSelect={select} />}
      {tab === 'all' && <AllList />}
      {tab === 'book' && <BookForm onDone={() => setTab('all')} />}
    </div>
  );
}

function CallList({ items, loading, selected, onSelect, recon }: { items: ConfirmItem[]; loading: boolean; selected: number | null; onSelect: (id: number) => void; recon: boolean }) {
  const cur = items.find((c) => c.appointment.id === selected) ?? items[0];
  return (
    <div className="cols">
      <div className="wide">
        <Card title={recon ? 'Lịch hẹn cần báo đổi lịch' : 'Lịch hẹn chờ xác nhận'} pad={false}
          sub={recon ? 'Bác sĩ nghỉ hoặc đổi người khám: gọi báo bệnh nhân và hỏi ý kiến' : 'Lịch gần nhất cần gọi trước'}>
          {loading ? <Loading /> : (
            <Table rows={items} rowKey={(c) => c.appointment.id} selected={cur?.appointment.id} onRow={(c) => onSelect(c.appointment.id)}
              empty={recon ? 'Không có lịch nào cần báo đổi.' : 'Không còn lịch nào chờ xác nhận.'}
              cols={[
                { head: 'Giờ hẹn', cell: (c) => <><b>{time(c.appointment.start)}</b> {shortDay(c.appointment.date)}</> },
                { head: 'Bệnh nhân', cell: (c) => <Who name={c.appointment.patientName} sub={phone(c.appointment.patientPhone)} /> },
                { head: 'Bác sĩ', cell: (c) => `BS ${c.appointment.doctorName}` },
                { head: 'Đã gọi', cell: (c) => `${c.noAnswerCount} / 3` },
                { head: 'Tình trạng', cell: (c) => c.maxAttemptsReached ? <Chip tone="red">Gọi 3 lần không được</Chip> : c.recentNoShows ? <Chip tone="amber">Từng vắng {c.recentNoShows} lần</Chip> : <Chip>Chưa liên hệ được</Chip> },
              ]} />
          )}
        </Card>
      </div>
      <div className="narrow">{cur && <CallPanel key={cur.appointment.id} item={cur} recon={recon} />}</div>
    </div>
  );
}

function CallPanel({ item, recon }: { item: ConfirmItem; recon: boolean }) {
  const a = item.appointment;
  const { run, busy } = useRun();
  const contacts = useGet<Contact[]>(`/appointments/${a.id}/contacts`);
  const [note, setNote] = useState('');
  const [moving, setMoving] = useState(false);
  const [slot, setSlot] = useState<PickedSlot | null>(null);
  const log = (result: string, ok: string) => run(result, async () => {
    await api.post(`/appointments/${a.id}/contacts`, { result, note: note || null });
    setNote('');
  }, ok);

  return (
    <Card title={recon ? 'Gọi báo đổi lịch' : 'Gọi xác nhận'} sheet>
      <div className="between"><Who name={a.patientName} sub={`${a.patientCode}`} /><Chip tone={item.noAnswerCount ? 'amber' : 'grey'}>Đã gọi {item.noAnswerCount} / 3 lần</Chip></div>
      {a.patientPhone && (
        <a href={`tel:${a.patientPhone}`} className="btn big num" style={{ fontSize: 17 }}><Icon name="phone" size={20} />{phone(a.patientPhone)}</a>
      )}
      <div className="stack tight">
        <Kv k="Giờ khám" v={apptWhen(a.start)} />
        <Kv k="Bác sĩ" v={`BS ${a.doctorName}, phòng ${a.roomCode}`} />
        <Kv k="Chuyên khoa" v={a.specialtyName} />
        {a.reason && <Kv k="Bệnh nhân ghi" v={a.reason} />}
        {a.services.length > 0 && <Kv k="Muốn làm thêm" v={a.services.map((s) => s.name).join(', ')} />}
      </div>
      {recon && <Note>Lịch bị ảnh hưởng do bác sĩ nghỉ hoặc đổi người khám. Hỏi bệnh nhân đồng ý khám với bác sĩ hiện tại, đổi giờ khác hay hủy.</Note>}
      {moving ? (
        <div className="stack tight">
          <span className="b small">Chọn giờ mới với BS {a.doctorName}</span>
          <SlotPicker doctorId={a.doctorId} value={slot} onChange={setSlot} />
          <div className="end">
            <Button kind="ghost" sm onClick={() => setMoving(false)}>Thôi</Button>
            <Button sm disabled={!slot} loading={busy === 'move'} onClick={() => slot && run('move', async () => {
              await api.post(`/appointments/${a.id}/contacts`, { result: 'PATIENT_AGREED', note: `Đổi sang ${apptWhen(slot.start)}` });
              await api.post(`/appointments/${a.id}/reschedule`, { slotId: slot.id });
            }, 'Đã đổi giờ và xác nhận lịch')}>Đổi sang {slot ? apptWhen(slot.start) : 'giờ mới'}</Button>
          </div>
        </div>
      ) : (
        <div className="stack tight">
          <span className="b small">Kết quả cuộc gọi</span>
          <div className="wrap">
            <Button sm icon="check" loading={busy === 'ok'} onClick={() => run('ok', async () => {
              await api.post(`/appointments/${a.id}/contacts`, { result: 'PATIENT_AGREED', note: note || null });
              await api.post(`/appointments/${a.id}/confirm`, {});
            }, 'Đã xác nhận lịch hẹn')}>Đã xác nhận</Button>
            <Button kind="ghost" sm loading={busy === 'NO_ANSWER'} onClick={() => log('NO_ANSWER', 'Đã ghi: không nghe máy')}>Không nghe máy</Button>
            <Button kind="ghost" sm loading={busy === 'CALLBACK_REQUESTED'} onClick={() => log('CALLBACK_REQUESTED', 'Đã ghi: hẹn gọi lại')}>Hẹn gọi lại</Button>
            <Button kind="ghost" sm onClick={() => setMoving(true)}>Đổi giờ</Button>
            <Button kind="danger" sm loading={busy === 'cancel'} onClick={() => {
              if (!window.confirm(`Hủy lịch hẹn của ${a.patientName}?`)) return;
              run('cancel', async () => {
                await api.post(`/appointments/${a.id}/contacts`, { result: 'PATIENT_DECLINED', note: note || null });
                await api.post(`/appointments/${a.id}/cancel`, { reason: note || 'Bệnh nhân không đến được' });
              }, 'Đã hủy lịch hẹn');
            }}>Hủy lịch</Button>
          </div>
        </div>
      )}
      <Area label="Ghi chú" placeholder="Ví dụ: nhờ gọi sau 11 giờ" value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
      {contacts.data && contacts.data.length > 0 && (
        <div className="stack tight">
          <span className="b small">Các lần gọi</span>
          {contacts.data.map((c) => (
            <span key={c.id} className="muted small">{timeDay(c.contactedAt)}: {contactResult[c.result] ?? c.result}{c.note ? `, ${c.note}` : ''} ({c.employeeName})</span>
          ))}
        </div>
      )}
      <span className="muted small">Gọi tối đa 3 lần. Quá 3 lần vẫn giữ lịch, không tự hủy.</span>
    </Card>
  );
}

function ChangeList({ items, loading, selected, onSelect }: { items: AppointmentChange[]; loading: boolean; selected: number | null; onSelect: (id: number) => void }) {
  // shortcut: lấy chi tiết từng lịch hẹn vì API yêu cầu đổi/hủy chưa kèm tên bệnh nhân; số yêu cầu chờ mỗi lúc ít nên chấp nhận được
  const appts = useQueries({ queries: items.map((r) => ({ queryKey: [`/appointments/${r.appointmentId}`], queryFn: () => api.get<Appointment>(`/appointments/${r.appointmentId}`) })) });
  const byId = new Map(appts.map((q) => q.data).filter((a): a is Appointment => !!a).map((a) => [a.id, a]));
  const cur = items.find((r) => r.id === selected) ?? items[0];
  const { run, busy } = useRun();
  const a = cur && byId.get(cur.appointmentId);
  return (
    <div className="cols">
      <div className="wide">
        <Card title="Bệnh nhân xin đổi hoặc hủy" sub="Gửi từ ứng dụng. Trong lúc chờ duyệt, lịch cũ vẫn giữ nguyên" pad={false}>
          {loading ? <Loading /> : (
            <Table rows={items} rowKey={(r) => r.id} selected={cur?.id} onRow={(r) => onSelect(r.id)} empty="Không có yêu cầu nào đang chờ."
              cols={[
                { head: 'Gửi lúc', cell: (r) => <b>{timeDay(r.createdAt)}</b> },
                { head: 'Bệnh nhân', cell: (r) => { const x = byId.get(r.appointmentId); return x ? <Who name={x.patientName} sub={phone(x.patientPhone)} /> : '…'; } },
                { head: 'Lịch đang hẹn', cell: (r) => { const x = byId.get(r.appointmentId); return x ? <Who name={apptWhen(x.start)} sub={`BS ${x.doctorName}`} /> : '…'; } },
                { head: 'Xin', cell: (r) => r.type === 'CANCEL' ? <Chip tone="red">Hủy lịch</Chip> : <Chip tone="blue">Đổi sang {r.requestedSlotStart ? apptWhen(r.requestedSlotStart) : ''}</Chip> },
              ]} />
          )}
        </Card>
      </div>
      <div className="narrow">
        {cur && a && (
          <Card title={cur.type === 'CANCEL' ? 'Xin hủy lịch' : 'Xin đổi giờ'} sheet>
            <Who name={a.patientName} sub={a.patientCode} />
            <div className="row">
              <div style={{ border: '1px solid var(--line)', borderRadius: 10, padding: 12 }} className="stack tight">
                <span className="muted xs">Lịch đang hẹn</span>
                <b style={cur.type === 'RESCHEDULE' ? { textDecoration: 'line-through', color: 'var(--muted)' } : undefined}>{apptWhen(a.start)}</b>
                <span className="small">BS {a.doctorName}</span>
              </div>
              {cur.type === 'RESCHEDULE' && cur.requestedSlotStart && (
                <div style={{ border: '2px solid var(--brand)', background: 'var(--soft-2)', borderRadius: 10, padding: 12 }} className="stack tight">
                  <span className="muted xs">Xin đổi sang</span><b>{apptWhen(cur.requestedSlotStart)}</b><span className="small">Cùng bác sĩ</span>
                </div>
              )}
            </div>
            {cur.reason && <Kv k="Lý do bệnh nhân ghi" v={cur.reason} />}
            <span className="muted small">
              {cur.type === 'CANCEL' ? 'Duyệt thì lịch bị hủy và giờ khám được trả lại cho người khác đặt.' : 'Duyệt thì lịch tự chuyển sang giờ mới, giờ cũ được trả lại. Nếu giờ mới đã có người đặt, hệ thống sẽ báo lỗi.'}
            </span>
            <div className="end">
              <Button kind="danger" loading={busy === 'reject'} onClick={() => run('reject', () => api.post(`/appointment-change-requests/${cur.id}/reject`), 'Đã từ chối, bệnh nhân sẽ nhận thông báo')}>Từ chối</Button>
              <Button icon="check" loading={busy === 'approve'} onClick={() => run('approve', () => api.post(`/appointment-change-requests/${cur.id}/approve`), 'Đã duyệt yêu cầu')}>
                {cur.type === 'CANCEL' ? 'Duyệt hủy lịch' : 'Duyệt đổi giờ'}
              </Button>
            </div>
            <span className="muted small">Duyệt hay từ chối, bệnh nhân đều nhận thông báo trên ứng dụng.</span>
          </Card>
        )}
      </div>
    </div>
  );
}

function AllList() {
  const [date, setDate] = useState(vnDate());
  const q = useGet<Page<Appointment>>(`/appointments?date=${date}&size=200`);
  const rows = [...(q.data?.items ?? [])].sort((a, b) => a.start.localeCompare(b.start));
  return (
    <Card title={`Lịch hẹn ${shortDay(date).toLowerCase()} (${dmy(date)})`} pad={false}
      right={<Field label="Ngày" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} style={{ width: 150 }} />}>
      {q.isLoading ? <Loading /> : (
        <Table rows={rows} rowKey={(a) => a.id} empty="Không có lịch hẹn nào trong ngày."
          cols={[
            { head: 'Giờ', cell: (a) => <b>{time(a.start)}</b> },
            { head: 'Bệnh nhân', cell: (a) => <Who name={a.patientName} sub={`${a.patientCode}, ${phone(a.patientPhone)}`} /> },
            { head: 'Bác sĩ', cell: (a) => <Who name={`BS ${a.doctorName}`} sub={`${a.specialtyName}, phòng ${a.roomCode}`} /> },
            { head: 'Kênh', cell: (a) => ({ APP_REQUEST: 'Ứng dụng', PHONE: 'Điện thoại', COUNTER: 'Tại quầy' })[a.channel] },
            { head: 'Trạng thái', cell: (a) => <Chip tone={apptStatus[a.status][1]}>{apptStatus[a.status][0]}</Chip> },
          ]} />
      )}
    </Card>
  );
}

function BookForm({ onDone }: { onDone: () => void }) {
  const [patient, setPatient] = useState<Patient | null>(null);
  const [doctorId, setDoctorId] = useState<number | null>(null);
  const [slot, setSlot] = useState<PickedSlot | null>(null);
  const [channel, setChannel] = useState<'PHONE' | 'COUNTER'>('PHONE');
  const [reason, setReason] = useState('');
  const { run, busy } = useRun();
  return (
    <div className="cols">
      <div className="wide">
        <Card title="Đặt lịch cho bệnh nhân" sub="Đặt qua điện thoại hoặc tại quầy thì lịch được xác nhận ngay" sheet>
          <span className="b small">1. Bệnh nhân</span>
          <PatientPicker value={patient} onChange={setPatient} />
          <span className="b small">2. Bác sĩ và giờ khám</span>
          <DoctorPicker value={doctorId} onChange={(d) => { setDoctorId(d); setSlot(null); }} />
          <SlotPicker doctorId={doctorId} value={slot} onChange={setSlot} />
          <span className="b small">3. Thông tin thêm</span>
          <div className="field"><span>Kênh đặt</span>
            <Seg label="Kênh đặt" value={channel} onChange={setChannel} items={[{ key: 'PHONE', label: 'Qua điện thoại' }, { key: 'COUNTER', label: 'Tại quầy' }]} />
          </div>
          <Field label="Lý do khám" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Ví dụ: ho, sốt 3 ngày" />
          <div className="end">
            <Button kind="ghost" onClick={onDone}>Thôi</Button>
            <Button icon="check" disabled={!patient || !slot} loading={busy === 'book'}
              onClick={() => run('book', () => api.post('/appointments', { patientId: patient!.id, slotId: slot!.id, channel, reason: reason || null }), 'Đã đặt lịch')
                .then((ok) => ok && onDone())}>
              {slot ? `Đặt lịch ${apptWhen(slot.start)}` : 'Đặt lịch'}
            </Button>
          </div>
        </Card>
      </div>
      <div className="narrow" />
    </div>
  );
}
