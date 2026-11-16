-- Gộp vai kỹ thuật viên vào điều dưỡng: điều dưỡng làm luôn xét nghiệm, nhập kết quả.
-- Giá trị SPECIALIST_STAFF vẫn còn trong kiểu role_code (Postgres không xóa được giá trị enum) nhưng không dùng nữa.
INSERT INTO user_roles (user_id, role)
SELECT user_id, 'NURSE' FROM user_roles WHERE role = 'SPECIALIST_STAFF'
ON CONFLICT DO NOTHING;
DELETE FROM user_roles WHERE role = 'SPECIALIST_STAFF';
