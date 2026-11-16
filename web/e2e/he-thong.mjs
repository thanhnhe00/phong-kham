// Kiểm thử cả hệ thống cùng lúc: bệnh nhân thao tác trên app (bản web của app Expo, khổ điện thoại), nhân viên thao tác
// trên web, cùng một backend thật. Kiểm tra dữ liệu đi qua lại giữa app và web (kể cả app tự làm mới sau 15 giây)
// và các tình huống hai người cùng bấm một lúc.
// Chạy: backend ở http://localhost:8080, `npm run build` (web), `npx expo export --platform web` (trong mobile/),
// rồi `npm run e2e:he-thong`. Ảnh chụp ở e2e/screenshots-he-thong/.
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const WEB_DIST = path.join(ROOT, '..', 'dist');
const APP_DIST = path.join(ROOT, '..', '..', 'mobile', 'dist');
const SHOTS = path.join(ROOT, 'screenshots-he-thong');
const API = new URL(process.env.API_TARGET ?? 'http://localhost:8080');
const WEB = 'http://localhost:8098';
const APP = 'http://localhost:8099';
const EDGE = process.env.BROWSER ?? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const LIVE = 25_000; // app, web tự hỏi lại máy chủ mỗi 15 giây: chờ tối đa 25 giây để thấy thay đổi từ phía bên kia
const PDF = path.join(os.tmpdir(), 'ket-qua-he-thong.pdf');
writeFileSync(PDF, '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');

// ---------- hai máy chủ tĩnh (web, app) cùng chuyển tiếp /api ----------
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.png': 'image/png', '.json': 'application/json' };
function staticServer(dist) {
  return http.createServer((req, res) => {
    if (req.url?.startsWith('/api/')) {
      const up = http.request({ hostname: API.hostname, port: API.port, path: req.url, method: req.method, headers: { ...req.headers, host: API.host } }, (r) => {
        res.writeHead(r.statusCode ?? 502, r.headers);
        r.pipe(res);
      });
      up.on('error', () => { res.writeHead(502); res.end(); });
      req.pipe(up);
      return;
    }
    let file = path.join(dist, decodeURIComponent((req.url ?? '/').split('?')[0]));
    if (!file.startsWith(dist) || !existsSync(file) || statSync(file).isDirectory()) file = path.join(dist, 'index.html');
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
    createReadStream(file).pipe(res);
  });
}
const servers = [staticServer(WEB_DIST), staticServer(APP_DIST)];

// ---------- API: chỉ dùng để chuẩn bị phiên khám và kiểm tra kết quả, không thay cho thao tác ----------
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

