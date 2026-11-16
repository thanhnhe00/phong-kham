# Danh sách API

Gốc: `http://localhost:8080/api`. Chi tiết request/response và thử trực tiếp: **Swagger UI `/api/docs`**
(bấm **Authorize**, dán `token` lấy từ `/auth/login`).

Quy ước chung:
- Gửi token: header `Authorization: Bearer <token>`. Mọi API cần đăng nhập, trừ các dòng ghi **Công khai**.
- Lỗi luôn có dạng `{"message": "...", "errors": {"tenTruong": "..."}}` (`errors` chỉ có khi dữ liệu nhập sai), thông báo tiếng Việt hiển thị được ngay.
  Mã lỗi: 400 dữ liệu sai · 401 chưa đăng nhập/phiên hết hạn · 403 không có quyền · 404 không tìm thấy · 409 trùng dữ liệu · 429 thử quá nhiều lần.
- Danh sách có phân trang trả `{"items": [...], "page": 0, "size": 20, "total": 57}`; tham số `?page=0&size=20`.
- Khi tài khoản đang dùng **mật khẩu tạm**, mọi API trả 403 trừ `GET /auth/me` và `POST /auth/password/change`.

## Tài khoản (UC03)

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| POST | `/auth/phone-codes` | Công khai | Xin mã xác minh. `purpose`: `REGISTER` hoặc `RESET_PASSWORD`. Trả `code`, `smsTo` (số phòng khám), `smsBody` (`PKXM <mã>`): người dùng nhắn `smsBody` tới `smsTo` **từ chính số đã khai**. Cách nhau tối thiểu 60 giây, tối đa 5 lần/giờ cho một số |
| POST | `/auth/phone-codes/check` | Công khai | `phone`, `purpose`, `code` → `{"status": "PENDING" \| "VERIFIED"}`. App hỏi lại 3 giây/lần; sai mã 5 lần thì phải xin mã mới |
| POST | `/sms/inbound` | Cổng SMS | Điện thoại làm cổng SMS chuyển tin đến. Xác thực bằng header `X-Gateway-Token` (thân `{"from","body"}`) hoặc chữ ký Android SMS Gateway `X-Signature` + `X-Timestamp`. Đúng mã **và** đúng số người gửi thì phiên được xác minh. Trả `{"result": "VERIFIED" \| "NO_MATCH" \| ...}` |
| POST | `/auth/register` | Công khai | Bệnh nhân đăng ký: `phone`, `code`, `password` (8–72 ký tự). Số phải đã nhắn tin xác minh (trong 30 phút). Trả token |
| POST | `/auth/login` | Công khai | `phone`, `password`. Sai 5 lần thì khóa 15 phút |
| POST | `/auth/password/reset` | Công khai | Quên mật khẩu: `phone`, `code` (đã nhắn tin xác minh), `newPassword`. Đăng xuất mọi phiên cũ |

Chống spam theo IP (trả 429 kèm `Retry-After`): xin mã 10 lần/giờ, hỏi trạng thái 120 lần/10 phút, đăng ký 10 lần/giờ,
đăng nhập 30 lần/10 phút, đặt lại mật khẩu 10 lần/giờ, đặt lịch trên app 20 lần/giờ cho mỗi IP.
| POST | `/auth/password/change` | Đã đăng nhập | `currentPassword`, `newPassword`. Trả token mới, token cũ hết hiệu lực |
| GET | `/auth/me` | Đã đăng nhập | Thông tin tài khoản, vai trò, `mustChangePassword` |

## Quản trị tài khoản (UC34) — chỉ ADMIN

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/admin/users?role=&q=` | Danh sách tài khoản, lọc vai trò / số điện thoại |
| PATCH | `/admin/users/{id}/status` | `{"status": "ACTIVE" \| "LOCKED" \| "DISABLED"}`. Khóa thì đăng xuất ngay |
| POST | `/admin/users/{id}/reset-password` | Cấp mật khẩu tạm (`temporaryPassword`) |

## Nhân viên và bác sĩ (UC31) — ADMIN

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/staff?role=&status=&q=` | Danh sách nhân viên |
| GET | `/staff/{id}` | Chi tiết nhân viên |
| POST | `/staff` | Tạo nhân viên kèm tài khoản: `fullName`, `phone`, `roles`, `specialtyIds`, `primarySpecialtyId`. Trả `temporaryPassword` (chỉ hiện một lần). Bác sĩ phải có chuyên khoa |
| PUT | `/staff/{id}` | Sửa thông tin, vai trò, chuyên khoa, tình trạng làm việc |
| GET/PUT | `/staff/{id}/doctor-profile` | Hồ sơ hành nghề: số chứng chỉ hành nghề (bắt buộc, không trùng), học hàm, phạm vi chuyên môn, mã liên thông kê đơn, giới thiệu, `isPublic` |
| GET/POST | `/staff/{id}/doctor-history` | Quá trình đào tạo (`EDUCATION`) / công tác (`WORK`) |
| DELETE | `/staff/{id}/doctor-history/{historyId}` | Xóa một mục quá trình |

