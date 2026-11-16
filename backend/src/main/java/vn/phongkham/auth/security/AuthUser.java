package vn.phongkham.auth.security;

import java.util.Set;
import vn.phongkham.auth.model.Role;

/** Người đang đăng nhập; controller lấy bằng @AuthenticationPrincipal AuthUser user */
public record AuthUser(Long id, Set<Role> roles, boolean mustChangePassword) {

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
