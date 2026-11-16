package vn.phongkham.visit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.visit.dto.VisitDtos.CheckInRequest;
import vn.phongkham.visit.dto.VisitDtos.MyQueueItem;
import vn.phongkham.visit.dto.VisitDtos.QrCheckInRequest;
import vn.phongkham.visit.dto.VisitDtos.QueueView;
import vn.phongkham.visit.dto.VisitDtos.ReasonRequest;
import vn.phongkham.visit.dto.VisitDtos.VisitResponse;
import vn.phongkham.visit.dto.VisitDtos.VitalsRequest;
import vn.phongkham.visit.dto.VisitDtos.VitalsResponse;
import vn.phongkham.visit.dto.VisitDtos.WalkInRequest;
import vn.phongkham.visit.service.QueueService;
import vn.phongkham.visit.service.ReceptionService;
import vn.phongkham.visit.service.VitalsService;

@Tag(name = "Tiếp đón và hàng đợi")
@RestController
public class VisitController {

    private static final String FRONT_DESK = "hasAnyRole('RECEPTIONIST','ADMIN')";

    private final ReceptionService reception;
    private final QueueService queue;
    private final VitalsService vitals;

    public VisitController(ReceptionService reception, QueueService queue, VitalsService vitals) {
        this.reception = reception;
        this.queue = queue;
        this.vitals = vitals;
    }

    @Operation(summary = "Check-in bằng mã QR trên app bệnh nhân (mã dùng một lần, đúng ngày khám)")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/reception/check-in/qr")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResponse checkInQr(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody QrCheckInRequest req) {
        return reception.checkInByQr(me, req.token(), req.companionName(), req.companionPhone());
    }

    @Operation(summary = "Check-in thủ công theo lịch hẹn")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/reception/check-in/appointments/{appointmentId}")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResponse checkIn(@AuthenticationPrincipal AuthUser me, @PathVariable Long appointmentId,
                                 @Valid @RequestBody CheckInRequest req) {
        return reception.checkInAppointment(me, appointmentId, req.companionName(), req.companionPhone());
    }

    @Operation(summary = "Tiếp nhận bệnh nhân vãng lai (khi phiên còn hạn mức)")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/reception/walk-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResponse walkIn(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody WalkInRequest req) {
        return reception.walkIn(me, req);
    }

    @Operation(summary = "Hàng đợi của phiên khám: đang khám, thứ tự chờ, chờ kết quả")
    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/queue/sessions/{sessionId}")
    public QueueView queue(@PathVariable Long sessionId) {
        return queue.queue(sessionId);
    }

    @Operation(summary = "Bác sĩ gọi người tiếp theo (thứ tự do hệ thống tính)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/queue/sessions/{sessionId}/call-next")
    public VisitResponse callNext(@AuthenticationPrincipal AuthUser me, @PathVariable Long sessionId) {
        return queue.callNext(me, sessionId);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/visits/{id}/start")
    public VisitResponse start(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return queue.start(me, id);
    }

    @Operation(summary = "Gọi lại bệnh nhân đã có kết quả cận lâm sàng")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/visits/{id}/call-back")
    public VisitResponse callBack(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return queue.callBack(me, id);
    }

    @Operation(summary = "Bệnh nhân bỏ về / gọi không có mặt")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','DOCTOR','ADMIN')")
    @PostMapping("/visits/{id}/left")
    public VisitResponse left(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return queue.markLeft(me, id);
    }

    @Operation(summary = "Ghi sinh hiệu (điều dưỡng đo trước khi vào khám)")
    @PreAuthorize("hasAnyRole('NURSE','DOCTOR')")
    @PostMapping("/visits/{id}/vitals")
    @ResponseStatus(HttpStatus.CREATED)
    public VitalsResponse recordVitals(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @Valid @RequestBody VitalsRequest req) {
        return vitals.record(me, id, req);
    }

    @Operation(summary = "Sửa lý do khám (điều dưỡng ghi lại khi đo sinh hiệu)")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','NURSE','DOCTOR')")
    @PatchMapping("/visits/{id}/reason")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateReason(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        vitals.updateReason(id, req.reason());
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/visits/{id}/vitals")
    public List<VitalsResponse> listVitals(@PathVariable Long id) {
        return vitals.list(id);
    }

    @Operation(summary = "App bệnh nhân: lượt khám hôm nay và số người phía trước")
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/me/queue")
    public List<MyQueueItem> myQueue(@AuthenticationPrincipal AuthUser me) {
        return queue.mine(me);
    }
}
