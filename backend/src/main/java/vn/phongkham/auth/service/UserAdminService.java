package vn.phongkham.auth.service;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.dto.UserAdminDtos.TemporaryPasswordResponse;
import vn.phongkham.auth.dto.UserAdminDtos.UserSummary;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.auth.repository.UserRepository;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.common.exception.ApiException;

/** UC34: admin xem, khóa/mở khóa tài khoản và cấp mật khẩu tạm. */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserAdminService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(Role role, String q, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        return PageResponse.of(users.search(role, query, pageable), UserAdminService::summary);
    }

    @Transactional
    public UserSummary setStatus(AuthUser actor, Long id, UserStatus status) {
        if (id.equals(actor.id())) {
            throw ApiException.badRequest("Không thể tự đổi trạng thái tài khoản của mình");
        }
        if (status == UserStatus.PENDING) {
            throw ApiException.badRequest("Không thể chuyển tài khoản về trạng thái chờ xác minh");
        }
        User u = load(id);
        if (status != UserStatus.ACTIVE && u.getStatus() == UserStatus.ACTIVE) {
            u.bumpTokenVersion(); // đăng xuất mọi phiên đang mở
        }
        if (status == UserStatus.ACTIVE) {
            u.setFailedLogins(0);
            u.setLockedUntil(null);
        }
        u.setStatus(status);
        return summary(u);
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(Long id) {
        User u = load(id);
        String temp = TemporaryPasswords.generate();
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        u.bumpTokenVersion();
        return new TemporaryPasswordResponse(temp);
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    static UserSummary summary(User u) {
        return new UserSummary(u.getId(), u.getPhone(), u.getEmail(), u.getRoles(), u.getStatus(),
                u.getLockedUntil(), u.getLastLoginAt());
    }
}
