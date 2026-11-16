import { describe, expect, it } from 'vitest';
import { firstOpenDay, monthCells, shiftMonth, slotsByDay } from './calendar';
import { qrMatrix } from './qr';
import type { OpenSession } from './types';

describe('lịch tháng', () => {
  it('tháng 11/2026 bắt đầu Chủ nhật: 6 ô trống đầu tuần thứ Hai', () => {
    const cells = monthCells('2026-11');
    expect(cells.slice(0, 7)).toEqual([null, null, null, null, null, null, '2026-11-01']);
    expect(cells.filter(Boolean)).toHaveLength(30);
  });
  it('chuyển tháng qua năm mới', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
  });
});

describe('gom giờ trống theo ngày', () => {
  const session = (date: string, shift: string, starts: string[], status = 'AVAILABLE'): OpenSession => ({
    sessionId: Math.random(),
    date,
    shiftName: shift,
    doctorId: 6,
    doctorName: 'Nguyễn Văn An',
    specialtyId: 1,
    specialtyName: 'Nội',
    roomCode: 'P101',
    slots: starts.map((s, i) => ({ id: i + 1, start: s, end: s, status })),
  });
  const now = new Date('2026-11-17T01:00:00Z'); // 08:00 ngày 17

  it('bỏ giờ đã qua và phiên hết chỗ, xếp buổi sáng trước buổi chiều', () => {
    const map = slotsByDay(
      [
        session('2026-11-17', 'Chiều', ['2026-11-17T06:30:00Z']),
        session('2026-11-17', 'Sáng', ['2026-11-17T00:30:00Z', '2026-11-17T01:30:00Z']),
        session('2026-11-18', 'Sáng', ['2026-11-18T00:30:00Z'], 'BOOKED'),
      ],
      now,
    );
    expect([...map.keys()]).toEqual(['2026-11-17']);
    const day = map.get('2026-11-17')!;
    expect(day.map((d) => d.shiftName)).toEqual(['Sáng', 'Chiều']);
    expect(day[0].slots).toHaveLength(1); // 07:30 đã qua
    expect(firstOpenDay(map)).toBe('2026-11-17');
  });
});

describe('mã QR', () => {
  it('ma trận vuông, có ba ô định vị ở góc', () => {
    const m = qrMatrix('token-thu-nghiem-0123456789');
    expect(m.length).toBeGreaterThanOrEqual(21);
    expect(m.every((row) => row.length === m.length)).toBe(true);
    // ô định vị: viền 7x7 đen ở góc trên trái
    expect(m[0].slice(0, 7).every(Boolean)).toBe(true);
    expect(m[6].slice(0, 7).every(Boolean)).toBe(true);
  });
});