## Thông tin công khai (UC01, UC02)

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| GET | `/specialties` | Công khai | Chuyên khoa đang hoạt động, kèm `consultationPrice` (phí khám) |
| GET | `/services?category=` | Công khai | Dịch vụ đang áp dụng và giá |
| GET | `/doctors?specialtyId=` | Công khai | Bác sĩ có hồ sơ công khai, đang làm việc |
| GET | `/doctors/{id}` | Công khai | Hồ sơ bác sĩ: chuyên môn, giới thiệu, quá trình |

## Danh mục (UC31)

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| GET | `/specialties/all` | ADMIN | Kể cả chuyên khoa đã ngừng |
| POST / PUT | `/specialties`, `/specialties/{id}` | ADMIN | `consultationServiceId` phải là dịch vụ loại `CONSULTATION` |
| GET | `/services/all` | ADMIN | Kể cả dịch vụ đã ngừng |
| POST / PUT | `/services`, `/services/{id}` | ADMIN | `category`: `CONSULTATION`, `LAB`, `IMAGING`, `ULTRASOUND`, `PROCEDURE`, `OTHER`; giá VND |
| GET | `/rooms` | Mọi nhân viên | Danh sách phòng |
| POST / PUT | `/rooms`, `/rooms/{id}` | ADMIN | Mã phòng không trùng |
| GET | `/medicines?q=&includeInactive=` | DOCTOR, ADMIN | Tìm theo hoạt chất / tên thương mại; `label` là chuỗi in trên đơn |
| POST / PUT | `/medicines`, `/medicines/{id}` | ADMIN | `drugClass`: `NORMAL`, `ADDICTIVE`, `PSYCHOTROPIC`, `PRECURSOR`, `TOXIC`, `ANTIBIOTIC` |

## Lịch bác sĩ (UC02, UC21, UC22, UC31, UC32)

Giờ nhập theo giờ Việt Nam (`"08:00"`); mốc thời gian trả về dạng ISO UTC (`start`, `startsAt`...).

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| GET | `/slots?from=&to=&doctorId=&specialtyId=` | Công khai | Slot còn trống gom theo phiên khám; mặc định 7 ngày tới, tối đa 31 ngày |
| GET | `/schedule/templates?doctorId=` | ADMIN | Lịch tuần |
| GET | `/schedule/templates/mine` | DOCTOR | Lịch tuần của mình |
| POST / PUT | `/schedule/templates`, `/schedule/templates/{id}` | ADMIN | Soạn lịch tuần: bác sĩ, chuyên khoa, phòng, `weekday` (1 = thứ Hai), buổi, giờ, `slotMinutes` (mặc định 30), `walkInCapacity`, ngày hiệu lực. Sửa thì quay về `DRAFT`. Trùng bác sĩ/phòng → 409 |
| POST | `/schedule/templates/{id}/confirm` | DOCTOR (bác sĩ được xếp) | `DRAFT` → `DOCTOR_CONFIRMED` |
| POST | `/schedule/templates/{id}/approve` | ADMIN | `DOCTOR_CONFIRMED` → `APPROVED` |
| POST | `/schedule/templates/{id}/deactivate` | ADMIN | Ngừng áp dụng |
| POST | `/schedule/sessions/generate` | ADMIN | `{from, to}` (≤ 62 ngày): sinh phiên + slot từ lịch tuần đã duyệt, bỏ qua ngày nghỉ, chạy lại không trùng. Trả `created`, `alreadyExisted`, `skipped[]` |
| POST | `/schedule/sessions` | ADMIN | Phiên khám ngoài lịch tuần (khám bù) |
| GET | `/schedule/sessions?from=&to=&doctorId=&specialtyId=&roomId=` | Nhân viên | Danh sách phiên kèm số slot còn trống |
| GET | `/schedule/sessions/mine?from=&to=` | DOCTOR | Phiên của mình |
| GET | `/schedule/sessions/{id}` | Nhân viên | Chi tiết phiên + toàn bộ slot |
| POST | `/schedule/sessions/{id}/cancel` | ADMIN | Hủy phiên: slot bị khóa, lịch hẹn chuyển `NEEDS_RECONFIRMATION` để lễ tân gọi |
| GET / POST / DELETE | `/schedule/closures`, `/schedule/closures/{date}` | GET: nhân viên; còn lại ADMIN | Ngày phòng khám nghỉ |
| GET | `/schedule/change-requests?status=` | DOCTOR (của mình), ADMIN (tất cả) | Yêu cầu nghỉ / đổi ca |
| POST | `/schedule/change-requests` | DOCTOR | `type`: `LEAVE` (xin nghỉ), `REPLACEMENT` (+`replacementDoctorId`), `SWAP` (+`replacementDoctorId`, `swapSessionId`). Gửi trước giờ khám ≥ 24 giờ; người thay phải cùng chuyên khoa và rảnh |
| POST | `/schedule/change-requests/{id}/respond` | DOCTOR được nhờ | `{accept}` |
| POST | `/schedule/change-requests/{id}/withdraw` | DOCTOR gửi | Rút yêu cầu |
| POST | `/schedule/change-requests/{id}/decide` | ADMIN | `{approve, note}`. Duyệt: đổi bác sĩ / hủy phiên, lịch hẹn liên quan chuyển `NEEDS_RECONFIRMATION` |

