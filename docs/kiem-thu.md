# Kiểm thử

Tài liệu cho chương 5 của báo cáo: các bộ kiểm thử, cách chạy, kịch bản và kết quả.

## 1. Các bộ kiểm thử

| Bộ | Loại | Tệp | Lệnh | Số ca |
|---|---|---|---|---|
| Backend | API, tích hợp với PostgreSQL thật (Testcontainers) | `backend/src/test/java/` | `cd backend && ./mvnw test` (cần Docker) | 66 |
| Web nhân viên | Đơn vị | `web/src/lib/logic.test.ts` | `cd web && npm test` | 10 |
| Web nhân viên | Đầu-cuối, 4 vai trên giao diện | `web/e2e/run.mjs` | `cd web && npm run build && npm run e2e` | 21 bước |
| Ứng dụng bệnh nhân | Đơn vị | `mobile/src/lib/*.test.ts` | `cd mobile && npm test` | 29 |
| Ứng dụng bệnh nhân | Đầu-cuối trên giao diện app | `mobile/e2e/run.mjs` | `cd mobile && npx expo export --platform web && npm run e2e` | 28 bước |
| Cả hệ thống | Đầu-cuối: app và web cùng lúc, có tranh chấp | `web/e2e/he-thong.mjs` | `cd web && npm run e2e:he-thong` (cần cả hai bản build) | 16 bước |
| Hiệu năng | Tải đồng thời, tranh chấp đặt lịch | `backend/perf/hieu-nang.mjs` | `cd backend && node perf/hieu-nang.mjs` | 5 kịch bản |

Các kịch bản đầu-cuối và hiệu năng cần backend đang chạy ở `http://localhost:8080` có dữ liệu mẫu (`docker compose up -d`). Kịch bản đầu-cuối dùng Microsoft Edge (đổi bằng biến `BROWSER`), chụp ảnh từng bước vào `e2e/screenshots*/` (không đưa lên Git). Chúng ghi dữ liệu thử vào database và cần còn giờ khám trống trong ngày: chạy ban ngày, trước 21:00 với kịch bản của app.

Ở mỗi lần đẩy code, GitHub Actions (`.github/workflows/ci.yml`) chạy kiểm thử backend, web (lint, đơn vị, build) và app (kiểu, lint, đơn vị). Các kịch bản đầu-cuối chạy trên máy.

## 2. Kịch bản kiểm thử chức năng cả hệ thống

Bệnh nhân thao tác trên app (khổ điện thoại), nhân viên thao tác trên web, cùng một lượt khám (`web/e2e/he-thong.mjs`).

| Mã | Kịch bản | Kết quả mong đợi | Kết quả |
|---|---|---|---|
| HT01 | Bệnh nhân đăng ký bằng số điện thoại, tạo hồ sơ trên app | Nhắn tin xác minh tới phòng khám, app tự sang bước đặt mật khẩu, tạo tài khoản, vào trang chủ | Đạt |
| HT02 | Bệnh nhân đặt lịch hôm nay trên app | Lịch ở trạng thái chờ xác nhận | Đạt |
| HT03 | Lịch vừa đặt hiện ở việc cần làm của lễ tân | Hiện trong tối đa 25 giây, không tải lại | Đạt |
| HT04 | Hai lễ tân cùng bấm "Đã xác nhận" một lịch | Một người thành công, người kia được báo; bệnh nhân nhận một thông báo | Đạt |
| HT05 | App sau khi lịch được xác nhận | Hiện nút mở mã check-in | Đạt |
| HT06 | App mở mã QR, lễ tân quét trên web | Web cấp số; app tự hiện đúng số thứ tự | Đạt |
| HT07 | Điều dưỡng đo sinh hiệu | App hiện bước đo sinh hiệu đã xong | Đạt |
| HT08 | Bác sĩ mở hai thẻ cùng bấm "Gọi vào phòng" | Chỉ gọi một người, thẻ kia được báo | Đạt |
| HT09 | App sau khi bác sĩ gọi | Hiện "Đến lượt bạn" | Đạt |
| HT10 | Bác sĩ khám, chỉ định công thức máu, cho đi xét nghiệm | App hiện bước xét nghiệm, "Quay lại phòng" | Đạt |
| HT11 | Điều dưỡng nhập kết quả kèm PDF, bác sĩ duyệt | Bệnh nhân thấy kết quả và tệp | Đạt |
| HT12 | Bác sĩ gọi lại, kê đơn, ký, kết thúc khám | App hiện hóa đơn cần trả | Đạt |
| HT13 | Bệnh nhân trả trên app đúng lúc thu ngân bấm thu | Hóa đơn thu đủ đúng một lần | Đạt |
| HT14 | App xem chi tiết lượt khám, gửi góp ý | Có kết luận, kết quả, tệp, đơn thuốc; gửi được góp ý | Đạt |
| HT15 | Quản lý xem phản hồi, thu chi | Thấy góp ý và giao dịch vừa phát sinh | Đạt |
| HT16 | Bệnh nhân xin hủy trên app, lễ tân từ chối | App báo chưa được duyệt, cho gửi lại | Đạt |

