package vn.phongkham.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chống spam theo địa chỉ IP cho các thao tác dễ bị lạm dụng: xin mã xác minh, đăng ký, đăng nhập,
 * đặt lại mật khẩu, đặt lịch. Vượt ngưỡng thì trả 429 kèm Retry-After, chưa vào tới controller.
 * Đứng trước Spring Security để chặn sớm, không tốn công kiểm tra token / mật khẩu.
 *
 * shortcut: đếm trong bộ nhớ của một tiến trình (cửa sổ cố định), chạy nhiều bản backend thì chuyển sang Redis.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Một luật: phương thức + đường dẫn (sau /api), tối đa bao nhiêu lần trong một cửa sổ thời gian */
    record Rule(String method, String path, int limit, Duration window) {
    }

    static final List<Rule> RULES = List.of(
            new Rule("POST", "/auth/phone-codes", 10, Duration.ofHours(1)),
            new Rule("POST", "/auth/phone-codes/check", 120, Duration.ofMinutes(10)), // app hỏi lại 3 giây/lần
            new Rule("POST", "/auth/register", 10, Duration.ofHours(1)),
            new Rule("POST", "/auth/login", 30, Duration.ofMinutes(10)),
            new Rule("POST", "/auth/password/reset", 10, Duration.ofHours(1)),
            new Rule("POST", "/me/appointments", 20, Duration.ofHours(1)));

    private record Window(long startMillis, AtomicInteger count) {
    }

    private final boolean enabled;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger sinceCleanup = new AtomicInteger();

    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Rule rule = RULES.stream()
                .filter(r -> r.method().equals(request.getMethod()) && r.path().equals(path))
                .findFirst().orElse(null);
        if (rule != null) {
            long retryAfter = hit(rule, request.getRemoteAddr(), System.currentTimeMillis());
            if (retryAfter > 0) {
                long minutes = Math.max(1, (retryAfter + 59) / 60);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"message\":\"Bạn thao tác quá nhiều lần, vui lòng thử lại sau " + minutes + " phút\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Ghi nhận một lần gọi; trả 0 nếu còn trong hạn mức, ngược lại số giây phải chờ */
    long hit(Rule rule, String ip, long now) {
        if (sinceCleanup.incrementAndGet() >= 1000) {
            sinceCleanup.set(0);
            windows.values().removeIf(w -> now - w.startMillis() > Duration.ofHours(1).toMillis());
        }
        long size = rule.window().toMillis();
        Window w = windows.compute(rule.path() + "|" + ip,
                (k, old) -> old == null || now - old.startMillis() >= size ? new Window(now, new AtomicInteger()) : old);
        if (w.count().incrementAndGet() <= rule.limit()) {
            return 0;
        }
        return Math.max(1, (w.startMillis() + size - now + 999) / 1000);
    }
}
