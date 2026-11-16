// Trang chủ đổi nội dung chính theo tình huống của hồ sơ đang xem (thiết kế A1–A7).
// Hàm thuần, không gọi API: dễ kiểm thử, cùng một logic cho mọi màn hình.
import { daysUntil, vnDate } from './format';
import type { AppNotification, Appointment, MyQueueItem, VisitRecord } from './types';

const ACTIVE = new Set(['PENDING_CONFIRMATION', 'CONFIRMED', 'NEEDS_RECONFIRMATION']);
/** Quá giờ hẹn bao lâu thì lịch chuyển thành bỏ lỡ (app.booking.no-show-after của backend) */
export const NO_SHOW_MINUTES = 15;

export type HomeMain =
  /** A5–A7: đã check-in hôm nay */
  | { kind: 'visit'; item: MyQueueItem; record: VisitRecord | null }
  /** A4: hôm nay có lịch, chưa check-in */
  | { kind: 'today'; appointment: Appointment; lateMinutes: number }
  /** A1, A3: lịch sắp tới gần nhất */
  | { kind: 'upcoming'; appointment: Appointment; count: number }
  /** A2: chưa có lịch, có thể đang đến hạn tái khám */
  | { kind: 'none'; followUp: FollowUp | null };

export interface FollowUp {
  date: string;
  doctorId: number;
  doctorName: string;
  specialtyId: number;
  visitDate: string;
}

export type Todo =
  | { kind: 'pay'; visitId: number; invoiceId: number; amount: number; visitDate: string }
  | { kind: 'followUp'; followUp: FollowUp }
  | { kind: 'result'; notificationId: number; visitId: number | null; title: string; body: string };

export interface HomeInput {
  patientId: number;
  appointments: Appointment[];
  queue: MyQueueItem[];
  /** Hồ sơ các lượt khám gần nhất của bệnh nhân (mới nhất trước) */
  records: VisitRecord[];
  notifications: AppNotification[];
  now?: Date;
}

export function homeMain(input: HomeInput): HomeMain {
  const now = input.now ?? new Date();
  const today = vnDate(now);
  const visitToday = input.queue.find(
    (q) => q.visit.patientId === input.patientId && q.visit.date === today && q.visit.status !== 'LEFT' && q.visit.status !== 'CANCELLED',
  );
  if (visitToday) {
    const record = input.records.find((r) => r.visit.id === visitToday.visit.id) ?? null;
    return { kind: 'visit', item: visitToday, record };
  }
  const active = input.appointments
    .filter((a) => a.patientId === input.patientId && ACTIVE.has(a.status) && a.date >= today)
    .sort((a, b) => a.start.localeCompare(b.start));
  const first = active[0];
  if (first && first.date === today) {
    const late = Math.floor((now.getTime() - Date.parse(first.start)) / 60_000);
    // Quá giờ giữ lịch thì lịch sẽ thành bỏ lỡ: không còn là lịch hôm nay nữa
    if (late <= NO_SHOW_MINUTES) return { kind: 'today', appointment: first, lateMinutes: Math.max(0, late) };
    const next = active.find((a) => a.date > today);
    if (next) return { kind: 'upcoming', appointment: next, count: active.length - 1 };
  } else if (first) {
    return { kind: 'upcoming', appointment: first, count: active.length };
  }
  return { kind: 'none', followUp: followUpOf(input.records, now) };
}

/** Lịch tái khám bác sĩ hẹn mà chưa tới ngày, lấy từ lượt khám mới nhất */
export function followUpOf(records: VisitRecord[], now: Date = new Date()): FollowUp | null {
  const latest = records.find((r) => r.visit.status === 'COMPLETED' && r.emr?.followUpDate);
  if (!latest?.emr?.followUpDate || daysUntil(latest.emr.followUpDate, now) < 0) return null;
  return {
    date: latest.emr.followUpDate,
    doctorId: latest.visit.doctorId,
    doctorName: latest.visit.doctorName,
    specialtyId: latest.visit.specialtyId,
    visitDate: latest.visit.date,
  };
}

