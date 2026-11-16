package vn.phongkham.schedule.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.schedule.dto.ScheduleDtos.CancelResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.ClosureRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.ClosureResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.GenerateRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.GenerateResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.OpenSession;
import vn.phongkham.schedule.dto.ScheduleDtos.SessionDetail;
import vn.phongkham.schedule.dto.ScheduleDtos.SessionRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.SessionResponse;
import vn.phongkham.schedule.service.SessionService;

@Tag(name = "Phiên khám và slot")
@RestController
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @Operation(summary = "Slot còn trống (công khai), mặc định 7 ngày tới; lọc bác sĩ / chuyên khoa")
    @GetMapping("/slots")
    public List<OpenSession> openSlots(@RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(required = false) Long doctorId,
                                       @RequestParam(required = false) Long specialtyId) {
        return service.openSlots(from, to, doctorId, specialtyId);
    }

    @Operation(summary = "Sinh phiên khám + slot từ lịch tuần đã duyệt (chạy lại không tạo trùng)")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/schedule/sessions/generate")
    public GenerateResponse generate(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GenerateRequest req) {
        return service.generate(me, req.from(), req.to());
    }

    @Operation(summary = "Tạo phiên khám ngoài lịch tuần (khám bù, tăng ca)")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/schedule/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDetail create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SessionRequest req) {
        return service.create(me, req);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/schedule/sessions")
    public List<SessionResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                      @RequestParam(required = false) Long doctorId,
                                      @RequestParam(required = false) Long specialtyId,
                                      @RequestParam(required = false) Long roomId) {
        return service.list(from, to, doctorId, specialtyId, roomId);
    }

    @Operation(summary = "Phiên khám của bác sĩ đang đăng nhập")
    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/schedule/sessions/mine")
    public List<SessionResponse> mine(@AuthenticationPrincipal AuthUser me, @RequestParam LocalDate from,
                                      @RequestParam LocalDate to) {
        return service.mine(me, from, to);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/schedule/sessions/{id}")
    public SessionDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @Operation(summary = "Hủy phiên khám; lịch hẹn chuyển 'cần xác nhận lại' để lễ tân gọi bệnh nhân")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/schedule/sessions/{id}/cancel")
    public CancelResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/schedule/closures")
    public List<ClosureResponse> closures(@RequestParam(required = false) LocalDate from,
                                          @RequestParam(required = false) LocalDate to) {
        return service.listClosures(from, to);
    }

    @Operation(summary = "Đánh dấu ngày nghỉ (lễ, Tết): không sinh phiên khám vào ngày này")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/schedule/closures")
    @ResponseStatus(HttpStatus.CREATED)
    public ClosureResponse addClosure(@Valid @RequestBody ClosureRequest req) {
        return service.addClosure(req);
    }

    @PreAuthorize(Patterns.ADMIN)
    @DeleteMapping("/schedule/closures/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClosure(@PathVariable LocalDate date) {
        service.deleteClosure(date);
    }
}
