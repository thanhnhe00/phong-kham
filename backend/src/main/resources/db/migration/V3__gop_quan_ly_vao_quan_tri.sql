-- Một cơ sở: gộp vai Quản lý (MANAGER) vào Quản trị (ADMIN), hiển thị chung là "Quản lý".
-- Giá trị MANAGER vẫn còn trong kiểu role_code (Postgres không xóa được giá trị enum) nhưng không dùng nữa.
INSERT INTO user_roles (user_id, role)
SELECT user_id, 'ADMIN' FROM user_roles WHERE role = 'MANAGER'
ON CONFLICT DO NOTHING;
DELETE FROM user_roles WHERE role = 'MANAGER';
