package vn.phongkham.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.SmsGatewayAuth;
import vn.phongkham.auth.service.PhoneVerificationService;

@Tag(name = "Tài khoản")
@RestController
@RequestMapping("/sms")
public class SmsController {

    private final SmsGatewayAuth gateway;
    private final PhoneVerificationService verifications;

    public SmsController(SmsGatewayAuth gateway, PhoneVerificationService verifications) {
        this.gateway = gateway;
        this.verifications = verifications;
    }

    /** Thân đọc dạng chuỗi gốc vì chữ ký HMAC của cổng Android tính trên đúng các byte đã gửi */
    @Operation(summary = "Cổng SMS chuyển tin nhắn xác minh đến (X-Gateway-Token hoặc chữ ký HMAC)")
    @PostMapping("/inbound")
    public Map<String, String> inbound(@RequestBody(required = false) String raw,
                                       @RequestHeader(value = "X-Signature", required = false) String signature,
                                       @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
                                       @RequestHeader(value = "X-Gateway-Token", required = false) String token) {
        var sms = gateway.authenticate(raw, signature, timestamp, token);
        return Map.of("result", verifications.receiveSms(sms.from(), sms.body()));
    }
}
