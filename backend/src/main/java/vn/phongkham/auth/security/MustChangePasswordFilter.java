package vn.phongkham.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Tài khoản đang dùng mật khẩu tạm chỉ được xem thông tin của mình và đổi mật khẩu. */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of("/auth/me", "/auth/password/change");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (auth != null && auth.getPrincipal() instanceof AuthUser user && user.mustChangePassword()
                && !ALLOWED.contains(path)) {
            JsonErrors.write(response, HttpServletResponse.SC_FORBIDDEN, "Bạn cần đổi mật khẩu tạm trước khi tiếp tục");
            return;
        }
        chain.doFilter(request, response);
    }
}
