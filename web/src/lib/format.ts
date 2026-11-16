// Định dạng ngày giờ theo giờ Việt Nam (UTC+7) và tiền VND; dùng chung logic với app bệnh nhân.
import type { Sex, Vitals } from './types';

const VN_OFFSET = 7 * 3600_000;
const WEEKDAYS = ['Chủ nhật', 'thứ Hai', 'thứ Ba', 'thứ Tư', 'thứ Năm', 'thứ Sáu', 'thứ Bảy'];
const SHORT = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

const pad = (n: number) => String(n).padStart(2, '0');
const vn = (d: Date | string) => new Date(new Date(d).getTime() + VN_OFFSET);

/** "yyyy-MM-dd" theo giờ Việt Nam */
export function vnDate(d: Date | string = new Date()): string {
  const x = vn(d);
  return `${x.getUTCFullYear()}-${pad(x.getUTCMonth() + 1)}-${pad(x.getUTCDate())}`;
}

/** "08:30" */
export function time(d: Date | string | null | undefined): string {
  if (!d) return '';
  const x = vn(d);
  return `${pad(x.getUTCHours())}:${pad(x.getUTCMinutes())}`;
}

export const weekdayOf = (date: string) => new Date(date.slice(0, 10) + 'T00:00:00Z').getUTCDay();

/** "thứ Ba 17/11" */
export function dayLabel(date: string): string {
  const [, m, d] = date.slice(0, 10).split('-');
  return `${WEEKDAYS[weekdayOf(date)]} ${d}/${m}`;
}

/** "Thứ Ba, 17/11/2026" */
export function longDate(date: string): string {
  const w = WEEKDAYS[weekdayOf(date)];
  return `${w[0].toUpperCase()}${w.slice(1)}, ${dmy(date)}`;
}

export const shortWeekday = (date: string) => SHORT[weekdayOf(date)];