## Hồ sơ bệnh nhân (UC04, UC13)

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| GET | `/patients?q=` | Nhân viên | Tìm theo tên, số điện thoại, mã `BN...`, số giấy tờ |
| GET | `/patients/possible-duplicates?fullName=&dateOfBirth=&phone=&identityNumber=` | RECEPTIONIST, ADMIN | Hồ sơ có thể trùng (gọi trước khi tạo); không tự gộp |
| POST / PUT | `/patients`, `/patients/{id}` | RECEPTIONIST, ADMIN | Hồ sơ hành chính. Có số giấy tờ thì phải chọn loại; CCCD đủ 12 số; số giấy tờ không trùng |
| GET | `/patients/{id}` | Nhân viên | Hồ sơ + dị ứng + người đại diện (`ageInMonths` dùng cho đơn thuốc trẻ em) |
| POST | `/patients/{id}/link-code` | RECEPTIONIST, ADMIN | Mã liên kết 8 ký tự, hạn 24 giờ, dùng một lần |
| GET / POST / DELETE | `/patients/{id}/allergies[/{allergyId}]` | Ghi: RECEPTIONIST, NURSE, DOCTOR | Dị ứng |
| GET / POST / DELETE | `/patients/{id}/representatives[/{id}]` | Ghi: RECEPTIONIST, ADMIN | Người đại diện hợp pháp (ghi người xác minh) |
| GET | `/me/patients` | PATIENT | Hồ sơ tài khoản quản lý (của mình và người thân) |
| POST | `/me/patients` | PATIENT | `{relationship, profile}`; mỗi tài khoản một hồ sơ `SELF` |
| POST | `/me/patients/link` | PATIENT | `{code, relationship}` liên kết hồ sơ phòng khám đã tạo |
| GET / PUT / DELETE | `/me/patients/{id}` | PATIENT (đã liên kết) | Xem; sửa thông tin liên hệ (họ tên, ngày sinh, giấy tờ do phòng khám sửa); gỡ liên kết |

## Lịch hẹn (UC05-08, UC14-16)

