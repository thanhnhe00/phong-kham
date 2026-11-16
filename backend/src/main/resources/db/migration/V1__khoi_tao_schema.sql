-- Clinic Booking - PostgreSQL 16
-- Phạm vi giai đoạn 1: một phòng khám, khám ngoại trú, kê đơn (không bán thuốc).
-- Đã bỏ khỏi giai đoạn 1: kho vật tư, chat, backup_jobs, business_documents, OTP mỗi lần đăng nhập.
-- Quy ước: khóa chính BIGINT IDENTITY; mã nghiệp vụ hiển thị (appointment_code, visit_code...) có UNIQUE riêng;
-- mọi mốc thời gian là TIMESTAMPTZ (giờ VN do backend quy đổi khi hiển thị).
-- Quy tắc DB tự chặn được thì đặt ở DB (CHECK, UNIQUE có điều kiện, EXCLUDE); phần còn lại ghi ở cuối file cho backend.

CREATE EXTENSION IF NOT EXISTS btree_gist; -- cho EXCLUDE chống trùng phòng/trùng giờ bác sĩ

-- ============================================================
-- KIỂU LIỆT KÊ
-- ============================================================
CREATE TYPE role_code            AS ENUM ('PATIENT','RECEPTIONIST','NURSE','DOCTOR','SPECIALIST_STAFF','MANAGER','ADMIN');
CREATE TYPE user_status          AS ENUM ('PENDING','ACTIVE','LOCKED','DISABLED');
CREATE TYPE sex                  AS ENUM ('MALE','FEMALE','OTHER','UNKNOWN');
CREATE TYPE identity_type        AS ENUM ('CCCD','PASSPORT','PERSONAL_ID','OTHER','NOT_PROVIDED');
CREATE TYPE account_relationship AS ENUM ('SELF','PARENT','LEGAL_GUARDIAN','FAMILY_MEMBER','OTHER');
CREATE TYPE verify_purpose       AS ENUM ('REGISTER','RESET_PASSWORD','CHANGE_PHONE');
CREATE TYPE employment_status    AS ENUM ('ACTIVE','ON_LEAVE','SUSPENDED','RESIGNED');
CREATE TYPE history_kind         AS ENUM ('EDUCATION','WORK');
CREATE TYPE service_category     AS ENUM ('CONSULTATION','LAB','IMAGING','ULTRASOUND','PROCEDURE','OTHER');
CREATE TYPE template_status      AS ENUM ('DRAFT','DOCTOR_CONFIRMED','APPROVED','INACTIVE');
CREATE TYPE session_status       AS ENUM ('DRAFT','PUBLISHED','CLOSED','CANCELLED');
CREATE TYPE session_change_type  AS ENUM ('LEAVE','REPLACEMENT','SWAP');
CREATE TYPE request_status       AS ENUM ('PENDING_COLLEAGUE','PENDING_MANAGER','APPROVED','REJECTED','WITHDRAWN');
CREATE TYPE slot_status          AS ENUM ('AVAILABLE','HELD','BOOKED','BLOCKED');
CREATE TYPE booking_channel      AS ENUM ('APP_REQUEST','PHONE','COUNTER');
-- Lịch hẹn chỉ sống tới lúc check-in; từ đó trạng thái nằm ở visits (một nguồn trạng thái cho mỗi giai đoạn).
-- Yêu cầu đổi/hủy KHÔNG là trạng thái: là dòng PENDING trong appointment_change_requests.
CREATE TYPE appointment_status   AS ENUM ('PENDING_CONFIRMATION','CONFIRMED','NEEDS_RECONFIRMATION','CHECKED_IN','CANCELLED','NO_SHOW');
CREATE TYPE change_request_type  AS ENUM ('RESCHEDULE','CANCEL');
CREATE TYPE simple_request_status AS ENUM ('PENDING','APPROVED','REJECTED');
CREATE TYPE contact_channel      AS ENUM ('PHONE','SMS','IN_APP');
CREATE TYPE contact_direction    AS ENUM ('OUTBOUND','INBOUND');
CREATE TYPE contact_purpose      AS ENUM ('CONFIRM_BOOKING','DOCTOR_CHANGE','RESCHEDULE','CANCEL','FOLLOW_UP','PAYMENT','OTHER');
CREATE TYPE contact_result       AS ENUM ('REACHED','NO_ANSWER','PATIENT_AGREED','PATIENT_DECLINED','CALLBACK_REQUESTED','FAILED','OTHER');
CREATE TYPE visit_source         AS ENUM ('APPOINTMENT','WALK_IN');
CREATE TYPE checkin_method       AS ENUM ('QR_RECEPTION','QR_SELF','MANUAL');
CREATE TYPE queue_group          AS ENUM ('ON_TIME','LATE','WALK_IN');
CREATE TYPE visit_status         AS ENUM ('WAITING','CALLED','IN_PROGRESS','AWAITING_RESULTS','COMPLETED','LEFT','CANCELLED');
CREATE TYPE record_status        AS ENUM ('DRAFT','SIGNED','AMENDED');
CREATE TYPE diagnosis_type       AS ENUM ('PRIMARY','SECONDARY','DIFFERENTIAL');
CREATE TYPE allergy_severity     AS ENUM ('MILD','MODERATE','SEVERE','UNKNOWN');
CREATE TYPE order_status         AS ENUM ('OPEN','PARTIALLY_DONE','COMPLETED','CANCELLED');
CREATE TYPE order_item_status    AS ENUM ('ORDERED','IN_PROGRESS','PERFORMED','REFUSED','CANCELLED');
CREATE TYPE performance_status   AS ENUM ('DONE','VOIDED');
CREATE TYPE result_status        AS ENUM ('DRAFT','SUBMITTED','REVIEWED','AMENDED');
CREATE TYPE drug_class           AS ENUM ('NORMAL','ADDICTIVE','PSYCHOTROPIC','PRECURSOR','TOXIC','ANTIBIOTIC');
CREATE TYPE prescription_status  AS ENUM ('DRAFT','SIGNED','REPLACED','CANCELLED');
CREATE TYPE invoice_status       AS ENUM ('UNPAID','PARTIALLY_PAID','PAID','REFUNDED','CANCELLED');
CREATE TYPE invoice_item_type    AS ENUM ('CONSULTATION','SERVICE','OTHER');
CREATE TYPE payment_kind         AS ENUM ('PAYMENT','REFUND');
CREATE TYPE payment_method       AS ENUM ('CASH','BANK_TRANSFER','ONLINE_SIMULATED','OTHER');
CREATE TYPE payment_status       AS ENUM ('PENDING','SUCCESS','FAILED');
CREATE TYPE file_category        AS ENUM ('EMR_ATTACHMENT','SCAN','PHOTO','LAB_FILE','IMAGING','ULTRASOUND','AVATAR','OTHER');
CREATE TYPE file_visibility      AS ENUM ('STAFF_ONLY','PATIENT_VISIBLE','PUBLIC');
CREATE TYPE notification_channel AS ENUM ('IN_APP','PUSH','SMS');
CREATE TYPE delivery_status      AS ENUM ('QUEUED','SENT','FAILED','SUPPRESSED');
CREATE TYPE feedback_status      AS ENUM ('NEW','IN_REVIEW','RESOLVED','CLOSED');

