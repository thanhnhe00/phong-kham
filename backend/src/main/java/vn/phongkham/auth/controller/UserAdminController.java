package vn.phongkham.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.dto.UserAdminDtos.StatusRequest;
import vn.phongkham.auth.dto.UserAdminDtos.TemporaryPasswordResponse;
import vn.phongkham.auth.dto.UserAdminDtos.UserSummary;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.auth.service.UserAdminService;
import vn.phongkham.common.dto.PageResponse;

@Tag(name = "Quản trị tài khoản")
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách tài khoản, lọc theo vai trò / số điện thoại")
    @GetMapping
    public PageResponse<UserSummary> list(@RequestParam(required = false) Role role,
                                          @RequestParam(required = false) String q,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return service.list(role, q, pageable);
    }

    @Operation(summary = "Khóa / mở khóa / vô hiệu hóa tài khoản")
    @PatchMapping("/{id}/status")
    public UserSummary setStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                 @Valid @RequestBody StatusRequest req) {
        return service.setStatus(me, id, req.status());
    }

    @Operation(summary = "Cấp mật khẩu tạm (người dùng phải đổi ở lần đăng nhập sau)")
    @PostMapping("/{id}/reset-password")
    public TemporaryPasswordResponse resetPassword(@PathVariable Long id) {
        return service.resetPassword(id);
    }
}