Trạng thái: `PENDING_CONFIRMATION` (yêu cầu từ app, chờ gọi) → `CONFIRMED` → `CHECKED_IN` (bước sau);
`NEEDS_RECONFIRMATION` (đổi bác sĩ / hủy phiên, cần gọi lại); `CANCELLED`; `NO_SHOW` (quá giờ hẹn 15 phút chưa check-in, tự động).

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| POST | `/me/appointments` | PATIENT | `{patientId, slotId, reason, serviceIds}`: gửi yêu cầu, slot được giữ. Vắng mặt ≥ 3 lần/30 ngày → 403, phải gọi phòng khám |
| GET | `/me/appointments?patientId=` | PATIENT | Lịch hẹn của các hồ sơ mình quản lý |
| GET | `/me/appointments/{id}` | PATIENT | Chi tiết |
| POST | `/me/appointments/{id}/change-requests` | PATIENT | `{type: RESCHEDULE + requestedSlotId \| CANCEL, reason}`; lễ tân xử lý. Đang có yêu cầu chờ → 409 |
| GET | `/me/appointments/{id}/change-requests` | PATIENT | Các yêu cầu đã gửi cho lịch này, mới nhất trước (`PENDING` / `APPROVED` / `REJECTED`): app dùng để ẩn nút khi đang chờ và báo khi bị từ chối |
| GET | `/me/appointments/{id}/qr` | PATIENT | Mã QR check-in (chỉ lịch `CONFIRMED`, hết hạn cuối ngày khám, mỗi lần gọi cấp mã mới) |
| POST | `/appointments` | RECEPTIONIST, ADMIN | Tạo qua điện thoại / tại quầy (`channel`: `PHONE` / `COUNTER`), xác nhận ngay |
| GET | `/appointments?date=&doctorId=&patientId=&status=` | Nhân viên | Danh sách |
| GET | `/appointments/confirmation-queue` | RECEPTIONIST, ADMIN | Hàng chờ gọi xác nhận, kèm `noAnswerCount`, `maxAttemptsReached`, `recentNoShows` |
| GET | `/appointments/{id}` | Nhân viên | Chi tiết |
| POST / GET | `/appointments/{id}/contacts` | RECEPTIONIST, ADMIN | Ghi / xem nhật ký gọi (`result`: `REACHED`, `NO_ANSWER`, `PATIENT_AGREED`, `PATIENT_DECLINED`, `CALLBACK_REQUESTED`...). 3 lần không nghe máy: giữ chờ, không tự hủy |
| POST | `/appointments/{id}/confirm` | RECEPTIONIST, ADMIN | Xác nhận sau khi bệnh nhân đồng ý |
| POST | `/appointments/{id}/reschedule` | RECEPTIONIST, ADMIN | `{slotId}` đổi giờ (slot cũ được trả lại) |
| POST | `/appointments/{id}/cancel` | RECEPTIONIST, ADMIN | `{reason}` |
| GET | `/appointment-change-requests?status=` | RECEPTIONIST, ADMIN | Yêu cầu đổi/hủy của bệnh nhân (mặc định đang chờ) |
| POST | `/appointment-change-requests/{id}/approve` \| `/reject` | RECEPTIONIST, ADMIN | Duyệt thì tự hủy / đổi sang giờ bệnh nhân chọn |

## Tiếp đón và hàng đợi (UC08, UC17, UC18, UC20, UC21)

Trạng thái lượt khám: `WAITING` → `CALLED` → `IN_PROGRESS` ⇄ `AWAITING_RESULTS` → `COMPLETED`; `LEFT` (bỏ về).
Thứ tự gọi: đúng giờ (`ON_TIME`) trước, đến muộn (`LATE`) sau, cùng nhóm theo giờ check-in; cứ 3 lượt đặt lịch xen 1 vãng lai (`app.queue.walk-in-every`). Lễ tân không sửa thứ tự.

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| POST | `/reception/check-in/qr` | RECEPTIONIST, ADMIN | `{token, companionName, companionPhone}`: quét QR trên app (dùng một lần, đúng ngày, không quá giờ hẹn 15 phút) → lượt khám + số thứ tự |
| POST | `/reception/check-in/appointments/{appointmentId}` | RECEPTIONIST, ADMIN | Check-in thủ công |
| POST | `/reception/walk-ins` | RECEPTIONIST, ADMIN | `{patientId, sessionId, reason, companionName}`; hết hạn mức vãng lai của phiên → 409 |
| GET | `/queue/sessions/{sessionId}` | Nhân viên | `current` (đang gọi/khám), `waiting` (kèm `position` và `vitals`: lần đo sinh hiệu gần nhất, `null` nếu chưa đo), `awaitingResults`, `done` |
| POST | `/queue/sessions/{sessionId}/call-next` | DOCTOR của phiên | Gọi người tiếp theo (đang có người được gọi/khám → 409); bệnh nhân nhận thông báo `CALLED` |
| POST | `/visits/{id}/start` | DOCTOR | `CALLED` → `IN_PROGRESS` |
| POST | `/visits/{id}/call-back` | DOCTOR | Gọi lại người đã có kết quả (`AWAITING_RESULTS` → `CALLED`) |
| POST | `/visits/{id}/left` | RECEPTIONIST, DOCTOR, ADMIN | Bỏ về / gọi không có mặt |
| POST / GET | `/visits/{id}/vitals` | Ghi: NURSE, DOCTOR; xem: nhân viên | Mạch, nhiệt độ, huyết áp, nhịp thở, SpO2, cân nặng, chiều cao. Mỗi lần ghi là một lần đo mới (lưu người đo, giờ đo), không ghi đè |
| PATCH | `/visits/{id}/reason` | RECEPTIONIST, NURSE, DOCTOR | `{reason}`: sửa lý do khám (điều dưỡng ghi lại khi đo sinh hiệu); 204 |
| GET | `/me/queue` | PATIENT | Lượt khám hôm nay + `peopleAhead` |

