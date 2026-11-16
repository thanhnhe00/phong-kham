package vn.phongkham.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.UserStatus;

/** Dữ liệu vào/ra của các API /admin/users (UC34) */
public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    public record UserSummary(Long id, String phone, String email, Set<Role> roles, UserStatus status,
                              Instant lockedUntil, Instant lastLoginAt) {
    }

    public record StatusRequest(@NotNull(message = "Vui lòng chọn trạng thái") UserStatus status) {
    }

    public record TemporaryPasswordResponse(String temporaryPassword) {
    }
}
