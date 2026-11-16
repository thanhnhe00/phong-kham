// Kiểm thử đầu-cuối app bệnh nhân trên bản web (react-native-web) với backend thật.
// Bệnh nhân thao tác qua giao diện; lễ tân, điều dưỡng, bác sĩ, quản lý thao tác qua API để đẩy lượt khám qua từng trạng thái.
// Chạy: backend ở http://localhost:8080 (docker compose up), `npx expo export --platform web`, rồi `npm run e2e`.
// Ảnh chụp từng bước lưu ở e2e/screenshots/.
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, statSync } from 'node:fs';
import http from 'node:http';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const require = createRequire(import.meta.url);
const { proxyApi } = require('./proxy.js');

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const DIST = path.join(ROOT, '..', 'dist');
const SHOTS = path.join(ROOT, 'screenshots');
const API = process.env.API_PROXY_TARGET ?? 'http://localhost:8080';
const PORT = 8099;
const EDGE = process.env.BROWSER ?? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PASSWORD = 'matkhau123';
/** Tệp PDF nhỏ nhất hợp lệ làm phiếu kết quả thử nghiệm */
const PDF = Buffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n',
);

// ---------- máy chủ tĩnh + chuyển tiếp /api ----------
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.ttf': 'font/ttf', '.png': 'image/png', '.ico': 'image/x-icon', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  if (proxyApi(req, res)) return;
  const url = decodeURIComponent((req.url ?? '/').split('?')[0]);
  let file = path.join(DIST, url);
  if (!file.startsWith(DIST) || !existsSync(file) || statSync(file).isDirectory()) file = path.join(DIST, 'index.html'); // ứng dụng một trang
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
  createReadStream(file).pipe(res);
});

