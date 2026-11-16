# Triển khai: CI/CD và giám sát

## 1. Tổng quan quy trình

```
 Lập trình viên            GitHub (máy ảo của GitHub)                        Máy chạy hệ thống (self-hosted runner)
 ──────────────            ──────────────────────────                        ─────────────────────────────────────
 git push main  ──►  CI  (ci.yml)                                             
                     ├─ backend: 66 test, PostgreSQL thật (Testcontainers)
                     ├─ web: lint, test, build
                     └─ mobile: typecheck, lint, test
                          │ đạt
                          ▼
                     CD  (cd.yml) — job "Đóng gói image"
                     ├─ build image backend  ─┐
                     └─ build image web      ─┴─► ghcr.io/<chủ repo>/phong-kham-{backend,web}:sha-<commit> và :latest
                          │
                          ▼  job "Triển khai lên máy chủ" (chạy trên máy của nhóm)
                                                                  ──►  docker compose pull backend web
                                                                       docker compose up -d --no-build
                                                                       kiểm tra /api/actuator/health = UP
                                                                       (database và số liệu giám sát giữ nguyên)

 Giám sát (chạy cùng hệ thống):  backend ──số liệu──► Prometheus (15 giây/lần, 6 luật cảnh báo) ──► Grafana (bảng biểu đồ)
```

| Thành phần | Tệp | Vai trò |
|---|---|---|
| CI | `.github/workflows/ci.yml` | Mỗi lần đẩy code hoặc mở pull request: chạy toàn bộ kiểm thử tự động. Lỗi thì báo đỏ, không triển khai |
| CD | `.github/workflows/cd.yml` | CI trên `main` đạt thì đóng gói image, đẩy lên ghcr.io, triển khai lên máy chủ |
| Đóng gói | `backend/Dockerfile`, `web/Dockerfile`, `web/nginx.conf` | Backend: build bằng Maven, chạy bằng JRE 25. Web: build bằng Node, phục vụ bằng nginx, chuyển `/api` sang backend |
| Chạy hệ thống | `docker-compose.yml` | database, backend, web, Prometheus, Grafana |
| Giám sát | `monitoring/` | Cấu hình Prometheus, luật cảnh báo, nguồn dữ liệu và bảng biểu đồ Grafana (tự nạp khi khởi động) |

App bệnh nhân (Expo) không triển khai lên máy chủ: CI kiểm tra code; file cài đặt APK/IPA phát hành riêng bằng EAS Build của Expo.

## 2. Địa chỉ trên máy chạy hệ thống

| Dịch vụ | Địa chỉ | Ghi chú |
|---|---|---|
| Web nhân viên | http://localhost:8088 | nginx; `/api/...` chuyển sang backend |
| API | http://localhost:8080/api | app bệnh nhân, kiểm thử gọi thẳng |
| Grafana | http://localhost:3005 | tài khoản `admin`, mật khẩu `GRAFANA_ADMIN_PASSWORD` trong `.env` |
| Prometheus | http://localhost:9095 | tab Alerts: trạng thái 6 luật cảnh báo |

Cổng đổi được bằng `WEB_PORT`, `GRAFANA_PORT`, `PROMETHEUS_PORT` trong `.env`.

## 3. Cài self-hosted runner (làm một lần, khoảng 5 phút)

Runner là chương trình của GitHub chạy trên máy của nhóm, nhận lệnh triển khai từ GitHub. Máy cần Docker Desktop đang chạy.

1. Trên GitHub: repo → **Settings → Actions → Runners → New self-hosted runner** → chọn **Windows**, **x64**.
2. Mở PowerShell, làm đúng các lệnh GitHub hiển thị (tạo thư mục `C:\actions-runner`, tải về, giải nén, `.\config.cmd --url ... --token ...`).
   Khi `config.cmd` hỏi: tên nhóm runner Enter, tên runner Enter, nhãn thêm Enter, thư mục làm việc Enter,
   **"Run the runner as service?" chọn N** (chạy dạng dịch vụ Windows sẽ dùng tài khoản hệ thống, không gọi được Docker Desktop).
3. Chạy runner: `cd C:\actions-runner; .\run.cmd` — để cửa sổ này mở (thấy `Listening for Jobs` là được).
4. Trên GitHub: **Settings → Secrets and variables → Actions → tab Variables → New repository variable**, tạo 2 biến:
   - `DEPLOY_ENABLED` = `true`
   - `DEPLOY_ENV_FILE` = đường dẫn tệp `.env` thật trên máy, vd. `C:\Users\3510a\Desktop\he-thong-phong-kham\.env`

