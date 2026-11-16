package vn.phongkham.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import vn.phongkham.IntegrationTest;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;

/** Bước 2 - UC31, UC34: tạo tài khoản nhân viên, mật khẩu tạm, khóa tài khoản, phân quyền. */
class AccountAdminTest extends IntegrationTest {

    private String login(String phone, String password) throws Exception {
        return read(post("/auth/login", null, """
                {"phone":"%s","password":"%s"}""".formatted(phone, password)).andExpect(status().isOk()), "$.token");
    }

    @Test
    void newStaffMustChangeTemporaryPasswordBeforeWorking() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        String phone = newPhone();
        String temp = read(post("/staff", manager, """
                {"fullName":"Lê Văn Tân","phone":"%s","roles":["RECEPTIONIST"]}""".formatted(phone))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staff.employeeCode").value(org.hamcrest.Matchers.startsWith("NV")))
                .andExpect(jsonPath("$.staff.roles[0]").value("RECEPTIONIST")), "$.temporaryPassword");

        String token = login(phone, temp);
        get("/auth/me", token).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.fullName").value("Lê Văn Tân"));
        get("/rooms", token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn cần đổi mật khẩu tạm trước khi tiếp tục"));

        String newToken = read(post("/auth/password/change", token, """
                {"currentPassword":"%s","newPassword":"matkhaumoi1"}""".formatted(temp)).andExpect(status().isOk()), "$.token");
        get("/rooms", newToken).andExpect(status().isOk());
    }

    @Test
    void staffCannotHavePatientRoleAndDoctorNeedsSpecialty() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        post("/staff", admin, """
                {"fullName":"A","phone":"%s","roles":["PATIENT"]}""".formatted(newPhone()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vai trò nhân viên không hợp lệ"));
        post("/staff", admin, """
                {"fullName":"A","phone":"%s","roles":["DOCTOR"]}""".formatted(newPhone()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Bác sĩ phải có ít nhất một chuyên khoa"));
    }

    @Test
    void lockingAccountKillsSessionAndBlocksLogin() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String phone = newPhone();
        User u = createUser(phone, "matkhau123", Role.PATIENT);
        String userToken = login(phone, "matkhau123");

        patch("/admin/users/" + u.getId() + "/status", admin, """
                {"status":"LOCKED"}""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED"));

        get("/auth/me", userToken).andExpect(status().isUnauthorized());
        post("/auth/login", null, """
                {"phone":"%s","password":"matkhau123"}""".formatted(phone))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị khóa, vui lòng liên hệ phòng khám"));

        patch("/admin/users/" + u.getId() + "/status", admin, """
                {"status":"ACTIVE"}""").andExpect(status().isOk());
        login(phone, "matkhau123");
    }

    @Test
    void adminCannotLockSelf() throws Exception {
        String phone = newPhone();
        User admin = createUser(phone, "matkhau123", Role.ADMIN);
        patch("/admin/users/" + admin.getId() + "/status", login(phone, "matkhau123"), """
                {"status":"LOCKED"}""").andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminManagesAccounts() throws Exception {
        get("/admin/users", tokenFor(Role.RECEPTIONIST)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này"));
        get("/admin/users", tokenFor(Role.PATIENT)).andExpect(status().isForbidden());
        get("/admin/users?role=ADMIN", tokenFor(Role.ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void adminResetPasswordIssuesTemporaryPassword() throws Exception {
        String phone = newPhone();
        User u = createUser(phone, "matkhau123", Role.DOCTOR);
        String oldToken = login(phone, "matkhau123");

        String temp = read(post("/admin/users/" + u.getId() + "/reset-password", tokenFor(Role.ADMIN), "{}")
                .andExpect(status().isOk()), "$.temporaryPassword");

        get("/auth/me", oldToken).andExpect(status().isUnauthorized());
        get("/auth/me", login(phone, temp)).andExpect(jsonPath("$.mustChangePassword").value(true));
    }
}
