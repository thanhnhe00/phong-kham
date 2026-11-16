import { describe, expect, it } from 'vitest';
import { addDays, age, apptWhen, daysUntil, dmy, initials, maskId, parseDmy, phone, relativeDay, time, vnDate, vnd } from './format';

describe('giờ Việt Nam', () => {
  it('đổi mốc UTC sang ngày và giờ Việt Nam', () => {
    // 17:30 UTC ngày 16 là 00:30 ngày 17 ở Việt Nam
    expect(vnDate('2026-11-16T17:30:00Z')).toBe('2026-11-17');
    expect(time('2026-11-17T01:30:00Z')).toBe('08:30');
  });
  it('ghi lịch hẹn kèm thứ', () => {
    expect(apptWhen('2026-11-17T01:30:00Z')).toBe('08:30, thứ Ba 17/11');
  });
  it('đếm ngày đến lịch hẹn theo ngày Việt Nam', () => {
    const now = new Date('2026-11-15T03:00:00Z'); // 10:00 ngày 15/11
    expect(daysUntil('2026-11-17', now)).toBe(2);
    expect(relativeDay('2026-11-15', now)).toBe('Hôm nay');
    expect(relativeDay('2026-11-16', now)).toBe('Ngày mai');
    expect(relativeDay('2026-11-17', now)).toBe('Còn 2 ngày');
    expect(relativeDay('2026-11-14', now)).toBe('Đã qua');
  });
  it('cộng ngày qua cuối tháng', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
    expect(addDays('2026-11-17', -1)).toBe('2026-11-16');
  });
});

describe('định dạng', () => {
  it('tiền VND có dấu chấm ngăn hàng nghìn', () => {
    expect(vnd(130000)).toBe('130.000 đ');
    expect(vnd(0)).toBe('0 đ');
    expect(vnd(1234567)).toBe('1.234.567 đ');
    expect(vnd(-50000)).toBe('-50.000 đ');
  });
  it('số điện thoại và giấy tờ', () => {
    expect(phone('0912345678')).toBe('0912 345 678');
    expect(maskId('079085001234')).toBe('••••••••1234');
    expect(maskId(null)).toBe('Chưa có');
  });
  it('ngày sinh nhập tay', () => {
    expect(parseDmy('15/03/1985')).toBe('1985-03-15');
    expect(parseDmy('5/3/1985')).toBe('1985-03-05');
    expect(parseDmy('31/02/2020')).toBeNull();
    expect(parseDmy('1985-03-15')).toBeNull();
    expect(dmy('1985-03-15')).toBe('15/03/1985');
  });
  it('tuổi tính theo ngày Việt Nam, chưa tới sinh nhật thì chưa cộng', () => {
    const now = new Date('2026-11-15T03:00:00Z');
    expect(age('1985-03-15', now)).toBe(41);
    expect(age('1985-11-16', now)).toBe(40);
  });
  it('chữ cái đầu của họ tên', () => {
    expect(initials('Trần Văn Bình')).toBe('TB');
    expect(initials('Khang')).toBe('K');
    expect(initials('')).toBe('?');
  });
});
