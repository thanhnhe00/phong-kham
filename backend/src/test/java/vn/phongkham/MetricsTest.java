package vn.phongkham;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Giám sát: Prometheus đọc được số liệu bằng token; người ngoài (kể cả đã đăng nhập) thì không. */
@AutoConfigureMetrics // test mặc định tắt xuất số liệu; bật lại để kiểm tra đúng như khi chạy thật
class MetricsTest extends IntegrationTest {

    @Test
    void prometheusReadsMetricsWithTokenOthersCannot() throws Exception {
        get("/specialties", null).andExpect(status().isOk()); // tạo ít nhất một request để có số liệu HTTP

        mvc.perform(MockMvcRequestBuilders.get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/actuator/prometheus").header("Authorization", "Bearer doan-bua"))
                .andExpect(status().isUnauthorized());
        get("/actuator/prometheus", tokenFor(vn.phongkham.auth.model.Role.ADMIN)).andExpect(status().isUnauthorized());

        mvc.perform(MockMvcRequestBuilders.get("/actuator/prometheus").header("Authorization", "Bearer test-metrics-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("hikaricp_connections_active")))
                .andExpect(content().string(containsString("application=\"phong-kham\"")));
        // kiểm tra sức khỏe vẫn công khai
        get("/actuator/health", null).andExpect(status().isOk());
    }
}
