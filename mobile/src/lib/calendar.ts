// Lưới lịch tháng (tuần bắt đầu thứ Hai) và gom slot trống theo ngày cho bước chọn giờ.
import { vnDate, weekdayOf } from './format';
import type { OpenSession, Slot } from './types';

/** Các ô của một tháng: null là ô trống đầu tháng. month: "yyyy-MM" */
export function monthCells(month: string): (string | null)[] {
  const first = `${month}-01`;
  const lead = (weekdayOf(first) + 6) % 7; // thứ Hai = 0
  const [y, m] = month.split('-').map(Number);
  const days = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const cells: (string | null)[] = Array(lead).fill(null);
  for (let d = 1; d <= days; d++) cells.push(`${month}-${String(d).padStart(2, '0')}`);
  return cells;
}

export function shiftMonth(month: string, n: number): string {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(Date.UTC(y, m - 1 + n, 1));
  return d.toISOString().slice(0, 7);
}

export interface DaySlots {
  shiftName: string;
  sessionId: number;
  roomCode: string;
  doctorName: string;
  slots: Slot[];
}

/** Gom các phiên còn slot trống theo ngày; slot đã qua giờ thì bỏ. */
export function slotsByDay(sessions: OpenSession[], now: Date = new Date()): Map<string, DaySlots[]> {
  const map = new Map<string, DaySlots[]>();
  for (const s of sessions) {
    const slots = s.slots.filter((x) => x.status === 'AVAILABLE' && Date.parse(x.start) > now.getTime());
    if (slots.length === 0) continue;
    const list = map.get(s.date) ?? [];
    list.push({ shiftName: s.shiftName, sessionId: s.sessionId, roomCode: s.roomCode, doctorName: s.doctorName, slots });
    map.set(s.date, list);
  }
  for (const list of map.values()) list.sort((a, b) => a.slots[0].start.localeCompare(b.slots[0].start));
  return map;
}

/** Ngày đầu tiên còn chỗ, để chọn sẵn */
export const firstOpenDay = (byDay: Map<string, DaySlots[]>) => [...byDay.keys()].sort()[0] ?? null;

export const todayMonth = (now: Date = new Date()) => vnDate(now).slice(0, 7);
