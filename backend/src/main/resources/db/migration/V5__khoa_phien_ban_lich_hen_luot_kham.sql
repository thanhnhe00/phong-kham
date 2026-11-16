-- Khóa lạc quan: hai người cùng đổi trạng thái một lịch hẹn / lượt khám (hai lễ tân cùng xác nhận, bác sĩ bấm gọi số ở hai thẻ)
-- thì người lưu sau bị từ chối (409) thay vì ghi đè và gửi thông báo trùng.
ALTER TABLE appointments ADD COLUMN row_version INT NOT NULL DEFAULT 0;
ALTER TABLE visits ADD COLUMN row_version INT NOT NULL DEFAULT 0;
