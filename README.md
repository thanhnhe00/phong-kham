# Hệ thống phòng khám

Đồ án: hệ thống đặt lịch và quy trình khám ngoại trú cho **một phòng khám tư** (khám, kê đơn, không bán thuốc).
Bệnh nhân dùng app di động; nhân viên (lễ tân, điều dưỡng, bác sĩ, quản lý) dùng web.

| Thư mục | Nội dung | Công nghệ |
|---|---|---|
| [backend/](backend/) | REST API theo mô hình MVC | Java 25, Spring Boot 4.1, Spring Data JPA, Flyway, PostgreSQL 16 |
| [web/](web/) | Web cho nhân viên | React + Vite + TypeScript |
| [mobile/](mobile/) | App cho bệnh nhân | Expo SDK 58 + Expo Router |
| [docs/](docs/) | Đặc tả nghiệp vụ, ERD, nghiên cứu nghiệp vụ | |

Hướng dẫn chạy và kiểm thử từng phần: [web/README.md](web/README.md), [mobile/README.md](mobile/README.md).

## Tài liệu

- [docs/dac-ta-nghiep-vu.docx](docs/dac-ta-nghiep-vu.docx): phạm vi, tác nhân, 34 use case, quy tắc, trạng thái, danh mục bảng, kiểm thử.
- [docs/erd.mmd](docs/erd.mmd): sơ đồ ERD (Mermaid; mở bằng Mermaid Live Editor hoặc extension Mermaid).
- [docs/nghien-cuu-nghiep-vu.md](docs/nghien-cuu-nghiep-vu.md): nghiên cứu luồng phòng khám tư thực tế và quy định (Thông tư 32/2023, 13/2025, 26/2025).
- Schema CSDL: [backend/src/main/resources/db/migration/V1__khoi_tao_schema.sql](backend/src/main/resources/db/migration/V1__khoi_tao_schema.sql) (nguồn duy nhất; Flyway tự chạy khi backend khởi động).

## Chạy toàn bộ bằng Docker

```bash
cp .env.example .env                              # sửa JWT_SECRET; SEED_DEMO=true tạo dữ liệu demo
docker compose up -d --build
curl http://localhost:8080/api/actuator/health   # {"status":"UP"}
```

- Swagger (xem và thử API): http://localhost:8080/api/docs
- Danh sách API và tài khoản demo: [docs/api.md](docs/api.md). Quản lý demo: `0900000001` / `12345678`.

## Xác minh số điện thoại và chống spam