/** Việc cần làm, xếp theo mức cần xử lý: thanh toán, đặt lịch tái khám, rồi xem kết quả mới. */
export function homeTodos(input: HomeInput, main: HomeMain): Todo[] {
  const todos: Todo[] = [];
  const visitNow = main.kind === 'visit' ? main.item.visit.id : null;
  for (const r of input.records) {
    // Hóa đơn của lượt đang khám đã hiện ở nội dung chính, không lặp lại
    if (r.invoice && r.invoice.remaining > 0 && r.visit.id !== visitNow && r.invoice.status !== 'CANCELLED') {
      todos.push({ kind: 'pay', visitId: r.visit.id, invoiceId: r.invoice.id, amount: r.invoice.remaining, visitDate: r.visit.date });
    }
  }
  // Tái khám: nhắc ngay sau khi khám xong. Lúc không có lịch thì nội dung chính đã nhắc, lúc đã có lịch thì thôi.
  if (main.kind === 'visit') {
    const f = main.record?.emr?.followUpDate && main.record.visit.status === 'COMPLETED' ? followUpOf([main.record], input.now) : null;
    if (f) todos.push({ kind: 'followUp', followUp: f });
  }
  for (const n of input.notifications) {
    if (n.type === 'RESULTS_READY' && !n.readAt) {
      todos.push({
        kind: 'result',
        notificationId: n.id,
        visitId: n.relatedEntityType === 'VISIT' ? n.relatedEntityId : null,
        title: n.title,
        body: n.body,
      });
    }
  }
  return todos;
}

/** Các bước trong ngày khám của một lượt; bước không áp dụng (không có xét nghiệm) thì không có. */
export interface DayStep {
  label: string;
  note: string;
  state: 'done' | 'now' | 'todo';
}

export function visitSteps(record: VisitRecord | null, item: MyQueueItem, fmt: (iso: string) => string): DayStep[] {
  const v = item.visit;
  const steps: DayStep[] = [{ label: 'Check-in tại quầy', note: v.checkedInAt ? fmt(v.checkedInAt) : '', state: 'done' }];
  const vitals = record?.vitals?.[0];
  if (vitals) steps.push({ label: 'Đo huyết áp, cân nặng', note: fmt(vitals.recordedAt), state: 'done' });
  const examDone = v.status === 'AWAITING_RESULTS' || v.status === 'COMPLETED';
  const examNow = v.status === 'CALLED' || v.status === 'IN_PROGRESS';
  steps.push({
    label: 'Khám với bác sĩ',
    note: v.startedAt && (examDone || v.status === 'IN_PROGRESS') ? fmt(v.startedAt) : v.status === 'CALLED' ? 'Đến lượt' : v.status === 'IN_PROGRESS' ? 'Đang khám' : 'Đang chờ',
    state: examDone ? 'done' : examNow ? 'now' : v.status === 'WAITING' ? 'now' : 'todo',
  });
  const tests = (record?.orders ?? []).flatMap((o) => o.items);
  if (tests.length > 0 || v.status === 'AWAITING_RESULTS') {
    const names = tests.map((t) => t.serviceName).join(', ') || 'theo chỉ định';
    steps.push({ label: /^xét nghiệm/i.test(names) ? names : `Xét nghiệm ${names}`, note: v.status === 'AWAITING_RESULTS' ? 'Đang làm' : '', state: v.status === 'AWAITING_RESULTS' ? 'now' : v.status === 'COMPLETED' ? 'done' : 'todo' });
    steps.push({ label: 'Bác sĩ đọc kết quả', note: '', state: v.status === 'COMPLETED' ? 'done' : 'todo' });
  }
  const paid = record?.invoice?.status === 'PAID';
  steps.push({ label: 'Thanh toán', note: paid ? 'Xong' : '', state: paid ? 'done' : v.status === 'COMPLETED' ? 'now' : 'todo' });
  return steps;
}
