package vn.phongkham.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.phongkham.IntegrationTest;

/** Chống spam theo IP: một máy tạo hàng loạt tài khoản / xin mã liên tục thì bị chặn, máy khác không ảnh hưởng. */
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitTest extends IntegrationTest {

    private ResultActions requestCodeFrom(String ip, String phone) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/auth/phone-codes")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\",\"purpose\":\"REGISTER\"}".formatted(phone)));
    }

    @Test
    void oneIpRequestingCodesForManyPhonesIsBlockedOthersAreNot() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestCodeFrom("203.0.113.7", newPhone()).andExpect(status().isOk());
        }
        requestCodeFrom("203.0.113.7", newPhone())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Bạn thao tác quá nhiều lần")));
        requestCodeFrom("198.51.100.9", newPhone()).andExpect(status().isOk());
    }

    @Test
    void loginSprayFromOneIpIsBlocked() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(MockMvcRequestBuilders.post("/auth/login").with(r -> {
                r.setRemoteAddr("203.0.113.50");
                return r;
            }).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"%s\",\"password\":\"thu-mat-khau\"}".formatted(newPhone())))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(MockMvcRequestBuilders.post("/auth/login").with(r -> {
            r.setRemoteAddr("203.0.113.50");
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"0900000001\",\"password\":\"thu-mat-khau\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
