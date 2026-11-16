package vn.phongkham.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.notification.service.NotificationService;
import vn.phongkham.notification.service.NotificationService.NotificationResponse;

@Tag(name = "Thông báo của tôi")
@RestController
@RequestMapping("/me")
public class NotificationController {

    public record PushTokenRequest(@NotBlank(message = "Thiếu token") @Size(max = 255, message = "Token quá dài") String token,
                                   @Size(max = 20, message = "Nền tảng tối đa 20 ký tự") String platform) {
    }

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthUser me,
                                                   @PageableDefault(size = 30) Pageable pageable) {
        return service.mine(me, pageable);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser me) {
        return Map.of("unread", service.unreadCount(me));
    }

    @PostMapping("/notifications/{id}/read")
    public NotificationResponse read(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.markRead(me, id);
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> readAll(@AuthenticationPrincipal AuthUser me) {
        return Map.of("updated", service.markAllRead(me));
    }

    @Operation(summary = "Đăng ký thiết bị nhận thông báo đẩy (Expo push token)")
    @PostMapping("/push-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerPushToken(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PushTokenRequest req) {
        service.registerPushToken(me, req.token(), req.platform());
    }

    @DeleteMapping("/push-tokens/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePushToken(@AuthenticationPrincipal AuthUser me, @PathVariable String token) {
        service.removePushToken(me, token);
    }
}
