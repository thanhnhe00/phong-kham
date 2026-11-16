import { describe, expect, it } from 'vitest';
import { homeMain, homeTodos, visitSteps, type HomeInput } from './home-state';
import type { AppNotification, Appointment, MyQueueItem, Visit, VisitRecord } from './types';

const NOW = new Date('2026-11-17T01:40:00Z'); // 08:40 thứ Ba 17/11

const appt = (over: Partial<Appointment>): Appointment => ({
  id: 1,
  code: 'LH1',
  status: 'CONFIRMED',
  patientId: 1,
  patientName: 'Trần Văn Bình',
  slotId: 1,
  start: '2026-11-17T01:30:00Z',
  end: '2026-11-17T02:00:00Z',
  date: '2026-11-17',
  shiftName: 'Sáng',
  doctorId: 6,
  doctorName: 'Nguyễn Văn An',
  specialtyId: 1,
  specialtyName: 'Nội tổng quát',
  roomCode: 'P101',
  reason: null,
  services: [],
  confirmedAt: null,
  cancelledAt: null,
  cancelReason: null,
  createdAt: '2026-11-14T13:00:00Z',
  ...over,
});

const visit = (over: Partial<Visit>): Visit => ({
  id: 10,
  code: 'LK10',
  source: 'APPOINTMENT',
  status: 'WAITING',
  queueNumber: 16,
  patientId: 1,
  patientName: 'Trần Văn Bình',
  sessionId: 1,
  date: '2026-11-17',
  shiftName: 'Sáng',
  doctorId: 6,
  doctorName: 'Nguyễn Văn An',
  specialtyId: 1,
  specialtyName: 'Nội tổng quát',
  roomCode: 'P101',
  appointmentId: 1,
  reason: null,
  checkedInAt: '2026-11-17T01:21:00Z',
  calledAt: null,
  startedAt: null,
  completedAt: null,
  ...over,
});

const record = (v: Visit, over: Partial<VisitRecord> = {}): VisitRecord => ({
  visit: v,
  vitals: [],
  emr: null,
  orders: [],
  prescriptions: [],
  invoice: null,
  ...over,
});

const input = (over: Partial<HomeInput>): HomeInput => ({ patientId: 1, appointments: [], queue: [], records: [], notifications: [], now: NOW, ...over });

describe('nội dung chính của trang chủ', () => {
  it('A1: lịch sắp tới gần nhất, đếm số lịch', () => {
    const m = homeMain(
      input({
        appointments: [
          appt({ id: 2, date: '2026-11-20', start: '2026-11-20T02:00:00Z' }),
          appt({ id: 3, date: '2026-11-18', start: '2026-11-18T02:00:00Z', status: 'PENDING_CONFIRMATION' }),
        ],
      }),
    );
    expect(m.kind).toBe('upcoming');
    if (m.kind === 'upcoming') {
      expect(m.appointment.id).toBe(3);
      expect(m.count).toBe(2);
    }
  });

  it('bỏ qua lịch đã hủy, bỏ lỡ và lịch của hồ sơ khác', () => {
    const m = homeMain(
      input({
        appointments: [
          appt({ id: 2, date: '2026-11-18', start: '2026-11-18T02:00:00Z', status: 'CANCELLED' }),
          appt({ id: 3, date: '2026-11-19', start: '2026-11-19T02:00:00Z', patientId: 2 }),
          appt({ id: 4, date: '2026-11-16', start: '2026-11-16T02:00:00Z', status: 'NO_SHOW' }),
        ],
      }),
    );
    expect(m.kind).toBe('none');
  });

  it('A4: hôm nay có lịch, trễ 10 phút vẫn là lịch hôm nay', () => {
    const m = homeMain(input({ appointments: [appt({})] }));
    expect(m).toMatchObject({ kind: 'today', lateMinutes: 10 });
  });

  it('A4: quá 15 phút thì không còn là lịch hôm nay, chuyển sang lịch sau', () => {
    const late = new Date('2026-11-17T01:46:00Z'); // 08:46
    const m = homeMain(input({ now: late, appointments: [appt({}), appt({ id: 9, date: '2026-11-24', start: '2026-11-24T01:30:00Z' })] }));
    expect(m.kind).toBe('upcoming');
    if (m.kind === 'upcoming') expect(m.appointment.id).toBe(9);
  });

  it('A5: đã check-in thì trang chủ chuyển sang lượt khám, kể cả khi còn lịch sắp tới', () => {
    const q: MyQueueItem = { visit: visit({}), peopleAhead: 2 };
    const m = homeMain(input({ queue: [q], appointments: [appt({ status: 'CHECKED_IN' }), appt({ id: 9, date: '2026-11-24', start: '2026-11-24T01:30:00Z' })] }));
    expect(m.kind).toBe('visit');
  });

  it('lượt khám bỏ về thì không còn là lượt khám hôm nay', () => {
    const q: MyQueueItem = { visit: visit({ status: 'LEFT' }), peopleAhead: null };
    expect(homeMain(input({ queue: [q] })).kind).toBe('none');
  });

  it('A2: không có lịch, đến hạn tái khám', () => {
    const old = visit({ id: 5, status: 'COMPLETED', date: '2026-11-06', doctorName: 'Trần Thị Bình', specialtyId: 2 });
    const r = record(old, { emr: { status: 'SIGNED', chiefComplaint: null, conclusion: null, treatmentPlan: null, advice: null, followUpDate: '2026-11-20', diagnoses: [], signedByName: null } });
    const m = homeMain(input({ records: [r] }));
    expect(m).toMatchObject({ kind: 'none', followUp: { date: '2026-11-20', doctorName: 'Trần Thị Bình', specialtyId: 2 } });
  });

  it('ngày tái khám đã qua thì thôi nhắc', () => {
    const old = visit({ id: 5, status: 'COMPLETED', date: '2026-10-06' });
    const r = record(old, { emr: { status: 'SIGNED', chiefComplaint: null, conclusion: null, treatmentPlan: null, advice: null, followUpDate: '2026-11-01', diagnoses: [], signedByName: null } });
    expect(homeMain(input({ records: [r] }))).toMatchObject({ kind: 'none', followUp: null });
  });
});

