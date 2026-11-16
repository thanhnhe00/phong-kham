package vn.phongkham.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.phongkham.IntegrationTest;
import vn.phongkham.auth.model.Role;

/** Bước 2 - UC03: đăng ký (xác minh bằng tin nhắn gửi đến phòng khám), đăng nhập, quên/đổi mật khẩu. */
class AuthTest extends IntegrationTest {

    private String requestCode(String phone, String purpose) throws Exception {
        return read(post("/auth/phone-codes", null, """
                {"phone":"%s","purpose":"%s"}""".formatted(phone, purpose)).andExpect(status().isOk()), "$.code");
    }

    private String check(String phone, String purpose, String code) throws Exception {
        return read(post("/auth/phone-codes/check", null, """
                {"phone":"%s","purpose":"%s","code":"%s"}""".formatted(phone, purpose, code)).andExpect(status().isOk()), "$.status");
    }

    private String login(String phone, String password) throws Exception {
        return read(post("/auth/login", null, """
                {"phone":"%s","password":"%s"}""".formatted(phone, password)).andExpect(status().isOk()), "$.token");
    }

    @Test
    void patientTextsCodeToClinicThenRegistersAndLogsIn() throws Exception {
        String phone = newPhone();
        String code = read(post("/auth/phone-codes", null, """
                {"phone":"%s","purpose":"REGISTER"}""".formatted(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsTo").value("0900000000")), "$.code");
        assertThat(code).hasSize(6);
        post("/auth/phone-codes/check", null, """
                {"phone":"%s","purpose":"REGISTER","code":"%s"}""".formatted(phone, code.equals("000000") ? "111111" : "000000"))
                .andExpect(status().isBadRequest());

        // Chưa nhắn tin thì chưa đăng ký được
        assertThat(check(phone, "REGISTER", code)).isEqualTo("PENDING");
        post("/auth/register", null, """
                {"phone":"%s","code":"%s","password":"matkhau123"}""".formatted(phone, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phòng khám chưa nhận được tin nhắn xác minh từ số " + phone));

        smsArrives(phone, "PKXM " + code).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("VERIFIED"));
        assertThat(check(phone, "REGISTER", code)).isEqualTo("VERIFIED");

        String token = read(post("/auth/register", null, """
                {"phone":"%s","code":"%s","password":"matkhau123"}""".formatted(phone, code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.roles[0]").value("PATIENT")), "$.token");
        get("/auth/me", token).andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(phone));
        login(phone, "matkhau123");
    }

    /** Xin mã mới như thể đã qua 60 giây chờ (dời giờ tạo các phiên cũ) */
    private String requestCodeAfterGap(String phone) throws Exception {
        jdbc.update("update phone_verifications set created_at = created_at - interval '2 minutes' where phone = ?", phone);
        return requestCode(phone, "REGISTER");
    }

    @Test
    void smsFromAnotherNumberOrFakeGatewayDoesNotVerify() throws Exception {
        String phone = newPhone();
        String code = requestCode(phone, "REGISTER");

        // Kẻ xấu khai số của người khác rồi tự nhắn mã từ SIM của mình: số gửi không khớp
        smsArrives(newPhone(), "PKXM " + code).andExpect(jsonPath("$.result").value("NO_MATCH"));
        // Gọi thẳng /sms/inbound giả làm cổng: không có token hoặc sai token
        mvc.perform(MockMvcRequestBuilders.post("/sms/inbound").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"%s\",\"body\":\"PKXM %s\"}".formatted(phone, code))).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/sms/inbound").header("X-Gateway-Token", "doan-bua")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"%s\",\"body\":\"PKXM %s\"}".formatted(phone, code))).andExpect(status().isUnauthorized());

        assertThat(check(phone, "REGISTER", code)).isEqualTo("PENDING");
    }

    @Test
    void androidGatewaySignedMessageVerifiesPhoneWrittenWithCountryCode() throws Exception {
        String phone = newPhone();
        String code = requestCode(phone, "REGISTER");
        String body = """
                {"event":"sms:received","payload":{"phoneNumber":"+84%s","message":"PKXM %s"}}""".formatted(phone.substring(1), code);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = HexFormat.of().formatHex(mac.doFinal((body + ts).getBytes(StandardCharsets.UTF_8)));

        // Chữ ký sai bị từ chối
        mvc.perform(MockMvcRequestBuilders.post("/sms/inbound").header("X-Signature", "00" + sig.substring(2))
                .header("X-Timestamp", ts).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/sms/inbound").header("X-Signature", sig).header("X-Timestamp", ts)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("VERIFIED"));
        assertThat(check(phone, "REGISTER", code)).isEqualTo("VERIFIED");
    }

    @Test
    void wrongCodeIsCountedAndBlockedAfterFiveTries() throws Exception {
        String phone = newPhone();
        String code = requestCode(phone, "REGISTER");
        smsArrives(phone, "PKXM " + code);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            post("/auth/register", null, """
                    {"phone":"%s","code":"%s","password":"matkhau123"}""".formatted(phone, wrong))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Mã xác minh không đúng hoặc đã hết hạn"));
        }
        // Đã sai 5 lần: mã đúng cũng không dùng được nữa
        post("/auth/register", null, """
                {"phone":"%s","code":"%s","password":"matkhau123"}""".formatted(phone, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nhập sai quá nhiều lần, hãy yêu cầu mã mới"));
    }

    @Test
    void verifiedCodeCanBeUsedOnlyOnce() throws Exception {
        String phone = newPhone();
        createUser(phone, "matkhau123", Role.PATIENT);
        String code = requestCode(phone, "RESET_PASSWORD");
        smsArrives(phone, "PKXM " + code);
        post("/auth/password/reset", null, """
                {"phone":"%s","code":"%s","newPassword":"matkhaumoi1"}""".formatted(phone, code)).andExpect(status().isNoContent());
        post("/auth/password/reset", null, """
                {"phone":"%s","code":"%s","newPassword":"matkhaumoi2"}""".formatted(phone, code)).andExpect(status().isBadRequest());
        login(phone, "matkhaumoi1");
    }

    @Test
    void cannotRegisterExistingPhoneOrResendTooSoonOrTooOften() throws Exception {
        String used = newPhone();
        createUser(used, "matkhau123", Role.PATIENT);
        post("/auth/phone-codes", null, """
                {"phone":"%s","purpose":"REGISTER"}""".formatted(used))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Số điện thoại đã được đăng ký"));

        String phone = newPhone();
        requestCode(phone, "REGISTER");
        post("/auth/phone-codes", null, """
                {"phone":"%s","purpose":"REGISTER"}""".formatted(phone)).andExpect(status().isTooManyRequests());

        // Qua 60 giây vẫn chỉ được 5 lần mỗi giờ cho một số
        for (int i = 0; i < 4; i++) {
            requestCodeAfterGap(phone);
        }
        jdbc.update("update phone_verifications set created_at = created_at - interval '2 minutes' where phone = ?", phone);
        post("/auth/phone-codes", null, """
                {"phone":"%s","purpose":"REGISTER"}""".formatted(phone))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Số điện thoại này đã yêu cầu xác minh quá nhiều lần, vui lòng thử lại sau 1 giờ"));
    }

    @Test
    void invalidInputGivesFieldErrorsInVietnamese() throws Exception {
        post("/auth/register", null, """
                {"phone":"123","code":"","password":"ngan"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").value("Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0"))
                .andExpect(jsonPath("$.errors.password").value("Mật khẩu phải từ 8 đến 72 ký tự"));
    }

    @Test
    void fiveWrongPasswordsLockLoginTemporarily() throws Exception {
        String phone = newPhone();
        createUser(phone, "matkhau123", Role.PATIENT);
        for (int i = 0; i < 5; i++) {
            post("/auth/login", null, """
                    {"phone":"%s","password":"sai-mat-khau"}""".formatted(phone))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Số điện thoại hoặc mật khẩu không đúng"));
        }
        post("/auth/login", null, """
                {"phone":"%s","password":"matkhau123"}""".formatted(phone)).andExpect(status().isTooManyRequests());
    }

    @Test
    void resetPasswordLogsOutOldSessions() throws Exception {
        String phone = newPhone();
        createUser(phone, "matkhau123", Role.PATIENT);
        String oldToken = login(phone, "matkhau123");

        String code = requestCode(phone, "RESET_PASSWORD");
        smsArrives(phone, "pkxm " + code + " cam on");
        post("/auth/password/reset", null, """
                {"phone":"%s","code":"%s","newPassword":"matkhaumoi1"}""".formatted(phone, code))
                .andExpect(status().isNoContent());

        get("/auth/me", oldToken).andExpect(status().isUnauthorized());
        login(phone, "matkhaumoi1");
    }

    @Test
    void resetCodeForUnknownPhoneLooksTheSame() throws Exception {
        String phone = newPhone();
        String code = read(post("/auth/phone-codes", null, """
                {"phone":"%s","purpose":"RESET_PASSWORD"}""".formatted(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsTo").value("0900000000")), "$.code");
        // Nhắn tin cũng không tạo được gì vì số này không có phiên thật
        smsArrives(phone, "PKXM " + code).andExpect(jsonPath("$.result").value("NO_MATCH"));
    }

    @Test
    void changePasswordReturnsNewTokenAndKillsOldOne() throws Exception {
        String phone = newPhone();
        createUser(phone, "matkhau123", Role.PATIENT);
        String oldToken = login(phone, "matkhau123");

        post("/auth/password/change", oldToken, """
                {"currentPassword":"sai-roi-nhe","newPassword":"matkhaumoi1"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng"));

        String newToken = read(post("/auth/password/change", oldToken, """
                {"currentPassword":"matkhau123","newPassword":"matkhaumoi1"}""").andExpect(status().isOk()), "$.token");

        get("/auth/me", oldToken).andExpect(status().isUnauthorized());
        get("/auth/me", newToken).andExpect(status().isOk());
    }
}
