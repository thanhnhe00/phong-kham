package vn.phongkham.patient;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import vn.phongkham.ClinicFixtures;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;

/** Bước 5 - UC04, UC13: hồ sơ bệnh nhân, kiểm tra trùng, liên kết tài khoản app, người đại diện, dị ứng. */
class PatientTest extends ClinicFixtures {

    private static final String PROFILE = """
            {"fullName":"Trần Văn Bệnh","dateOfBirth":"1985-03-15","sex":"MALE","identityType":"CCCD",
             "identityNumber":"001085012345","phone":"0912345678","ward":"Phường Bến Nghé","province":"TP. Hồ Chí Minh"}""";

    @Test
    void receptionistCreatesPatientAndDuplicatesAreDetectedNotMerged() throws Exception {
        String receptionist = tokenFor(Role.RECEPTIONIST);
        post("/patients", receptionist, PROFILE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientCode").value("BN000001"))
                .andExpect(jsonPath("$.identityType").value("CCCD"));

        get("/patients/possible-duplicates?fullName=tran van benh&dateOfBirth=1985-03-15", receptionist)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        get("/patients/possible-duplicates?fullName=Trần Văn Bệnh&dateOfBirth=1985-03-15", receptionist)
                .andExpect(jsonPath("$", hasSize(1)));
        get("/patients/possible-duplicates?fullName=Người Khác&dateOfBirth=1985-03-15&phone=0912345678", receptionist)
                .andExpect(jsonPath("$", hasSize(1)));

        post("/patients", receptionist, PROFILE).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đã có hồ sơ bệnh nhân với số giấy tờ này (BN000001)"));
        get("/patients?q=001085012345", receptionist).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void identityRulesFollowPrescriptionRequirements() throws Exception {
        String receptionist = tokenFor(Role.RECEPTIONIST);
        post("/patients", receptionist, """
                {"fullName":"A","dateOfBirth":"1990-01-01","identityType":"CCCD","identityNumber":"123"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Số CCCD phải gồm 12 chữ số"));
        post("/patients", receptionist, """
                {"fullName":"A","dateOfBirth":"1990-01-01","identityNumber":"B1234567"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vui lòng chọn loại giấy tờ tùy thân"));
        post("/patients", receptionist, """
                {"fullName":"Bé Na","dateOfBirth":"2025-06-01"}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.identityType").value("NOT_PROVIDED"));
        post("/patients", tokenFor(Role.DOCTOR), PROFILE).andExpect(status().isForbidden());
    }

    @Test
    void parentManagesOwnAndChildProfilesButOnlyOneSelf() throws Exception {
        PatientApp mom = patientApp("Nguyễn Thị Mẹ");
        post("/me/patients", mom.token(), """
                {"relationship":"PARENT","profile":{"fullName":"Nguyễn Bé Con","dateOfBirth":"2023-01-10"}}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.relationship").value("PARENT"));
        post("/me/patients", mom.token(), """
                {"relationship":"SELF","profile":{"fullName":"X","dateOfBirth":"1990-01-01"}}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Tài khoản đã có hồ sơ của chính mình"));

        get("/me/patients", mom.token()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void linkCodeIsSingleUseAndConnectsAppToClinicRecord() throws Exception {
        String receptionist = tokenFor(Role.RECEPTIONIST);
        Integer patientId = read(post("/patients", receptionist, PROFILE), "$.id");
        String code = read(post("/patients/" + patientId + "/link-code", receptionist, "{}").andExpect(status().isOk()), "$.code");

        User dad = createUser(newPhone(), "matkhau123", Role.PATIENT);
        String dadToken = tokens.issue(dad).token();
        post("/me/patients/link", dadToken, """
                {"code":"%s","relationship":"SELF"}""".formatted(code.toLowerCase()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patient.patientCode").value("BN000001"));
        get("/me/patients/" + patientId, dadToken).andExpect(status().isOk());

        String other = tokens.issue(createUser(newPhone(), "matkhau123", Role.PATIENT)).token();
        post("/me/patients/link", other, """
                {"code":"%s","relationship":"FAMILY_MEMBER"}""".formatted(code))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Mã liên kết không đúng hoặc đã hết hạn"));
        // Không liên kết thì không xem được (404 để không lộ hồ sơ tồn tại)
        get("/me/patients/" + patientId, other).andExpect(status().isNotFound());
    }

    @Test
    void patientEditsOnlyContactFieldsAndSeesAllergiesRecordedByNurse() throws Exception {
        PatientApp p = patientApp("Phạm Văn Dị Ứng");
        post("/patients/" + p.patientId() + "/allergies", tokenFor(Role.NURSE), """
                {"allergen":"Penicillin","reaction":"Nổi mề đay","severity":"SEVERE"}""").andExpect(status().isCreated());

        put("/me/patients/" + p.patientId(), p.token(), """
                {"phone":"0987654321","addressLine":"12 Lê Lợi","province":"Hà Nội"}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.patient.phone").value("0987654321"))
                .andExpect(jsonPath("$.patient.fullName").value("Phạm Văn Dị Ứng"));

        get("/me/patients/" + p.patientId(), p.token())
                .andExpect(jsonPath("$.allergies[0].allergen").value("Penicillin"))
                .andExpect(jsonPath("$.allergies[0].severity").value("SEVERE"));
    }

    @Test
    void representativeIsRecordedWithVerifier() throws Exception {
        String receptionist = tokenFor(Role.RECEPTIONIST);
        Integer child = read(post("/patients", receptionist, """
                {"fullName":"Bé Bi","dateOfBirth":"2024-02-02"}"""), "$.id");
        post("/patients/" + child + "/representatives", receptionist, """
                {"fullName":"Lê Văn Bố","relationship":"Cha","phone":"0901112223","identityNumber":"001080000111"}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.verifiedAt").exists());
        get("/patients/" + child, tokenFor(Role.DOCTOR)).andExpect(jsonPath("$.representatives", hasSize(1)))
                .andExpect(jsonPath("$.patient.ageInMonths").isNumber());
    }
}