describe('việc cần làm', () => {
  const unpaid = record(visit({ id: 5, status: 'COMPLETED', date: '2026-11-02' }), {
    invoice: { id: 7, code: 'HD7', visitId: 5, totalAmount: 230000, paidAmount: 100000, remaining: 130000, status: 'PARTIALLY_PAID', issuedAt: null, items: [] },
  });
  const result: AppNotification = { id: 3, type: 'RESULTS_READY', title: 'Đã có kết quả', body: 'Công thức máu', relatedEntityType: 'VISIT', relatedEntityId: 5, readAt: null, createdAt: '2026-11-15T00:00:00Z' };

  it('xếp thanh toán trước kết quả mới; kết quả đã đọc thì không nhắc', () => {
    const inp = input({ records: [unpaid], notifications: [result, { ...result, id: 4, readAt: '2026-11-15T01:00:00Z' }] });
    const todos = homeTodos(inp, homeMain(inp));
    expect(todos.map((t) => t.kind)).toEqual(['pay', 'result']);
    expect(todos[0]).toMatchObject({ amount: 130000, visitId: 5 });
  });

  it('hóa đơn của lượt đang khám hôm nay không lặp lại ở việc cần làm', () => {
    const today = record(visit({ status: 'COMPLETED' }), {
      invoice: { id: 8, code: 'HD8', visitId: 10, totalAmount: 230000, paidAmount: 0, remaining: 230000, status: 'UNPAID', issuedAt: null, items: [] },
    });
    const inp = input({ queue: [{ visit: visit({ status: 'COMPLETED' }), peopleAhead: null }], records: [today] });
    expect(homeTodos(inp, homeMain(inp))).toEqual([]);
  });

  it('đã trả đủ thì không nhắc thanh toán', () => {
    const paid = record(unpaid.visit, { invoice: { ...unpaid.invoice!, remaining: 0, paidAmount: 230000, status: 'PAID' } });
    const inp = input({ records: [paid] });
    expect(homeTodos(inp, homeMain(inp))).toEqual([]);
  });
});

describe('các bước trong ngày khám', () => {
  const fmt = (iso: string) => iso.slice(11, 16);

  it('không có chỉ định thì không có bước xét nghiệm', () => {
    const steps = visitSteps(null, { visit: visit({}), peopleAhead: 2 }, fmt);
    expect(steps.map((s) => s.label)).toEqual(['Check-in tại quầy', 'Khám với bác sĩ', 'Thanh toán']);
    expect(steps[1]).toMatchObject({ state: 'now', note: 'Đang chờ' });
  });

  it('có chỉ định thì thêm bước xét nghiệm và đọc kết quả', () => {
    const v = visit({ status: 'AWAITING_RESULTS', startedAt: '2026-11-17T02:05:00Z' });
    const r = record(v, { orders: [{ id: 1, orderedAt: '2026-11-17T02:12:00Z', items: [{ id: 1, serviceName: 'Công thức máu', category: 'LAB', performances: [] }] }] });
    const steps = visitSteps(r, { visit: v, peopleAhead: null }, fmt);
    expect(steps.map((s) => s.label)).toEqual(['Check-in tại quầy', 'Khám với bác sĩ', 'Xét nghiệm Công thức máu', 'Bác sĩ đọc kết quả', 'Thanh toán']);
    expect(steps[2].state).toBe('now');
    expect(steps[1].state).toBe('done');
  });

  it('khám xong chưa trả tiền thì đang ở bước thanh toán', () => {
    const v = visit({ status: 'COMPLETED' });
    const steps = visitSteps(record(v), { visit: v, peopleAhead: null }, fmt);
    expect(steps[steps.length - 1]).toMatchObject({ label: 'Thanh toán', state: 'now' });
  });
});