/** "17/11/2026" */
export function dmy(date: string | null | undefined): string {
  if (!date) return '';
  const [y, m, d] = date.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

/** "17/11" */
export function dm(date: string): string {
  const [, m, d] = date.slice(0, 10).split('-');
  return `${d}/${m}`;
}

/** "08:30, thứ Ba 17/11" */
export const apptWhen = (start: string) => `${time(start)}, ${dayLabel(vnDate(start))}`;

/** "08:30 17/11" cho mốc thời gian ISO */
export const timeDay = (iso: string) => `${time(iso)} ${dm(vnDate(iso))}`;

export function addDays(date: string, n: number): string {
  const d = new Date(date + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** Thứ Hai của tuần chứa date */
export function mondayOf(date: string): string {
  return addDays(date, -((weekdayOf(date) + 6) % 7));
}

export function daysUntil(date: string, now: Date = new Date()): number {
  return Math.round((Date.parse(date + 'T00:00:00Z') - Date.parse(vnDate(now) + 'T00:00:00Z')) / 86_400_000);
}

/** "Hôm nay", "Mai", "thứ Năm 19/11" */
export function shortDay(date: string, now: Date = new Date()): string {
  const n = daysUntil(date, now);
  if (n === 0) return 'Hôm nay';
  if (n === 1) return 'Mai';
  return dayLabel(date);
}

/** 130000 -> "130.000 đ" */
export function vnd(n: number | null | undefined): string {
  const v = Math.round(Number(n ?? 0));
  return `${v < 0 ? '−' : ''}${String(Math.abs(v)).replace(/\B(?=(\d{3})+(?!\d))/g, '.')} đ`;
}

/** "0912345678" -> "0912 345 678" */
export function phone(p: string | null | undefined): string {
  if (!p) return '';
  const d = p.replace(/\D/g, '');
  return d.length === 10 ? `${d.slice(0, 4)} ${d.slice(4, 7)} ${d.slice(7)}` : p;
}

export function age(dob: string, now: Date = new Date()): number {
  const [y, m, d] = dob.split('-').map(Number);
  const [ty, tm, td] = vnDate(now).split('-').map(Number);
  return ty - y - (tm < m || (tm === m && td < d) ? 1 : 0);
}

/** Trẻ dưới 6 tuổi ghi tháng: "18 tháng" */
export function ageText(dob: string | null | undefined, now: Date = new Date()): string {
  if (!dob) return '';
  const years = age(dob, now);
  if (years >= 6) return `${years} tuổi`;
  const [y, m, d] = dob.split('-').map(Number);
  const [ty, tm, td] = vnDate(now).split('-').map(Number);
  const months = (ty - y) * 12 + (tm - m) - (td < d ? 1 : 0);
  return months < 24 ? `${months} tháng` : `${years} tuổi`;
}

export const sexLabel = (s: Sex | null | undefined) => (s === 'MALE' ? 'Nam' : s === 'FEMALE' ? 'Nữ' : '');

/** "Nam, 41 tuổi" */
export const personLine = (sex: Sex | null | undefined, dob: string | null | undefined) =>
  [sexLabel(sex), ageText(dob)].filter(Boolean).join(', ');

/** "15/03/1985" -> "1985-03-15", sai định dạng trả null */
export function parseDmy(s: string): string | null {
  const m = s.trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!m) return null;
  const iso = `${m[3]}-${pad(+m[2])}-${pad(+m[1])}`;
  const d = new Date(iso + 'T00:00:00Z');
  return d.getUTCDate() === +m[1] && d.getUTCMonth() + 1 === +m[2] ? iso : null;
}

export function initials(name: string | null | undefined): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  return ((parts.length > 1 ? parts[0][0] : '') + parts[parts.length - 1][0]).toUpperCase();
}

/** Số phút từ mốc ISO tới bây giờ */
export const minutesSince = (iso: string, now: Date = new Date()) => Math.max(0, Math.round((now.getTime() - Date.parse(iso)) / 60_000));

export const decimal = (n: number | null | undefined) => (n == null ? '' : String(n).replace('.', ','));

export interface VitalAlert { text: string; severe: boolean }

/** Cảnh báo sinh hiệu bất thường (người lớn) để tô màu cho bác sĩ, điều dưỡng; severe = cần báo bác sĩ ngay */
export function vitalsAlerts(v: Vitals | null | undefined): VitalAlert[] {
  if (!v) return [];
  const out: VitalAlert[] = [];
  const t = v.temperatureC;
  if (t != null && t >= 38) out.push({ text: `${t >= 39 ? 'Sốt cao' : 'Sốt'} ${decimal(t)} °C`, severe: t >= 39 });
  if (v.spo2Percent != null && v.spo2Percent < 94) out.push({ text: `SpO₂ thấp ${v.spo2Percent}%`, severe: true });
  const sys = v.systolicBp ?? 0;
  const dia = v.diastolicBp ?? 0;
  if (sys >= 140 || dia >= 90) out.push({ text: `Huyết áp ${sys >= 180 || dia >= 110 ? 'rất cao' : 'cao'} ${sys}/${dia}`, severe: sys >= 180 || dia >= 110 });
  if (v.pulsePerMin != null && (v.pulsePerMin > 120 || v.pulsePerMin < 50)) out.push({ text: `Mạch ${v.pulsePerMin}`, severe: true });
  return out;
}

/** "HA 120/80, 36,8 °C" */
export function vitalsShort(v: Vitals | null | undefined): string {
  if (!v) return '';
  const parts: string[] = [];
  if (v.systolicBp != null) parts.push(`HA ${v.systolicBp}/${v.diastolicBp}`);
  if (v.temperatureC != null) parts.push(`${decimal(v.temperatureC)} °C`);
  if (v.pulsePerMin != null) parts.push(`mạch ${v.pulsePerMin}`);
  if (v.spo2Percent != null) parts.push(`SpO₂ ${v.spo2Percent}%`);
  return parts.join(', ');
}