Chưa có runner hoặc `DEPLOY_ENABLED` khác `true` thì CD vẫn đóng gói và đẩy image, chỉ bỏ qua bước triển khai.

## 4. Demo CD

1. Sửa một dòng chữ trên giao diện web, commit, `git push` lên `main`.
2. Tab **Actions** trên GitHub: CI chạy (khoảng 3–5 phút) → đạt → CD chạy: "Đóng gói image" → "Triển khai lên máy chủ".
3. Cửa sổ runner hiện job đang chạy; xong thì mở http://localhost:8088 (Ctrl+F5) thấy dòng chữ mới.
4. Bước "Kiểm tra sau triển khai" in ra `Hệ thống đã chạy bản sha-...`.

**Quay lại bản cũ:** Actions → workflow **CD** → chọn lần chạy của bản cũ → **Re-run jobs**. Hoặc trên máy:
`$env:IMAGE_TAG="sha-<12 ký tự đầu của commit>"; docker compose pull backend web; docker compose up -d --no-build`.

## 5. Giám sát

**Số liệu** (backend xuất ở `/api/actuator/prometheus`, chỉ đọc được bằng `METRICS_TOKEN`; nginx chặn đường này từ bên ngoài):
số yêu cầu và độ trễ theo từng API, mã trạng thái, bộ nhớ và CPU của JVM, kết nối cơ sở dữ liệu (HikariCP).

**Bảng "Phòng khám: tình trạng hệ thống"** (Grafana mở mặc định, tự làm mới 10 giây):

| Ô | Ý nghĩa |
|---|---|
| Backend | ĐANG CHẠY / NGỪNG |
| Đã chạy được | thời gian từ lần khởi động gần nhất (về 0 sau mỗi lần triển khai) |
| Yêu cầu / giây, Độ trễ p95 | tải hiện tại; p95 là thời gian mà 95% yêu cầu xử lý xong |
| Lỗi máy chủ (5xx) | tỷ lệ yêu cầu lỗi do máy chủ |
| Bị chặn chống spam (1 giờ) | số yêu cầu trả 429 (vượt giới hạn theo IP, khóa đăng nhập sau 5 lần sai) |
| Biểu đồ | yêu cầu theo mã trạng thái, độ trễ p50/p95/p99, API chậm nhất, đăng nhập/đăng ký/xác minh/đặt lịch mỗi phút, heap JVM, CPU, kết nối CSDL |

**Cảnh báo** (`monitoring/prometheus/alerts.yml`, xem ở Prometheus → Alerts hoặc Grafana → Alerting → Alert rules):

| Cảnh báo | Điều kiện | Mức |
|---|---|---|
| BackendKhongPhanHoi | không lấy được số liệu từ backend quá 1 phút | nghiêm trọng |
| TyLeLoiMayChuCao | hơn 5% yêu cầu lỗi 5xx trong 5 phút | nghiêm trọng |
| PhanHoiCham | độ trễ p95 trên 1 giây trong 5 phút | cảnh báo |
| NghiSpam | hơn 20 yêu cầu bị chặn (429) trong 5 phút | cảnh báo |
| HetKetNoiCSDL | có yêu cầu chờ kết nối CSDL quá 2 phút | cảnh báo |
| BoNhoJvmGanDay | heap JVM dùng trên 90% trong 5 phút | cảnh báo |

Thử cảnh báo khi demo: `docker compose stop backend`, chờ khoảng 1 phút → BackendKhongPhanHoi chuyển **Firing**, ô Backend chuyển **NGỪNG**;
`docker compose start backend` để trở lại.

Hiện cảnh báo chỉ hiển thị trên Prometheus/Grafana, chưa gửi tin. Muốn nhận qua email/Telegram: Grafana → Alerting → Contact points.

## 6. Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Job triển khai đứng "Waiting for a runner" | Cửa sổ `run.cmd` đã tắt hoặc máy tắt: chạy lại `C:\actions-runner\run.cmd` |
| Bước kéo image báo `denied` | Image trên ghcr.io để riêng tư; bước đăng nhập dùng token của lần chạy, kiểm tra repo → Settings → Actions → General → Workflow permissions có quyền **Read and write** |
| Cổng 8088/3005/9095 đã bị dùng | Đổi `WEB_PORT`, `GRAFANA_PORT`, `PROMETHEUS_PORT` trong `.env` |
| Grafana không có số liệu | Prometheus → Status → Targets: job `backend` phải UP; nếu 401 thì `METRICS_TOKEN` trong `.env` trống hoặc sai |
