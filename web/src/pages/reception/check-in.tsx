import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PatientPicker } from '../../components/patient-form';
import { Button, Card, Chip, Field, Loading, Note, PageHead, Select, Table, Who } from '../../components/ui';
import { api } from '../../lib/api';
import { minutesSince, phone, time, vnDate } from '../../lib/format';
import { useGet, useRun, useTodayQueues } from '../../lib/hooks';
import { apptStatus } from '../../lib/labels';
import type { Appointment, Page, Patient, Visit } from '../../lib/types';

export function CheckInPage() {
  const [params] = useSearchParams();
  const today = vnDate();
  const appts = useGet<Page<Appointment>>(`/appointments?date=${today}&size=200`, true);
  const { run, busy } = useRun();
  const [done, setDone] = useState<Visit | null>(null);
  const [token, setToken] = useState('');
  const qrRef = useRef<HTMLInputElement>(null);
  const walkRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (params.get('mode') === 'qr') qrRef.current?.focus();
    if (params.get('mode') === 'walkin') walkRef.current?.scrollIntoView();
  }, [params]);

  const rows = [...(appts.data?.items ?? [])].filter((a) => a.status !== 'CANCELLED' && a.status !== 'PENDING_CONFIRMATION')
    .sort((a, b) => a.start.localeCompare(b.start));

  const checkIn = (key: string, fn: () => Promise<Visit>) => run(key, async () => setDone(await fn()));

  const scan = () => {
    const t = token.trim();
    if (!t) return;
    checkIn('qr', () => api.post<Visit>('/reception/check-in/qr', { token: t })).then(() => setToken(''));
  };

  return (
    <div className="page">
      <PageHead title="Tiếp đón" sub="Bệnh nhân đưa mã QR trên ứng dụng, hoặc đọc số điện thoại để tìm lịch hẹn" />
      <div className="ticket flat" style={{ padding: '12px 16px', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap', display: 'flex' }}>
        <Field ref={qrRef} label="Quét mã QR (máy quét tự điền, hoặc dán mã rồi bấm Enter)" value={token} onChange={(e) => setToken(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && scan()} style={{ flex: 1 }} autoComplete="off" />
        <Button icon="qr" onClick={scan} loading={busy === 'qr'}>Check-in bằng mã QR</Button>
      </div>
      {done && (
        <Note tone="teal" icon="check">
          Đã check-in <b>{done.patientName}</b>{done.appointmentId ? '' : ' (khám không hẹn)'}. Số thứ tự <b>{done.queueNumber}</b>, phòng {done.roomCode}, BS {done.doctorName}.
          {done.appointmentId ? ' Ứng dụng của bệnh nhân đã hiện số và số người phía trước.' : ''}
        </Note>
      )}
      <div className="cols">
        <div className="wide">
          <Card title="Bệnh nhân có hẹn hôm nay" sub="Quá giờ hẹn 15 phút chưa đến thì lịch tự chuyển thành vắng mặt" pad={false}>
            {appts.isLoading ? <Loading /> : (
              <Table rows={rows} rowKey={(a) => a.id} empty="Hôm nay chưa có lịch hẹn nào."
                cols={[
                  { head: 'Giờ hẹn', cell: (a) => <b>{time(a.start)}</b> },
                  { head: 'Bệnh nhân', cell: (a) => <Who name={a.patientName} sub={phone(a.patientPhone)} /> },
                  { head: 'Bác sĩ', cell: (a) => `BS ${a.doctorName}` },
                  { head: 'Trạng thái', cell: (a) => {
                    if (a.status === 'CONFIRMED' && Date.parse(a.start) < Date.now()) return <Chip tone="amber">Trễ {minutesSince(a.start)} phút</Chip>;
                    return <Chip tone={a.status === 'CONFIRMED' ? 'grey' : apptStatus[a.status][1]}>{a.status === 'CONFIRMED' ? 'Chưa đến' : apptStatus[a.status][0]}</Chip>;
                  } },
                  { head: <span className="sr">Thao tác</span>, right: true, cell: (a) => a.status === 'CONFIRMED' && (
                    <Button sm kind={Date.parse(a.start) < Date.now() ? 'primary' : 'outline'} loading={busy === `a${a.id}`}
                      onClick={() => checkIn(`a${a.id}`, () => api.post<Visit>(`/reception/check-in/appointments/${a.id}`, {}))}>Check-in</Button>
                  ) },
                ]} />
            )}
          </Card>
        </div>
        <div className="narrow" ref={walkRef}>
          <WalkIn onDone={setDone} />
        </div>
      </div>
    </div>
  );
}

function WalkIn({ onDone }: { onDone: (v: Visit) => void }) {
  const { sessions, queues } = useTodayQueues();
  const [patient, setPatient] = useState<Patient | null>(null);
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [companion, setCompanion] = useState('');
  const [companionPhone, setCompanionPhone] = useState('');
  const { run, busy } = useRun();
  const open = sessions.filter((s) => s.status === 'PUBLISHED' && Date.parse(s.endsAt) > Date.now());
  const isChild = patient ? patient.ageInMonths < 72 : false;

  const submit = () => run('walkin', async () => {
    const v = await api.post<Visit>('/reception/walk-ins', {
      patientId: patient!.id, sessionId, reason: reason || null, companionName: companion || null, companionPhone: companionPhone || null,
    });
    onDone(v);
    setPatient(null);
    setReason('');
    setCompanion('');
    setCompanionPhone('');
  });

  return (
    <Card title="Khám không hẹn" sheet>
      <PatientPicker value={patient} onChange={setPatient} />
      {patient && (
        <>
          <Select label="Phòng khám" value={sessionId ?? ''} onChange={(e) => setSessionId(Number(e.target.value) || null)}>
            <option value="">Chọn bác sĩ đang khám</option>
            {open.map((s) => {
              const q = queues.find((x) => x.sessionId === s.id);
              return <option key={s.id} value={s.id}>{s.specialtyName}: BS {s.doctorName}, phòng {s.roomCode} ({q?.waiting.length ?? 0} người chờ)</option>;
            })}
          </Select>
          <Field label="Lý do khám" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Ví dụ: sốt, đau đầu từ tối qua" />
          <div className="row">
            <Field label={isChild ? 'Người đi cùng (bắt buộc với trẻ em)' : 'Người đi cùng'} value={companion} onChange={(e) => setCompanion(e.target.value)} />
            <Field label="Số điện thoại người đi cùng" inputMode="tel" value={companionPhone} onChange={(e) => setCompanionPhone(e.target.value)} />
          </div>
          <div className="between">
            <span className="muted small">Khám không hẹn xếp xen kẽ: cứ 3 người có hẹn thì 1 người không hẹn.</span>
            <Button disabled={!sessionId || (isChild && !companion.trim())} loading={busy === 'walkin'} onClick={submit}>Cấp số</Button>
          </div>
        </>
      )}
      {open.length === 0 && <span className="muted small">Hiện không có phiên khám nào đang mở.</span>}
    </Card>
  );
}
