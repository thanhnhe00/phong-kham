// Kiểm thử đầu-cuối web nhân viên với backend thật: lễ tân, điều dưỡng, bác sĩ, quản lý cùng thao tác qua giao diện
// trên một lượt khám (xác nhận lịch, đặt lịch hộ, check-in QR, đo sinh hiệu, khám, xét nghiệm, trả lại, duyệt, kê đơn,
// kết thúc, thu tiền, hoàn tiền, tạo nhân viên). Phía bệnh nhân (đặt lịch trên app, lấy mã QR) gọi thẳng API.
// Chạy: backend ở http://localhost:8080 (docker compose up), `npm run build`, rồi `npm run e2e`. Ảnh chụp ở e2e/screenshots/.
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.join(ROOT, '..', 'dist');
const SHOTS = path.join(ROOT, 'screenshots');
const API = new URL(process.env.API_TARGET ?? 'http://localhost:8080');
const PORT = 8098;
const BASE = `http://localhost:${PORT}`;
const EDGE = process.env.BROWSER ?? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PDF = path.join(os.tmpdir(), 'ket-qua-cong-thuc-mau.pdf');
writeFileSync(PDF, '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');

// ---------- máy chủ tĩnh cho dist/ + chuyển tiếp /api ----------
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  if (req.url?.startsWith('/api/')) {
    const up = http.request({ hostname: API.hostname, port: API.port, path: req.url, method: req.method, headers: { ...req.headers, host: API.host } }, (r) => {
      res.writeHead(r.statusCode ?? 502, r.headers);
      r.pipe(res);
    });
    up.on('error', () => { res.writeHead(502); res.end(); });
    req.pipe(up);
    return;
  }
  let file = path.join(DIST, decodeURIComponent((req.url ?? '/').split('?')[0]));
  if (!file.startsWith(DIST) || !existsSync(file) || statSync(file).isDirectory()) file = path.join(DIST, 'index.html');
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
  createReadStream(file).pipe(res);
});

