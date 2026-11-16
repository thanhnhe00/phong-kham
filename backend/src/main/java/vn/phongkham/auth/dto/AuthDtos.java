package vn.phongkham.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.VerifyPurpose;
import vn.phongkham.common.Patterns;

/** Dữ liệu vào/ra của các API /auth */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record PhoneCodeRequest(
            @NotBlank(message = "Vui lòng nhập số điện thoại") @Pattern(regexp = Patterns.PHONE, message = Patterns.PHONE_MESSAGE) String phone,
            @NotNull(message = "Thiếu mục đích xác minh") VerifyPurpose purpose) {
    }

    /**
     * Người dùng nhắn smsBody tới smsTo từ chính số điện thoại đã khai.
     * smsTo null nghĩa là phòng khám chưa cấu hình số nhận tin (SMS_VERIFY_NUMBER).
     */
    public record PhoneCodeResponse(Instant expiresAt, String code, String smsTo, String smsBody) {
    }

    public record PhoneCodeCheckRequest(
            @NotBlank(message = "Vui lòng nhập số điện thoại") @Pattern(regexp = Patterns.PHONE, message = Patterns.PHONE_MESSAGE) String phone,
            @NotNull(message = "Thiếu mục đích xác minh") VerifyPurpose purpose,
            @NotBlank(message = "Thiếu mã xác minh") String code) {
    }

    /** PENDING: phòng khám chưa nhận được tin nhắn; VERIFIED: đã nhận, dùng mã này để đăng ký / đặt lại mật khẩu */
    public record PhoneCodeStatusResponse(String status) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Vui lòng nhập số điện thoại") @Pattern(regexp = Patterns.PHONE, message = Patterns.PHONE_MESSAGE) String phone,
            @NotBlank(message = "Vui lòng nhập mã xác minh") String code,
            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Vui lòng nhập số điện thoại") String phone,
            @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Vui lòng nhập số điện thoại") @Pattern(regexp = Patterns.PHONE, message = Patterns.PHONE_MESSAGE) String phone,
            @NotBlank(message = "Vui lòng nhập mã xác minh") String code,
            @NotBlank(message = "Vui lòng nhập mật khẩu mới") @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự") String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
            @NotBlank(message = "Vui lòng nhập mật khẩu mới") @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự") String newPassword) {
    }

    public record MeResponse(Long id, String phone, String email, Set<Role> roles, boolean mustChangePassword,
                             Long employeeId, String fullName) {
    }

    public record AuthResponse(String token, Instant expiresAt, MeResponse user) {
    }
}
