# Web nhân viên

React + Vite + TypeScript, gọi backend Spring Boot (`../backend`). Một trang đăng nhập chung, mỗi người chỉ thấy menu của vai mình (một người có thể giữ nhiều vai).

| Vai | Màn hình |
|---|---|
| Lễ tân | Tổng quan (việc cần làm, tình trạng phòng), Lịch hẹn (gọi xác nhận, duyệt xin đổi/hủy từ app, báo đổi lịch, đặt lịch hộ), Tiếp đón (check-in QR, check-in thủ công, khám không hẹn), Hàng đợi, Thu ngân |
| Điều dưỡng | Đo sinh hiệu (kèm lý do khám, dị ứng, cảnh báo bất thường), Xét nghiệm (làm, nhập kết quả, tải phiếu, bị trả lại, chờ duyệt) |
| Bác sĩ | Khám bệnh (người tiếp theo, gọi số; phiếu khám, chỉ định và duyệt kết quả, kê đơn và in đơn, lịch sử), Duyệt kết quả, Lịch làm việc (xin nghỉ, nhờ khám thay, đổi ca) |
| Quản lý | Báo cáo, Lịch làm việc (tạo phiên, khám bù, duyệt nghỉ), Nhân viên (tài khoản, mật khẩu tạm, khóa), Thu chi (hoàn tiền), Danh mục, Phản hồi |
| Chung | Bệnh nhân (tìm, tạo/sửa hồ sơ, dị ứng, mã liên kết app, lịch sử khám), đổi mật khẩu |

## Chạy

```bash
npm install
npm run dev        # http://localhost:5173, /api chuyển tiếp sang http://localhost:8080 (đổi bằng API_TARGET)
```

Backend phải đang chạy (`docker compose up -d` ở thư mục gốc). Tài khoản thử (mật khẩu `12345678`): quản lý `0900000001`, lễ tân `0900000003`, điều dưỡng `0900000004`, bác sĩ `0900000011`–`0900000014`.

## Kiểm thử

```bash
npm run lint
npm test                       # kiểm thử đơn vị: ngày giờ, cảnh báo sinh hiệu, giai đoạn xét nghiệm, kê đơn, chọn phiên
npm run build && npm run e2e   # kiểm thử đầu-cuối với backend thật, ảnh chụp ở e2e/screenshots/
```

Kiểm thử đầu-cuối (`e2e/run.mjs`) mở bản build bằng Microsoft Edge (đổi bằng biến `BROWSER`). Lễ tân, điều dưỡng, bác sĩ, quản lý thao tác qua giao diện trên một lượt khám: xác nhận lịch đặt trên app, đặt lịch hộ, check-in QR, đo sinh hiệu (dị ứng, sốt cao), gọi số, khám, chỉ định, nhập kết quả kèm PDF, trả lại rồi duyệt, kê đơn (phải xác nhận dị ứng), kết thúc khám, thu tiền, bỏ về, hoàn tiền, tạo nhân viên và đổi mật khẩu tạm. Phía bệnh nhân (đăng ký, đặt lịch, lấy mã QR) gọi thẳng API.

Kiểm thử cả hệ thống cùng lúc (`npm run e2e:he-thong`, cần thêm `npx expo export --platform web` trong `mobile/`): bệnh nhân thao tác trên app, nhân viên trên web, cùng một lượt khám. Kiểm tra app tự cập nhật khi nhân viên thao tác (số thứ tự, đến lượt, đi xét nghiệm, hóa đơn) và ba tình huống bấm cùng lúc: hai lễ tân cùng xác nhận một lịch, bác sĩ bấm gọi số ở hai thẻ, bệnh nhân trả trên app đúng lúc thu ngân bấm thu. Ảnh chụp ở `e2e/screenshots-he-thong/`.

Các kịch bản cần một bác sĩ còn ít nhất 2 giờ trống trong ngày: nó thử lần lượt các bác sĩ có sẵn, mở thêm phiên khám bù, và nếu hết chỗ thì tạo một bác sĩ thử nghiệm. Mỗi lần chạy ghi thêm dữ liệu thử vào database, nên chạy trước 23:30.

## Cấu trúc

```
src/App.tsx            định tuyến, chặn trang theo vai
src/layout/shell.tsx   khung trang: menu theo vai, tìm bệnh nhân
src/pages/             auth, patients, reception/*, nurse/*, doctor/*, admin/*
src/components/        ui (nút, bảng, ô nhập, thẻ, cuống vé số thứ tự), chọn bệnh nhân, chọn giờ, nhập kết quả
src/lib/               gọi API, đăng nhập, định dạng giờ Việt Nam, nhãn trạng thái, giai đoạn xét nghiệm
```

Màn hình trong ngày (hàng đợi, sinh hiệu, xét nghiệm, thu ngân) tự làm mới mỗi 15 giây.