## 3. Kịch bản đầu-cuối riêng từng phần

**Web (`web/e2e/run.mjs`, 21 bước):** đăng nhập sai báo lỗi; lễ tân thấy việc cần làm, gọi xác nhận, đặt lịch hộ có tạo hồ sơ mới, check-in bằng QR và thủ công; điều dưỡng đo sinh hiệu, thêm dị ứng, cảnh báo sốt cao; bác sĩ gọi số, ghi phiếu khám, chỉ định; điều dưỡng nhập kết quả kèm PDF; bác sĩ trả lại, điều dưỡng sửa và gửi lại, bác sĩ duyệt; bác sĩ kê đơn (phải xác nhận dị ứng mới ký được), kết thúc khám; lễ tân thu tiền, tính tiền trả lại, báo bệnh nhân bỏ về; quản lý hoàn tiền một phần, xem báo cáo và lịch, tạo nhân viên; nhân viên mới phải đổi mật khẩu tạm.

**Ứng dụng (`mobile/e2e/run.mjs`, 28 bước):** khách chưa đăng nhập xem chuyên khoa, bác sĩ, giờ trống; khách chọn giờ trước rồi đăng ký (hoặc đăng nhập) thì quay lại đúng giờ đã chọn để gửi; đăng ký 4 bước, thêm hồ sơ người thân, đặt lịch 3 bước, trang chủ theo từng tình huống (chờ xác nhận, đã xác nhận, đang chờ, đến lượt, đi xét nghiệm, chờ thanh toán, đã thanh toán), thông báo, mã QR, chi tiết lượt khám, mở tệp PDF (người khác không tải được), góp ý, hồ sơ sức khỏe che số CCCD, đặt lịch tái khám, xin hủy (đang chờ, bị từ chối), đăng xuất, đăng nhập lại, quên mật khẩu.

## 4. Kiểm thử backend theo nhóm

| Lớp | Số ca | Nội dung |
|---|---|---|
| `BackendApplicationTests` | 6 | Lỗi trả đúng định dạng tiếng Việt (401, 400, 404); Swagger, kiểm tra sức khỏe công khai |
| `AuthTest` | 11 | Đăng ký sau khi nhắn tin xác minh; tin từ số khác hoặc cổng SMS giả không xác minh được; chữ ký cổng Android; mỗi phiên xác minh dùng một lần; tối đa 5 lần xin mã/giờ cho một số; khóa đăng nhập sau 5 lần sai; đổi, khôi phục mật khẩu đăng xuất phiên cũ |
| `MetricsTest` | 1 | Prometheus đọc được số liệu bằng METRICS_TOKEN; không token, token sai, hay đã đăng nhập quản lý đều bị chặn |
| `RateLimitTest` | 2 | Một IP xin mã cho nhiều số bị chặn ở lần thứ 11 (429, Retry-After), IP khác không ảnh hưởng; dò mật khẩu nhiều tài khoản từ một IP bị chặn |
| `AccountAdminTest` | 6 | Mật khẩu tạm phải đổi; khóa tài khoản cắt phiên ngay; chỉ quản lý quản trị tài khoản |
| `CatalogTest` | 9 | Chuyên khoa, dịch vụ, thuốc; trùng mã báo lỗi dễ hiểu; dữ liệu mẫu chạy được |
| `PatientTest` | 6 | Kiểm tra trùng hồ sơ; mã liên kết dùng một lần; quy tắc giấy tờ; hồ sơ người thân |
| `ScheduleTest` | 8 | Lịch tuần cần bác sĩ xác nhận và quản lý duyệt; không trùng phòng, bác sĩ; nghỉ, nhờ thay, đổi ca |
| `AppointmentTest` | 7 | Giữ chỗ chờ gọi xác nhận; đổi, hủy do lễ tân duyệt; vắng mặt; lọc theo ngày; hai lễ tân cùng xác nhận chỉ một thành công |
| `QueueOrderTest` | 5 | Thứ tự gọi: đúng giờ, trễ, xen người không hẹn |
| `ClinicFlowTest` | 5 | Trọn lượt khám từ check-in QR đến thanh toán; trả lại kết quả; đối chiếu dị ứng; đơn trẻ em; nhắc lịch |