/** Bác sĩ có phiên hôm nay còn ít nhất n giờ trống (giống e2e/run.mjs: thử bác sĩ có sẵn, bác sĩ thử nghiệm, mở phiên khám bù) */
async function pickDoctor(manager, n) {
  const today = vnNow().toISOString().slice(0, 10);
  const rooms = await call(manager, 'GET', '/rooms');
  const free = async (id) => (await call(null, 'GET', `/slots?doctorId=${id}&from=${today}&to=${today}`))
    .flatMap((s) => s.slots).filter((x) => x.status === 'AVAILABLE' && Date.parse(x.start) > Date.now() + 2 * 60_000);
  const toMin = (iso) => { const [h, m] = vnTime(iso).split(':').map(Number); return h * 60 + m; };
  const hhmm = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  const tests = (await call(manager, 'GET', `/staff?q=${encodeURIComponent('Bác Sĩ Thử')}&size=50`)).items.map((x) => [x.phone, null, 'matkhau123']);
  for (const [phone, room, password = '12345678'] of [['0900000011', 'P101'], ['0900000012', 'P102'], ['0900000013', 'P103'], ['0900000014', 'P104'], ...tests]) {
    const me = await call(await apiLogin(phone, password).catch(() => null), 'GET', '/auth/me').catch(() => null);
    if (!me) continue;
    const card = await call(null, 'GET', `/doctors/${me.employeeId}`);
    if (room && (await free(me.employeeId)).length < n) {
      const sessions = await call(manager, 'GET', `/schedule/sessions?from=${today}&to=${today}&doctorId=${me.employeeId}`);
      const t = vnNow();
      const start = Math.max(Math.ceil((t.getUTCHours() * 60 + t.getUTCMinutes() + 10) / 15) * 15, ...sessions.filter((s) => s.status !== 'CANCELLED').map((s) => toMin(s.endsAt)));
      const end = Math.min(start + 120, 23 * 60 + 59);
      if (end - start >= 15 * n) {
        await call(manager, 'POST', '/schedule/sessions', {
          doctorId: me.employeeId, specialtyId: card.specialties[0].id, roomId: rooms.find((r) => r.code === room).id, date: today,
          shiftName: 'Khám bù', startTime: hhmm(start), endTime: hhmm(end), slotMinutes: 15, walkInCapacity: 3,
        }).catch(() => undefined);
      }
    }
    const slots = await free(me.employeeId);
    if (slots.length < n) continue;
    // bỏ qua bác sĩ còn người khám dở hoặc đi xét nghiệm từ lần chạy trước (nút gọi số sẽ bị khóa đúng nghiệp vụ)
    const sid = (await call(null, 'GET', `/slots?doctorId=${me.employeeId}&from=${today}&to=${today}`)).find((x) => x.slots.some((y) => y.id === slots[0].id)).sessionId;
    const q = await call(manager, 'GET', `/queue/sessions/${sid}`);
    if (q.current.some((v) => v.status === 'IN_PROGRESS') || q.awaitingResults.length) continue;
    return { phone, password, id: me.employeeId, name: me.fullName, title: card.academicTitle, specialty: card.specialties[0] };
  }
  // Mọi bác sĩ, mọi phòng đã kín giờ hôm nay: tạo phòng thử và bác sĩ thử (Nội tổng quát) rồi mở phiên khám bù
  const t = vnNow();
  const start = Math.ceil((t.getUTCHours() * 60 + t.getUTCMinutes() + 5) / 15) * 15;
  const end = Math.min(start + 120, 23 * 60 + 59);
  if (end - start < 15 * n) throw new Error('Hôm nay đã quá khuya để mở phiên khám thử nghiệm (cần chạy trước 23:30).');
  const phone = randPhone();
  const room = await call(manager, 'POST', '/rooms', { code: `THU${phone.slice(-4)}`, name: 'Phòng thử nghiệm', isActive: true });
  const created = await call(manager, 'POST', '/staff', { fullName: `Bác Sĩ Thử ${phone.slice(-4)}`, phone, roles: ['DOCTOR'], specialtyIds: [1], primarySpecialtyId: 1 });
  await call(manager, 'PUT', `/staff/${created.staff.id}/doctor-profile`, { practiceLicenseNo: `CCHN-THU-${phone.slice(-6)}`, academicTitle: 'BS', isPublic: true });
  const tmp = await apiLogin(phone, created.temporaryPassword);
  await call(tmp, 'POST', '/auth/password/change', { currentPassword: created.temporaryPassword, newPassword: 'matkhau123' });
  await call(manager, 'POST', '/schedule/sessions', {
    doctorId: created.staff.id, specialtyId: 1, roomId: room.id, date: today, shiftName: 'Khám bù', startTime: hhmm(start), endTime: hhmm(end), slotMinutes: 15, walkInCapacity: 3,
  });
  const card = await call(null, 'GET', `/doctors/${created.staff.id}`);
  return { phone, password: 'matkhau123', id: created.staff.id, name: created.staff.fullName, title: card.academicTitle, specialty: card.specialties[0] };
}

// ---------- trình duyệt ----------
let browser;
let step = 0;
const results = [];
const pages = {};

