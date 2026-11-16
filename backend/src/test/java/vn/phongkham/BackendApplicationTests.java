package vn.phongkham;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import vn.phongkham.auth.model.Role;

/** Bước 1 - nền tảng: app khởi động, Flyway + Hibernate khớp schema, lỗi trả JSON tiếng Việt, có Swagger. */
class BackendApplicationTests extends IntegrationTest {

    @Test
    void healthIsPublic() throws Exception {
        get("/actuator/health", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void swaggerDocsArePublic() throws Exception {
        get("/v3/api-docs", null).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/auth/login']").exists());
    }

    @Test
    void protectedRouteWithoutTokenGives401InVietnamese() throws Exception {
        get("/rooms", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập hoặc phiên đăng nhập đã hết hiệu lực"));
    }

    @Test
    void garbageTokenGives401() throws Exception {
        get("/rooms", "khong-phai-token").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRouteGives404Json() throws Exception {
        get("/khong-ton-tai", tokenFor(Role.ADMIN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đường dẫn"));
    }

    @Test
    void malformedJsonGives400() throws Exception {
        post("/auth/login", null, "{khong phai json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên không đúng định dạng"));
    }
}