**Xác minh bằng tin nhắn người dùng gửi đến** (khi đăng ký và quên mật khẩu): app hiện nội dung `PKXM <mã 6 số>`
và số của phòng khám, người dùng bấm "Mở tin nhắn đã soạn sẵn" rồi gửi **từ chính SIM của số đã khai**. Một điện thoại
của phòng khám làm cổng SMS, chuyển tin về `POST /api/sms/inbound`. Đúng mã **và** đúng số người gửi (do nhà mạng xác
định, không giả được bằng phần mềm) thì số được xác minh, app tự sang bước tiếp theo.
- Phòng khám không tốn tiền tin nhắn; kẻ xấu không bắt phòng khám gửi tin để "đốt tiền" được.
- Muốn tạo 100 tài khoản ảo phải có 100 SIM thật và tự trả 100 tin nhắn.
- Đặt trong `.env`: `SMS_VERIFY_NUMBER` (số SIM của máy cổng) và một trong hai cách xác thực cổng:
  - **Android**: cài app [SMS Gateway for Android](https://sms-gate.app), thêm webhook sự kiện `sms:received` trỏ tới
    `https://<máy chủ>/api/sms/inbound`, chép **Signing Key** vào `SMS_GATEWAY_SIGNING_KEY`.
  - **iPhone**: app Phím tắt, tự động hóa "Khi nhận tin nhắn chứa PKXM" → "Lấy nội dung URL" (POST tới địa chỉ trên,
    header `X-Gateway-Token: <SMS_GATEWAY_TOKEN>`, thân JSON `{"from": <người gửi>, "body": <nội dung>}`).
- Chưa có điện thoại làm cổng: giả lập một tin nhắn đến bằng `curl` (đúng token trong `.env`):
  `curl -X POST localhost:8080/api/sms/inbound -H "X-Gateway-Token: <token>" -H "Content-Type: application/json" -d '{"from":"09xxxxxxxx","body":"PKXM 123456"}'`.
  Các kịch bản e2e làm đúng như vậy.

**Chống spam theo IP** (`config/RateLimitFilter.java`, trả 429 kèm `Retry-After`): mỗi IP xin mã tối đa 10 lần/giờ,
đăng ký 10 lần/giờ, đăng nhập 30 lần/10 phút, đặt lại mật khẩu 10 lần/giờ, đặt lịch trên app 20 lần/giờ. Thêm vào đó:
một số điện thoại xin mã tối đa 5 lần/giờ, đăng nhập sai 5 lần khóa tài khoản 15 phút, vắng mặt 3 lần trong 30 ngày
thì bị khóa đặt lịch qua app. `RATE_LIMIT_ENABLED=false` chỉ dùng trên máy chạy kiểm thử tự động và đo hiệu năng
(mọi yêu cầu cùng một IP). Chạy sau Caddy/nginx: backend lấy IP thật từ `X-Forwarded-For` của proxy nội bộ.

## Chạy từng phần khi phát triển

Cần JDK 25 (`JAVA_HOME` phải trỏ tới JDK 25, không phải JDK đi kèm IDE), Node 22+, Docker.

```bash
docker compose up -d db                  # chỉ PostgreSQL, cổng 5432

cd backend
JWT_SECRET=chuoi-bi-mat-it-nhat-32-ky-tu-abcdef SEED_DEMO=true ./mvnw spring-boot:run   # http://localhost:8080/api
./mvnw test                              # cần Docker đang chạy (test tự dựng PostgreSQL riêng)

cd web && npm install && npm run dev     # http://localhost:5173
cd mobile && npm install && npx expo start
```

## Cấu trúc backend (MVC)

Code nằm trong `backend/src/main/java/vn/phongkham/`, chia theo nghiệp vụ, mỗi nghiệp vụ có cùng các lớp:

```
<nghiep-vu>/              vd. appointment, schedule, visit, emr, prescription, invoice
  controller/   C: nhận request, kiểm tra quyền, trả response. Không chứa logic.
  service/      Use case: logic nghiệp vụ, transaction.
  repository/   Truy cập DB (Spring Data JPA).
  model/        M: entity ánh xạ bảng.
  dto/          V: dữ liệu vào/ra của API (có validate).
config/         Cấu hình chung (bảo mật, ...)
```

Quy ước:
- Schema chỉ đổi bằng migration mới `V<n>__<mo_ta>.sql`; không sửa migration đã chạy. Hibernate ở chế độ `validate`.
- Mọi API mặc định cần đăng nhập; chỉ mở những route công khai (khai báo trong `config/SecurityConfig.java`), phân quyền bằng `@PreAuthorize` trên controller.
- Lỗi nghiệp vụ: `throw ApiException.badRequest("...")` (tiếng Việt); `GlobalExceptionHandler` chuyển thành JSON.
- DTO request dùng kiểu đối tượng (`Boolean`, `Integer`), không dùng `boolean`/`int`: Jackson 3 báo lỗi khi thiếu trường kiểu nguyên thủy.
- Query JPQL không truyền `null` cho tham số chuỗi/enum (PostgreSQL không xác định được kiểu): dùng `""` hoặc cờ `anyXxx` như `UserRepository.search`.
- Test: kế thừa `IntegrationTest` (gọi API qua MockMvc, PostgreSQL thật bằng Testcontainers, dữ liệu xóa trước mỗi test).
- Comment, thông báo lỗi, giao diện dùng tiếng Việt.

## Nộp bài và đưa lên Git

Số chương và định dạng cuối cùng theo file quy định trình bày đồ án của khoa. Bố cục dưới đây là bố cục thường gặp, kèm phần tài liệu đã có để viết từng chương.

### Bố cục báo cáo

| Phần | Nội dung | Lấy từ |
|---|---|---|
| Bìa, lời cam đoan, lời cảm ơn, mục lục, danh mục hình, bảng, từ viết tắt | Theo mẫu của trường | |
| Mở đầu | Lý do chọn đề tài, mục tiêu, phạm vi, phương pháp | Mục 1 của `docs/dac-ta-nghiep-vu.docx` |
| Chương 1: Khảo sát và cơ sở lý thuyết | Quy trình phòng khám thực tế, quy định pháp lý, hệ thống tương tự, công nghệ | `docs/nghien-cuu-nghiep-vu.md` |
| Chương 2: Phân tích và thiết kế | Tác nhân, use case, quy trình, ERD, thiết kế CSDL, kiến trúc MVC, API, thiết kế giao diện | `docs/dac-ta-nghiep-vu.docx`, `docs/erd.mmd`, migration SQL, `docs/api.md`, `docs/thiet-ke/` |
| Chương 3: Cài đặt và triển khai | Cấu trúc code, công nghệ, Docker, giao diện từng vai | README các phần, ảnh chụp màn hình từ kiểm thử đầu-cuối |
| Chương 4: Kiểm thử và kết quả | Ca kiểm thử, kết quả, ảnh chụp | Kiểm thử tự động: backend 65, web 10 + 21 bước, app 29 + 28 bước, cả hệ thống 16 bước (`web/e2e/he-thong.mjs`) |
| Kết luận và hướng phát triển | Đã làm được, hạn chế (cổng SMS là một điện thoại, cập nhật bằng hỏi lại 15 giây, chưa có BHYT, chưa đẩy thông báo), hướng mở rộng | Mục "giai đoạn sau" trong đặc tả |
| Tài liệu tham khảo, phụ lục | Văn bản luật, tài liệu kỹ thuật, hướng dẫn cài đặt, tài khoản demo | Nguồn trong `docs/nghien-cuu-nghiep-vu.md`, `docs/api.md` |

Hồ sơ bảo vệ thường gồm: báo cáo (bản in và PDF), slide, mã nguồn (link Git), kịch bản demo và tài khoản demo, có thể thêm video demo.

### Các file trong repo

| File, thư mục | Mục đích | Trạng thái |
|---|---|---|
| `README.md` | Giới thiệu đề tài, công nghệ, cách chạy, tài khoản demo, cấu trúc | Có. Còn thiếu: tên đề tài chính thức, thành viên nhóm, giảng viên hướng dẫn, tên trường, ảnh chụp màn hình |
| `.gitignore` | Không đưa `.env`, `node_modules`, `target`, `dist`, ảnh kiểm thử lên Git | Có (gốc, backend, web, mobile) |
| `.env.example` | Mẫu biến môi trường, không chứa bí mật thật | Có |
| `docker-compose.yml` | Chạy hệ thống bằng một lệnh | Có (database và backend). Web chưa có trong compose, đang chạy bằng `npm run dev` |
| `backend/`, `web/`, `mobile/` | Mã nguồn | Xong, mỗi phần có README riêng |
| `docs/` | Đặc tả, ERD, nghiên cứu nghiệp vụ, danh sách API, thiết kế giao diện app (PDF) | Có |
| `backend/perf/` | Kịch bản kiểm thử hiệu năng và kết quả | Có |
| `docs/kiem-thu.md` | Các bộ kiểm thử, cách chạy, kịch bản, kết quả, hiệu năng | Có |
| `docs/kich-ban-demo.md` | Chuẩn bị, các bước demo, câu hỏi hay gặp khi bảo vệ | Có |
| `docs/hinh-anh/` | Ảnh màn hình app, web và sơ đồ (kiến trúc, use case, ERD) dùng trong báo cáo | Có (42 ảnh) |
| `docs/bao-cao/` | Báo cáo BTL theo bố cục thầy giao (.docx) | Có. Còn điền: phân công, link Google Sheet (chỗ tô vàng) |
| `docs/slide/` | Slide bảo vệ | Chưa có |
| `CONTRIBUTING.md` | Quy ước làm việc nhóm: nhánh, commit, kiểm thử trước khi gộp | Có |
| `LICENSE` | Giấy phép mã nguồn | Chưa có. Đồ án thường để MIT, hoặc không đặt nếu trường giữ bản quyền |
| `.github/workflows/ci.yml` | GitHub tự chạy kiểm thử backend, web, app mỗi lần đẩy code | Có |

### Kiểm tra trước khi đưa lên Git

- `.env` (chứa `JWT_SECRET`, mật khẩu database) không được lên Git; đã có trong `.gitignore`. `JWT_SECRET` trong `.env.example` chỉ là chuỗi mẫu.
- Không có `node_modules/`, `backend/target/`, `web/dist/`, `mobile/dist/`, ảnh chụp kiểm thử: các `.gitignore` đã loại ra. Chạy `git status` xem lại trước lần commit đầu.
- Thư mục `.git` ở gốc đang rỗng: dùng luôn, hoặc xóa rồi `git init` lại.
- Database đang chạy trên máy có nhiều dữ liệu thử từ kiểm thử đầu-cuối. Dữ liệu không nằm trong repo; trước khi demo nên tạo lại database bằng dữ liệu mẫu (`SEED_DEMO=true`).