## Khám bệnh (UC23-UC30)

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| GET | `/visits/{id}/emr` | DOCTOR, NURSE, ADMIN | Bệnh án (lễ tân không xem nội dung chuyên môn) |
| PUT | `/visits/{id}/emr` | DOCTOR của lượt khám | Lưu nháp: lý do khám, bệnh sử, tiền sử, khám toàn thân/bộ phận, tóm tắt CLS, kết luận, hướng điều trị, lời dặn, `followUpDate`, `diagnoses[]` (`icd10Code`, `name`, `type`: `PRIMARY` (1 cái) / `SECONDARY` / `DIFFERENTIAL`) |
| POST | `/visits/{id}/emr/sign` | DOCTOR | Ký (cần lý do khám + chẩn đoán chính); đã ký không sửa đè |
| POST | `/visits/{id}/emr/amend` | DOCTOR | `{reason, content}`: lưu bản cũ, tăng `version`, trạng thái `AMENDED` |
| GET | `/visits/{id}/emr/amendments` | DOCTOR, NURSE, ADMIN | Lịch sử điều chỉnh kèm `previousSnapshot` |
| POST / GET | `/visits/{id}/orders` | Tạo: DOCTOR | Chỉ định dịch vụ `{items:[{serviceId, quantity}]}`, giá chốt lúc chỉ định |
| POST | `/order-items/{id}/refuse` | DOCTOR | `{note}` bệnh nhân từ chối (không tính tiền) |
| POST | `/order-items/{id}/cancel` | DOCTOR | Bớt dịch vụ chưa làm |
| GET | `/order-items/worklist?category=` | NURSE, DOCTOR | Việc cận lâm sàng của lượt khám còn mở mà kết quả chưa được duyệt: chờ làm, đang nhập, bị trả lại (`result.returnNote`), chờ duyệt. Điều dưỡng lọc `category=LAB`; kèm `doctorId` để bác sĩ lọc việc của mình |
| POST | `/order-items/{id}/perform` | NURSE, DOCTOR | Ghi một lần thực hiện |
| POST | `/performances/{id}/result` | NURSE, DOCTOR | `{resultText, conclusion, submit}` |
| POST | `/results/{id}/files` | NURSE, DOCTOR | multipart `file`: JPG/PNG/PDF ≤ 10 MB |
| POST | `/results/{id}/review` | DOCTOR của lượt khám | Duyệt → bệnh nhân xem được, nhận thông báo `RESULTS_READY` |
| POST | `/results/{id}/return` | DOCTOR của lượt khám | `{reason}`: trả kết quả đang chờ duyệt về cho điều dưỡng sửa (về `DRAFT`, lưu `returnNote`; gửi lại thì xóa) |
| POST | `/results/{id}/amend` | NURSE, DOCTOR | `{resultText, conclusion, reason}`: lưu bản cũ, ẩn với bệnh nhân tới khi duyệt lại |
| GET | `/files/{id}` | Nhân viên lâm sàng | Tải tệp |
| GET | `/me/files/{id}` | PATIENT | Tải tệp của kết quả đã duyệt |
| POST | `/visits/{id}/await-results` | DOCTOR | Bệnh nhân đi làm CLS |
| POST / GET | `/visits/{id}/prescriptions` | Tạo: DOCTOR | Đơn nháp `{items:[{medicineId, quantity, dosePerTime, timesPerDay, route, timing, days}], advice}`; mỗi thuốc ≤ 30 ngày |
| PUT / DELETE | `/prescriptions/{id}` | DOCTOR | Sửa / xóa đơn nháp |
| POST | `/prescriptions/{id}/sign` | DOCTOR | Ký: cần chẩn đoán chính; người lớn cần CCCD/định danh; trẻ < 72 tháng cần cân nặng + người đưa đến khám; thuốc độc xếp trước; mã đơn `PK001<năm><số>`. Bệnh nhân có dị ứng: body `{allergyChecked: true, allergyNote}` bắt buộc, lưu ở `allergyAck` |
| POST | `/prescriptions/{id}/replace` | DOCTOR | `{reason, content}`: đơn thay thế; ký xong đơn cũ thành `REPLACED` |
| POST | `/visits/{id}/complete` | DOCTOR | Kết thúc: bệnh án đã ký, không còn đơn nháp, dịch vụ xong và kết quả đã duyệt → lập hóa đơn, thông báo `VISIT_COMPLETED` |
| GET | `/visits/{id}/record` | DOCTOR, NURSE, ADMIN | Hồ sơ đầy đủ lượt khám |
| GET | `/patients/{patientId}/visits` | DOCTOR, NURSE, ADMIN | Lịch sử khám của một bệnh nhân, mới nhất trước |
| GET | `/me/visits?patientId=` | PATIENT | Lịch sử khám |
| GET | `/me/visits/{id}` | PATIENT | Chi tiết: chỉ bệnh án đã ký, kết quả đã duyệt, đơn đã ký, hóa đơn |

