import { describe, expect, it } from 'vitest';
import { pickSession } from '../pages/doctor/today';
import { stripUnit, suggestQuantity, withUnit } from '../pages/doctor/exam-rx';
import { ageText, dayLabel, mondayOf, parseDmy, personLine, time, vitalsAlerts, vitalsShort, vnd, vnDate } from './format';
import type { OrderItem, Session, Vitals } from './types';
import { stageOf } from './work';

const vit = (v: Partial<Vitals>): Vitals => ({
  id: 1, pulsePerMin: null, temperatureC: null, systolicBp: null, diastolicBp: null, respiratoryRate: null, spo2Percent: null,
  weightKg: null, heightCm: null, recordedByName: null, recordedAt: '', ...v,
});

describe('ngày giờ, tiền theo giờ Việt Nam', () => {
  it('đổi mốc UTC sang ngày, giờ Việt Nam', () => {
    expect(vnDate('2026-11-16T18:30:00Z')).toBe('2026-11-17');
    expect(time('2026-11-17T01:30:00Z')).toBe('08:30');
    expect(dayLabel('2026-11-17')).toBe('thứ Ba 17/11');
    expect(mondayOf('2026-11-22')).toBe('2026-11-16');
  });
  it('tiền VND có dấu chấm ngăn nghìn', () => {
    expect(vnd(230000)).toBe('230.000 đ');
    expect(vnd(-20000)).toBe('−20.000 đ');
  });
  it('tuổi trẻ dưới 2 tuổi tính bằng tháng', () => {
    const now = new Date('2026-11-17T03:00:00Z');
    expect(ageText('2025-05-10', now)).toBe('18 tháng');
    expect(ageText('1985-03-15', now)).toBe('41 tuổi');
    expect(personLine('MALE', '1985-03-15')).toMatch(/^Nam, \d+ tuổi$/);
  });
  it('ngày sinh nhập dạng ngày/tháng/năm, ngày không có thật thì từ chối', () => {
    expect(parseDmy('15/03/1985')).toBe('1985-03-15');
    expect(parseDmy('31/02/2020')).toBeNull();
  });
});

describe('cảnh báo sinh hiệu', () => {
  it('sốt cao, SpO₂ thấp là cảnh báo nặng; sốt nhẹ, huyết áp cao là cảnh báo thường', () => {
    expect(vitalsAlerts(vit({ temperatureC: 39.2 }))).toEqual([{ text: 'Sốt cao 39,2 °C', severe: true }]);
    expect(vitalsAlerts(vit({ temperatureC: 38.1 }))).toEqual([{ text: 'Sốt 38,1 °C', severe: false }]);
    expect(vitalsAlerts(vit({ spo2Percent: 91 }))[0].severe).toBe(true);
    expect(vitalsAlerts(vit({ systolicBp: 150, diastolicBp: 95 }))).toEqual([{ text: 'Huyết áp cao 150/95', severe: false }]);
    expect(vitalsAlerts(vit({ systolicBp: 185, diastolicBp: 100 }))[0].severe).toBe(true);
    expect(vitalsAlerts(vit({ temperatureC: 36.8, systolicBp: 120, diastolicBp: 80, pulsePerMin: 80 }))).toEqual([]);
    expect(vitalsAlerts(null)).toEqual([]);
  });
  it('tóm tắt ngắn cho danh sách', () => {
    expect(vitalsShort(vit({ systolicBp: 120, diastolicBp: 80, temperatureC: 36.8 }))).toBe('HA 120/80, 36,8 °C');
  });
});

describe('giai đoạn của mục xét nghiệm', () => {
  const item = (performances: OrderItem['performances'], status: OrderItem['status'] = 'PERFORMED'): OrderItem => ({
    id: 1, serviceId: 1, serviceName: 'Công thức máu', category: 'LAB', quantity: 1, unitPrice: 80000, status, refusalNote: null, performances,
  });
  const perf = (result: Partial<NonNullable<OrderItem['performances'][0]['result']>> | null) => ({
    id: 1, orderItemId: 1, performedByName: 'ĐD', performedAt: '2026-11-17T02:00:00Z', status: 'DONE' as const,
    result: result && { id: 9, performanceId: 1, status: 'DRAFT' as const, version: 1, resultText: '', conclusion: '', enteredByName: '', enteredAt: '', reviewedByName: null, reviewedAt: null, returnNote: null, files: [], ...result },
  });
  it('chờ làm, đang nhập, bị trả lại, chờ duyệt, đã duyệt', () => {
    expect(stageOf(item([], 'ORDERED'))).toBe('todo');
    expect(stageOf(item([perf(null)]))).toBe('entering');
    expect(stageOf(item([perf({ status: 'DRAFT' })]))).toBe('entering');
    expect(stageOf(item([perf({ status: 'DRAFT', returnNote: 'Ghi rõ giờ lấy mẫu' })]))).toBe('returned');
    expect(stageOf(item([perf({ status: 'SUBMITTED' })]))).toBe('review');
    expect(stageOf(item([perf({ status: 'REVIEWED' })]))).toBe('done');
    expect(stageOf(item([], 'REFUSED'))).toBe('refused');
  });
});

describe('kê đơn', () => {
  it('số lượng gợi ý = liều × số lần × số ngày, làm tròn lên', () => {
    expect(suggestQuantity({ dosePerTime: '1', timesPerDay: '2', days: '7' })).toBe('14');
    expect(suggestQuantity({ dosePerTime: '1,5', timesPerDay: '3', days: '5' })).toBe('23');
    expect(suggestQuantity({ dosePerTime: 'nửa', timesPerDay: '2', days: '5' })).toBe('');
  });
  it('liều lưu kèm đơn vị, sửa thì tách đơn vị ra', () => {
    expect(withUnit('1', 'viên')).toBe('1 viên');
    expect(withUnit('1 viên', 'viên')).toBe('1 viên');
    expect(stripUnit('1 viên', 'viên')).toBe('1');
  });
});

describe('chọn phiên khám của bác sĩ', () => {
  const s = (id: number, start: string, end: string) => ({ id, startsAt: start, endsAt: end } as Session);
  const list = [s(1, '2026-11-17T00:30:00Z', '2026-11-17T04:30:00Z'), s(2, '2026-11-17T06:30:00Z', '2026-11-17T10:00:00Z')];
  it('đang trong ca thì lấy ca đó, giữa hai ca thì lấy ca sắp tới, hết ca thì lấy ca cuối', () => {
    expect(pickSession(list, Date.parse('2026-11-17T02:00:00Z'))?.id).toBe(1);
    expect(pickSession(list, Date.parse('2026-11-17T05:00:00Z'))?.id).toBe(2);
    expect(pickSession(list, Date.parse('2026-11-17T12:00:00Z'))?.id).toBe(2);
    expect(pickSession([], Date.now())).toBeUndefined();
  });
});
