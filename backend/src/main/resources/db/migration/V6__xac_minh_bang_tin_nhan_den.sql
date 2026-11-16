-- Xác minh số điện thoại bằng tin nhắn NGƯỜI DÙNG gửi đến số của phòng khám (thay cho phòng khám gửi mã đi).
-- verified_at: lúc cổng SMS báo đã nhận tin đúng mã từ đúng số; used_at: lúc phiên xác minh đã được dùng
-- để đăng ký hoặc đặt lại mật khẩu (mỗi phiên chỉ dùng một lần).
ALTER TABLE phone_verifications ADD COLUMN used_at TIMESTAMPTZ;
CREATE INDEX idx_phone_verify_created ON phone_verifications(phone, created_at);
