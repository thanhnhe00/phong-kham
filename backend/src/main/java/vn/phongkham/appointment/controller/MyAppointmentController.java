package vn.phongkham.appointment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.appointment.dto.AppointmentDtos.AppointmentResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.BookingRequest;
import vn.phongkham.appointment.dto.AppointmentDtos.ChangeRequestCreate;
import vn.phongkham.appointment.dto.AppointmentDtos.ChangeRequestResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.QrResponse;
import vn.phongkham.appointment.service.MyAppointmentService;
import vn.phongkham.auth.security.AuthUser;

@Tag(name = "Lịch hẹn của tôi (app bệnh nhân)")
@RestController
@RequestMapping("/me/appointments")
@PreAuthorize("hasRole('PATIENT')")
public class MyAppointmentController {

    private final MyAppointmentService service;

    public MyAppointmentController(MyAppointmentService service) {
        this.service = service;
    }

    @Operation(summary = "Gửi yêu cầu đặt lịch (giữ slot, chờ phòng khám gọi xác nhận)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse request(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BookingRequest req) {
        return service.request(me, req);
    }

    @Operation(summary = "Lịch hẹn của các hồ sơ tài khoản quản lý (lọc theo patientId)")
    @GetMapping
    public List<AppointmentResponse> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long patientId) {
        return service.list(me, patientId);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.get(me, id);
    }

    @Operation(summary = "Xin đổi giờ (RESCHEDULE + requestedSlotId) hoặc hủy (CANCEL); lễ tân xử lý")
    @PostMapping("/{id}/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequestResponse requestChange(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                               @Valid @RequestBody ChangeRequestCreate req) {
        return service.requestChange(me, id, req);
    }

    @Operation(summary = "Các yêu cầu đổi/hủy đã gửi cho lịch này, mới nhất trước (để app biết yêu cầu nào đang chờ, đã duyệt hay bị từ chối)")
    @GetMapping("/{id}/change-requests")
    public List<ChangeRequestResponse> changeRequests(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.changeRequests(me, id);
    }

    @Operation(summary = "Mã QR check-in (chỉ lịch đã xác nhận; mỗi lần gọi cấp mã mới)")
    @GetMapping("/{id}/qr")
    public QrResponse qr(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.qr(me, id);
    }
}
