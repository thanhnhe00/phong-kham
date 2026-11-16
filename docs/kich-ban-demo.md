# Kịch bản demo khi bảo vệ

Đi trọn một lượt khám, mỗi vai mở trên một cửa sổ riêng để hội đồng thấy dữ liệu chảy qua lại giữa app và web. Thời lượng khoảng 12–15 phút.

## Chuẩn bị (trước buổi bảo vệ)

1. Làm sạch database và nạp dữ liệu mẫu (xóa toàn bộ dữ liệu thử):
   ```bash
   docker compose down -v        # xóa database và tệp cũ
   docker compose up -d --build  # Flyway tạo bảng, SEED_DEMO=true nạp dữ liệu mẫu
   ```
2. Chạy web: `cd web && npm run dev` (http://localhost:5173).
3. Chạy app: `cd mobile && npx expo start`, mở bằng Expo Go trên điện thoại cùng mạng Wi-Fi, đặt `EXPO_PUBLIC_API_URL=http://<IP máy>:8080/api`. Dự phòng: bấm `w` để mở app trên trình duyệt, thu nhỏ khung cho giống điện thoại.
4. Mở sẵn 4 cửa sổ trình duyệt ẩn danh (mỗi vai một phiên đăng nhập): lễ tân `0900000003`, điều dưỡng `0900000004`, bác sĩ `0900000011`, quản lý `0900000001`. Mật khẩu đều là `12345678`.
5. Bảo đảm hôm nay BS Nguyễn Văn An còn giờ trống (dữ liệu mẫu có lịch thứ Hai đến thứ Sáu, sáng 7:30–11:30, chiều 13:30–17:00). Bảo vệ ngoài giờ đó thì quản lý vào Lịch làm việc, bấm "Thêm ca khám bù" cho BS An trước giờ demo.
6. Chuẩn bị một tệp PDF bất kỳ làm phiếu kết quả xét nghiệm.

## Các bước

| # | Ai | Thao tác | Điểm cần nói |
|---|---|---|---|
| 1 | Bệnh nhân (app) | Đăng ký bằng số điện thoại mới, bấm "Mở tin nhắn đã soạn sẵn" và gửi `PKXM <mã>` tới số phòng khám, app tự sang bước đặt mật khẩu, tạo hồ sơ | Xác minh chính chủ bằng số người gửi; phòng khám không tốn tiền tin nhắn. Không có điện thoại làm cổng SMS thì giả lập tin đến bằng lệnh `curl` trong README |
| 2 | Bệnh nhân | Đặt lịch: Nội tổng quát, BS An, giờ gần nhất hôm nay, ghi triệu chứng | Lịch là yêu cầu giữ chỗ, chờ lễ tân gọi xác nhận |
| 3 | Lễ tân (web) | Tổng quan thấy lịch mới trong "Việc cần làm", vào Lịch hẹn, bấm "Đã xác nhận" | Gọi tối đa 3 lần không nghe máy vẫn giữ lịch, không tự hủy |
| 4 | Bệnh nhân | Trang chủ đổi sang "đã xác nhận", bấm "Mở mã check-in" | Mã QR dùng một lần, chỉ trong ngày khám |
| 5 | Lễ tân | Tiếp đón: quét mã QR (hoặc check-in thủ công trong bảng) | App tự hiện số thứ tự và số người phía trước |
| 6 | Điều dưỡng (web) | Đo sinh hiệu: huyết áp 130/85, nhiệt độ 39,2 | Ô nhiệt độ chuyển đỏ, cảnh báo báo bác sĩ; ca cấp cứu không xếp hàng |
| 7 | Bác sĩ (web) | Khám bệnh: thấy người tiếp theo kèm cảnh báo sốt, bấm "Gọi vào phòng", "Bắt đầu khám" | App hiện "Đến lượt bạn"; thứ tự gọi do hệ thống xếp (đúng giờ, trễ, xen người không hẹn) |
| 8 | Bác sĩ | Ghi chẩn đoán J03.9 Viêm amidan cấp, chỉ định Xét nghiệm công thức máu, "Cho đi xét nghiệm" | App hiện bước xét nghiệm; giá chốt tại lúc chỉ định |
| 9 | Điều dưỡng | Xét nghiệm: "Đã làm, nhập kết quả", tải tệp PDF, "Gửi bác sĩ duyệt" | |
| 10 | Bác sĩ | Duyệt kết quả: thử "Trả lại điều dưỡng sửa" kèm lý do, điều dưỡng gửi lại, bác sĩ "Duyệt kết quả" | Bệnh nhân chỉ thấy kết quả sau khi bác sĩ duyệt |
| 11 | Bác sĩ | Gọi lại vào phòng, kê Paracetamol; nếu bệnh nhân có dị ứng thì phải tích xác nhận đối chiếu; ký đơn, "In đơn" | Kiểm tra theo Thông tư 26/2025: tối đa 30 ngày, CCCD, cân nặng trẻ em |
| 12 | Bác sĩ | Phiếu khám: "Ký và kết thúc khám" | Chỉ kết thúc được khi đủ điều kiện; hệ thống lập một hóa đơn cho cả lượt khám |
| 13 | Bệnh nhân | Trang chủ hiện hóa đơn; xem chi tiết lượt khám, mở tệp kết quả, gửi góp ý | |
| 14 | Lễ tân hoặc bệnh nhân | Thu ngân bấm thu, hoặc bệnh nhân bấm "Trả trên app" | Hai bên cùng bấm thì chỉ thu một lần |
| 15 | Quản lý (web) | Báo cáo, Thu chi (thấy giao dịch, thử hoàn tiền có lý do), Phản hồi (thấy góp ý vừa gửi), Lịch làm việc | |

## Nếu còn thời gian

- Bệnh nhân xin hủy một lịch khác trên app; lễ tân từ chối ở tab "Xin đổi / hủy"; app báo chưa được duyệt.
- Bác sĩ xin nghỉ một buổi ở Lịch làm việc; quản lý duyệt; lịch hẹn của buổi đó sang "Cần báo đổi lịch" ở màn lễ tân.
- Quản lý tạo tài khoản nhân viên mới; đăng nhập bằng mật khẩu tạm thì bị bắt đổi mật khẩu.
- Chạy `npm run e2e:he-thong` để hội đồng xem kiểm thử tự động thao tác app và web cùng lúc.

## Câu hỏi hay gặp

| Câu hỏi | Trả lời ngắn |
|---|---|
| App biết đến lượt bằng cách nào? | App hỏi lại máy chủ mỗi 15 giây trong ngày khám; hướng phát triển là đẩy tức thời bằng WebSocket |
| Hai người cùng thao tác một lúc thì sao? | Khóa dòng khi giữ chỗ và khi thu tiền, khóa lạc quan trên lịch hẹn và lượt khám: người sau được báo, không ghi đè (có kiểm thử) |
| Sao lễ tân không sửa thứ tự gọi? | Để công bằng; ca nặng thì cảnh báo sinh hiệu cho bác sĩ thấy, ca cấp cứu xử lý ngoài hàng đợi |
| Dữ liệu bệnh án có sửa được không? | Đã ký thì không ghi đè, chỉ lập bản điều chỉnh và lưu bản cũ |
| Hệ thống chịu tải bao nhiêu? | 100 người dùng đồng thời gọi liên tục: p95 dưới 0,6 giây, không lỗi (docs/kiem-thu.md mục 5) |