async function shot(page, name) {
  step += 1;
  await page.screenshot({ path: path.join(SHOTS, `${String(step).padStart(2, '0')}-${name}.png`) });
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
const CLICKABLE = 'button,a,[role="button"],[role="link"],[role="tab"],[role="radio"],[role="checkbox"],tr[aria-selected],label.check';
/** Bấm phần tử bấm được đang hiện có chữ t (trong vùng có chữ within nếu cho); nhiều cái thì lấy cái chữ ngắn nhất */
async function click(page, t, within) {
  const find = ([s, w, sel]) => {
    const roots = w ? [...document.querySelectorAll('tr, section, .between, .stack, form, [role="radiogroup"]')].filter((r) => r.innerText.includes(w)) : [document];
    for (const root of roots.sort((a, b) => (a.innerText?.length ?? 0) - (b.innerText?.length ?? 0))) {
      const els = [...root.querySelectorAll(sel)].filter((e) => {
        const r = e.getBoundingClientRect();
        const onScreen = e.checkVisibility({ visibilityProperty: true, opacityProperty: true }); // app giữ màn cũ ẩn trong trang: bỏ phần bị ẩn
        return r.width > 0 && r.height > 0 && onScreen && !e.disabled && e.getAttribute('aria-disabled') !== 'true' && e.innerText?.includes(s);
      });
      els.sort((a, b) => a.innerText.length - b.innerText.length);
      if (els[0]) return els[0];
    }
    return null;
  };
  await page.waitForFunction(find, { timeout: 15_000 }, [t, within, CLICKABLE]);
  const el = await page.evaluateHandle(find, [t, within, CLICKABLE]);
  await el.asElement().click();
}
const see = (page, t, timeout = 15_000) => page.waitForFunction((s) => document.body.innerText.includes(s), { timeout }, t);
async function fill(page, label, value, nth = 0) {
  await page.waitForFunction(([l, n]) => document.querySelectorAll(`[aria-label="${l}"]`).length > n, { timeout: 15_000 }, [label, nth]);
  const el = (await page.$$(`[aria-label="${label}"]`))[nth];
  await el.focus();
  await el.evaluate((e) => e.select?.());
  await el.press('Backspace');
  await el.type(value);
}
const go = (page, url) => page.goto(url, { waitUntil: 'networkidle0' });
/** Thông báo nhỏ của web (toast) sau một thao tác: trả về chữ, đỏ hay không */
const toastOf = (page) => page.waitForSelector('.toast', { timeout: 15_000 }).then((h) => h.evaluate((e) => ({ text: e.innerText, err: e.classList.contains('err') })));

async function newPage(key, mobile) {
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();
  await page.setViewport(mobile ? { width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true } : { width: 1366, height: 900 });
  page.on('pageerror', (e) => console.log(`    [lỗi trang ${key}]`, e.message));
  page.on('dialog', (d) => d.accept());
  pages[key] = page;
  return page;
}
async function staff(key, phone, password = '12345678') {
  const page = await newPage(key, false);
  await go(page, WEB + '/');
  await fill(page, 'Số điện thoại', phone);
  await fill(page, 'Mật khẩu', password);
  await click(page, 'Đăng nhập');
  await page.waitForFunction(() => document.querySelector('.side'), { timeout: 30_000 });
  return page;
}

// ---------- kịch bản ----------
async function main() {
  for (const [d, cmd] of [[WEB_DIST, 'npm run build (web)'], [APP_DIST, 'npx expo export --platform web (mobile)']]) {
    if (!existsSync(path.join(d, 'index.html'))) throw new Error(`Chưa có bản build: chạy \`${cmd}\` trước.`);
  }
  rmSync(SHOTS, { recursive: true, force: true });
  mkdirSync(SHOTS, { recursive: true });
  await new Promise((r) => servers[0].listen(8098, r));
  await new Promise((r) => servers[1].listen(8099, r));

  const manager = await apiLogin('0900000001');
  const doc = await pickDoctor(manager, 2);
  const docLabel = `${doc.title ? doc.title + ' ' : 'BS '}${doc.name}`;
  const suffix = String(Math.floor(Math.random() * 1e5)).padStart(5, '0');
  const name = `Trần Văn Bình ${suffix}`;
  const phone = randPhone();
  console.log(`\nBác sĩ ${doc.name}, bệnh nhân ${name} (${phone})\n`);
  browser = await puppeteer.launch({ executablePath: EDGE, headless: true, args: ['--lang=vi-VN'] });

  // ===== Bệnh nhân trên app =====
  const app = await newPage('app', true);
  const patientToken = () => app.evaluate(() => localStorage.getItem('pk.token'));

  await check('App: bệnh nhân đăng ký, nhắn tin xác minh tới phòng khám, tạo hồ sơ', async () => {
    await go(app, APP + '/tao-tai-khoan');
    await fill(app, 'Số điện thoại', phone);
    await click(app, 'Tiếp tục');
    // app hiện nội dung cần nhắn; "bệnh nhân" nhắn từ đúng số của mình, cổng SMS chuyển về backend
    await app.waitForSelector('[data-testid="sms-body"]', { timeout: 15_000 });
    await smsArrives(phone, await app.$eval('[data-testid="sms-body"]', (el) => el.textContent.trim()));
    await see(app, 'Đặt mật khẩu');
    await fill(app, 'Mật khẩu', 'matkhau123');
    await fill(app, 'Nhập lại mật khẩu', 'matkhau123');
    await click(app, 'Tạo tài khoản');
    await see(app, 'Thông tin người khám');
    await fill(app, 'Họ và tên', name);
    await fill(app, 'Ngày sinh', '15/03/1985');
    await fill(app, 'Số CCCD', '0790850' + suffix);
    await click(app, 'Lưu hồ sơ');
    await see(app, 'Đặt lịch khám');
  });

  let apptId;
  await check(`App: đặt lịch hôm nay với ${docLabel}`, async () => {
    await click(app, 'Đặt lịch khám');
    await see(app, 'Khám cho ai?');
    await click(app, doc.specialty.name);
    await click(app, 'Tiếp tục');
    await click(app, doc.name);
    await app.waitForSelector('[aria-label*="đã chọn"]', { timeout: 15_000 });
    const slot = await app.$$eval('[role="button"]', (els) => els.map((e) => e.innerText.trim()).find((t) => /^\d\d:\d\d$/.test(t)));
    await click(app, slot);
    await click(app, 'Tiếp tục');
    await fill(app, 'Bạn bị làm sao? (không bắt buộc)', 'Đau họng, sốt 3 ngày');
    await click(app, 'Gửi yêu cầu đặt lịch');
    await see(app, 'Đã gửi yêu cầu đặt lịch');
    apptId = (await call(await patientToken(), 'GET', '/me/appointments'))[0].id;
    await shot(app, 'app-da-dat-lich');
  });

  // ===== Lễ tân trên web =====
  const lt = await staff('le-tan', '0900000003');
  const lt2 = await staff('le-tan-2', '0900000003'); // lễ tân thứ hai, cùng ca
  await check('Web: lịch vừa đặt trên app hiện ở việc cần làm của lễ tân', async () => {
    await see(lt, name, LIVE);
    await shot(lt, 'web-le-tan-thay-lich-moi');
  });

  await check('Hai lễ tân cùng bấm "Đã xác nhận" một lịch: chỉ một người thành công, người kia được báo, lịch chỉ xác nhận một lần', async () => {
    for (const p of [lt, lt2]) {
      await go(p, `${WEB}/le-tan/lich-hen?tab=confirm&id=${apptId}`);
      await see(p, 'Đau họng, sốt 3 ngày');
    }
    const [a, b] = await Promise.all([lt, lt2].map(async (p) => { await click(p, 'Đã xác nhận'); return toastOf(p); }));
    const ok = [a, b].filter((t) => !t.err);
    if (ok.length !== 1) throw new Error(`Mong 1 người thành công, thực tế: ${JSON.stringify([a, b])}`);
    const err = [a, b].find((t) => t.err);
    if (!/không ở trạng thái chờ xác nhận|Người khác vừa cập nhật/.test(err.text)) throw new Error('Người bấm sau không được báo rõ: ' + err.text);
    const appt = await call(await patientToken(), 'GET', `/me/appointments/${apptId}`);
    if (appt.status !== 'CONFIRMED') throw new Error('Lịch chưa xác nhận: ' + appt.status);
    await shot(lt2, 'web-le-tan-bam-sau-bi-bao');
  });

  await check('App: lịch chuyển sang đã xác nhận, có nút mở mã check-in', async () => {
    await go(app, APP + '/');
    await see(app, 'Mở mã check-in');
    await shot(app, 'app-da-xac-nhan');
  });

  let visit;
  await check('App mở mã QR, lễ tân quét trên web; app tự hiện số thứ tự (không tải lại trang)', async () => {
    const qr = app.waitForResponse((r) => /\/me\/appointments\/\d+\/qr/.test(r.url()) && r.status() === 200, { timeout: 15_000 });
    await click(app, 'Mở mã check-in');
    const { token } = await (await qr).json();
    await app.waitForSelector('[data-testid="qr"] svg rect', { timeout: 15_000 });
    await shot(app, 'app-ma-qr');
    await go(lt, WEB + '/le-tan/tiep-don');
    await fill(lt, 'Quét mã QR (máy quét tự điền, hoặc dán mã rồi bấm Enter)', token);
    await lt.keyboard.press('Enter');
    await see(lt, 'Số thứ tự');
    visit = (await call(await patientToken(), 'GET', '/me/queue'))[0].visit;
    await go(app, APP + '/');
    await app.waitForSelector('[data-testid="queue-number"]', { timeout: LIVE });
    const n = await app.$eval('[data-testid="queue-number"]', (el) => el.textContent.trim());
    if (n !== String(visit.queueNumber)) throw new Error(`App hiện số ${n}, web cấp số ${visit.queueNumber}`);
    await shot(app, 'app-so-thu-tu');
  });

  // ===== Điều dưỡng, bác sĩ trên web =====
  const dd = await staff('dieu-duong', '0900000004');
  await check('Web điều dưỡng: đo sinh hiệu; app hiện bước đo huyết áp đã xong', async () => {
    await go(dd, WEB + '/dieu-duong/sinh-hieu');
    await click(dd, name);
    await fill(dd, 'Huyết áp', '130/85');
    await fill(dd, 'Nhiệt độ', '38,4');
    await fill(dd, 'Cân nặng', '68');
    await click(dd, 'Lưu sinh hiệu');
    await see(dd, `Đã lưu sinh hiệu số ${visit.queueNumber}`);
    await see(app, 'Đo huyết áp, cân nặng', LIVE);
  });

  const bs = await staff('bac-si', doc.phone, doc.password);
  const bs2 = await staff('bac-si-the-2', doc.phone, doc.password); // bác sĩ mở hai thẻ
  await check('Bác sĩ mở hai thẻ cùng bấm "Gọi vào phòng": chỉ gọi một người, thẻ kia được báo', async () => {
    // dọn người còn chờ của lần chạy trước trong phiên để bệnh nhân này là người tiếp theo
    const rec = await apiLogin('0900000003');
    const q = await call(rec, 'GET', `/queue/sessions/${visit.sessionId}`);
    for (const w of q.waiting.filter((x) => x.visit.id !== visit.id)) await call(rec, 'POST', `/visits/${w.visit.id}/left`);
    for (const c of q.current.filter((x) => x.status === 'CALLED')) await call(rec, 'POST', `/visits/${c.id}/left`);
    // bác sĩ có thể có nhiều phiên trong ngày (phiên khám bù của các lần chạy trước): mở đúng thẻ phiên rồi mới tìm bệnh nhân
    const s = (await call(await apiLogin(doc.phone, doc.password), 'GET', `/schedule/sessions/${visit.sessionId}`)).session;
    for (const p of [bs, bs2]) {
      await go(p, WEB + '/bac-si');
      await click(p, `${s.shiftName} ${vnTime(s.startsAt)}`).catch(() => undefined);
      await see(p, name);
    }
    await Promise.all([bs, bs2].map((p) => click(p, 'Gọi vào phòng')));
    await new Promise((r) => setTimeout(r, 2500));
    const states = await Promise.all([bs, bs2].map((p) => p.evaluate(() => ({ url: location.pathname, toast: document.querySelector('.toast')?.innerText ?? '' }))));
    const called = states.filter((s) => s.url.startsWith('/bac-si/kham/'));
    if (called.length !== 1) throw new Error('Mong đúng một thẻ vào phiếu khám: ' + JSON.stringify(states));
    const other = states.find((s) => !s.url.startsWith('/bac-si/kham/'));
    if (!other.toast) throw new Error('Thẻ bấm sau không có thông báo');
    if (bs.url().includes('/bac-si/kham/')) pages['bac-si'] = bs; else { pages['bac-si'] = bs2; }
    const v = (await call(await patientToken(), 'GET', '/me/queue'))[0].visit;
    if (v.status !== 'CALLED') throw new Error('Bệnh nhân chưa ở trạng thái được gọi: ' + v.status);
    await shot(bs2, 'web-bac-si-the-2');
  });
  const doctor = pages['bac-si'];

  await check('App: hiện "Đến lượt bạn" sau khi bác sĩ gọi (tự làm mới)', async () => {
    await see(app, 'Đến lượt bạn', LIVE);
    await shot(app, 'app-den-luot');
  });

  await check('Web bác sĩ: khám, chỉ định công thức máu, cho đi xét nghiệm; app hiện bước xét nghiệm', async () => {
    await click(doctor, 'Bắt đầu khám');
    await see(doctor, 'Trước khi kết thúc');
    await fill(doctor, 'Mã ICD-10', 'J03.9', 0);
    await fill(doctor, 'Chẩn đoán chính', 'Viêm amidan cấp');
    await click(doctor, 'Chỉ định và kết quả');
    await fill(doctor, 'Tìm xét nghiệm, chẩn đoán hình ảnh', 'công thức');
    await click(doctor, 'Chỉ định', 'Xét nghiệm công thức máu');
    await see(doctor, 'Đã chỉ định');
    await click(doctor, 'Phiếu khám');
    await click(doctor, 'Cho đi xét nghiệm');
    await see(doctor, 'Danh sách khám');
    await see(app, 'Quay lại phòng', LIVE);
    await see(app, 'Xét nghiệm công thức máu');
    await shot(app, 'app-di-xet-nghiem');
  });

  await check('Web: điều dưỡng nhập kết quả kèm PDF, bác sĩ duyệt; bệnh nhân thấy kết quả', async () => {
    await go(dd, WEB + '/dieu-duong/xet-nghiem');
    await click(dd, name);
    await click(dd, 'Đã làm, nhập kết quả');
    await see(dd, 'Đã ghi nhận thực hiện');
    await fill(dd, 'Kết quả', 'WBC 12,5 G/L');
    await fill(dd, 'Nhận xét, kết luận', 'Bạch cầu tăng');
    await (await dd.$('input[type=file]')).uploadFile(PDF);
    await see(dd, 'ket-qua-he-thong.pdf');
    await click(dd, 'Gửi bác sĩ duyệt');
    await see(dd, 'Đã gửi bác sĩ duyệt');
    await go(doctor, WEB + '/bac-si/duyet-ket-qua');
    await click(doctor, name);
    await click(doctor, 'Duyệt kết quả', 'Trả lại điều dưỡng sửa');
    await see(doctor, 'Đã duyệt');
    const rec = await call(await patientToken(), 'GET', `/me/visits/${visit.id}`);
    if (rec.orders[0].items[0].performances[0].result?.conclusion !== 'Bạch cầu tăng') throw new Error('Bệnh nhân chưa thấy kết quả');
  });

  await check('Web bác sĩ: gọi lại, kê đơn, kết thúc; app hiện hóa đơn cần trả', async () => {
    await go(doctor, WEB + '/bac-si');
    const s = (await call(await apiLogin(doc.phone, doc.password), 'GET', `/schedule/sessions/${visit.sessionId}`)).session;
    await click(doctor, `${s.shiftName} ${vnTime(s.startsAt)}`).catch(() => undefined);
    await click(doctor, 'Gọi lại vào phòng', name);
    await see(doctor, 'Bắt đầu khám');
    await click(doctor, 'Bắt đầu khám');
    await click(doctor, 'Đơn thuốc');
    await fill(doctor, 'Thêm thuốc', 'Para');
    await click(doctor, 'Paracetamol');
    await click(doctor, 'Ký đơn');
    await see(doctor, 'Đơn 1: đã ký');
    await click(doctor, 'Phiếu khám');
    await click(doctor, 'Ký và kết thúc khám');
    await see(doctor, 'Đã kết thúc khám');
    await see(app, 'Hóa đơn hôm nay', LIVE);
    await see(app, 'Viêm amidan cấp');
    await shot(app, 'app-hoa-don');
  });

  await check('Bệnh nhân trả trên app đúng lúc thu ngân bấm thu trên web: hóa đơn chỉ thu đủ một lần, không thu thừa', async () => {
    await go(lt, WEB + '/le-tan/thu-ngan');
    await click(lt, name);
    await see(lt, 'Còn phải trả');
    await go(app, APP + '/');
    await see(app, 'Trả trên app');
    await Promise.all([click(app, 'Trả trên app'), click(lt, 'Thu ', 'Còn phải trả')]);
    await new Promise((r) => setTimeout(r, 3000));
    const inv = (await call(await patientToken(), 'GET', `/me/visits/${visit.id}`)).invoice;
    const paid = inv.payments.filter((p) => p.kind === 'PAYMENT' && p.status === 'SUCCESS');
    if (inv.status !== 'PAID' || inv.paidAmount !== inv.totalAmount || paid.length !== 1) {
      throw new Error(`Hóa đơn sai: ${inv.status}, đã thu ${inv.paidAmount}/${inv.totalAmount}, ${paid.length} lần thu`);
    }
    await go(app, APP + '/');
    await see(app, 'Đã thanh toán');
    await shot(app, 'app-da-thanh-toan');
    await shot(lt, 'web-thu-ngan-sau-tranh-chap');
  });

  await check('App: chi tiết lượt khám có kết luận, kết quả kèm tệp, đơn thuốc; gửi góp ý', async () => {
    await go(app, `${APP}/luot-kham/${visit.id}`);
    await see(app, 'Bác sĩ kết luận');
    await see(app, 'ket-qua-he-thong.pdf');
    await see(app, 'Đơn thuốc');
    await fill(app, 'Nội dung', `Góp ý thử ${suffix}: bác sĩ khám kỹ.`);
    await click(app, 'Gửi góp ý');
    await see(app, 'Đã gửi góp ý');
    await shot(app, 'app-chi-tiet-luot-kham');
  });

  const ql = await staff('quan-ly', '0900000001');
  await check('Web quản lý: thấy góp ý vừa gửi từ app và giao dịch của bệnh nhân', async () => {
    await go(ql, WEB + '/quan-ly/phan-hoi');
    await see(ql, name);
    await click(ql, name);
    await see(ql, `Góp ý thử ${suffix}`);
    await go(ql, WEB + '/quan-ly/thu-chi');
    await see(ql, name);
    await shot(ql, 'web-quan-ly-thu-chi');
  });

  await check('App xin hủy một lịch mới; web lễ tân từ chối; app báo chưa được duyệt', async () => {
    // mỗi bệnh nhân chỉ một lịch trong một phiên: lịch thứ hai đặt với bác sĩ khác bác sĩ của lượt khám chính
    const [spec2, doc2] = doc.name === 'Nguyễn Văn An' ? ['Nhi', 'Trần Thị Bình'] : ['Nội tổng quát', 'Nguyễn Văn An'];
    await go(app, APP + '/');
    await click(app, 'Đặt lịch khám');
    await click(app, spec2);
    await click(app, 'Tiếp tục');
    await click(app, doc2);
    await app.waitForSelector('[aria-label*="đã chọn"]', { timeout: 15_000 });
    const slot = await app.$$eval('[role="button"]', (els) => els.map((e) => e.innerText.trim()).find((t) => /^\d\d:\d\d$/.test(t)));
    await click(app, slot);
    await click(app, 'Tiếp tục');
    await click(app, 'Gửi yêu cầu đặt lịch');
    await see(app, 'Đã gửi yêu cầu đặt lịch');
    await click(app, 'Xem lịch hẹn');
    await click(app, 'Xin hủy lịch');
    await see(app, 'Đã gửi yêu cầu hủy lịch');
    await go(lt, WEB + '/le-tan/lich-hen?tab=changes');
    await see(lt, name, LIVE);
    await click(lt, name);
    await click(lt, 'Từ chối');
    await see(lt, 'Đã từ chối');
    await app.reload({ waitUntil: 'networkidle0' });
    await see(app, 'chưa được duyệt');
    await shot(app, 'app-xin-huy-bi-tu-choi');
  });
}

main()
  .catch((e) => results.some((r) => !r.ok) || console.log('Lỗi chuẩn bị:', e.stack))
  .finally(async () => {
    await browser?.close();
    servers.forEach((s) => s.close());
    const ok = results.filter((r) => r.ok).length;
    console.log(`\n${ok}/${results.length} bước đạt. Ảnh chụp: ${SHOTS}`);
    process.exit(ok === results.length && results.length > 0 ? 0 : 1);
  });
