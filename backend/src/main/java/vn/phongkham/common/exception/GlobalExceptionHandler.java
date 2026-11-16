package vn.phongkham.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.phongkham.common.dto.ErrorResponse;

/** Chuyển mọi lỗi thành ErrorResponse tiếng Việt với mã HTTP phù hợp. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Tên ràng buộc UNIQUE trong DB -> thông báo cho người dùng */
    private static final Map<String, String> UNIQUE_MESSAGES = Map.ofEntries(
            Map.entry("users_phone_key", "Số điện thoại đã được sử dụng"),
            Map.entry("users_email_key", "Email đã được sử dụng"),
            Map.entry("specialties_code_key", "Mã chuyên khoa đã tồn tại"),
            Map.entry("specialties_name_key", "Tên chuyên khoa đã tồn tại"),
            Map.entry("rooms_code_key", "Mã phòng đã tồn tại"),
            Map.entry("services_code_key", "Mã dịch vụ đã tồn tại"),
            Map.entry("doctor_profiles_practice_license_no_key", "Số chứng chỉ hành nghề đã tồn tại"),
            Map.entry("doctor_profiles_prescriber_code_key", "Mã liên thông người kê đơn đã tồn tại"),
            Map.entry("ex_session_room", "Phòng đã có phiên khám khác trùng giờ"),
            Map.entry("ex_session_doctor", "Bác sĩ đã có phiên khám khác trùng giờ"),
            Map.entry("uq_slot_active_appointment", "Khung giờ này vừa có người đặt, vui lòng chọn giờ khác"),
            Map.entry("uq_patient_identity", "Đã có hồ sơ bệnh nhân với số giấy tờ này"),
            Map.entry("uq_appointment_open_request", "Lịch hẹn đã có yêu cầu đổi/hủy đang chờ xử lý"),
            Map.entry("uq_session_open_change", "Phiên khám này đã có yêu cầu đang chờ xử lý"));

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(g -> errors.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse("Dữ liệu chưa hợp lệ", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ErrorResponse> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("Dữ liệu gửi lên không đúng định dạng"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        String constraint = e.getCause() instanceof ConstraintViolationException c ? c.getConstraintName() : null;
        String message = constraint == null ? null : UNIQUE_MESSAGES.get(constraint);
        if (message == null) {
            log.warn("Vi phạm ràng buộc dữ liệu: {}", constraint, e);
            message = "Dữ liệu bị trùng hoặc vi phạm ràng buộc";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(message));
    }

    /** Hai người cùng sửa một bản ghi (khóa lạc quan): người lưu sau phải tải lại */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> conflict(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("Người khác vừa cập nhật dữ liệu này. Hãy tải lại trang rồi thử lại."));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ErrorResponse> denied(AuthorizationDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("Bạn không có quyền thực hiện thao tác này"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noRoute(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("Không tìm thấy đường dẫn"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorResponse.of("Phương thức không được hỗ trợ"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Lỗi không mong đợi", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of("Lỗi hệ thống, vui lòng thử lại sau"));
    }
}