## Hóa đơn, thông báo, phản hồi, báo cáo (UC09-UC12, UC19, UC28, UC33)

| Phương thức | Đường dẫn | Ai gọi | Mô tả |
|---|---|---|---|
| GET | `/invoices?status=&from=&to=` | RECEPTIONIST, ADMIN | Mặc định hôm nay; `status=UNPAID` theo dõi chưa thu |
| GET | `/invoices/{id}`, `/visits/{id}/invoice` | Nhân viên | Hóa đơn: phí khám + dịch vụ đã làm, các lần thu/hoàn |
| POST | `/invoices/{id}/payments` | RECEPTIONIST, ADMIN | `{method: CASH \| BANK_TRANSFER \| OTHER, amount}`; không vượt số còn phải thu |
| POST | `/invoices/{id}/refunds` | ADMIN | `{amount, note}` |
| POST | `/me/invoices/{id}/pay` | PATIENT | Thanh toán trực tuyến phần còn lại (mô phỏng) |
| GET | `/me/notifications`, `/me/notifications/unread-count` | Đã đăng nhập | Thông báo: `APPOINTMENT_CONFIRMED`, `APPOINTMENT_RECONFIRM`, `APPOINTMENT_CANCELLED`, `REMINDER` (trước 24 giờ), `CALLED`, `RESULTS_READY`, `VISIT_COMPLETED` |
| POST | `/me/notifications/{id}/read`, `/me/notifications/read-all` | Đã đăng nhập | Đánh dấu đã đọc |
| POST / DELETE | `/me/push-tokens`, `/me/push-tokens/{token}` | Đã đăng nhập | Đăng ký thiết bị nhận thông báo đẩy |
| POST / GET | `/me/feedback` | PATIENT | Phản hồi riêng gửi phòng khám |
| GET / PATCH | `/feedback`, `/feedback/{id}` | ADMIN | Xử lý phản hồi (`NEW`, `IN_REVIEW`, `RESOLVED`, `CLOSED`) |
| GET | `/reports/summary?from=&to=` | ADMIN | Lượt khám, vãng lai, không đến, hủy; phát sinh, thực thu, hoàn, chưa thu; theo ngày / bác sĩ / dịch vụ |
| GET | `/reports/mine?from=&to=` | DOCTOR | Lượt khám, hoàn tất, hẹn tái khám, không đến, số phiên |

## Tài khoản demo (`SEED_DEMO=true`, mật khẩu `12345678`)

Dữ liệu demo có sẵn lịch tuần đã duyệt (thứ 2 – thứ 6, sáng 7:30–11:30, chiều 13:30–17:00) và phiên khám 14 ngày tới.

| Số điện thoại | Vai trò |
|---|---|
| 0900000001 | Quản lý phòng khám (ADMIN): tài khoản, danh mục, lịch làm việc, báo cáo |
| 0900000003 | Lễ tân (RECEPTIONIST) |
| 0900000004 | Điều dưỡng (NURSE): đo sinh hiệu, thực hiện xét nghiệm, nhập kết quả |
| 0900000011 – 0900000014 | Bác sĩ Nội, Nhi, Tai Mũi Họng, Da liễu |
| 0900000099 | Bệnh nhân (app), có sẵn hồ sơ BN000001 |
