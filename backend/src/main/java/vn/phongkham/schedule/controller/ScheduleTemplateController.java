package vn.phongkham.schedule.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.schedule.dto.ScheduleDtos.TemplateRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.TemplateResponse;
import vn.phongkham.schedule.service.ScheduleTemplateService;

@Tag(name = "Lịch tuần của bác sĩ")
@RestController
@RequestMapping("/schedule/templates")
public class ScheduleTemplateController {

    private final ScheduleTemplateService service;

    public ScheduleTemplateController(ScheduleTemplateService service) {
        this.service = service;
    }

    @PreAuthorize(Patterns.ADMIN)
    @GetMapping
    public List<TemplateResponse> list(@RequestParam(required = false) Long doctorId) {
        return service.list(doctorId);
    }

    @Operation(summary = "Lịch tuần của bác sĩ đang đăng nhập")
    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/mine")
    public List<TemplateResponse> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @Operation(summary = "Quản lý soạn lịch tuần (trạng thái DRAFT, chờ bác sĩ xác nhận)")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TemplateRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Sửa lịch tuần (quay về DRAFT, cần xác nhận và duyệt lại)")
    @PreAuthorize(Patterns.ADMIN)
    @PutMapping("/{id}")
    public TemplateResponse update(@PathVariable Long id, @Valid @RequestBody TemplateRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Bác sĩ xác nhận lịch tuần của mình")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/{id}/confirm")
    public TemplateResponse confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.confirm(me, id);
    }

    @Operation(summary = "Quản lý duyệt lịch tuần đã được bác sĩ xác nhận")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/{id}/approve")
    public TemplateResponse approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.approve(me, id);
    }

    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/{id}/deactivate")
    public TemplateResponse deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
