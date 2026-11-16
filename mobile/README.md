# App bệnh nhân

Expo SDK 58 + Expo Router, gọi backend Spring Boot (`../backend`). Bệnh nhân đặt lịch, xem lịch hẹn, mở mã check-in, theo dõi số thứ tự trong ngày khám, xem kết quả, đơn thuốc, hóa đơn và quản lý hồ sơ người thân.

## Chạy

```bash
npm install
npx expo start          # quét QR bằng Expo Go, hoặc bấm w để mở bản web
```

Backend phải đang chạy (`docker compose up -d` ở thư mục gốc). Địa chỉ API:

| Nơi chạy | Cấu hình |
|---|---|
| Bản web (`w`) | Tự gọi `/api`, Metro chuyển tiếp sang `http://localhost:8080` (`metro.config.js`) |
| Máy ảo Android | Mặc định `http://10.0.2.2:8080/api` (`app.json` → `extra.apiUrl`) |
| Điện thoại thật | Sửa `extra.apiUrl` thành IP LAN của máy chạy backend, hoặc đặt `EXPO_PUBLIC_API_URL` |

Tài khoản thử: `0900000099` / `12345678` (có sẵn hồ sơ BN000001). Đăng ký mới: app hiện nội dung `PKXM <mã>` để nhắn tới số phòng khám; điện thoại làm cổng SMS chuyển tin về backend (xem README gốc, mục "Xác minh số điện thoại và chống spam", có cách giả lập bằng `curl`).

## Kiểm thử

```bash
npm run typecheck       # TypeScript
npm run lint            # ESLint của Expo
npm test                # kiểm thử đơn vị: ngày giờ, tiền, luật trang chủ, lịch tháng, mã QR
npx expo export --platform web && npm run e2e   # kiểm thử đầu-cuối với backend thật
```

Kiểm thử đầu-cuối (`e2e/run.mjs`) mở bản web bằng Microsoft Edge ở kích thước điện thoại (đổi trình duyệt bằng biến `BROWSER`). Bệnh nhân thao tác qua giao diện. Lễ tân, điều dưỡng, bác sĩ và quản lý thao tác qua API để đưa lượt khám qua đủ các trạng thái. Kịch bản gồm 26 bước, từ đăng ký, đặt lịch, xác nhận, check-in bằng QR, chờ khám, xét nghiệm, khám xong, thanh toán, mở tệp kết quả PDF, đến đặt lịch tái khám, xin hủy (đang chờ, bị từ chối) và quên mật khẩu. Ảnh chụp từng bước lưu ở `e2e/screenshots/`. Kịch bản mở thêm một phiên khám bù trong ngày, nên cần chạy trong khoảng 06:00 – 21:00.

## Cấu trúc

```
src/app/(auth)/        đăng nhập, tạo tài khoản, quên mật khẩu
src/app/(app)/(tabs)/  Trang chủ, Lịch hẹn, Hồ sơ sức khỏe, Tài khoản
src/app/(app)/         đặt lịch (3 bước), chi tiết lịch hẹn, xin đổi giờ, mã check-in,
                       chi tiết lượt khám, hóa đơn, thông báo, tạo/liên kết hồ sơ, đổi mật khẩu
src/components/        giao diện dùng chung: phiếu hẹn, dải đầu trang, lịch chọn giờ, mã QR...
src/lib/               gọi API, đăng nhập, định dạng, luật trang chủ (home-state.ts), kiểu dữ liệu
```

Trang chủ đổi nội dung theo tình huống (`src/lib/home-state.ts`):

| Thứ tự ưu tiên | Tình huống | Nội dung chính |
|---|---|---|
| 1 | Đã check-in hôm nay | Số thứ tự và số người phía trước, rồi bước xét nghiệm, rồi hóa đơn khi khám xong |
| 2 | Hôm nay có lịch, chưa check-in | Phiếu hẹn kèm mã check-in; báo trễ, giữ lịch đến 15 phút sau giờ hẹn |
| 3 | Có lịch sắp tới | Phiếu hẹn gần nhất (chờ xác nhận / đã xác nhận / đổi bác sĩ) |
| 4 | Không có lịch | Mời đặt lịch, hoặc đặt lịch tái khám nếu bác sĩ đã hẹn |

Việc cần làm luôn xếp theo thứ tự: thanh toán, rồi đặt lịch tái khám, rồi xem kết quả mới.
