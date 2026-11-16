package vn.phongkham.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Ghi lỗi JSON ở tầng filter (trước khi vào controller nên không qua GlobalExceptionHandler). */
final class JsonErrors {

    private JsonErrors() {
    }

    /** message chỉ là chuỗi hằng trong code, không chứa dấu nháy nên không cần escape */
    static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
