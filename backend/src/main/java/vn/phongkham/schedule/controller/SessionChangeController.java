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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.schedule.dto.ScheduleDtos.ChangeRequestCreate;
import vn.phongkham.schedule.dto.ScheduleDtos.ChangeRequestResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.DecisionRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.RespondRequest;
import vn.phongkham.schedule.model.ScheduleEnums.RequestStatus;
import vn.phongkham.schedule.service.SessionChangeService;

@Tag(name = "Xin nghỉ / đổi ca")
@RestController
@RequestMapping("/schedule/change-requests")
public class SessionChangeController {

    private final SessionChangeService service;

    public SessionChangeController(SessionChangeService service) {
        this.service = service;
    }

    @Operation(summary = "Quản lý: mọi yêu cầu. Bác sĩ: yêu cầu mình gửi hoặc được nhờ")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    @GetMapping
    public List<ChangeRequestResponse> list(@AuthenticationPrincipal AuthUser me,
                                            @RequestParam(required = false) RequestStatus status) {
        return service.list(me, status);
    }

    @Operation(summary = "Bác sĩ gửi yêu cầu: LEAVE (xin nghỉ), REPLACEMENT (nhờ người thay), SWAP (đổi chéo ca)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeRequestResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangeRequestCreate req) {
        return service.create(me, req);
    }

    @Operation(summary = "Bác sĩ được nhờ đồng ý / từ chối")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/{id}/respond")
    public ChangeRequestResponse respond(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                         @Valid @RequestBody RespondRequest req) {
        return service.respond(me, id, req.accept());
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/{id}/withdraw")
    public ChangeRequestResponse withdraw(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.withdraw(me, id);
    }

    @Operation(summary = "Quản lý duyệt / từ chối")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/{id}/decide")
    public ChangeRequestResponse decide(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                        @Valid @RequestBody DecisionRequest req) {
        return service.decide(me, id, req.approve(), req.note());
    }
}
