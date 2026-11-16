// Kiểm thử hiệu năng backend: nhiều người dùng đồng thời gọi các API dùng nhiều nhất trong ngày khám.
// Chạy: backend ở http://localhost:8080 có dữ liệu mẫu (SEED_DEMO=true), rồi `node perf/hieu-nang.mjs`.
// Kết quả in ra bảng và ghi perf/ket-qua.json (dùng cho báo cáo).
import { readFileSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const API = (process.env.API_TARGET ?? 'http://localhost:8080') + '/api';
const SECONDS = Number(process.env.SECONDS ?? 20);

async function call(token, method, path, body) {
  const res = await fetch(API + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}
const login = async (phone, password = '12345678') => (await call(null, 'POST', '/auth/login', { phone, password })).data.token;
// Đóng vai cổng SMS để tạo tài khoản bệnh nhân (người dùng nhắn tin xác minh tới phòng khám)
const GATEWAY_TOKEN = process.env.SMS_GATEWAY_TOKEN
  ?? readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '.env'), 'utf8').match(/^SMS_GATEWAY_TOKEN=(.*)$/m)?.[1].trim();
const smsArrives = (from, body) => fetch(API + '/sms/inbound', {
  method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Gateway-Token': GATEWAY_TOKEN ?? '' }, body: JSON.stringify({ from, body }),
});
const vnToday = () => new Date(Date.now() + 7 * 3600_000).toISOString().slice(0, 10);
const addDays = (d, n) => new Date(Date.parse(d) + n * 86_400_000).toISOString().slice(0, 10);
const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];

/** users người dùng ảo, mỗi người gọi liên tục các yêu cầu trong reqs trong SECONDS giây */
async function load(name, users, reqs, seconds = SECONDS) {
  const times = [];
  let errors = 0;
  const end = Date.now() + seconds * 1000;
  const t0 = Date.now();
  await Promise.all(Array.from({ length: users }, async (_, u) => {
    let i = u;
    while (Date.now() < end) {
      const r = reqs[i++ % reqs.length];
      const s = performance.now();
      try {
        const res = await fetch(API + r.path, { method: r.method ?? 'GET', headers: { 'Content-Type': 'application/json', ...(r.token ? { Authorization: `Bearer ${r.token}` } : {}) }, body: r.body ? JSON.stringify(r.body) : undefined });
        await res.arrayBuffer();
        if (res.status >= 400) errors++;
      } catch {
        errors++;
      }
      times.push(performance.now() - s);
    }
  }));
  const elapsed = (Date.now() - t0) / 1000;
  times.sort((a, b) => a - b);
  const row = {
    name, users, seconds: Math.round(elapsed), requests: times.length, rps: Math.round(times.length / elapsed),
    avg: Math.round(times.reduce((a, b) => a + b, 0) / times.length), p50: Math.round(pct(times, 50)), p95: Math.round(pct(times, 95)),
    p99: Math.round(pct(times, 99)), max: Math.round(times[times.length - 1]), errors, errorRate: +(errors / times.length * 100).toFixed(2),
  };
  console.log(`${name}: ${row.users} người, ${row.requests} yêu cầu, ${row.rps} yêu cầu/giây, TB ${row.avg} ms, p95 ${row.p95} ms, p99 ${row.p99} ms, lỗi ${row.errorRate}%`);
  return row;
}

async function main() {
  const today = vnToday();
  const reception = await login('0900000003');
  const patient = await login('0900000099');
  const doctors = (await call(null, 'GET', '/doctors?specialtyId=1')).data;
  const an = doctors.find((d) => d.fullName === 'Nguyễn Văn An') ?? doctors[0];
  const sessions = (await call(reception, 'GET', `/schedule/sessions?from=${today}&to=${addDays(today, 7)}`)).data;
  const sid = sessions[0]?.id;
  const rows = [];

  rows.push(await load('Khách xem chuyên khoa, bác sĩ, giờ trống (không đăng nhập)', 50, [
    { path: '/specialties' }, { path: `/doctors?specialtyId=1` }, { path: `/slots?doctorId=${an.id}&from=${today}&to=${addDays(today, 14)}` },
  ]));
  rows.push(await load('App bệnh nhân hỏi lại số thứ tự, lịch hẹn, thông báo', 100, [
    { path: '/me/queue', token: patient }, { path: '/me/appointments', token: patient }, { path: '/me/notifications?size=50', token: patient },
  ]));
  rows.push(await load('Web lễ tân: hàng đợi, lịch chờ xác nhận, hóa đơn chưa thu', 30, [
    ...(sid ? [{ path: `/queue/sessions/${sid}`, token: reception }] : []),
    { path: '/appointments/confirmation-queue', token: reception }, { path: '/invoices?status=UNPAID&size=100', token: reception },
    { path: `/appointments?date=${today}&size=200`, token: reception },
  ]));
  rows.push(await load('Đăng nhập (băm mật khẩu BCrypt, cố ý chậm để chống dò mật khẩu)', 10, [
    { path: '/auth/login', method: 'POST', body: { phone: '0900000003', password: '12345678' } },
  ], 15));

  // Tranh chấp: nhiều bệnh nhân cùng giành một giờ khám: đúng một người được giữ chỗ
  const N = 20;
  const free = (await call(null, 'GET', `/slots?doctorId=${an.id}&from=${addDays(today, 1)}&to=${addDays(today, 14)}`)).data
    .flatMap((s) => s.slots).filter((x) => x.status === 'AVAILABLE');
  const slot = free[free.length - 1];
  const tokens = [];
  for (let i = 0; i < N; i++) {
    const phone = '08' + String(Math.floor(Math.random() * 1e8)).padStart(8, '0');
    const { code, smsBody } = (await call(null, 'POST', '/auth/phone-codes', { phone, purpose: 'REGISTER' })).data;
    await smsArrives(phone, smsBody);
    const t = (await call(null, 'POST', '/auth/register', { phone, code, password: 'matkhau123' })).data.token;
    const p = (await call(t, 'POST', '/me/patients', { relationship: 'SELF', profile: { fullName: `Hiệu Năng ${i}`, dateOfBirth: '1990-01-01', sex: 'MALE' } })).data;
    tokens.push({ t, pid: p.patient.id });
  }
  const s0 = performance.now();
  const results = await Promise.all(tokens.map(({ t, pid }) => call(t, 'POST', '/me/appointments', { patientId: pid, slotId: slot.id })));
  const ms = Math.round(performance.now() - s0);
  const won = results.filter((r) => r.status === 201).length;
  const lost = results.filter((r) => r.status === 409).length;
  const race = { users: N, slot: slot.start, success: won, conflict: lost, other: N - won - lost, ms };
  console.log(`${N} bệnh nhân cùng đặt một giờ: ${won} thành công, ${lost} được báo đã có người đặt, ${race.other} lỗi khác, ${ms} ms`);

  const env = { cpu: os.cpus()[0].model, cores: os.cpus().length, ramGB: Math.round(os.totalmem() / 1e9), os: `${os.type()} ${os.release()}`, node: process.version, at: new Date().toISOString() };
  writeFileSync(new URL('./ket-qua.json', import.meta.url), JSON.stringify({ env, rows, race }, null, 2));
  if (won !== 1) process.exit(1);
}

main().catch((e) => { console.error(e); process.exit(1); });
