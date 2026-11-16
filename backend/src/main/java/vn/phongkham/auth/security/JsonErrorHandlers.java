package vn.phongkham.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 401/403 ở tầng Spring Security trả JSON tiếng Việt giống các lỗi khác. */
public final class JsonErrorHandlers {

    public static final AuthenticationEntryPoint UNAUTHORIZED = (req, res, ex) ->
            JsonErrors.write(res, HttpServletResponse.SC_UNAUTHORIZED, "Chưa đăng nhập hoặc phiên đăng nhập đã hết hiệu lực");

    public static final AccessDeniedHandler FORBIDDEN = (req, res, ex) ->
            JsonErrors.write(res, HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");

    private JsonErrorHandlers() {
    }
}
