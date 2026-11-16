package vn.phongkham.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.model.PhoneVerification;
import vn.phongkham.auth.model.VerifyPurpose;
import vn.phongkham.auth.repository.PhoneVerificationRepository;
import vn.phongkham.common.exception.ApiException;

/**
 * Xác minh số điện thoại kiểu "nhắn tin để xác thực": thay vì phòng khám gửi mã đi (tốn tiền, kẻ xấu bấm
 * "gửi mã" liên tục là phòng khám mất tiền), NGƯỜI DÙNG nhắn "PKXM &lt;mã&gt;" từ chính SIM của họ tới số của
 * phòng khám. Bằng chứng chính chủ là SỐ NGƯỜI GỬI do nhà mạng xác định; mã chỉ để nối tin nhắn với đúng phiên.
 *
 * <ol>
 *   <li>App xin mã: {@link #issue} trả mã 6 số, app hiện mã và mở sẵn màn soạn tin.</li>
 *   <li>Cổng SMS (điện thoại của phòng khám) chuyển tin tới /sms/inbound: {@link #receiveSms}. Đúng mã và đúng số
 *       người gửi thì phiên được đánh dấu đã xác minh.</li>
 *   <li>App hỏi lại {@link #check} tới khi đã xác minh, rồi đăng ký / đặt lại mật khẩu: {@link #consume}.</li>
 * </ol>
 */
