# Quy ước làm việc nhóm

## Nhánh và commit

- Không đẩy thẳng lên `main`. Mỗi việc một nhánh, đặt tên theo phần và việc: `be/thu-tien`, `web/man-dieu-duong`, `app/dat-lich`, `docs/bao-cao`, `fix/loc-lich-hen-theo-ngay`.
- Commit nhỏ, mỗi commit một ý, viết tiếng Việt, mở đầu bằng động từ: `Thêm màn duyệt kết quả`, `Sửa lỗi mất chẩn đoán khi đổi tab`.
- Gộp vào `main` bằng pull request, có ít nhất một bạn khác xem trước. CI (`.github/workflows/ci.yml`) phải xanh.

## Trước khi mở pull request

Chạy kiểm thử của phần mình sửa (chi tiết ở [docs/kiem-thu.md](docs/kiem-thu.md)):

| Sửa phần | Lệnh |
|---|---|
| `backend/` | `./mvnw test` (cần Docker đang chạy) |
| `web/` | `npm run lint && npm test && npm run build` |
| `mobile/` | `npm run typecheck && npm run lint && npm test` |
| Sửa luồng khám, lịch hẹn, thanh toán | Thêm `npm run e2e` (web, mobile) và `npm run e2e:he-thong` (web) với backend đang chạy |

Sửa lỗi thì thêm một ca kiểm thử bắt được lỗi đó, để lỗi không quay lại.

## Quy tắc trong mã nguồn

- **Cơ sở dữ liệu:** chỉ đổi lược đồ bằng tệp migration mới `backend/src/main/resources/db/migration/V<số>__<mo_ta>.sql`. Không sửa migration đã chạy.
- **API:** đổi API thì cập nhật [docs/api.md](docs/api.md); Swagger tự cập nhật theo code.
- **Bí mật:** không commit `.env`, mật khẩu, khóa. Biến mới thêm vào `.env.example` với giá trị mẫu.
- **Ngôn ngữ:** chú thích, thông báo lỗi, chữ trên giao diện dùng tiếng Việt.
- **Ứng dụng Expo:** thêm thư viện bằng `npx expo install`, không sửa tay thư mục `ios/`, `android/`.
- **Giao diện:** màn mới vẽ lên bảng thiết kế trước, nhóm duyệt rồi mới làm.

## Tài khoản thử

Mật khẩu `12345678`: quản lý `0900000001`, lễ tân `0900000003`, điều dưỡng `0900000004`, bác sĩ `0900000011` đến `0900000014`, bệnh nhân `0900000099`.