`ClinicFlowTest` mở một phiên khám từ giờ hiện tại nên tự bỏ qua nếu chạy sau 22:30.

## 5. Kiểm thử hiệu năng

Máy chạy thử: Intel Xeon E3-1220 v3 (4 nhân), 17 GB RAM, Windows 10; backend và PostgreSQL trong Docker trên cùng máy. Mỗi kịch bản 15–20 giây, người dùng ảo gọi liên tục không nghỉ.

| Kịch bản | Người dùng đồng thời | Tổng yêu cầu | Yêu cầu/giây | TB (ms) | p95 (ms) | p99 (ms) | Lỗi |
|---|---|---|---|---|---|---|---|
| Khách xem chuyên khoa, bác sĩ, giờ trống | 50 | 8.397 | 417 | 120 | 277 | 444 | 0% |
| App bệnh nhân hỏi lại số thứ tự, lịch hẹn, thông báo | 100 | 8.802 | 437 | 228 | 584 | 803 | 0% |
| Web lễ tân: hàng đợi, lịch chờ xác nhận, hóa đơn | 30 | 5.385 | 268 | 112 | 229 | 302 | 0% |
| Đăng nhập (BCrypt, cố ý chậm) | 10 | 284 | 19 | 436 | 686 | 1.036 | 0% |

Tranh chấp: 20 bệnh nhân cùng đặt một giờ khám thì 1 người được giữ chỗ, 19 người được báo "đã có người đặt", không lỗi khác, xong trong 132 ms.

Số liệu chi tiết lưu ở `backend/perf/ket-qua.json` sau mỗi lần chạy.

## 6. Thống kê

| Bộ | Số ca | Đạt | Không đạt |
|---|---|---|---|
| Backend | 66 | 66 | 0 |
| Web, đơn vị | 10 | 10 | 0 |
| Web, đầu-cuối | 21 | 21 | 0 |
| Ứng dụng, đơn vị | 29 | 29 | 0 |
| Ứng dụng, đầu-cuối | 28 | 28 | 0 |
| Cả hệ thống | 16 | 16 | 0 |
| Hiệu năng | 5 | 5 | 0 |
| **Tổng** | **175** | **175** | **0** |

Kết quả lần chạy cuối ngày 16/11/2026.

## 7. Lỗi phát hiện nhờ kiểm thử

| Phần | Lỗi | Khắc phục |
|---|---|---|
| Backend | Lọc lịch hẹn theo ngày trả lỗi 500 | Ép kiểu tham số ngày trong truy vấn; thêm ca kiểm thử |
| Backend | Hai lễ tân cùng xác nhận một lịch đều thành công, bệnh nhân nhận hai thông báo | Khóa lạc quan (cột `row_version`, migration V5) cho lịch hẹn, lượt khám; ca kiểm thử chạy song song |
| Ứng dụng | Trạng thái lượt khám làm mới mỗi 60 giây, báo sai "Mời vào phòng" | Làm mới mỗi 15 giây khi có lượt khám trong ngày |
| Ứng dụng | Kết thúc khám có thể làm app ngừng làm mới trước khi nhận hóa đơn | Giữ nhịp 15 giây suốt ngày có lượt khám |
| Web | Bác sĩ đổi tab khi chưa lưu thì mất chẩn đoán đang gõ | Giữ nội dung các tab khi chuyển qua lại |
