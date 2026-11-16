package vn.phongkham.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.dto.AuthDtos.AuthResponse;
import vn.phongkham.auth.dto.AuthDtos.ChangePasswordRequest;
import vn.phongkham.auth.dto.AuthDtos.LoginRequest;
import vn.phongkham.auth.dto.AuthDtos.MeResponse;
import vn.phongkham.auth.dto.AuthDtos.PhoneCodeCheckRequest;
import vn.phongkham.auth.dto.AuthDtos.PhoneCodeRequest;
import vn.phongkham.auth.dto.AuthDtos.PhoneCodeResponse;
import vn.phongkham.auth.dto.AuthDtos.PhoneCodeStatusResponse;
import vn.phongkham.auth.dto.AuthDtos.RegisterRequest;
import vn.phongkham.auth.dto.AuthDtos.ResetPasswordRequest;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.auth.model.VerifyPurpose;
import vn.phongkham.auth.repository.UserRepository;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.auth.security.TokenService;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.staff.repository.EmployeeRepository;

/** UC03: đăng ký có xác minh số điện thoại (nhắn tin đến phòng khám), đăng nhập, quên/đổi mật khẩu. */
@Service
public class AuthService {

    private static final String WRONG_LOGIN = "Số điện thoại hoặc mật khẩu không đúng";

    private final UserRepository users;
    private final EmployeeRepository employees;
    private final PhoneVerificationService verifications;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final int maxFailedLogins;
    private final Duration lockDuration;
    private final String smsTo;

    public AuthService(UserRepository users, EmployeeRepository employees, PhoneVerificationService verifications,
                       PasswordEncoder encoder, TokenService tokens,
                       @Value("${app.auth.max-failed-logins:5}") int maxFailedLogins,
                       @Value("${app.auth.lock-duration:15m}") Duration lockDuration,
                       @Value("${app.sms.verify-number:}") String smsTo) {
        this.users = users;
        this.employees = employees;
        this.verifications = verifications;
        this.encoder = encoder;
        this.tokens = tokens;
        this.maxFailedLogins = maxFailedLogins;
        this.lockDuration = lockDuration;
        this.smsTo = smsTo.isBlank() ? null : smsTo.trim();
    }

    public PhoneCodeResponse requestCode(PhoneCodeRequest req) {
        boolean exists = users.existsByPhone(req.phone());
        var issued = switch (req.purpose()) {
            case REGISTER -> {
                if (exists) {
                    throw ApiException.conflict("Số điện thoại đã được đăng ký");
                }
                yield verifications.issue(req.phone(), req.purpose());
            }
            // Số chưa có tài khoản vẫn nhận mã giả, trả lời giống hệt: người ngoài không dò được số nào có tài khoản
            case RESET_PASSWORD -> exists ? verifications.issue(req.phone(), req.purpose()) : verifications.decoy();
            default -> throw ApiException.badRequest("Mục đích xác minh chưa được hỗ trợ");
        };
        return new PhoneCodeResponse(issued.expiresAt(), issued.code(), smsTo,
                PhoneVerificationService.SMS_PREFIX + " " + issued.code());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PhoneCodeStatusResponse checkCode(PhoneCodeCheckRequest req) {
        return new PhoneCodeStatusResponse(verifications.check(req.phone(), req.purpose(), req.code()).name());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByPhone(req.phone())) {
            throw ApiException.conflict("Số điện thoại đã được đăng ký");
        }
        verifications.consume(req.phone(), VerifyPurpose.REGISTER, req.code());
        User u = new User();
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setStatus(UserStatus.ACTIVE);
        u.setPhoneVerifiedAt(Instant.now());
        u.setPasswordChangedAt(Instant.now());
        u.setRoles(EnumSet.of(Role.PATIENT));
        users.save(u);
        return authResponse(u);
    }

    /** noRollbackFor: số lần đăng nhập sai phải được lưu dù request trả lỗi */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByPhone(req.phone()).orElseThrow(() -> ApiException.unauthorized(WRONG_LOGIN));
        Instant now = Instant.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, u.getLockedUntil()).toMinutes());
            throw ApiException.tooManyRequests("Đăng nhập sai nhiều lần, vui lòng thử lại sau " + minutes + " phút");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            u.setFailedLogins(u.getFailedLogins() + 1);
            if (u.getFailedLogins() >= maxFailedLogins) {
                u.setFailedLogins(0);
                u.setLockedUntil(now.plus(lockDuration));
            }
            throw ApiException.unauthorized(WRONG_LOGIN);
        }
        switch (u.getStatus()) {
            case ACTIVE -> { }
            case PENDING -> throw ApiException.forbidden("Tài khoản chưa xác minh số điện thoại");
            default -> throw ApiException.forbidden("Tài khoản đã bị khóa, vui lòng liên hệ phòng khám");
        }
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(now);
        return authResponse(u);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(ResetPasswordRequest req) {
        User u = users.findByPhone(req.phone()).orElseThrow(() -> ApiException.badRequest("Mã xác minh không đúng hoặc đã hết hạn"));
        verifications.consume(req.phone(), VerifyPurpose.RESET_PASSWORD, req.code());
        setPassword(u, req.newPassword());
        u.setFailedLogins(0);
        u.setLockedUntil(null);
    }

    @Transactional
    public AuthResponse changePassword(AuthUser me, ChangePasswordRequest req) {
        User u = load(me.id());
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw ApiException.badRequest("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        setPassword(u, req.newPassword());
        // Token cũ hết hiệu lực; trả token mới để người dùng không phải đăng nhập lại
        return authResponse(u);
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthUser me) {
        return toMe(load(me.id()));
    }

    private void setPassword(User u, String raw) {
        u.setPasswordHash(encoder.encode(raw));
        u.setPasswordChangedAt(Instant.now());
        u.setMustChangePassword(false);
        u.bumpTokenVersion();
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.unauthorized("Tài khoản không tồn tại"));
    }

    private AuthResponse authResponse(User u) {
        var t = tokens.issue(u);
        return new AuthResponse(t.token(), t.expiresAt(), toMe(u));
    }

    private MeResponse toMe(User u) {
        var e = employees.findByUserId(u.getId()).orElse(null);
        return new MeResponse(u.getId(), u.getPhone(), u.getEmail(), u.getRoles(), u.isMustChangePassword(),
                e == null ? null : e.getId(), e == null ? null : e.getFullName());
    }
}