@Service
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);
    public static final String SMS_PREFIX = "PKXM";
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_GAP = Duration.ofSeconds(60);
    /** Một số điện thoại xin tối đa ngần này phiên mỗi giờ */
    static final int MAX_PER_PHONE_HOUR = 5;
    static final int MAX_ATTEMPTS = 5;
    /** Đã nhắn tin xác minh xong thì phải đăng ký / đặt lại mật khẩu trong thời gian này */
    static final Duration USE_WITHIN = Duration.ofMinutes(30);
    private static final String INVALID = "Mã xác minh không đúng hoặc đã hết hạn";
    private static final Pattern CODE_IN_SMS = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    public enum Status { PENDING, VERIFIED }

    public record Issued(Instant expiresAt, String code) {
    }

    private final PhoneVerificationRepository repo;
    private final SecureRandom random = new SecureRandom();

    public PhoneVerificationService(PhoneVerificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Issued issue(String phone, VerifyPurpose purpose) {
        Instant now = Instant.now();
        repo.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose)
                .filter(v -> v.getCreatedAt().isAfter(now.minus(RESEND_GAP)))
                .ifPresent(v -> {
                    throw ApiException.tooManyRequests("Vui lòng chờ 60 giây trước khi yêu cầu mã mới");
                });
        if (repo.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= MAX_PER_PHONE_HOUR) {
            throw ApiException.tooManyRequests("Số điện thoại này đã yêu cầu xác minh quá nhiều lần, vui lòng thử lại sau 1 giờ");
        }
        String code = newCode();
        PhoneVerification v = new PhoneVerification();
        v.setPhone(phone);
        v.setPurpose(purpose);
        v.setCodeHash(hash(phone, code));
        v.setExpiresAt(now.plus(CODE_TTL));
        repo.save(v);
        return new Issued(v.getExpiresAt(), code);
    }

    /** Mã giả cho số không có tài khoản khi quên mật khẩu: trả lời giống hệt để người ngoài không dò được số nào có tài khoản */
    public Issued decoy() {
        return new Issued(Instant.now().plus(CODE_TTL), newCode());
    }

    /**
     * App hỏi phòng khám đã nhận được tin nhắn chưa. Mã sai bị đếm như nhập sai.
     * Nơi gọi phải dùng @Transactional(noRollbackFor = ApiException.class) để lần nhập sai vẫn được lưu.
     */
    public Status check(String phone, VerifyPurpose purpose, String code) {
        PhoneVerification v = find(phone, purpose, code);
        if (v.getVerifiedAt() == null && v.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Mã xác minh đã hết hạn, vui lòng yêu cầu mã mới");
        }
        return v.getVerifiedAt() == null ? Status.PENDING : Status.VERIFIED;
    }

    /**
     * Dùng phiên đã xác minh để đăng ký / đặt lại mật khẩu: đúng số, đúng mục đích, đúng mã, đã nhắn tin,
     * chưa dùng, xác minh chưa quá 30 phút. Đánh dấu đã dùng nên không dùng lại được.
     * Nơi gọi phải dùng @Transactional(noRollbackFor = ApiException.class).
     */
    public void consume(String phone, VerifyPurpose purpose, String code) {
        PhoneVerification v = find(phone, purpose, code);
        if (v.getVerifiedAt() == null) {
            throw ApiException.badRequest("Phòng khám chưa nhận được tin nhắn xác minh từ số " + phone);
        }
        if (v.getVerifiedAt().isBefore(Instant.now().minus(USE_WITHIN))) {
            throw ApiException.badRequest("Phiên xác minh đã hết hạn, vui lòng xác minh lại");
        }
        v.setUsedAt(Instant.now());
    }

    /**
     * Một tin nhắn cổng SMS chuyển đến. Đúng mã của một phiên đang chờ VÀ số người gửi trùng số của phiên đó
     * thì phiên được xác minh. Trả kết quả để ghi log / trả về cho cổng.
     */
    @Transactional
    public String receiveSms(String fromRaw, String body) {
        String phone = normalizePhone(fromRaw);
        Matcher m = CODE_IN_SMS.matcher(body == null ? "" : body);
        String result;
        if (phone == null) {
            // Người gửi là email (iMessage) hoặc tên danh bạ: không chứng minh được số điện thoại
            result = "INVALID_SENDER";
        } else if (!m.find()) {
            result = "NO_CODE";
        } else {
            String h = hash(phone, m.group(1));
            var match = repo.findByPhoneAndVerifiedAtIsNullAndExpiresAtAfter(phone, Instant.now()).stream()
                    .filter(v -> same(v.getCodeHash(), h))
                    .findFirst();
            match.ifPresent(v -> v.setVerifiedAt(Instant.now()));
            result = match.isPresent() ? "VERIFIED" : "NO_MATCH";
        }
        log.info("Tin nhắn xác minh từ {}: {}", phone == null ? "(không rõ số)" : phone, result);
        return result;
    }

    /** Phiên chưa dùng của số này khớp mã; sai mã thì đếm vào phiên mới nhất, quá 5 lần là khóa */
    private PhoneVerification find(String phone, VerifyPurpose purpose, String code) {
        List<PhoneVerification> open = repo.findByPhoneAndPurposeAndUsedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
                phone, purpose, Instant.now().minus(CODE_TTL.plus(USE_WITHIN)));
        if (open.isEmpty()) {
            throw ApiException.badRequest(INVALID);
        }
        PhoneVerification newest = open.getFirst();
        if (newest.getAttempts() >= MAX_ATTEMPTS) {
            throw ApiException.badRequest("Nhập sai quá nhiều lần, hãy yêu cầu mã mới");
        }
        String h = hash(phone, code == null ? "" : code);
        return open.stream().filter(v -> same(v.getCodeHash(), h)).findFirst().orElseThrow(() -> {
            newest.setAttempts((short) (newest.getAttempts() + 1));
            return ApiException.badRequest(INVALID);
        });
    }

    /** "+84 912 345 678", "84912345678", "0912.345.678" → "0912345678"; không phải số di động VN thì null */
    static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String d = raw.replaceAll("[\\s.\\-()]", "");
        if (d.startsWith("+84")) {
            d = "0" + d.substring(3);
        } else if (d.startsWith("84") && d.length() == 11) {
            d = "0" + d.substring(2);
        }
        return d.matches("0\\d{9}") ? d : null;
    }

    private String newCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String phone, String code) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((phone + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