// ---------- API (phía bệnh nhân và kiểm tra kết quả) ----------
async function call(token, method, p, body) {
  const res = await fetch(`${API.origin}/api${p}`, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${method} ${p} -> ${res.status} ${data?.message ?? text}`);
  return data;
}
const apiLogin = async (phone, password = '12345678') => (await call(null, 'POST', '/auth/login', { phone, password })).token;
const vnNow = () => new Date(Date.now() + 7 * 3600_000);
const vnTime = (iso) => new Date(Date.parse(iso) + 7 * 3600_000).toISOString().slice(11, 16);
const randPhone = () => '09' + String(Math.floor(Math.random() * 1e8)).padStart(8, '0');

// ---------- đóng vai cổng SMS: chuyển tin nhắn xác minh người dùng gửi tới phòng khám ----------
const GATEWAY_TOKEN =
  process.env.SMS_GATEWAY_TOKEN ?? readFileSync(path.join(ROOT, '..', '..', '.env'), 'utf8').match(/^SMS_GATEWAY_TOKEN=(.*)$/m)?.[1].trim();
async function smsArrives(from, body) {
  const res = await fetch(`${API.origin}/api/sms/inbound`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Gateway-Token': GATEWAY_TOKEN ?? '' },
    body: JSON.stringify({ from, body }),
  });
  const data = await res.json();
  if (data.result !== 'VERIFIED') throw new Error(`Cổng SMS: ${res.status} ${data.result ?? data.message}`);
}

async function patientAccount(fullName, cccd) {
  const phone = randPhone();
  const { code, smsBody } = await call(null, 'POST', '/auth/phone-codes', { phone, purpose: 'REGISTER' });
  await smsArrives(phone, smsBody);
  const { token } = await call(null, 'POST', '/auth/register', { phone, code, password: 'matkhau123' });
  const p = await call(token, 'POST', '/me/patients', {
    relationship: 'SELF', profile: { fullName, dateOfBirth: '1985-03-15', sex: 'MALE', identityType: 'CCCD', identityNumber: cccd, phone },
  });
  return { token, phone, patientId: p.patient.id };
}

// ---------- trình duyệt ----------
let browser;
let step = 0;
const results = [];
const pages = {};

async function shot(page, name) {
  step += 1;
  await page.screenshot({ path: path.join(SHOTS, `${String(step).padStart(2, '0')}-${name}.png`), fullPage: true });
}
async function check(name, fn) {
  try {
    await fn();
    results.push({ name, ok: true });
    console.log(`  ✓ ${name}`);
  } catch (e) {
    results.push({ name, ok: false });
    console.log(`  ✗ ${name}\n      ${e.message ?? e}`);
    for (const [k, p] of Object.entries(pages)) await shot(p, `loi-${k}`).catch(() => undefined);
    throw e;
  }
}
/** Phần tử bấm được đang hiện, chứa chữ t (trong vùng within nếu có); nhiều cái thì lấy cái chữ ngắn nhất */
async function click(page, t, within) {
  const find = ([s, w]) => {
    const roots = w ? [...document.querySelectorAll('tr, section, .between, form')].filter((r) => r.innerText.includes(w)) : [document];
    for (const root of roots.sort((a, b) => (a.innerText?.length ?? 0) - (b.innerText?.length ?? 0))) {
      const els = [...root.querySelectorAll('button,a,[role="tab"],[role="radio"],tr[aria-selected],label.check')].filter((e) => {
        const r = e.getBoundingClientRect();
        return r.width > 0 && r.height > 0 && !e.disabled && e.innerText?.includes(s);
      });
      els.sort((a, b) => a.innerText.length - b.innerText.length);
      if (els[0]) return els[0];
    }
    return null;
  };
  await page.waitForFunction(find, { timeout: 15_000 }, [t, within]);
  const el = await page.evaluateHandle(find, [t, within]);
  await el.asElement().click();
}
const see = (page, t, timeout = 15_000) => page.waitForFunction((s) => document.body.innerText.includes(s), { timeout }, t);
const gone = (page, t, timeout = 15_000) => page.waitForFunction((s) => !document.body.innerText.includes(s), { timeout }, t);
async function fill(page, label, value, nth = 0) {
  await page.waitForFunction(([l, n]) => document.querySelectorAll(`[aria-label="${l}"]`).length > n, { timeout: 15_000 }, [label, nth]);
  const el = (await page.$$(`[aria-label="${label}"]`))[nth];
  await el.focus();
  await el.evaluate((e) => e.select?.()); // chọn hết chữ cũ (kể cả ô nhiều dòng) rồi gõ đè
  await el.press('Backspace');
  await el.type(value);
}
const go = (page, p) => page.goto(BASE + p, { waitUntil: 'networkidle0' });

async function staffPage(key, phone, password = '12345678') {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport({ width: 1366, height: 900 });
  page.on('pageerror', (e) => console.log(`    [lỗi trang ${key}]`, e.message));
  page.on('dialog', (d) => d.accept(d.type() === 'prompt' ? 'Kiểm thử' : undefined));
  if (process.env.DEBUG) { page.on('request', (r) => key !== 'le-tan' && r.url().includes('/api/') && console.log('    [gửi]', key, r.method(), r.url().replace(BASE, ''))); page.on('requestfailed', (r) => console.log('    [hỏng]', key, r.url(), r.failure()?.errorText)); }
  if (process.env.DEBUG) page.on('response', (r) => r.url().includes('/api/') && (key !== 'le-tan' || r.status() >= 400) && console.log(`    [${key}] ${r.status()} ${r.request().method()} ${r.url().replace(BASE, '')}`));
  await go(page, '/');
  await fill(page, 'Số điện thoại', phone);
  await fill(page, 'Mật khẩu', password);
  if (process.env.DEBUG) console.log('    [đăng nhập]', key, await page.$eval('[aria-label="Số điện thoại"]', (e) => e.value));
  await click(page, 'Đăng nhập');
  if (process.env.DEBUG) console.log('    [đã bấm]', key, await page.evaluate(() => document.activeElement?.outerHTML.slice(0, 80)));
  // chờ đăng nhập xong (vào trang làm việc, hoặc màn đổi mật khẩu tạm) rồi mới chuyển trang
  await page.waitForFunction(() => document.querySelector('.side') || document.body.innerText.includes('Bạn đang dùng mật khẩu tạm'), { timeout: 60_000 });
  pages[key] = page;
  return page;
}

// ---------- kịch bản ----------
async function main() {
  if (!existsSync(path.join(DIST, 'index.html'))) throw new Error('Chưa có bản build: chạy `npm run build` trước.');
  rmSync(SHOTS, { recursive: true, force: true });
  mkdirSync(SHOTS, { recursive: true });
  await new Promise((r) => server.listen(PORT, r));

  // Chuẩn bị: cần một bác sĩ có phiên hôm nay còn ít nhất 2 giờ trống. Thử lần lượt BS An, Bình, Châu, Dung;
  // ai chưa đủ giờ thì quản lý mở phiên khám bù nối sau phiên cuối (mỗi lần chạy dùng mất 2 giờ khám).
  const manager = await apiLogin('0900000001');
  const today = vnNow().toISOString().slice(0, 10);
  const rooms = await call(manager, 'GET', '/rooms');
  const candidates = [['0900000011', 'P101'], ['0900000012', 'P102'], ['0900000013', 'P103'], ['0900000014', 'P104']];
  const freeSlots = async (doctorId) => (await call(null, 'GET', `/slots?doctorId=${doctorId}&from=${today}&to=${today}`))
    .flatMap((s) => s.slots.map((x) => ({ ...x, sessionId: s.sessionId }))).filter((x) => x.status === 'AVAILABLE' && Date.parse(x.start) > Date.now() + 2 * 60_000);
  const toMin = (iso) => { const [hh, mm] = vnTime(iso).split(':').map(Number); return hh * 60 + mm; };
  const hhmm = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  // bác sĩ thử nghiệm do các lần chạy trước tạo (mật khẩu đã đổi thành matkhau123) cũng dùng lại được
  const testDocs = (await call(manager, 'GET', `/staff?q=${encodeURIComponent('Bác Sĩ Thử')}&size=50`)).items.map((x) => [x.phone, null, 'matkhau123']);
  let doc = null;
  for (const [phone, roomCode, password = '12345678'] of [...candidates, ...testDocs]) {
    const me = await call(await apiLogin(phone, password).catch(() => null), 'GET', '/auth/me').catch(() => null);
    if (!me) continue;
    const card = (await call(null, 'GET', `/doctors/${me.employeeId}`));
    const specialtyId = card.specialties?.[0]?.id ?? card.specialtyIds?.[0];
    if (roomCode && (await freeSlots(me.employeeId)).length < 2) {
      const sessions = await call(manager, 'GET', `/schedule/sessions?from=${today}&to=${today}&doctorId=${me.employeeId}`);
      const n = vnNow();
      const start = Math.max(Math.ceil((n.getUTCHours() * 60 + n.getUTCMinutes() + 10) / 15) * 15, ...sessions.filter((s) => s.status !== 'CANCELLED').map((s) => toMin(s.endsAt)));
      const end = Math.min(start + 120, 23 * 60 + 59);
      if (end - start < 30) continue;
      await call(manager, 'POST', '/schedule/sessions', {
        doctorId: me.employeeId, specialtyId, roomId: rooms.find((r) => r.code === roomCode).id, date: today, shiftName: 'Khám bù',
        startTime: hhmm(start), endTime: hhmm(end), slotMinutes: 15, walkInCapacity: 3,
      }).catch(() => undefined); // phòng đang bận giờ đó thì thử bác sĩ khác
    }
    const slots = await freeSlots(me.employeeId);
    if (slots.length >= 2) {
      // bỏ qua bác sĩ còn người khám dở hoặc đi xét nghiệm từ lần chạy trước (nút gọi số bị khóa đúng nghiệp vụ)
      const q = await call(manager, 'GET', `/queue/sessions/${slots[0].sessionId}`);
      if (q.current.some((v) => v.status === 'IN_PROGRESS') || q.awaitingResults.length) continue;
      doc = { phone, password, id: me.employeeId, name: me.fullName, specialtyId };
      break;
    }
  }
  if (!doc) {
    // Các bác sĩ có sẵn đã kín giờ hôm nay: tạo một bác sĩ thử nghiệm (Nội tổng quát) và mở phiên cho người này
    const phone = randPhone();
    const n = vnNow();
    const start = Math.ceil((n.getUTCHours() * 60 + n.getUTCMinutes() + 5) / 15) * 15;
    const end = Math.min(start + 120, 23 * 60 + 59);
    if (end - start < 30) throw new Error('Hôm nay không còn giờ để mở phiên khám thử nghiệm (cần chạy trước 23:30).');
    const created = await call(manager, 'POST', '/staff', { fullName: `Bác Sĩ Thử ${phone.slice(-4)}`, phone, roles: ['DOCTOR'], specialtyIds: [1], primarySpecialtyId: 1 });
    await call(manager, 'PUT', `/staff/${created.staff.id}/doctor-profile`, { practiceLicenseNo: `CCHN-THU-${phone.slice(-6)}`, academicTitle: 'BS', isPublic: true });
    const t = await apiLogin(phone, created.temporaryPassword);
    await call(t, 'POST', '/auth/password/change', { currentPassword: created.temporaryPassword, newPassword: 'matkhau123' });
    const free = [(await call(manager, 'POST', '/rooms', { code: `THU${phone.slice(-4)}`, name: 'Phòng thử nghiệm', isActive: true })).id];
    for (const roomId of free) {
      const ok = await call(manager, 'POST', '/schedule/sessions', {
        doctorId: created.staff.id, specialtyId: 1, roomId, date: today, shiftName: 'Khám bù', startTime: hhmm(start), endTime: hhmm(end), slotMinutes: 15, walkInCapacity: 3,
      }).then(() => true, () => false);
      if (ok) break;
    }
    doc = { phone, password: 'matkhau123', id: created.staff.id, name: created.staff.fullName, specialtyId: 1 };
  }
  const an = doc;
  const [slot1, slot2] = await freeSlots(doc.id);
  const session = (await call(manager, 'GET', `/schedule/sessions/${slot1.sessionId}`)).session;
  // Dọn người còn chờ của lần chạy trước trong phiên này để bệnh nhân thử nghiệm là "người tiếp theo"
  const reception = await apiLogin('0900000003');
  const q0 = await call(reception, 'GET', `/queue/sessions/${session.id}`);
  for (const w of q0.waiting) await call(reception, 'POST', `/visits/${w.visit.id}/left`).catch(() => undefined);

  const suffix = String(Math.floor(Math.random() * 1e5)).padStart(5, '0');
  const p1 = await patientAccount(`Trần Văn Bình ${suffix}`, '0790850' + suffix);
  const appt1 = await call(p1.token, 'POST', '/me/appointments', { patientId: p1.patientId, slotId: slot1.id, reason: 'Đau họng, sốt nhẹ 3 ngày' });
  console.log(`\nBS ${doc.name}, phiên ${session.shiftName} ${vnTime(session.startsAt)}, bệnh nhân ${p1.phone}\n`);

  browser = await puppeteer.launch({ executablePath: EDGE, headless: true, args: ['--lang=vi-VN'] });

  await check('Đăng nhập sai mật khẩu báo lỗi tiếng Việt', async () => {
    const ctx = await browser.createBrowserContext();
    const page = await ctx.newPage();
    await page.setViewport({ width: 1366, height: 900 });
    await go(page, '/');
    await fill(page, 'Số điện thoại', '0900000003');
    await fill(page, 'Mật khẩu', 'sai-mat-khau');
    await click(page, 'Đăng nhập');
    await page.waitForSelector('[role="alert"]', { timeout: 10_000 });
    await shot(page, 'dang-nhap-sai');
    await ctx.close();
  });

  const lt = await staffPage('le-tan', '0900000003');
  await check('Lễ tân: tổng quan có việc gọi xác nhận lịch đặt trên ứng dụng', async () => {
    await see(lt, 'Việc cần làm');
    await see(lt, p1.patientName ?? `Trần Văn Bình ${suffix}`);
    await shot(lt, 'le-tan-tong-quan');
  });

  await check('Lễ tân: gọi xác nhận, bấm Đã xác nhận thì lịch chuyển sang đã xác nhận', async () => {
    await go(lt, `/le-tan/lich-hen?tab=confirm&id=${appt1.id}`);
    await see(lt, 'Gọi xác nhận');
    await see(lt, 'Đau họng, sốt nhẹ 3 ngày');
    await shot(lt, 'le-tan-goi-xac-nhan');
    await click(lt, 'Đã xác nhận');
    await see(lt, 'Đã xác nhận lịch hẹn');
    const a = await call(p1.token, 'GET', `/me/appointments/${appt1.id}`);
    if (a.status !== 'CONFIRMED') throw new Error('Lịch hẹn chưa được xác nhận: ' + a.status);
  });

  const name2 = `Lê Thị Hoa ${suffix}`;
  await check('Lễ tân: đặt lịch hộ bệnh nhân mới (tạo hồ sơ, chọn bác sĩ, chọn giờ)', async () => {
    await go(lt, '/le-tan/lich-hen?tab=book');
    await click(lt, 'Tạo hồ sơ mới');
    await fill(lt, 'Họ và tên', name2);
    await fill(lt, 'Số điện thoại', randPhone());
    await fill(lt, 'Ngày sinh (ngày/tháng/năm)', '02/09/1990');
    await click(lt, 'Nữ');
    await fill(lt, 'Số giấy tờ (không bắt buộc)', '0791900' + suffix);
    await click(lt, 'Tạo hồ sơ');
    await see(lt, 'Đổi người');
    await lt.select('[aria-label="Chuyên khoa"]', String(doc.specialtyId));
    await lt.waitForFunction((id) => [...document.querySelectorAll('[aria-label="Bác sĩ"] option')].some((o) => o.value === String(id)), { timeout: 15_000 }, an.id);
    await lt.select('[aria-label="Bác sĩ"]', String(an.id));
    await lt.waitForSelector('[aria-label="Ngày"]', { timeout: 15_000 });
    await lt.select('[aria-label="Ngày"]', today);
    await click(lt, vnTime(slot2.start));
    await fill(lt, 'Lý do khám', 'Khám sức khỏe');
    await shot(lt, 'le-tan-dat-lich-ho');
    await click(lt, 'Đặt lịch ' + vnTime(slot2.start));
    await see(lt, 'Đã đặt lịch');
  });

  let visit1;
  await check('Lễ tân: check-in bằng mã QR trên ứng dụng, cấp số thứ tự', async () => {
    const { token } = await call(p1.token, 'GET', `/me/appointments/${appt1.id}/qr`);
    await go(lt, '/le-tan/tiep-don');
    await fill(lt, 'Quét mã QR (máy quét tự điền, hoặc dán mã rồi bấm Enter)', token);
    await lt.keyboard.press('Enter');
    await see(lt, 'Số thứ tự');
    visit1 = (await call(p1.token, 'GET', '/me/queue'))[0].visit;
    await see(lt, `Số thứ tự ${visit1.queueNumber}`);
    await shot(lt, 'le-tan-check-in-qr');
  });

  await check('Lễ tân: check-in thủ công người đặt lịch hộ', async () => {
    await click(lt, 'Check-in', name2);
    await see(lt, `Đã check-in ${name2}`);
  });

  const dd = await staffPage('dieu-duong', '0900000004');
  await check('Điều dưỡng: đo sinh hiệu, thêm dị ứng, sốt cao thì hiện cảnh báo đỏ', async () => {
    await go(dd, '/dieu-duong/sinh-hieu');
    await click(dd, `Trần Văn Bình ${suffix}`);
    await see(dd, 'Lưu sinh hiệu');
    await fill(dd, 'Thêm dị ứng', 'Penicillin');
    await click(dd, 'Thêm', 'Dị ứng');
    await see(dd, 'Đã thêm dị ứng');
    await fill(dd, 'Huyết áp', '130/85');
    await fill(dd, 'Mạch', '96');
    await fill(dd, 'Nhiệt độ', '39,2');
    await fill(dd, 'SpO₂', '98');
    await fill(dd, 'Cân nặng', '68');
    await see(dd, 'Sốt cao 39,2 °C');
    await shot(dd, 'dieu-duong-sinh-hieu');
    await click(dd, 'Lưu sinh hiệu');
    await see(dd, `Đã lưu sinh hiệu số ${visit1.queueNumber}`);
  });

  const bs = await staffPage('bac-si', doc.phone, doc.password);
  await check('Bác sĩ: người tiếp theo có cảnh báo sốt, gọi vào phòng, bắt đầu khám', async () => {
    await go(bs, '/bac-si');
    await click(bs, `${session.shiftName} ${vnTime(session.startsAt)}`).catch(() => undefined); // nhiều phiên thì chọn đúng tab
    await see(bs, `Trần Văn Bình ${suffix}`);
    await see(bs, 'Sốt cao 39,2 °C');
    await shot(bs, 'bac-si-nguoi-tiep-theo');
    await click(bs, 'Gọi vào phòng');
    await see(bs, 'Bắt đầu khám');
    await see(bs, 'Dị ứng Penicillin');
    await click(bs, 'Bắt đầu khám');
    await see(bs, 'Trước khi kết thúc');
  });

  await check('Bác sĩ: ghi phiếu khám, chỉ định công thức máu, cho đi xét nghiệm', async () => {
    await fill(bs, 'Khám', 'Họng đỏ, amidan sưng độ II');
    await fill(bs, 'Mã ICD-10', 'J03.9', 0);
    await fill(bs, 'Chẩn đoán chính', 'Viêm amidan cấp');
    await fill(bs, 'Hướng điều trị', 'Kháng sinh 7 ngày');
    await click(bs, 'Lưu nháp');
    await see(bs, 'Đã lưu nháp');
    await click(bs, 'Chỉ định và kết quả');
    await fill(bs, 'Tìm xét nghiệm, chẩn đoán hình ảnh', 'công thức');
    await click(bs, 'Chỉ định', 'Xét nghiệm công thức máu');
    await see(bs, 'Đã chỉ định');
    await shot(bs, 'bac-si-chi-dinh');
    await click(bs, 'Phiếu khám');
    await click(bs, 'Cho đi xét nghiệm');
    await see(bs, 'Danh sách khám');
    await see(bs, 'Đi xét nghiệm');
  });

  await check('Điều dưỡng: làm xét nghiệm, nhập kết quả, tải phiếu PDF, gửi bác sĩ duyệt', async () => {
    await go(dd, '/dieu-duong/xet-nghiem');
    await click(dd, `Trần Văn Bình ${suffix}`);
    await click(dd, 'Đã làm, nhập kết quả');
    await see(dd, 'Đã ghi nhận thực hiện');
    await fill(dd, 'Kết quả', 'WBC 12,5 G/L, NEU 78%');
    await fill(dd, 'Nhận xét, kết luận', 'Bạch cầu tăng');
    const input = await dd.$('input[type=file]');
    await input.uploadFile(PDF);
    await see(dd, 'ket-qua-cong-thuc-mau.pdf');
    await shot(dd, 'dieu-duong-nhap-ket-qua');
    await click(dd, 'Gửi bác sĩ duyệt');
    await see(dd, 'Đã gửi bác sĩ duyệt');
  });

  await check('Bác sĩ: trả kết quả lại cho điều dưỡng kèm lý do', async () => {
    await go(bs, '/bac-si/duyet-ket-qua');
    await click(bs, `Trần Văn Bình ${suffix}`);
    await see(bs, 'Bạch cầu tăng');
    await click(bs, 'Trả lại điều dưỡng sửa');
    await fill(bs, 'Lý do trả lại', 'Ghi rõ giờ lấy mẫu');
    await click(bs, 'Trả lại', 'Lý do trả lại');
    await see(bs, 'Đã trả lại cho điều dưỡng');
  });

  await check('Điều dưỡng: thấy kết quả bị trả lại, sửa và gửi lại', async () => {
    await go(dd, '/dieu-duong/xet-nghiem');
    await see(dd, 'kết quả bị bác sĩ trả lại');
    await click(dd, 'Bị trả lại');
    await click(dd, `Trần Văn Bình ${suffix}`);
    await see(dd, 'Ghi rõ giờ lấy mẫu');
    await shot(dd, 'dieu-duong-bi-tra-lai');
    await fill(dd, 'Kết quả', 'WBC 12,5 G/L, NEU 78% (lấy mẫu 21:50)');
    await click(dd, 'Gửi bác sĩ duyệt');
    await see(dd, 'Đã gửi bác sĩ duyệt');
  });

  await check('Bác sĩ: duyệt kết quả, bệnh nhân xem được kết quả và tệp trên ứng dụng', async () => {
    await go(bs, '/bac-si/duyet-ket-qua');
    await click(bs, `Trần Văn Bình ${suffix}`);
    await see(bs, 'lấy mẫu 21:50');
    await click(bs, 'Duyệt kết quả', 'Trả lại điều dưỡng sửa');
    await see(bs, 'Đã duyệt');
    const rec = await call(p1.token, 'GET', `/me/visits/${visit1.id}`);
    const r = rec.orders[0].items[0].performances[0].result;
    if (r?.conclusion !== 'Bạch cầu tăng' || r.files.length !== 1) throw new Error('Bệnh nhân chưa thấy kết quả đã duyệt');
  });

  await check('Bác sĩ: gọi lại vào phòng, kê đơn; dị ứng phải xác nhận mới ký được', async () => {
    await go(bs, '/bac-si');
    await click(bs, `${session.shiftName} ${vnTime(session.startsAt)}`).catch(() => undefined);
    await click(bs, 'Gọi lại vào phòng', `Trần Văn Bình ${suffix}`);
    await see(bs, 'Bắt đầu khám');
    await click(bs, 'Bắt đầu khám');
    await see(bs, 'Trước khi kết thúc');
    await click(bs, 'Đơn thuốc');
    await fill(bs, 'Thêm thuốc', 'Para');
    await click(bs, 'Paracetamol');
    await see(bs, 'Số lượng');
    const disabled = await bs.$$eval('button', (bs) => bs.find((b) => b.innerText.includes('Ký đơn'))?.disabled);
    if (!disabled) throw new Error('Nút Ký đơn phải bị khóa khi chưa xác nhận dị ứng');
    await click(bs, 'Tôi đã đối chiếu dị ứng');
    await shot(bs, 'bac-si-ke-don');
    await click(bs, 'Ký đơn');
    await see(bs, 'Đã ký đơn');
    await see(bs, 'Đơn 1: đã ký');
    await see(bs, 'In đơn');
  });

  await check('Bác sĩ: ký bệnh án, kết thúc khám, hóa đơn chuyển sang thu ngân', async () => {
    await click(bs, 'Phiếu khám');
    await see(bs, 'Có kê đơn: đơn đã ký');
    await shot(bs, 'bac-si-truoc-khi-ket-thuc');
    await click(bs, 'Ký và kết thúc khám');
    await see(bs, 'Đã kết thúc khám');
    const rec = await call(p1.token, 'GET', `/me/visits/${visit1.id}`);
    if (rec.visit.status !== 'COMPLETED' || !rec.invoice) throw new Error('Lượt khám chưa kết thúc');
  });

  let invoiceId;
  await check('Lễ tân: thu tiền hóa đơn, tính tiền trả lại khách', async () => {
    await go(lt, '/le-tan/thu-ngan');
    await click(lt, `Trần Văn Bình ${suffix}`);
    await fill(lt, 'Khách đưa', '500000');
    await see(lt, 'Trả lại khách');
    await shot(lt, 'le-tan-thu-ngan');
    await click(lt, 'Thu ', 'Khách đưa');
    await see(lt, 'Đã thu');
    invoiceId = (await call(p1.token, 'GET', `/me/visits/${visit1.id}`)).invoice.id;
    const inv = (await call(p1.token, 'GET', `/me/visits/${visit1.id}`)).invoice;
    if (inv.status !== 'PAID') throw new Error('Hóa đơn chưa thu đủ: ' + inv.status);
  });

  await check('Lễ tân: báo người đặt lịch hộ bỏ về', async () => {
    await go(lt, '/le-tan/hang-doi');
    await click(lt, `BS ${doc.name}, ${vnTime(session.startsAt)}`);
    await click(lt, name2);
    await click(lt, 'Báo bệnh nhân bỏ về');
    await see(lt, 'Đã ghi nhận bỏ về');
  });

  const ql = await staffPage('quan-ly', '0900000001');
  await check('Quản lý: thu chi thấy giao dịch, hoàn một phần tiền có lý do', async () => {
    await go(ql, '/quan-ly/thu-chi');
    await click(ql, `Trần Văn Bình ${suffix}`);
    await fill(ql, 'Số tiền hoàn', '20000');
    await fill(ql, 'Lý do', 'Thu thừa');
    await click(ql, 'Hoàn tiền', 'Số tiền hoàn');
    await see(ql, 'Đã hoàn tiền');
    const inv = (await call(p1.token, 'GET', `/me/visits/${visit1.id}`)).invoice;
    if (inv.id !== invoiceId || inv.paidAmount !== inv.totalAmount - 20000) throw new Error('Hoàn tiền chưa đúng');
    await shot(ql, 'quan-ly-thu-chi');
  });

  await check('Quản lý: báo cáo và lịch làm việc hiển thị', async () => {
    await go(ql, '/quan-ly/bao-cao');
    await see(ql, 'Lượt khám mỗi ngày');
    await shot(ql, 'quan-ly-bao-cao');
    await go(ql, '/quan-ly/lich');
    await see(ql, `BS ${doc.name}`);
    await shot(ql, 'quan-ly-lich');
  });

  const staffPhone = randPhone();
  let temp;
  await check('Quản lý: tạo tài khoản lễ tân, nhận mật khẩu tạm', async () => {
    await go(ql, '/quan-ly/nhan-vien');
    await click(ql, 'Thêm nhân viên');
    await fill(ql, 'Họ và tên', `Lễ tân thử ${suffix}`);
    await fill(ql, 'Số điện thoại', staffPhone);
    await click(ql, 'Lễ tân', 'Vai trò');
    await click(ql, 'Tạo tài khoản');
    await see(ql, 'Mật khẩu tạm:');
    temp = await ql.evaluate(() => document.body.innerText.match(/Mật khẩu tạm: (\S+)\./)?.[1]);
    if (!temp) throw new Error('Không thấy mật khẩu tạm');
    await shot(ql, 'quan-ly-tao-nhan-vien');
  });

  await check('Nhân viên mới đăng nhập bằng mật khẩu tạm phải đổi mật khẩu rồi mới vào làm', async () => {
    const page = await staffPage('nhan-vien-moi', staffPhone, temp);
    await see(page, 'Bạn đang dùng mật khẩu tạm');
    await fill(page, 'Mật khẩu tạm', temp);
    await fill(page, 'Mật khẩu mới (ít nhất 8 ký tự)', 'matkhaumoi1');
    await fill(page, 'Nhập lại mật khẩu mới', 'matkhaumoi1');
    await click(page, 'Đổi mật khẩu');
    await see(page, 'Việc cần làm');
    await shot(page, 'nhan-vien-moi-vao-lam');
  });
}

main()
  .catch((e) => results.some((r) => !r.ok) || console.log('Lỗi chuẩn bị:', e.stack))
  .finally(async () => {
    await browser?.close();
    server.close();
    const ok = results.filter((r) => r.ok).length;
    console.log(`\n${ok}/${results.length} bước đạt. Ảnh chụp: ${SHOTS}`);
    process.exit(ok === results.length && results.length > 0 ? 0 : 1);
  });
