package vn.phongkham.appointment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.appointment.dto.AppointmentDtos.AppointmentResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.CancelRequest;
import vn.phongkham.appointment.dto.AppointmentDtos.ChangeRequestResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.ContactLogged;
import vn.phongkham.appointment.dto.AppointmentDtos.ContactRequest;
import vn.phongkham.appointment.dto.AppointmentDtos.ContactResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.QueueItem;
import vn.phongkham.appointment.dto.AppointmentDtos.RescheduleRequest;
import vn.phongkham.appointment.dto.AppointmentDtos.StaffBookingRequest;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.model.AppointmentEnums.SimpleRequestStatus;
import vn.phongkham.appointment.service.AppointmentService;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.common.dto.PageResponse;

@Tag(name = "Lịch hẹn (lễ tân / trực ban)")
@RestController
public class AppointmentController {

    /** Ai được tạo / xác nhận / đổi / hủy lịch hẹn */
    private static final String FRONT_DESK = "hasAnyRole('RECEPTIONIST','ADMIN')";

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @Operation(summary = "Tạo lịch qua điện thoại / tại quầy (xác nhận ngay)")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StaffBookingRequest req) {
        return service.create(me, req);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/appointments")
    public PageResponse<AppointmentResponse> search(@RequestParam(required = false) LocalDate date,
                                                    @RequestParam(required = false) Long doctorId,
                                                    @RequestParam(required = false) Long patientId,
                                                    @RequestParam(required = false) AppointmentStatus status,
                                                    @PageableDefault(size = 50) Pageable pageable) {
        return service.search(date, doctorId, patientId, status, pageable);
    }

    @Operation(summary = "Hàng chờ xác nhận: yêu cầu mới từ app và lịch cần xác nhận lại, giờ khám gần nhất trước")
    @PreAuthorize(FRONT_DESK)
    @GetMapping("/appointments/confirmation-queue")
    public List<QueueItem> confirmationQueue() {
        return service.confirmationQueue();
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/appointments/{id}")
    public AppointmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Ghi một lần gọi điện / liên hệ (tối đa 3 lần không nghe máy thì giữ chờ, không tự hủy)")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/appointments/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactLogged logContact(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                    @Valid @RequestBody ContactRequest req) {
        return service.logContact(me, id, req);
    }

    @PreAuthorize(FRONT_DESK)
    @GetMapping("/appointments/{id}/contacts")
    public List<ContactResponse> listContacts(@PathVariable Long id) {
        return service.listContacts(id);
    }

    @Operation(summary = "Xác nhận lịch sau khi bệnh nhân đồng ý qua điện thoại")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/appointments/{id}/confirm")
    public AppointmentResponse confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.confirm(me, id);
    }

    @Operation(summary = "Đổi sang khung giờ khác (đã thống nhất với bệnh nhân)")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/appointments/{id}/reschedule")
    public AppointmentResponse reschedule(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                          @Valid @RequestBody RescheduleRequest req) {
        return service.reschedule(me, id, req.slotId());
    }

    @PreAuthorize(FRONT_DESK)
    @PostMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return service.cancel(id, req.reason());
    }

    @Operation(summary = "Yêu cầu đổi/hủy của bệnh nhân (mặc định đang chờ)")
    @PreAuthorize(FRONT_DESK)
    @GetMapping("/appointment-change-requests")
    public List<ChangeRequestResponse> changeRequests(@RequestParam(required = false) SimpleRequestStatus status) {
        return service.listChangeRequests(status);
    }

    @PreAuthorize(FRONT_DESK)
    @PostMapping("/appointment-change-requests/{id}/approve")
    public ChangeRequestResponse approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.approveChangeRequest(me, id);
    }

    @PreAuthorize(FRONT_DESK)
    @PostMapping("/appointment-change-requests/{id}/reject")
    public ChangeRequestResponse reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.rejectChangeRequest(me, id);
    }
}
