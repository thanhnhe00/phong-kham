package vn.phongkham.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
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
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.auth.service.AuthService;

@Tag(name = "Tài khoản")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Xin mã xác minh số điện thoại (đăng ký / quên mật khẩu)",
            description = "Người dùng nhắn smsBody tới smsTo từ chính số đã khai; cổng SMS chuyển tin về /sms/inbound.")
    @PostMapping("/phone-codes")
    public PhoneCodeResponse requestCode(@Valid @RequestBody PhoneCodeRequest req) {
        return service.requestCode(req);
    }

    @Operation(summary = "Hỏi phòng khám đã nhận tin nhắn xác minh chưa (PENDING / VERIFIED)")
    @PostMapping("/phone-codes/check")
    public PhoneCodeStatusResponse checkCode(@Valid @RequestBody PhoneCodeCheckRequest req) {
        return service.checkCode(req);
    }

    @Operation(summary = "Bệnh nhân đăng ký tài khoản bằng mã xác minh")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return service.register(req);
    }

    @Operation(summary = "Đăng nhập bằng số điện thoại và mật khẩu")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @Operation(summary = "Đặt lại mật khẩu bằng mã xác minh (mọi phiên cũ bị đăng xuất)")
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        service.resetPassword(req);
    }

    @Operation(summary = "Đổi mật khẩu (trả token mới, token cũ hết hiệu lực)")
    @PostMapping("/password/change")
    public AuthResponse changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest req) {
        return service.changePassword(me, req);
    }

    @Operation(summary = "Thông tin tài khoản đang đăng nhập")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser me) {
        return service.me(me);
    }
}
