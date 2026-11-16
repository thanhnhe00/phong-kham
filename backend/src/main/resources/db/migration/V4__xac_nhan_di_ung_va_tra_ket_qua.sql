-- Bác sĩ xác nhận đã đối chiếu dị ứng khi ký đơn cho bệnh nhân có dị ứng (lưu ghi chú).
ALTER TABLE prescriptions ADD COLUMN allergy_ack TEXT;
-- Bác sĩ trả kết quả lại cho điều dưỡng sửa: lý do trả, xóa khi điều dưỡng gửi lại.
ALTER TABLE service_results ADD COLUMN return_note TEXT;
