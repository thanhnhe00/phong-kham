package vn.phongkham.staff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.staff.dto.StaffDtos.CreateStaffRequest;
import vn.phongkham.staff.dto.StaffDtos.CreatedStaffResponse;
import vn.phongkham.staff.dto.StaffDtos.DoctorProfileRequest;
import vn.phongkham.staff.dto.StaffDtos.DoctorProfileResponse;
import vn.phongkham.staff.dto.StaffDtos.HistoryRequest;
import vn.phongkham.staff.dto.StaffDtos.HistoryResponse;
import vn.phongkham.staff.dto.StaffDtos.StaffResponse;
import vn.phongkham.staff.dto.StaffDtos.UpdateStaffRequest;
import vn.phongkham.staff.model.StaffEnums.EmploymentStatus;
import vn.phongkham.staff.service.StaffService;

@Tag(name = "Nhân viên")
@RestController
@RequestMapping("/staff")
@PreAuthorize(Patterns.ADMIN)
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<StaffResponse> list(@RequestParam(required = false) Role role,
                                            @RequestParam(required = false) EmploymentStatus status,
                                            @RequestParam(required = false) String q,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(role, status, q, pageable);
    }

    @GetMapping("/{id}")
    public StaffResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Tạo nhân viên kèm tài khoản; trả mật khẩu tạm (chỉ hiện một lần)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedStaffResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateStaffRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    public StaffResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                @Valid @RequestBody UpdateStaffRequest req) {
        return service.update(me, id, req);
    }

    @GetMapping("/{id}/doctor-profile")
    public DoctorProfileResponse getDoctorProfile(@PathVariable Long id) {
        return service.getDoctorProfile(id);
    }

    @Operation(summary = "Tạo/cập nhật hồ sơ hành nghề và hồ sơ công khai của bác sĩ")
    @PutMapping("/{id}/doctor-profile")
    public DoctorProfileResponse saveDoctorProfile(@PathVariable Long id, @Valid @RequestBody DoctorProfileRequest req) {
        return service.saveDoctorProfile(id, req);
    }

    @GetMapping("/{id}/doctor-history")
    public List<HistoryResponse> listHistory(@PathVariable Long id) {
        return service.listHistory(id);
    }

    @PostMapping("/{id}/doctor-history")
    @ResponseStatus(HttpStatus.CREATED)
    public HistoryResponse addHistory(@PathVariable Long id, @Valid @RequestBody HistoryRequest req) {
        return service.addHistory(id, req);
    }

    @DeleteMapping("/{id}/doctor-history/{historyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHistory(@PathVariable Long id, @PathVariable Long historyId) {
        service.deleteHistory(id, historyId);
    }
}