// ---------- API cho vai trò nhân viên ----------
async function call(token, method, p, body) {
  const res = await fetch(API + '/api' + p, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${method} ${p} -> ${res.status} ${data?.message ?? text}`);
  return data;
}
const login = async (phone) => (await call(null, 'POST', '/auth/login', { phone, password: '12345678' })).token;

// ---------- đóng vai cổng SMS: chuyển tin nhắn xác minh người dùng gửi tới phòng khám ----------
const GATEWAY_TOKEN =
  process.env.SMS_GATEWAY_TOKEN ?? readFileSync(path.join(ROOT, '..', '..', '.env'), 'utf8').match(/^SMS_GATEWAY_TOKEN=(.*)$/m)?.[1].trim();
async function smsArrives(from, body) {
  const res = await fetch(API + '/api/sms/inbound', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Gateway-Token': GATEWAY_TOKEN ?? '' },
    body: JSON.stringify({ from, body }),
  });
  const data = await res.json();
  if (data.result !== 'VERIFIED') throw new Error(`Cổng SMS: ${res.status} ${data.result ?? data.message}`);
}

/** Kết thúc lượt khám còn sót của lần chạy trước (bệnh án tối thiểu, ký, hoàn tất) */
async function finishVisit(doctor, id) {
  try {
    await call(doctor, 'POST', `/visits/${id}/complete`);
  } catch {
    await call(doctor, 'PUT', `/visits/${id}/emr`, { chiefComplaint: 'Kiểm thử', diagnoses: [{ name: 'Kiểm thử tự động', type: 'PRIMARY' }] }).catch(() => undefined);
    await call(doctor, 'POST', `/visits/${id}/emr/sign`).catch(() => undefined);
    await call(doctor, 'POST', `/visits/${id}/complete`);
  }
}

// ---------- tiện ích trình duyệt ----------
let page;
let step = 0;
const results = [];
function vnNow() {
  return new Date(Date.now() + 7 * 3600_000);
}
async function shot(name) {
  step += 1;
  const file = path.join(SHOTS, `${String(step).padStart(2, '0')}-${name}.png`);
  await page.screenshot({ path: file });
  return file;
}
/** App hiện nội dung cần nhắn; "người dùng" nhắn đúng nội dung đó từ số của mình */
async function textVerificationSms(phone) {
  await page.waitForFunction(() => [...document.querySelectorAll('[data-testid="sms-body"]')].some((e) => e.checkVisibility()), { timeout: 15_000 });
  const body = await page.evaluate(() => [...document.querySelectorAll('[data-testid="sms-body"]')].find((e) => e.checkVisibility()).textContent.trim());
  await shot('nhan-tin-xac-minh');
  await smsArrives(phone, body);
}

async function check(name, fn) {
  const t0 = Date.now();
  try {
    await fn();
    results.push({ name, ok: true, ms: Date.now() - t0 });
    console.log(`  ✓ ${name}`);
  } catch (e) {
    results.push({ name, ok: false, error: String(e.message ?? e) });
    console.log(`  ✗ ${name}\n      ${e.message ?? e}`);
    if (process.env.E2E_DEBUG) console.log(await page.evaluate(() => [...document.querySelectorAll('input,textarea')].map((e) => [e.getAttribute('aria-label'), e.checkVisibility(), JSON.stringify(e.getBoundingClientRect())]).join(' | ')).catch((x) => String(x)));
    await shot('loi-' + name.replace(/[^a-z0-9]+/gi, '-').slice(0, 40)).catch(() => undefined);
    throw e;
  }
}
/** Bấm phần tử bấm được (nút, liên kết, tab, lựa chọn) đang hiện có chữ t; nhiều cái thì lấy cái có chữ ngắn nhất (sát nghĩa nhất). */
async function click(t) {
  const find = (s) => {
    const els = [...document.querySelectorAll('[role="button"],[role="link"],[role="tab"],[role="radio"],[role="checkbox"],a,button')];
    const vis = els.filter((e) => e.checkVisibility() && e.innerText && e.innerText.includes(s)); // ngoài khung nhìn vẫn tính, puppeteer tự cuộn tới khi bấm
    vis.sort((a, b) => a.innerText.length - b.innerText.length);
    return vis[0] ?? null;
  };
  await page.waitForFunction(find, { timeout: 15_000 }, t);
  const el = await page.evaluateHandle(find, t);
  await el.asElement().click();
}
const see = (t, timeout = 15_000) => page.waitForFunction((s) => document.body.innerText.includes(s), { timeout }, t);
// Màn cũ trong ngăn xếp có thể còn ô trùng nhãn (bị ẩn), nên chỉ gõ vào ô đang hiện
async function fill(label, value) {
  const find = (l) => [...document.querySelectorAll(`[aria-label="${l}"]`)].find((e) => e.checkVisibility()) ?? null;
  for (let i = 0; i < 5; i++) {
    await page.waitForFunction(find, { timeout: 15_000 }, label);
    const el = (await page.evaluateHandle(find, label)).asElement();
    await el.evaluate((e) => { e.focus(); e.select(); });
    await el.type(value);
    await new Promise((r) => setTimeout(r, 200));
    // màn vừa mở còn đang chuyển cảnh có thể vẽ lại ô nhập làm mất chữ: kiểm tra rồi gõ lại
    if (await page.evaluate((l, v) => document.querySelector(`[aria-label="${l}"]`) && [...document.querySelectorAll(`[aria-label="${l}"]`)].some((e) => e.checkVisibility() && e.value === v), label, value)) return;
  }
  throw new Error(`Không nhập được ô "${label}"`);
}
const reload = async () => {
  await page.reload({ waitUntil: 'networkidle0' });
};
const goto = (p) => page.goto(`http://localhost:${PORT}${p}`, { waitUntil: 'networkidle0' });
const patientToken = () => page.evaluate(() => localStorage.getItem('pk.token'));

// ---------- kịch bản ----------
async function main() {
  if (!existsSync(path.join(DIST, 'index.html'))) throw new Error('Chưa có bản web: chạy `npx expo export --platform web` trước.');
  const h = vnNow().getUTCHours();
  if (h >= 21 || h < 6) throw new Error('Kịch bản tạo phiên khám trong ngày, cần chạy trong khoảng 06:00 – 21:00 giờ Việt Nam.');
  rmSync(SHOTS, { recursive: true, force: true });
  mkdirSync(SHOTS, { recursive: true });
  await new Promise((r) => server.listen(PORT, r));

  // Chuẩn bị: quản lý mở phiên khám bù hôm nay cho BS Nguyễn Văn An để có thể check-in ngay trong ngày
  const manager = await login('0900000001');
  const doctor = await login('0900000011');
  const reception = await login('0900000003');
  const nurse = await login('0900000004');
  const today = vnNow().toISOString().slice(0, 10);
  const docs = await call(null, 'GET', '/doctors?specialtyId=1');
  const an = docs.find((d) => d.fullName === 'Nguyễn Văn An') ?? docs[0];
  const rooms = await call(manager, 'GET', '/rooms');
  const room = rooms.find((r) => r.code === 'P101') ?? rooms[0];
  const existing = (await call(manager, 'GET', `/schedule/sessions?from=${today}&to=${today}&doctorId=${an.id}`)) ?? [];
  const open = (await call(null, 'GET', `/slots?doctorId=${an.id}&from=${today}&to=${today}`)).flatMap((s) => s.slots).filter((x) => Date.parse(x.start) > Date.now() + 10 * 60_000);
  if (open.length < 3) {
    // cần ít nhất 3 giờ trống trong ngày: lượt khám, lịch tái khám, lịch khách đặt trước khi đăng ký; phiên mới nối sau phiên cuối để không trùng giờ
    const n = vnNow();
    const vnMin = (iso) => { const d = new Date(Date.parse(iso) + 7 * 3600_000); return d.getUTCHours() * 60 + d.getUTCMinutes(); };
    const lastEnd = Math.max(0, ...existing.filter((s) => s.status !== 'CANCELLED').map((s) => vnMin(s.endsAt)));
    const startMin = Math.max(Math.ceil((n.getUTCHours() * 60 + n.getUTCMinutes() + 20) / 30) * 30, lastEnd);
    if (startMin > 22 * 60) throw new Error('Hôm nay đã hết giờ để mở thêm phiên khám thử nghiệm.');
    const hhmm = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    await call(manager, 'POST', '/schedule/sessions', {
      doctorId: an.id,
      specialtyId: 1,
      roomId: room.id,
      date: today,
      shiftName: 'Khám bù',
      startTime: hhmm(startMin),
      endTime: hhmm(Math.min(startMin + 180, 23 * 60)),
      slotMinutes: 30,
      walkInCapacity: 5,
    });
  }

  const browser = await puppeteer.launch({ executablePath: EDGE, headless: true, args: ['--lang=vi-VN'] });
  page = await browser.newPage();
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  page.on('pageerror', (e) => console.log('    [lỗi trang]', e.message));

  const phone = '09' + String(Math.floor(Math.random() * 1e8)).padStart(8, '0');
  console.log(`\nBệnh nhân thử nghiệm: ${phone}\n`);

  await check('Mở app chưa đăng nhập: xem được chuyên khoa, có lối đăng nhập', async () => {
    await goto('/');
    await see('Bạn muốn khám chuyên khoa nào?');
    if (await page.evaluate(() => document.body.innerText.includes('Khám cho ai?'))) throw new Error('Khách không được thấy phần chọn người khám');
    await shot('khach-chuyen-khoa');
    await goto('/dang-nhap');
    await see('Phòng khám An Tâm');
    await see('Đăng ký bằng số điện thoại');
    await shot('dang-nhap');
  });

  await check('Đăng nhập sai mật khẩu báo lỗi tiếng Việt', async () => {
    await fill('Số điện thoại', '0900000099');
    await fill('Mật khẩu', 'sai-mat-khau');
    await click('Đăng nhập');
    await page.waitForSelector('[role="alert"]', { timeout: 10_000 });
  });

  await check('Đăng ký: số điện thoại, nhắn tin xác minh tới phòng khám, mật khẩu', async () => {
    await goto('/tao-tai-khoan');
    await fill('Số điện thoại', phone);
    await shot('dang-ky-1-so-dien-thoai');
    await click('Tiếp tục');
    await see('Nhắn tin để xác minh');
    await textVerificationSms(phone);
    await see('Đặt mật khẩu');
    await fill('Mật khẩu', PASSWORD);
    await fill('Nhập lại mật khẩu', PASSWORD);
    await shot('dang-ky-3-mat-khau');
    await click('Tạo tài khoản');
    await see('Thông tin người khám');
  });

  await check('Đăng ký bước 4: tạo hồ sơ khám, về trang chủ trống', async () => {
    await fill('Họ và tên', 'Trần Văn Bình');
    await fill('Ngày sinh', '15/03/1985');
    await fill('Số CCCD', '0790850' + String(Math.floor(Math.random() * 1e5)).padStart(5, '0'));
    await shot('dang-ky-4-ho-so');
    await click('Lưu hồ sơ');
    await see('Bình chưa có lịch khám');
    await see('Đặt lịch khám');
    await shot('trang-chu-chua-co-lich');
  });

  await check('Thêm hồ sơ người thân (con) và đổi hồ sơ đang xem', async () => {
    await goto('/tao-ho-so?nguoiThan=1');
    await fill('Họ và tên', 'Trần Minh Khang');
    await fill('Ngày sinh', '10/06/2021');
    await click('Lưu hồ sơ');
    await see('Bạn quản lý hồ sơ này với vai trò cha mẹ');
    await shot('trang-chu-ho-so-con');
    await page.locator('[aria-label^="Đổi hồ sơ"]').click();
    await see('Xem hồ sơ của');
    await new Promise((r) => setTimeout(r, 800)); // chờ tấm chọn hồ sơ trượt lên xong
    await shot('chon-ho-so');
    await click('Bản thân, mã');
    await see('Bình chưa có lịch khám');
  });

  let apptId;
  await check('Đặt lịch 3 bước: chuyên khoa, bác sĩ và giờ, xác nhận', async () => {
    await click('Đặt lịch khám');
    await see('Khám cho ai?');
    await click('Nội tổng quát');
    await shot('dat-lich-1-chuyen-khoa');
    await click('Tiếp tục');
    await click('Nguyễn Văn An'); // DB dev có thể còn bác sĩ thử từ e2e web, chọn đích danh bác sĩ đã mở phiên hôm nay
    await see('Còn ');
    await page.waitForSelector('[aria-label*="đã chọn"]', { timeout: 15_000 });
    // chọn giờ trống đầu tiên của ngày được chọn sẵn (phiên khám hôm nay)
    const firstSlot = await page.$$eval('[role="button"]', (els) => els.map((e) => e.innerText.trim()).find((t) => /^\d\d:\d\d$/.test(t)));
    await click(firstSlot);
    await shot('dat-lich-2-gio');
    await click('Tiếp tục');
    await fill('Bạn bị làm sao? (không bắt buộc)', 'Đau họng, sốt nhẹ 3 ngày nay');
    await page.locator('[role="checkbox"]').click();
    await shot('dat-lich-3-xac-nhan');
    await click('Gửi yêu cầu đặt lịch');
    await see('Đã gửi yêu cầu đặt lịch');
    await see('Chờ xác nhận');
    await shot('dat-lich-4-da-gui');
    const mine = await call(await patientToken(), 'GET', '/me/appointments');
    apptId = mine[0].id;
    if (mine[0].services.length !== 1) throw new Error('Không lưu xét nghiệm mong muốn');
  });

  await check('Trang chủ: lịch hôm nay đang chờ lễ tân xác nhận', async () => {
    await goto('/');
    await see('Lịch khám hôm nay');
    await see('Lịch này chưa được lễ tân xác nhận');
    await shot('trang-chu-hom-nay-cho-xac-nhan');
  });

  await check('Lễ tân xác nhận → trang chủ có nút mở mã check-in', async () => {
    await call(reception, 'POST', `/appointments/${apptId}/contacts`, { result: 'PATIENT_AGREED', purpose: 'CONFIRM_BOOKING', channel: 'PHONE' });
    await call(reception, 'POST', `/appointments/${apptId}/confirm`);
    await reload();
    await see('Mở mã check-in');
    await shot('trang-chu-hom-nay-da-xac-nhan');
  });

  await check('Thông báo xác nhận lịch đến app, có dấu chưa đọc', async () => {
    await goto('/thong-bao');
    await see('Đọc hết');
    await shot('thong-bao');
    await goto('/');
  });

  await check('Mở mã QR check-in', async () => {
    await click('Mở mã check-in');
    await page.waitForSelector('[data-testid="qr"] svg rect', { timeout: 15_000 });
    await shot('ma-checkin');
  });

  let visitId;
  await check('Lễ tân quét QR → trang chủ chuyển sang số thứ tự (A5)', async () => {
    const qr = await call(await patientToken(), 'GET', `/me/appointments/${apptId}/qr`);
    const v = await call(reception, 'POST', '/reception/check-in/qr', { token: qr.token });
    visitId = v.id;
    await goto('/');
    await see('Lượt khám hôm nay');
    await page.waitForSelector('[data-testid="queue-number"]', { timeout: 15_000 });
    const n = await page.$eval('[data-testid="queue-number"]', (el) => el.textContent.trim());
    if (n !== String(v.queueNumber)) throw new Error(`Số thứ tự ${n} khác ${v.queueNumber}`);
    await see('Các bước hôm nay');
    await shot('trang-chu-dang-cho-kham');
  });

  await check('Đo chỉ số, bác sĩ gọi vào → hiện "Đến lượt bạn"', async () => {
    await call(nurse, 'POST', `/visits/${visitId}/vitals`, { pulsePerMin: 82, temperatureC: 37.8, systolicBp: 130, diastolicBp: 85, spo2Percent: 98, weightKg: 68, heightCm: 170 });
    const v = (await call(await patientToken(), 'GET', '/me/queue')).find((q) => q.visit.id === visitId).visit;
    // Lần chạy trước còn sót bệnh nhân thử nghiệm trong phiên: cho họ "bỏ về" để bác sĩ gọi đúng người
    const view = await call(doctor, 'GET', `/queue/sessions/${v.sessionId}`);
    for (const o of view.current.filter((x) => x.id !== visitId)) {
      if (o.status === 'CALLED') await call(reception, 'POST', `/visits/${o.id}/left`);
      else await finishVisit(doctor, o.id); // đang khám thì phải khám xong, không cho "bỏ về"
    }
    for (const w of view.waiting.filter((x) => x.visit.id !== visitId)) await call(reception, 'POST', `/visits/${w.visit.id}/left`);
    await call(doctor, 'POST', `/queue/sessions/${v.sessionId}/call-next`);
    await reload();
    await see('Đến lượt bạn');
    await see('Đo huyết áp, cân nặng');
    await shot('trang-chu-den-luot');
  });

  let item;
  await check('Bác sĩ chỉ định xét nghiệm → trang chủ chuyển sang làm xét nghiệm (A6)', async () => {
    await call(doctor, 'POST', `/visits/${visitId}/start`);
    const labs = await call(null, 'GET', '/services?category=LAB');
    const order = await call(doctor, 'POST', `/visits/${visitId}/orders`, { items: [{ serviceId: labs[0].id, quantity: 1 }] });
    item = order.items[0];
    await call(doctor, 'POST', `/visits/${visitId}/await-results`);
    await reload();
    await see('Quay lại phòng');
    await see(item.serviceName);
    await shot('trang-chu-lam-xet-nghiem');
  });

  await check('Có kết quả, bác sĩ kết luận, kê đơn, hoàn tất → hóa đơn (A7)', async () => {
    const perf = await call(doctor, 'POST', `/order-items/${item.id}/perform`, { quantity: 1 });
    const res = await call(doctor, 'POST', `/performances/${perf.id}/result`, { resultText: 'WBC 12,5 G/L (tăng), NEU 78%', conclusion: 'Bạch cầu tăng nhẹ', submit: true });
    // Điều dưỡng đính kèm phiếu kết quả PDF (backend nhận dạng PDF theo nội dung tệp)
    const form = new FormData();
    form.append('file', new Blob([PDF], { type: 'application/pdf' }), 'cong-thuc-mau.pdf');
    const tech = await login('0900000004');
    const up = await fetch(`${API}/api/results/${res.id}/files`, { method: 'POST', headers: { Authorization: `Bearer ${tech}` }, body: form });
    if (!up.ok) throw new Error(`Tải tệp kết quả lên lỗi ${up.status}: ${await up.text()}`);
    await call(doctor, 'POST', `/results/${res.id}/review`);
    await call(doctor, 'POST', `/visits/${visitId}/call-back`);
    await call(doctor, 'POST', `/visits/${visitId}/start`);
    const follow = new Date(Date.now() + 7 * 3600_000 + 9 * 86_400_000).toISOString().slice(0, 10);
    await call(doctor, 'PUT', `/visits/${visitId}/emr`, {
      chiefComplaint: 'Đau họng, sốt nhẹ 3 ngày',
      conclusion: 'Viêm amidan cấp',
      treatmentPlan: 'Kháng sinh 7 ngày, hạ sốt khi cần',
      advice: 'Uống nhiều nước, súc họng nước muối',
      followUpDate: follow,
      diagnoses: [{ icd10Code: 'J03.9', name: 'Viêm amidan cấp', type: 'PRIMARY' }],
    });
    await call(doctor, 'POST', `/visits/${visitId}/emr/sign`);
    const meds = await call(doctor, 'GET', '/medicines?q=para');
    const rx = await call(doctor, 'POST', `/visits/${visitId}/prescriptions`, {
      items: [{ medicineId: (meds.items ?? meds)[0].id, quantity: 10, dosePerTime: '1 viên', timesPerDay: 2, route: 'Uống', timing: 'sau ăn', days: 5 }],
      advice: 'Tái khám nếu sốt trên 39 °C',
    });
    await call(doctor, 'POST', `/prescriptions/${rx.id}/sign`);
    await call(doctor, 'POST', `/visits/${visitId}/complete`);
    await reload();
    await see('Hóa đơn hôm nay');
    await see('Còn phải trả');
    await see('Viêm amidan cấp');
    await shot('trang-chu-kham-xong-cho-thanh-toan');
  });

  await check('Thanh toán trên app → hóa đơn đã thanh toán, nhắc đặt lịch tái khám', async () => {
    await click('Trả trên app');
    await see('Đã thanh toán');
    await see('Cần đặt lịch');
    await shot('trang-chu-da-thanh-toan');
  });

  await check('Chi tiết lượt khám: kết luận, chỉ số, kết quả, đơn thuốc, hóa đơn', async () => {
    await goto(`/luot-kham/${visitId}`);
    await see('Bác sĩ kết luận');
    await see('Kết quả xét nghiệm');
    await see('Đơn thuốc');
    await see('Hẹn tái khám');
    await see('cong-thuc-mau.pdf');
    await shot('chi-tiet-luot-kham');
  });

  await check('Mở tệp kết quả PDF: tải được tệp thật bằng mã đăng nhập của bệnh nhân', async () => {
    const got = page.waitForResponse((r) => r.url().includes('/api/me/files/'), { timeout: 15_000 });
    const tab = new Promise((resolve) => page.browser().once('targetcreated', resolve));
    await page.locator('[aria-label="Xem tệp cong-thuc-mau.pdf"]').click();
    const res = await got;
    if (res.status() !== 200) throw new Error(`Tải tệp trả ${res.status()}`);
    if (!String(res.headers()['content-type']).includes('application/pdf')) throw new Error('Tệp tải về không phải PDF');
    // trình duyệt mở thẻ mới hiển thị tệp; đóng lại và đưa app lên trước để các bước sau chạy tiếp
    const opened = await (await tab).page();
    await opened?.close();
    await page.bringToFront();
    // Nội dung đúng tệp đã tải lên (Puppeteer không đọc được thân phản hồi của fetch trong trang nên tải lại)
    const fileId = /\/me\/files\/(\d+)/.exec(res.url())[1];
    const own = await fetch(`${API}/api/me/files/${fileId}`, { headers: { Authorization: `Bearer ${await patientToken()}` } });
    if (!Buffer.from(await own.arrayBuffer()).equals(PDF)) throw new Error('Nội dung tệp tải về khác tệp đã tải lên');
    // Người khác không tải được tệp của bệnh nhân này
    const stranger = await fetch(`${API}/api/me/files/${fileId}`, { headers: { Authorization: `Bearer ${await login('0900000099')}` } });
    if (stranger.ok) throw new Error('Tài khoản khác tải được tệp kết quả');
  });

  await check('Gửi góp ý cho lần khám', async () => {
    await fill('Nội dung', 'Bác sĩ khám kỹ, thời gian chờ hợp lý.');
    await click('Gửi góp ý');
    await see('Đã gửi góp ý');
  });

  await check('Hồ sơ sức khỏe: lịch sử, kết quả, đơn thuốc; CCCD bị che', async () => {
    await goto('/ho-so');
    await see('Lần khám');
    const body = await page.evaluate(() => document.body.innerText);
    if (!/Giấy tờ: •+\d{4}/.test(body)) throw new Error('Số CCCD không được che');
    await shot('ho-so-suc-khoe');
    await click('Đơn thuốc');
    await see('Đơn 1 loại thuốc');
    await shot('ho-so-don-thuoc');
  });

  await check('Đặt lịch tái khám từ việc cần làm: chọn sẵn bác sĩ, gửi rồi xin hủy', async () => {
    await goto('/');
    await click('Tái khám');
    await see('Chọn bác sĩ và giờ');
    await page.waitForSelector('[aria-label*="đã chọn"]', { timeout: 15_000 });
    const slot = await page.$$eval('[role="button"]', (els) => els.map((e) => e.innerText.trim()).find((t) => /^\d\d:\d\d$/.test(t)));
    await click(slot);
    await click('Tiếp tục');
    await click('Gửi yêu cầu đặt lịch');
    await see('Đã gửi yêu cầu đặt lịch');
    await click('Xem lịch hẹn');
    await see('Chi tiết lịch hẹn');
    await click('Xin hủy lịch');
    await see('Đã gửi yêu cầu hủy lịch');
    await shot('lich-hen-xin-huy');
    const reqs = await call(reception, 'GET', '/appointment-change-requests');
    const mine = reqs.find((r) => r.type === 'CANCEL' && r.appointmentId === Number(page.url().split('/').pop()));
    if (!mine) throw new Error('Lễ tân không thấy yêu cầu hủy');
    globalThis.cancelRequestId = mine.id;
  });

  await check('Mở lại lịch hẹn: yêu cầu hủy vẫn đang chờ, không cho gửi thêm', async () => {
    await reload();
    await see('Đang chờ lễ tân duyệt');
    const hasButtons = await page.evaluate(() => document.body.innerText.includes('Xin hủy lịch'));
    if (hasButtons) throw new Error('Vẫn còn nút Xin hủy lịch khi đang có yêu cầu chờ');
  });

  await check('Lễ tân từ chối yêu cầu → app báo chưa được duyệt, cho gửi lại', async () => {
    await call(reception, 'POST', `/appointment-change-requests/${globalThis.cancelRequestId}/reject`, { note: 'Bệnh nhân đổi ý qua điện thoại' });
    await reload();
    await see('chưa được duyệt');
    await see('Xin hủy lịch');
    await shot('lich-hen-yeu-cau-bi-tu-choi');
  });

  await check('Danh sách lịch hẹn và lọc theo hồ sơ', async () => {
    await goto('/lich-hen');
    await see('Sắp tới');
    await shot('lich-hen');
    await click('Đã qua');
    await see('Đã check-in');
  });

  await check('Tài khoản: hồ sơ bản thân và người thân; đăng xuất', async () => {
    await goto('/tai-khoan');
    await see('Trần Minh Khang');
    await shot('tai-khoan');
    await click('Đăng xuất');
    await see('Bạn muốn khám chuyên khoa nào?');
  });

  await check('Đăng nhập lại bằng mật khẩu vừa tạo', async () => {
    await goto('/dang-nhap');
    await fill('Số điện thoại', phone);
    await fill('Mật khẩu', PASSWORD);
    await click('Đăng nhập');
    await see('Trang chủ');
  });

  await check('Quên mật khẩu: nhắn tin xác minh, đặt mật khẩu mới, vào thẳng app', async () => {
    await page.evaluate(() => localStorage.clear());
    await goto('/quen-mat-khau');
    await fill('Số điện thoại', phone);
    await click('Tiếp tục');
    await textVerificationSms(phone);
    await fill('Mật khẩu mới', 'matkhaumoi456');
    await shot('quen-mat-khau');
    await click('Đổi mật khẩu');
    await see('Trang chủ');
  });

  await check('Khách chọn giờ trước, đăng ký xong quay lại đúng giờ đã chọn để gửi', async () => {
    await page.evaluate(() => localStorage.clear());
    await goto('/');
    await click('Nội tổng quát');
    await click('Tiếp tục');
    await click('Nguyễn Văn An');
    await page.waitForSelector('[aria-label*="đã chọn"]', { timeout: 15_000 });
    const slot = await page.$$eval('[role="button"]', (els) => els.map((e) => e.innerText.trim()).find((t) => /^\d\d:\d\d$/.test(t)));
    await click(slot);
    await shot('khach-chon-gio');
    await click('Đăng nhập để đặt giờ này');
    await see('Đăng nhập hoặc đăng ký để giữ giờ');
    await see(slot);
    await shot('khach-dang-nhap-giu-gio');
    const phone2 = '09' + String(Math.floor(Math.random() * 1e8)).padStart(8, '0');
    await click('Đăng ký bằng số điện thoại');
    await fill('Số điện thoại', phone2);
    await click('Tiếp tục');
    await textVerificationSms(phone2);
    await see('Đặt mật khẩu');
    await fill('Mật khẩu', PASSWORD);
    await fill('Nhập lại mật khẩu', PASSWORD);
    await click('Tạo tài khoản');
    await see('Thông tin người khám');
    await fill('Họ và tên', 'Lê Thị Hoa');
    await fill('Ngày sinh', '20/08/1990');
    await click('Lưu hồ sơ');
    await see('Xác nhận thông tin');
    await see('Lê Thị Hoa');
    await see(slot);
    await shot('khach-xac-nhan-sau-dang-ky');
    await click('Gửi yêu cầu đặt lịch');
    await see('Đã gửi yêu cầu đặt lịch');
    const mine = await call(await patientToken(), 'GET', '/me/appointments');
    if (mine.length !== 1 || mine[0].status !== 'PENDING_CONFIRMATION') throw new Error('Không có lịch chờ xác nhận: ' + JSON.stringify(mine.map((a) => a.status)));
  });

  await check('Đã có tài khoản: chọn giờ khi chưa đăng nhập, đăng nhập xong vào thẳng bước xác nhận', async () => {
    await page.evaluate(() => localStorage.clear());
    await goto('/');
    await click('Nội tổng quát');
    await click('Tiếp tục');
    await click('Nguyễn Văn An');
    await page.waitForSelector('[aria-label*="đã chọn"]', { timeout: 15_000 });
    const slot = await page.$$eval('[role="button"]', (els) => els.map((e) => e.innerText.trim()).find((t) => /^\d\d:\d\d$/.test(t)));
    await click(slot);
    await click('Đăng nhập để đặt giờ này');
    await fill('Số điện thoại', phone);
    await fill('Mật khẩu', 'matkhaumoi456');
    await click('Đăng nhập');
    await see('Xác nhận thông tin');
    await see('Trần Văn Bình');
    await see(slot);
    await shot('da-co-tai-khoan-xac-nhan');
  });

  await browser.close();
}

main()
  .catch((e) => {
    if (!results.some((r) => !r.ok)) console.error('\nDừng kịch bản:', e.message ?? e);
  })
  .finally(() => {
    server.close();
    const failed = results.filter((r) => !r.ok).length;
    console.log(`\n${results.length - failed}/${results.length} bước đạt. Ảnh chụp: ${SHOTS}`);
    process.exit(failed || results.length === 0 ? 1 : 0);
  });
