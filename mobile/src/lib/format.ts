// Định dạng ngày giờ theo giờ Việt Nam (UTC+7, không có giờ mùa hè) và tiền VND.
// Tự cộng 7 giờ thay vì dựa vào Intl timeZone để chạy giống nhau trên Hermes, web và Node (kiểm thử).

const VN_OFFSET = 7 * 3600_000;
const WEEKDAYS = ['Chủ nhật', 'thứ Hai', 'thứ Ba', 'thứ Tư', 'thứ Năm', 'thứ Sáu', 'thứ Bảy'];
const SHORT = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

const pad = (n: number) => String(n).padStart(2, '0');
/** Date "giả" mà các hàm getUTC* trả về giờ Việt Nam */
const vn = (d: Date | string) => new Date(new Date(d).getTime() + VN_OFFSET);

/** "yyyy-MM-dd" theo giờ Việt Nam */
export function vnDate(d: Date | string = new Date()): string {
  const x = vn(d);
  return `${x.getUTCFullYear()}-${pad(x.getUTCMonth() + 1)}-${pad(x.getUTCDate())}`;
}

/** "08:30" */
export function time(d: Date | string): string {
  const x = vn(d);
  return `${pad(x.getUTCHours())}:${pad(x.getUTCMinutes())}`;
}

/** Thứ của một ngày "yyyy-MM-dd" (0 = Chủ nhật) */
export function weekdayOf(date: string): number {
  return new Date(date + 'T00:00:00Z').getUTCDay();
}

/** "thứ Ba 17/11" */
export function dayLabel(date: string): string {
  const [, m, d] = date.split('-');
  return `${WEEKDAYS[weekdayOf(date)]} ${d}/${m}`;
}

/** "T3" */
export const shortWeekday = (date: string) => SHORT[weekdayOf(date)];

/** "17/11/2026" */
export function dmy(date: string): string {
  const [y, m, d] = date.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

/** "17/11" */
export function dm(date: string): string {
  const [, m, d] = date.slice(0, 10).split('-');
  return `${d}/${m}`;
}

/** "08:30, thứ Ba 17/11" cho một lịch hẹn */
export function apptWhen(start: string): string {
  return `${time(start)}, ${dayLabel(vnDate(start))}`;
}

/** Số ngày từ hôm nay (giờ VN) đến ngày date */
export function daysUntil(date: string, now: Date = new Date()): number {
  const a = Date.parse(vnDate(now) + 'T00:00:00Z');
  const b = Date.parse(date + 'T00:00:00Z');
  return Math.round((b - a) / 86_400_000);
}

/** "Hôm nay", "Ngày mai", "Còn 2 ngày", "Đã qua" */
export function relativeDay(date: string, now: Date = new Date()): string {
  const n = daysUntil(date, now);
  if (n === 0) return 'Hôm nay';
  if (n === 1) return 'Ngày mai';
  if (n > 1) return `Còn ${n} ngày`;
  return 'Đã qua';
}

/** Cộng ngày cho chuỗi "yyyy-MM-dd" */
export function addDays(date: string, n: number): string {
  const d = new Date(date + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** 130000 -> "130.000 đ" */
export function vnd(n: number | null | undefined): string {
  const v = Math.round(Number(n ?? 0));
  return `${v < 0 ? '-' : ''}${String(Math.abs(v)).replace(/\B(?=(\d{3})+(?!\d))/g, '.')} đ`;
}

/** "0912345678" -> "0912 345 678" */
export function phone(p: string | null | undefined): string {
  if (!p) return '';
  const d = p.replace(/\D/g, '');
  return d.length === 10 ? `${d.slice(0, 4)} ${d.slice(4, 7)} ${d.slice(7)}` : p;
}

/** Che số giấy tờ, chỉ để lộ 4 số cuối */
export function maskId(id: string | null | undefined): string {
  if (!id) return 'Chưa có';
  return '•'.repeat(Math.max(0, id.length - 4)) + id.slice(-4);
}

/** Tuổi từ ngày sinh "yyyy-MM-dd" */
export function age(dob: string, now: Date = new Date()): number {
  const [y, m, d] = dob.split('-').map(Number);
  const [ty, tm, td] = vnDate(now).split('-').map(Number);
  return ty - y - (tm < m || (tm === m && td < d) ? 1 : 0);
}

/** "15/03/1985" -> "1985-03-15", sai định dạng trả null */
export function parseDmy(s: string): string | null {
  const m = s.trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!m) return null;
  const iso = `${m[3]}-${pad(+m[2])}-${pad(+m[1])}`;
  const d = new Date(iso + 'T00:00:00Z');
  return d.getUTCDate() === +m[1] && d.getUTCMonth() + 1 === +m[2] ? iso : null;
}

/** Chữ cái đầu của họ và tên: "Trần Văn Bình" -> "TB" */
export function initials(name: string | null | undefined): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  return ((parts.length > 1 ? parts[0][0] : '') + parts[parts.length - 1][0]).toUpperCase();
}

/** Tên ngắn để chào: "Trần Văn Bình" -> "Bình" */
export const givenName = (name: string) => name.trim().split(/\s+/).pop() ?? name;

export const doctorTitle = (title: string | null | undefined, name: string) => `${title ? title + ' ' : 'BS '}${name}`;

/** Tiêu đề tháng cho lịch sử khám: "Tháng 11, 2026" */
export function monthTitle(date: string): string {
  const [y, m] = date.split('-');
  return `Tháng ${Number(m)}, ${y}`;
}
