package vn.phongkham.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.phongkham.common.exception.ApiException;

/**
 * Xác thực tin nhắn do cổng SMS (điện thoại của phòng khám) chuyển tới, để không ai giả được "tin nhắn đến".
 * <ul>
 *   <li>Android "SMS Gateway": header X-Signature = HMAC-SHA256(thân gốc + X-Timestamp, SMS_GATEWAY_SIGNING_KEY),
 *       lệch giờ tối đa 5 phút; thân {"event":"sms:received","payload":{"phoneNumber","message"}}.</li>
 *   <li>iPhone Phím tắt hoặc cổng khác: header X-Gateway-Token = SMS_GATEWAY_TOKEN; thân {"from","body"}.</li>
 * </ul>
 * Chưa cấu hình khóa nào thì mọi tin bị từ chối.
 */
@Component
public class SmsGatewayAuth {

    public record InboundSms(String source, String from, String body) {
    }

    private static final long MAX_SKEW_SECONDS = 300;
    private final String token;
    private final String signingKey;
    private final JsonMapper json;

    public SmsGatewayAuth(@Value("${app.sms.gateway-token:}") String token,
                          @Value("${app.sms.signing-key:}") String signingKey, JsonMapper json) {
        this.token = token.trim();
        this.signingKey = signingKey.trim();
        this.json = json;
    }

    public InboundSms authenticate(String rawBody, String signature, String timestamp, String givenToken) {
        if (!signingKey.isEmpty() && signature != null && timestamp != null) {
            long ts;
            try {
                ts = Long.parseLong(timestamp.trim());
            } catch (NumberFormatException e) {
                throw ApiException.unauthorized("Chữ ký cổng SMS không hợp lệ");
            }
            String expected = hmac(rawBody + timestamp.trim());
            if (!same(signature.trim().toLowerCase(), expected)
                    || Math.abs(Instant.now().getEpochSecond() - ts) > MAX_SKEW_SECONDS) {
                throw ApiException.unauthorized("Chữ ký cổng SMS không hợp lệ");
            }
            JsonNode n = parse(rawBody);
            String event = n.path("event").asString("");
            if (!event.isEmpty() && !event.equals("sms:received")) {
                throw ApiException.badRequest("Chỉ nhận sự kiện sms:received");
            }
            JsonNode p = n.path("payload");
            // bản mới gửi số người gửi ở "sender", bản cũ ở "phoneNumber"
            String from = p.path("sender").asString("");
            return new InboundSms("ANDROID_GATEWAY", from.isEmpty() ? p.path("phoneNumber").asString("") : from,
                    p.path("message").asString(""));
        }
        if (!token.isEmpty() && givenToken != null && same(givenToken.trim(), token)) {
            JsonNode n = parse(rawBody);
            return new InboundSms("TOKEN", n.path("from").asString(""), n.path("body").asString(""));
        }
        throw ApiException.unauthorized("Cổng SMS chưa được xác thực");
    }

    private JsonNode parse(String raw) {
        try {
            return json.readTree(raw == null ? "{}" : raw);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Nội dung tin nhắn không đúng định dạng JSON");
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