-- ============================================================
-- CẤU HÌNH PHÒNG KHÁM
-- ============================================================
-- Thông tin phòng khám, giờ mở cửa, luật hàng đợi (walk_in_every...), mã liên thông cơ sở (mô phỏng)...
CREATE TABLE clinic_settings (
  key        VARCHAR(80) PRIMARY KEY,
  value      JSONB NOT NULL,
  updated_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ngày nghỉ/lễ: không sinh phiên khám từ lịch mẫu vào các ngày này
CREATE TABLE clinic_closures (
  closed_date DATE PRIMARY KEY,
  reason      VARCHAR(200) NOT NULL
);

-- ============================================================
-- TÀI KHOẢN VÀ VAI TRÒ
-- ============================================================
CREATE TABLE users (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  phone               VARCHAR(20) NOT NULL UNIQUE,  -- tên đăng nhập; chỉ định danh TÀI KHOẢN, không định danh bệnh nhân
  email               VARCHAR(190) UNIQUE,
  password_hash       VARCHAR(255) NOT NULL,
  status              user_status NOT NULL DEFAULT 'PENDING',
  phone_verified_at   TIMESTAMPTZ,
  token_version       INT NOT NULL DEFAULT 0,       -- tăng khi khóa/đổi mật khẩu để hủy mọi phiên cũ
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  failed_logins       INT NOT NULL DEFAULT 0,
  locked_until        TIMESTAMPTZ,
  last_login_at       TIMESTAMPTZ,
  password_changed_at TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Một tài khoản có thể nhiều vai trò (lễ tân kiêm thu ngân, quản lý kiêm admin)
CREATE TABLE user_roles (
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role        role_code NOT NULL,
  assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role)
);

-- Xác minh số điện thoại khi đăng ký/quên mật khẩu (OTP gửi đi hoặc tin nhắn gửi đến đều dùng được)
CREATE TABLE phone_verifications (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  phone       VARCHAR(20) NOT NULL,
  purpose     verify_purpose NOT NULL,
  code_hash   VARCHAR(255) NOT NULL,
  attempts    SMALLINT NOT NULL DEFAULT 0,
  expires_at  TIMESTAMPTZ NOT NULL,
  verified_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_phone_verify ON phone_verifications(phone, purpose, expires_at);

CREATE TABLE push_tokens (
  token      VARCHAR(255) PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform   VARCHAR(20),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_user ON push_tokens(user_id);

-- ============================================================
-- NHÂN SỰ, CHUYÊN KHOA, PHÒNG
-- ============================================================
CREATE TABLE specialties (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code        VARCHAR(30) NOT NULL UNIQUE,
  name        VARCHAR(120) NOT NULL UNIQUE,
  description TEXT,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE rooms (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code         VARCHAR(30) NOT NULL UNIQUE,   -- vd. P101
  name         VARCHAR(120) NOT NULL,
  specialty_id BIGINT REFERENCES specialties(id), -- phòng chuyên dùng của khoa (nếu có)
  is_active    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE employees (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id           BIGINT NOT NULL UNIQUE REFERENCES users(id),
  employee_code     VARCHAR(40) NOT NULL UNIQUE,
  full_name         VARCHAR(160) NOT NULL,
  job_title         VARCHAR(100),
  employment_status employment_status NOT NULL DEFAULT 'ACTIVE',
  hired_at          DATE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE employee_specialties (
  employee_id  BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
  specialty_id BIGINT NOT NULL REFERENCES specialties(id),
  is_primary   BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (employee_id, specialty_id)
);
CREATE UNIQUE INDEX uq_emp_primary_specialty ON employee_specialties(employee_id) WHERE is_primary;

-- Hồ sơ hành nghề + hồ sơ công khai của bác sĩ (trang danh sách bác sĩ trên app)
CREATE TABLE doctor_profiles (
  employee_id         BIGINT PRIMARY KEY REFERENCES employees(id) ON DELETE CASCADE,
  academic_title      VARCHAR(60),          -- ThS.BS, BSCKII...
  practice_license_no VARCHAR(60) NOT NULL UNIQUE, -- số chứng chỉ/giấy phép hành nghề, in trên đơn
  practice_scope      VARCHAR(255),         -- phạm vi hoạt động chuyên môn
  prescriber_code     VARCHAR(60) UNIQUE,   -- mã liên thông người kê đơn (mô phỏng)
  practice_since      SMALLINT CHECK (practice_since BETWEEN 1950 AND 2100),
  bio                 TEXT,
  expertise           TEXT,
  avatar_file_id      BIGINT,               -- FK thêm sau khi tạo document_files
  is_public           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE doctor_history (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  employee_id BIGINT NOT NULL REFERENCES doctor_profiles(employee_id) ON DELETE CASCADE,
  kind        history_kind NOT NULL,
  from_year   SMALLINT NOT NULL,
  to_year     SMALLINT,
  title       VARCHAR(200) NOT NULL,
  place       VARCHAR(200) NOT NULL,
  CHECK (to_year IS NULL OR to_year >= from_year)
);
CREATE INDEX idx_doctor_history ON doctor_history(employee_id);

-- ============================================================
-- BỆNH NHÂN
-- ============================================================
CREATE TABLE patients (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  patient_code    VARCHAR(40) NOT NULL UNIQUE,
  full_name       VARCHAR(160) NOT NULL,
  date_of_birth   DATE NOT NULL,           -- cần để tính tuổi/tháng tuổi (đơn thuốc trẻ < 72 tháng)
  sex             sex NOT NULL DEFAULT 'UNKNOWN',
  identity_type   identity_type NOT NULL DEFAULT 'NOT_PROVIDED',
  identity_number VARCHAR(40),
  phone           VARCHAR(20),             -- không unique: cả nhà có thể dùng chung một số
  email           VARCHAR(190),
  address_line    VARCHAR(255),
  ward            VARCHAR(120),            -- phường/xã (chính quyền 2 cấp từ 01/07/2025, không còn cấp huyện)
  province        VARCHAR(120),
  occupation      VARCHAR(120),
  ethnicity       VARCHAR(60),
  nationality     VARCHAR(60) NOT NULL DEFAULT 'Việt Nam',
  link_code_hash  VARCHAR(255),            -- mã lễ tân đưa để tài khoản app tự liên kết hồ sơ
  link_code_expires_at TIMESTAMPTZ,
  created_by      BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((identity_type = 'NOT_PROVIDED') = (identity_number IS NULL))
);
CREATE UNIQUE INDEX uq_patient_identity ON patients(identity_type, identity_number) WHERE identity_number IS NOT NULL;
CREATE INDEX idx_patient_name_dob ON patients(full_name, date_of_birth);
CREATE INDEX idx_patient_phone ON patients(phone);

-- Một hồ sơ nhiều tài khoản (bố và mẹ cùng quản lý hồ sơ con); một tài khoản nhiều hồ sơ
CREATE TABLE patient_accounts (
  patient_id   BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  relationship account_relationship NOT NULL DEFAULT 'SELF',
  verified_by  BIGINT REFERENCES employees(id) ON DELETE SET NULL, -- NULL = tự liên kết bằng mã
  linked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (patient_id, user_id)
);
CREATE INDEX idx_patient_accounts_user ON patient_accounts(user_id);

-- Người đại diện hợp pháp / người đưa đến khám (trẻ em, người không tự quyết)
CREATE TABLE patient_representatives (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  patient_id      BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  full_name       VARCHAR(160) NOT NULL,
  relationship    VARCHAR(80) NOT NULL,
  phone           VARCHAR(20),
  identity_number VARCHAR(40),
  authority_note  VARCHAR(255),
  verified_by     BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  verified_at     TIMESTAMPTZ
);
CREATE INDEX idx_representatives_patient ON patient_representatives(patient_id);

CREATE TABLE patient_allergies (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  patient_id  BIGINT NOT NULL REFERENCES patients(id),
  allergen    VARCHAR(160) NOT NULL,
  reaction    TEXT,
  severity    allergy_severity NOT NULL DEFAULT 'UNKNOWN',
  recorded_by BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_allergies_patient ON patient_allergies(patient_id);

-- ============================================================
-- DANH MỤC DỊCH VỤ VÀ GIÁ
-- ============================================================
CREATE TABLE services (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code                VARCHAR(40) NOT NULL UNIQUE,
  name                VARCHAR(160) NOT NULL,
  category            service_category NOT NULL,
  description         TEXT,
  list_price          NUMERIC(12,0) NOT NULL CHECK (list_price >= 0), -- VND, không lẻ
  estimated_minutes   SMALLINT CHECK (estimated_minutes > 0),
  requires_specialist BOOLEAN NOT NULL DEFAULT FALSE,
  is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE service_specialties (
  service_id   BIGINT NOT NULL REFERENCES services(id) ON DELETE CASCADE,
  specialty_id BIGINT NOT NULL REFERENCES specialties(id),
  PRIMARY KEY (service_id, specialty_id)
);

-- Phí khám của mỗi chuyên khoa là một dịch vụ loại CONSULTATION (dòng đầu tiên trên hóa đơn)
ALTER TABLE specialties ADD COLUMN consultation_service_id BIGINT REFERENCES services(id);

-- ============================================================
-- LỊCH BÁC SĨ: lịch mẫu tuần -> phiên khám theo ngày -> slot 30 phút
-- ============================================================
-- Quản lý soạn -> bác sĩ xác nhận -> quản lý duyệt. Bác sĩ không tự tạo.
CREATE TABLE schedule_templates (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  doctor_id       BIGINT NOT NULL REFERENCES employees(id),
  specialty_id    BIGINT NOT NULL REFERENCES specialties(id),
  room_id         BIGINT NOT NULL REFERENCES rooms(id),
  weekday         SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7), -- 1 = thứ Hai
  shift_name      VARCHAR(30) NOT NULL,                             -- Sáng / Chiều / Tối
  start_time      TIME NOT NULL,
  end_time        TIME NOT NULL,
  slot_minutes    SMALLINT NOT NULL DEFAULT 30 CHECK (slot_minutes BETWEEN 5 AND 120),
  walk_in_capacity SMALLINT NOT NULL DEFAULT 0 CHECK (walk_in_capacity >= 0), -- số lượt vãng lai tối đa/phiên
  effective_from  DATE NOT NULL,
  effective_to    DATE,
  status          template_status NOT NULL DEFAULT 'DRAFT',
  created_by      BIGINT NOT NULL REFERENCES employees(id),
  doctor_confirmed_at TIMESTAMPTZ,
  approved_by     BIGINT REFERENCES employees(id),
  approved_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (start_time < end_time),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND doctor_confirmed_at IS NOT NULL))
);
CREATE INDEX idx_templates_doctor ON schedule_templates(doctor_id, weekday);

-- Phiên khám cụ thể (một bác sĩ, một phòng, một buổi của một ngày). Sinh từ lịch mẫu hoặc quản lý tạo tay.
CREATE TABLE work_sessions (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  template_id      BIGINT REFERENCES schedule_templates(id) ON DELETE SET NULL,
  doctor_id        BIGINT NOT NULL REFERENCES employees(id),   -- bác sĩ đang phụ trách (đổi khi duyệt thay thế)
  original_doctor_id BIGINT NOT NULL REFERENCES employees(id), -- bác sĩ được xếp ban đầu
  specialty_id     BIGINT NOT NULL REFERENCES specialties(id),
  room_id          BIGINT NOT NULL REFERENCES rooms(id),
  session_date     DATE NOT NULL,
  shift_name       VARCHAR(30) NOT NULL,
  starts_at        TIMESTAMPTZ NOT NULL,
  ends_at          TIMESTAMPTZ NOT NULL,
  walk_in_capacity SMALLINT NOT NULL DEFAULT 0 CHECK (walk_in_capacity >= 0),
  status           session_status NOT NULL DEFAULT 'DRAFT',
  published_by     BIGINT REFERENCES employees(id),
  published_at     TIMESTAMPTZ,
  doctor_late_minutes SMALLINT,                                -- ghi nhận bác sĩ đến trễ
  CHECK (starts_at < ends_at),
  -- Chặn trùng phòng và trùng giờ của một bác sĩ ngay trong DB
  CONSTRAINT ex_session_room   EXCLUDE USING gist (room_id   WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (status <> 'CANCELLED'),
  CONSTRAINT ex_session_doctor EXCLUDE USING gist (doctor_id WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (status <> 'CANCELLED')
);
CREATE INDEX idx_sessions_date ON work_sessions(session_date, specialty_id);

-- Xin nghỉ / nhờ người thay / đổi chéo. Người thay đồng ý -> quản lý duyệt.
CREATE TABLE session_change_requests (
  id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id            BIGINT NOT NULL REFERENCES work_sessions(id),
  request_type          session_change_type NOT NULL,
  requested_by          BIGINT NOT NULL REFERENCES employees(id),
  replacement_doctor_id BIGINT REFERENCES employees(id),
  swap_session_id       BIGINT REFERENCES work_sessions(id),   -- ca đổi lại (SWAP)
  reason                TEXT NOT NULL,
  status                request_status NOT NULL DEFAULT 'PENDING_MANAGER',
  colleague_responded_at TIMESTAMPTZ,
  manager_id            BIGINT REFERENCES employees(id),
  manager_decided_at    TIMESTAMPTZ,
  manager_note          TEXT,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (request_type <> 'REPLACEMENT' OR replacement_doctor_id IS NOT NULL),
  CHECK (request_type <> 'SWAP' OR (replacement_doctor_id IS NOT NULL AND swap_session_id IS NOT NULL)),
  CHECK (request_type <> 'LEAVE' OR (replacement_doctor_id IS NULL AND swap_session_id IS NULL))
);
CREATE INDEX idx_session_changes ON session_change_requests(session_id);
CREATE UNIQUE INDEX uq_session_open_change ON session_change_requests(session_id)
  WHERE status IN ('PENDING_COLLEAGUE','PENDING_MANAGER');

CREATE TABLE appointment_slots (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id  BIGINT NOT NULL REFERENCES work_sessions(id) ON DELETE CASCADE,
  slot_start  TIMESTAMPTZ NOT NULL,
  slot_end    TIMESTAMPTZ NOT NULL,
  status      slot_status NOT NULL DEFAULT 'AVAILABLE',
  held_until  TIMESTAMPTZ,       -- yêu cầu từ app giữ slot đến lúc này; quá hạn chưa xác nhận thì nhả
  UNIQUE (session_id, slot_start),
  CHECK (slot_start < slot_end),
  CHECK ((status = 'HELD') = (held_until IS NOT NULL))
);
CREATE INDEX idx_slots_available ON appointment_slots(status, slot_start);

-- ============================================================
-- LỊCH HẸN
-- ============================================================
CREATE TABLE appointments (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  appointment_code VARCHAR(40) NOT NULL UNIQUE,
  patient_id       BIGINT NOT NULL REFERENCES patients(id),
  slot_id          BIGINT NOT NULL REFERENCES appointment_slots(id),
  channel          booking_channel NOT NULL,
  status           appointment_status NOT NULL DEFAULT 'PENDING_CONFIRMATION',
  reason           TEXT,                        -- lý do khám bệnh nhân nhập
  requested_by_user BIGINT REFERENCES users(id) ON DELETE SET NULL, -- tài khoản app gửi yêu cầu
  created_by       BIGINT REFERENCES employees(id) ON DELETE SET NULL, -- lễ tân tạo (điện thoại/quầy)
  confirmed_by     BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  confirmed_at     TIMESTAMPTZ,
  cancelled_at     TIMESTAMPTZ,
  cancel_reason    TEXT,
  reminded_at      TIMESTAMPTZ,                 -- đã nhắc 24h
  follow_up_of_visit_id BIGINT,                 -- lịch tái khám (FK thêm sau khi tạo visits)
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (channel <> 'APP_REQUEST' OR requested_by_user IS NOT NULL),
  CHECK (status NOT IN ('CONFIRMED','CHECKED_IN') OR confirmed_at IS NOT NULL)
);
-- Một slot chỉ có một lịch hẹn còn hiệu lực (lịch đã hủy/vắng không giữ slot)
CREATE UNIQUE INDEX uq_slot_active_appointment ON appointments(slot_id)
  WHERE status IN ('PENDING_CONFIRMATION','CONFIRMED','NEEDS_RECONFIRMATION','CHECKED_IN');
CREATE INDEX idx_appointments_patient ON appointments(patient_id, created_at);
CREATE INDEX idx_appointments_status ON appointments(status);

-- Dịch vụ bệnh nhân đăng ký trước qua lễ tân (Quyết định 4). Bác sĩ xem và chuyển thành chỉ định khi khám.
CREATE TABLE appointment_services (
  appointment_id BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
  service_id     BIGINT NOT NULL REFERENCES services(id),
  note           VARCHAR(255),
  PRIMARY KEY (appointment_id, service_id)
);

-- Bệnh nhân gửi yêu cầu đổi/hủy; lễ tân xử lý (không tự hủy)
CREATE TABLE appointment_change_requests (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  appointment_id    BIGINT NOT NULL REFERENCES appointments(id),
  request_type      change_request_type NOT NULL,
  requested_slot_id BIGINT REFERENCES appointment_slots(id) ON DELETE SET NULL,
  requested_by_user BIGINT REFERENCES users(id) ON DELETE SET NULL,
  reason            TEXT,
  status            simple_request_status NOT NULL DEFAULT 'PENDING',
  handled_by        BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  handled_at        TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (request_type <> 'RESCHEDULE' OR requested_slot_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_appointment_open_request ON appointment_change_requests(appointment_id) WHERE status = 'PENDING';

-- Nhật ký liên hệ duy nhất (gọi xác nhận, báo đổi bác sĩ, nhắc thanh toán...). Tối đa 3 lần gọi do backend đếm.
CREATE TABLE contact_logs (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  patient_id     BIGINT REFERENCES patients(id) ON DELETE SET NULL,
  appointment_id BIGINT REFERENCES appointments(id) ON DELETE SET NULL,
  employee_id    BIGINT NOT NULL REFERENCES employees(id),
  channel        contact_channel NOT NULL DEFAULT 'PHONE',
  direction      contact_direction NOT NULL DEFAULT 'OUTBOUND',
  purpose        contact_purpose NOT NULL,
  phone_number   VARCHAR(20),
  result         contact_result NOT NULL,
  note           TEXT,
  contacted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (patient_id IS NOT NULL OR appointment_id IS NOT NULL)
);
CREATE INDEX idx_contact_appointment ON contact_logs(appointment_id, purpose);

-- QR chỉ cấp khi lịch CONFIRMED; DB chỉ giữ hash
CREATE TABLE appointment_qr_tokens (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE CASCADE,
  token_hash     CHAR(64) NOT NULL UNIQUE,
  expires_at     TIMESTAMPTZ NOT NULL,
  used_at        TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- LƯỢT KHÁM (check-in + hàng đợi + trạng thái khám nằm chung một bảng)
-- ============================================================
CREATE TABLE visits (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  visit_code      VARCHAR(40) NOT NULL UNIQUE,
  source          visit_source NOT NULL,
  appointment_id  BIGINT UNIQUE REFERENCES appointments(id),  -- NULL với bệnh nhân vãng lai
  patient_id      BIGINT NOT NULL REFERENCES patients(id),
  session_id      BIGINT NOT NULL REFERENCES work_sessions(id), -- phiên khám/phòng được xếp vào
  doctor_id       BIGINT NOT NULL REFERENCES employees(id),     -- bác sĩ thực khám (lưu lại, không suy từ session)
  reason          TEXT,
  companion_name  VARCHAR(160),   -- người đưa đến khám (bắt buộc với trẻ < 72 tháng khi kê đơn)
  companion_phone VARCHAR(20),
  -- check-in
  checkin_method  checkin_method NOT NULL,
  checked_in_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  checked_in_by   BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  -- hàng đợi (thứ tự do backend tính từ queue_group + checked_in_at)
  queue_number    INT NOT NULL CHECK (queue_number > 0),
  queue_group     queue_group NOT NULL,
  status          visit_status NOT NULL DEFAULT 'WAITING',
  called_at       TIMESTAMPTZ,
  started_at      TIMESTAMPTZ,
  completed_at    TIMESTAMPTZ,
  CHECK ((source = 'APPOINTMENT') = (appointment_id IS NOT NULL)),
  CHECK ((source = 'WALK_IN') = (queue_group = 'WALK_IN')),
  CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL),
  UNIQUE (session_id, queue_number)
);
CREATE INDEX idx_visits_queue ON visits(session_id, status, queue_group, checked_in_at);
CREATE INDEX idx_visits_patient ON visits(patient_id, checked_in_at);

ALTER TABLE appointments ADD CONSTRAINT fk_appointment_follow_up
  FOREIGN KEY (follow_up_of_visit_id) REFERENCES visits(id) ON DELETE SET NULL;

-- Sinh hiệu do điều dưỡng đo trước khi vào khám (có thể đo lại nhiều lần)
CREATE TABLE visit_vitals (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  visit_id         BIGINT NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
  pulse_per_min    SMALLINT CHECK (pulse_per_min BETWEEN 20 AND 300),
  temperature_c    NUMERIC(4,1) CHECK (temperature_c BETWEEN 30 AND 45),
  systolic_bp      SMALLINT CHECK (systolic_bp BETWEEN 40 AND 300),
  diastolic_bp     SMALLINT CHECK (diastolic_bp BETWEEN 20 AND 200),
  respiratory_rate SMALLINT CHECK (respiratory_rate BETWEEN 5 AND 80),
  spo2_percent     SMALLINT CHECK (spo2_percent BETWEEN 50 AND 100),
  weight_kg        NUMERIC(5,2) CHECK (weight_kg > 0),
  height_cm        NUMERIC(5,1) CHECK (height_cm > 0),
  recorded_by      BIGINT NOT NULL REFERENCES employees(id),
  recorded_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vitals_visit ON visit_vitals(visit_id, recorded_at);

-- ============================================================
-- BỆNH ÁN NGOẠI TRÚ (mỗi lượt khám một phiếu, theo bố cục mẫu bệnh án ngoại trú của Thông tư 32/2023)
-- ============================================================
CREATE TABLE emr_records (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  visit_id         BIGINT NOT NULL UNIQUE REFERENCES visits(id),
  template_code    VARCHAR(40) NOT NULL DEFAULT 'OUTPATIENT_GENERAL',
  chief_complaint  TEXT,      -- lý do vào khám
  present_illness  TEXT,      -- bệnh sử
  personal_history TEXT,      -- tiền sử bản thân
  family_history   TEXT,      -- tiền sử gia đình
  general_exam     TEXT,      -- khám toàn thân
  organ_exam       TEXT,      -- khám các bộ phận
  paraclinical_summary TEXT,  -- tóm tắt kết quả cận lâm sàng
  conclusion       TEXT,      -- kết luận
  treatment_plan   TEXT,      -- hướng điều trị
  advice           TEXT,      -- lời dặn
  follow_up_date   DATE,
  status           record_status NOT NULL DEFAULT 'DRAFT',
  version          INT NOT NULL DEFAULT 1,
  signed_by        BIGINT REFERENCES employees(id),
  signed_at        TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status = 'DRAFT' OR (signed_by IS NOT NULL AND signed_at IS NOT NULL))
);

CREATE TABLE emr_diagnoses (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  emr_id         BIGINT NOT NULL REFERENCES emr_records(id) ON DELETE CASCADE,
  icd10_code     VARCHAR(10) CHECK (icd10_code ~ '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$'),
  diagnosis_name VARCHAR(255) NOT NULL,
  diagnosis_type diagnosis_type NOT NULL DEFAULT 'PRIMARY',
  note           TEXT
);
CREATE UNIQUE INDEX uq_emr_one_primary ON emr_diagnoses(emr_id) WHERE diagnosis_type = 'PRIMARY';

-- Sửa bệnh án đã ký: lưu ảnh chụp bản cũ, không ghi đè
CREATE TABLE emr_amendments (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  emr_id           BIGINT NOT NULL REFERENCES emr_records(id),
  from_version     INT NOT NULL,
  previous_snapshot JSONB NOT NULL,   -- toàn bộ phiếu + chẩn đoán trước khi sửa
  reason           TEXT NOT NULL,
  amended_by       BIGINT NOT NULL REFERENCES employees(id),
  amended_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (emr_id, from_version)
);

-- ============================================================
-- CHỈ ĐỊNH DỊCH VỤ VÀ KẾT QUẢ
-- ============================================================
CREATE TABLE service_orders (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  visit_id   BIGINT NOT NULL REFERENCES visits(id),
  ordered_by BIGINT NOT NULL REFERENCES employees(id),
  ordered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  note       TEXT,
  status     order_status NOT NULL DEFAULT 'OPEN'
);
CREATE INDEX idx_orders_visit ON service_orders(visit_id);

CREATE TABLE service_order_items (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  service_order_id   BIGINT NOT NULL REFERENCES service_orders(id),
  service_id         BIGINT NOT NULL REFERENCES services(id),
  requested_quantity SMALLINT NOT NULL DEFAULT 1 CHECK (requested_quantity > 0),
  unit_price         NUMERIC(12,0) NOT NULL CHECK (unit_price >= 0), -- chốt giá lúc chỉ định
  status             order_item_status NOT NULL DEFAULT 'ORDERED',
  refusal_note       TEXT,
  assigned_to        BIGINT REFERENCES employees(id) ON DELETE SET NULL, -- nhân viên chuyên môn được giao
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status <> 'REFUSED' OR refusal_note IS NOT NULL)
);
CREATE INDEX idx_order_items_order ON service_order_items(service_order_id);
CREATE INDEX idx_order_items_assignee ON service_order_items(assigned_to, status);

CREATE TABLE service_performances (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_item_id BIGINT NOT NULL REFERENCES service_order_items(id),
  performed_by  BIGINT NOT NULL REFERENCES employees(id),
  performed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  quantity      SMALLINT NOT NULL DEFAULT 1 CHECK (quantity > 0),
  status        performance_status NOT NULL DEFAULT 'DONE',
  note          TEXT
);
CREATE INDEX idx_performances_item ON service_performances(order_item_id);

CREATE TABLE service_results (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  performance_id BIGINT NOT NULL UNIQUE REFERENCES service_performances(id),
  result_text    TEXT,
  conclusion     TEXT,
  status         result_status NOT NULL DEFAULT 'DRAFT',
  version        INT NOT NULL DEFAULT 1,
  entered_by     BIGINT NOT NULL REFERENCES employees(id),
  entered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_by    BIGINT REFERENCES employees(id),   -- bác sĩ duyệt; chỉ REVIEWED mới hiện cho bệnh nhân
  reviewed_at    TIMESTAMPTZ,
  CHECK (status NOT IN ('REVIEWED','AMENDED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

CREATE TABLE service_result_amendments (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  result_id        BIGINT NOT NULL REFERENCES service_results(id),
  from_version     INT NOT NULL,
  previous_snapshot JSONB NOT NULL,
  reason           TEXT NOT NULL,
  amended_by       BIGINT NOT NULL REFERENCES employees(id),
  amended_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  reconfirmed_by   BIGINT REFERENCES employees(id),
  reconfirmed_at   TIMESTAMPTZ,
  UNIQUE (result_id, from_version)
);

-- ============================================================
-- TỆP ĐÍNH KÈM (nội dung tệp ở object storage/ổ đĩa; DB giữ storage_key)
-- ============================================================
CREATE TABLE document_files (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  patient_id        BIGINT REFERENCES patients(id) ON DELETE SET NULL,
  visit_id          BIGINT REFERENCES visits(id) ON DELETE SET NULL,
  service_result_id BIGINT REFERENCES service_results(id) ON DELETE SET NULL,
  uploaded_by       BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  file_name         VARCHAR(255) NOT NULL,
  storage_key       VARCHAR(500) NOT NULL UNIQUE,
  mime_type         VARCHAR(120) NOT NULL,
  size_bytes        BIGINT NOT NULL CHECK (size_bytes > 0),
  sha256            CHAR(64),
  category          file_category NOT NULL DEFAULT 'OTHER',
  visibility        file_visibility NOT NULL DEFAULT 'STAFF_ONLY',
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_files_visit ON document_files(visit_id);
CREATE INDEX idx_files_result ON document_files(service_result_id);

ALTER TABLE doctor_profiles ADD CONSTRAINT fk_doctor_avatar
  FOREIGN KEY (avatar_file_id) REFERENCES document_files(id) ON DELETE SET NULL;

-- ============================================================
-- ĐƠN THUỐC (Thông tư 26/2025/TT-BYT) - chỉ kê đơn, không bán
-- ============================================================
CREATE TABLE medicines (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  active_ingredient VARCHAR(200) NOT NULL,  -- tên chung quốc tế (bắt buộc ghi trên đơn)
  brand_name        VARCHAR(160),           -- tên thương mại (ghi trong ngoặc)
  strength          VARCHAR(80),            -- hàm lượng/nồng độ
  dosage_form       VARCHAR(80),            -- viên nén, siro...
  default_route     VARCHAR(40),            -- uống, tiêm...
  unit              VARCHAR(30) NOT NULL,   -- viên, chai, ống
  drug_class        drug_class NOT NULL DEFAULT 'NORMAL',
  is_active         BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_medicines_ingredient ON medicines(active_ingredient);

CREATE TABLE prescriptions (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  prescription_code VARCHAR(40) NOT NULL UNIQUE,   -- mã đơn (theo định dạng Hệ thống đơn thuốc quốc gia, mô phỏng)
  visit_id          BIGINT NOT NULL REFERENCES visits(id),
  diagnosis_text    TEXT NOT NULL,                 -- chẩn đoán in trên đơn (chép từ bệnh án lúc ký)
  advice            TEXT,
  status            prescription_status NOT NULL DEFAULT 'DRAFT',
  replaces_id       BIGINT UNIQUE REFERENCES prescriptions(id), -- đơn mới thay đơn nào
  replacement_reason TEXT,
  signed_by         BIGINT REFERENCES employees(id),
  signed_at         TIMESTAMPTZ,
  national_sent_at  TIMESTAMPTZ,                   -- đã gửi liên thông (mô phỏng)
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status = 'DRAFT' OR (signed_by IS NOT NULL AND signed_at IS NOT NULL)),
  CHECK ((replaces_id IS NULL) = (replacement_reason IS NULL))
);
CREATE INDEX idx_prescriptions_visit ON prescriptions(visit_id);

CREATE TABLE prescription_items (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  prescription_id BIGINT NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
  line_no         SMALLINT NOT NULL,                -- thuốc độc phải đứng trước (backend sắp)
  medicine_id     BIGINT NOT NULL REFERENCES medicines(id),
  medicine_label  VARCHAR(255) NOT NULL,            -- chép "hoạt chất (tên TM) hàm lượng" lúc kê, giữ nguyên khi danh mục đổi
  quantity        NUMERIC(8,2) NOT NULL CHECK (quantity > 0),
  dose_per_time   VARCHAR(60) NOT NULL,             -- "1 viên"
  times_per_day   SMALLINT NOT NULL CHECK (times_per_day BETWEEN 1 AND 12),
  route           VARCHAR(40) NOT NULL,             -- đường dùng
  timing          VARCHAR(120),                     -- thời điểm dùng: sau ăn sáng, trước ngủ...
  days            SMALLINT NOT NULL CHECK (days BETWEEN 1 AND 90), -- >30 chỉ khi bệnh thuộc danh mục kê dài ngày (backend kiểm)
  note            TEXT,
  UNIQUE (prescription_id, line_no)
);

-- ============================================================
-- HÓA ĐƠN VÀ THANH TOÁN (nội bộ, không phải hóa đơn điện tử hợp pháp)
-- ============================================================
CREATE TABLE invoices (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  invoice_code    VARCHAR(40) NOT NULL UNIQUE,
  visit_id        BIGINT NOT NULL UNIQUE REFERENCES visits(id), -- một hóa đơn tổng hợp mỗi lượt khám
  subtotal        NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  discount_amount NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
  total_amount    NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
  paid_amount     NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
  status          invoice_status NOT NULL DEFAULT 'UNPAID',
  issued_by       BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  issued_at       TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (total_amount = subtotal - discount_amount)
);

CREATE TABLE invoice_items (
  id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  invoice_id            BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  item_type             invoice_item_type NOT NULL,
  service_id            BIGINT REFERENCES services(id),
  service_order_item_id BIGINT UNIQUE REFERENCES service_order_items(id), -- một mục chỉ định chỉ tính tiền một lần
  description           VARCHAR(255) NOT NULL,
  quantity              NUMERIC(8,2) NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit_price            NUMERIC(12,0) NOT NULL CHECK (unit_price >= 0),
  line_total            NUMERIC(12,0) NOT NULL CHECK (line_total >= 0),
  CHECK (item_type <> 'SERVICE' OR service_order_item_id IS NOT NULL),
  CHECK (item_type <> 'CONSULTATION' OR service_id IS NOT NULL)
);
CREATE UNIQUE INDEX uq_invoice_one_consultation ON invoice_items(invoice_id) WHERE item_type = 'CONSULTATION';

CREATE TABLE payments (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  payment_code       VARCHAR(60) NOT NULL UNIQUE,
  invoice_id         BIGINT NOT NULL REFERENCES invoices(id),
  kind               payment_kind NOT NULL DEFAULT 'PAYMENT',
  method             payment_method NOT NULL,
  amount             NUMERIC(12,0) NOT NULL CHECK (amount > 0), -- hoàn tiền cũng dương, phân biệt bằng kind
  status             payment_status NOT NULL DEFAULT 'PENDING',
  provider_reference VARCHAR(160),
  received_by        BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  paid_at            TIMESTAMPTZ,
  note               TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (status <> 'SUCCESS' OR paid_at IS NOT NULL)
);
CREATE INDEX idx_payments_invoice ON payments(invoice_id, status);

-- ============================================================
-- THÔNG BÁO, PHẢN HỒI, AUDIT
-- ============================================================
CREATE TABLE notifications (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  notification_type   VARCHAR(60) NOT NULL,  -- APPOINTMENT_CONFIRMED, REMINDER, CALLED, RESULTS_READY...
  title               VARCHAR(180) NOT NULL,
  body                TEXT NOT NULL,
  related_entity_type VARCHAR(60),
  related_entity_id   BIGINT,
  is_void             BOOLEAN NOT NULL DEFAULT FALSE, -- nhắc lịch của lịch đã đổi/hủy
  read_at             TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, read_at, created_at);

CREATE TABLE notification_deliveries (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  notification_id    BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  channel            notification_channel NOT NULL,
  status             delivery_status NOT NULL DEFAULT 'QUEUED',
  provider_reference VARCHAR(160),
  error_message      VARCHAR(500),
  attempted_at       TIMESTAMPTZ,
  UNIQUE (notification_id, channel)
);

CREATE TABLE feedback (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  patient_id BIGINT NOT NULL REFERENCES patients(id),
  visit_id   BIGINT REFERENCES visits(id) ON DELETE SET NULL,
  category   VARCHAR(80),
  content    TEXT NOT NULL,
  status     feedback_status NOT NULL DEFAULT 'NEW',
  handled_by BIGINT REFERENCES employees(id) ON DELETE SET NULL,
  handled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Mọi thao tác nhạy cảm: xem/tải/in hồ sơ, ký, sửa, đổi lịch, thanh toán
CREATE TABLE audit_logs (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
  action      VARCHAR(100) NOT NULL,   -- EMR_VIEW, FILE_DOWNLOAD, PRESCRIPTION_SIGN...
  entity_type VARCHAR(80),
  entity_id   BIGINT,
  old_value   JSONB,
  new_value   JSONB,
  ip_address  INET,
  user_agent  VARCHAR(500),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, created_at);
CREATE INDEX idx_audit_user ON audit_logs(user_id, created_at);

ALTER TABLE clinic_settings ADD CONSTRAINT fk_settings_user FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

-- ============================================================
-- DỮ LIỆU DANH MỤC TỐI THIỂU
-- ============================================================
INSERT INTO clinic_settings(key, value) VALUES
 ('clinic_info',  '{"name": null, "address": null, "phone": null, "facility_code": null}'),
 ('opening_hours','{"note": "CẦN CẤU HÌNH"}'),
 ('booking',      '{"slot_minutes": 30, "app_hold_minutes": 1440, "max_contact_attempts": 3, "no_show_after_minutes": 15}'),
 ('queue',        '{"walk_in_every": 3}');

-- ============================================================
-- QUY TẮC BACKEND PHẢI ĐẢM BẢO (DB không tự chặn được hết)
-- ============================================================
-- 1. Đặt lịch: trong một transaction, SELECT slot FOR UPDATE, chỉ nhận khi AVAILABLE (hoặc HELD hết hạn),
--    tạo appointment rồi đổi slot sang HELD (yêu cầu từ app) / BOOKED (lễ tân tạo). uq_slot_active_appointment là lưới an toàn cuối.
-- 2. Yêu cầu từ app quá held_until mà chưa xác nhận: lễ tân xử lý theo nhật ký gọi; KHÔNG tự hủy khi chưa liên hệ (tối đa 3 lần gọi).
-- 3. Duyệt thay bác sĩ: đổi work_sessions.doctor_id, mọi lịch CONFIRMED của phiên chuyển NEEDS_RECONFIRMATION;
--    lễ tân gọi từng người, đồng ý -> CONFIRMED, không đồng ý -> đổi slot. Xin nghỉ không người thay -> phiên CANCELLED.
-- 4. QR: chỉ cấp khi CONFIRMED; check-in đánh dấu used_at nguyên tử (UPDATE ... WHERE used_at IS NULL).
-- 5. NO_SHOW: tác vụ nền chuyển CONFIRMED quá no_show_after_minutes mà chưa check-in.
-- 6. Hàng đợi: ON_TIME trước LATE; cứ walk_in_every lượt đặt lịch thì xen 1 WALK_IN (tránh vãng lai chờ mãi);
--    cùng nhóm theo checked_in_at. Walk-in chỉ nhận khi số walk-in của phiên < walk_in_capacity.
-- 7. Bệnh án/kết quả/đơn đã ký: không UPDATE trực tiếp; ghi *_amendments (snapshot) rồi tăng version, hoặc kê đơn thay thế
--    (đơn cũ -> REPLACED). Lễ tân/admin không ký thay bác sĩ.
-- 8. Đơn thuốc: bệnh nhân phải có identity_number (trừ trẻ em dùng thông tin người đưa đến);
--    trẻ < 72 tháng cần cân nặng (visit_vitals) + companion_name; days > 30 chỉ với bệnh trong danh mục kê dài ngày.
-- 9. Hóa đơn: dòng CONSULTATION lấy từ specialties.consultation_service_id; dòng SERVICE chỉ cho mục PERFORMED;
--    paid_amount = tổng PAYMENT SUCCESS - tổng REFUND SUCCESS, cập nhật status trong cùng transaction.
-- 10. Bệnh nhân chỉ thấy service_results REVIEWED và document_files PATIENT_VISIBLE của hồ sơ mình liên kết qua patient_accounts.
