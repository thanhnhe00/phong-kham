package vn.phongkham.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.phongkham.IntegrationTest;
import vn.phongkham.auth.model.Role;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;
import vn.phongkham.medicine.repository.MedicineRepository;
import vn.phongkham.room.repository.RoomRepository;
import vn.phongkham.seed.DemoDataSeeder;
import vn.phongkham.specialty.repository.SpecialtyRepository;
import vn.phongkham.staff.repository.DoctorHistoryRepository;
import vn.phongkham.staff.repository.DoctorProfileRepository;
import vn.phongkham.staff.repository.EmployeeSpecialtyRepository;

/** Bước 3 - UC01, UC02, UC31: chuyên khoa, phòng, dịch vụ, thuốc, hồ sơ bác sĩ. */
class CatalogTest extends IntegrationTest {

    @Autowired
    EmployeeSpecialtyRepository links;
    @Autowired
    DoctorProfileRepository profiles;
    @Autowired
    DoctorHistoryRepository history;
    @Autowired
    SpecialtyRepository specialties;
    @Autowired
    RoomRepository rooms;
    @Autowired
    MedicalServiceRepository services;
    @Autowired
    MedicineRepository medicines;

    private Integer createService(String manager, String code, String category, int price) throws Exception {
        return read(post("/services", manager, """
                {"code":"%s","name":"Dịch vụ %s","category":"%s","listPrice":%d}""".formatted(code, code, category, price))
                .andExpect(status().isCreated()), "$.id");
    }

    private Integer createSpecialty(String manager, String code, Integer feeId) throws Exception {
        return read(post("/specialties", manager, """
                {"code":"%s","name":"Khoa %s","consultationServiceId":%s}""".formatted(code, code, feeId))
                .andExpect(status().isCreated()), "$.id");
    }

    /** Tạo bác sĩ qua API, có hồ sơ công khai */
    private Integer createDoctor(String manager, String name, Integer specialtyId, String license) throws Exception {
        Integer id = read(post("/staff", manager, """
                {"fullName":"%s","phone":"%s","roles":["DOCTOR"],"specialtyIds":[%d]}""".formatted(name, newPhone(), specialtyId))
                .andExpect(status().isCreated()), "$.staff.id");
        put("/staff/" + id + "/doctor-profile", manager, """
                {"academicTitle":"BSCKI","practiceLicenseNo":"%s","practiceSince":2010,"bio":"Giới thiệu"}""".formatted(license))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void specialtyShowsConsultationFeePublicly() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Integer fee = createService(manager, "KHAM_NOI", "CONSULTATION", 150000);
        createSpecialty(manager, "NOI", fee);

        get("/specialties", null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NOI"))
                .andExpect(jsonPath("$[0].consultationPrice").value(150000));
        get("/services?category=CONSULTATION", null).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void consultationFeeMustBeConsultationService() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Integer lab = createService(manager, "XN", "LAB", 80000);
        post("/specialties", manager, """
                {"code":"NOI","name":"Nội","consultationServiceId":%d}""".formatted(lab))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phí khám phải là dịch vụ loại Khám (CONSULTATION)"));
    }

    @Test
    void duplicateCodesGiveReadableConflict() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        post("/rooms", manager, """
                {"code":"P101","name":"Phòng 101"}""").andExpect(status().isCreated());
        post("/rooms", manager, """
                {"code":"P101","name":"Phòng khác"}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Mã phòng đã tồn tại"));
    }

    @Test
    void onlyManagerOrAdminEditsCatalog() throws Exception {
        post("/services", tokenFor(Role.RECEPTIONIST), """
                {"code":"X","name":"X","category":"LAB","listPrice":1}""").andExpect(status().isForbidden());
        post("/rooms", tokenFor(Role.PATIENT), """
                {"code":"P1","name":"P1"}""").andExpect(status().isForbidden());
        get("/rooms", tokenFor(Role.PATIENT)).andExpect(status().isForbidden());
        get("/rooms", tokenFor(Role.NURSE)).andExpect(status().isOk());
    }

    @Test
    void publicDoctorListFiltersBySpecialtyAndHidesIncompleteDoctors() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Integer noi = createSpecialty(manager, "NOI", null);
        Integer nhi = createSpecialty(manager, "NHI", null);
        Integer an = createDoctor(manager, "Nguyễn Văn An", noi, "CCHN-1");
        createDoctor(manager, "Trần Thị Bình", nhi, "CCHN-2");
        // Bác sĩ chưa có hồ sơ hành nghề thì không hiện công khai
        post("/staff", manager, """
                {"fullName":"Chưa Có Hồ Sơ","phone":"%s","roles":["DOCTOR"],"specialtyIds":[%d]}""".formatted(newPhone(), noi))
                .andExpect(status().isCreated());

        get("/doctors", null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        get("/doctors?specialtyId=" + noi, null).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$[0].specialties[0].isPrimary").value(true));

        post("/staff/" + an + "/doctor-history", manager, """
                {"kind":"WORK","fromYear":2010,"title":"Bác sĩ điều trị","place":"BV Bạch Mai"}""").andExpect(status().isCreated());
        get("/doctors/" + an, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].place").value("BV Bạch Mai"))
                .andExpect(jsonPath("$.bio").value("Giới thiệu"));
    }

    @Test
    void duplicatePracticeLicenseIsRejected() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Integer noi = createSpecialty(manager, "NOI", null);
        createDoctor(manager, "A", noi, "CCHN-1");
        Integer b = read(post("/staff", manager, """
                {"fullName":"B","phone":"%s","roles":["DOCTOR"],"specialtyIds":[%d]}""".formatted(newPhone(), noi)), "$.staff.id");
        put("/staff/" + b + "/doctor-profile", manager, """
                {"practiceLicenseNo":"CCHN-1"}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Số chứng chỉ hành nghề đã tồn tại"));
    }

    @Test
    void nonDoctorCannotHaveDoctorProfile() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Integer id = read(post("/staff", manager, """
                {"fullName":"Lễ tân","phone":"%s","roles":["RECEPTIONIST"]}""".formatted(newPhone())), "$.staff.id");
        put("/staff/" + id + "/doctor-profile", manager, """
                {"practiceLicenseNo":"CCHN-9"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Nhân viên chưa có vai trò bác sĩ"));
    }

    @Test
    void doctorSearchesMedicinesByIngredient() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        post("/medicines", manager, """
                {"activeIngredient":"Paracetamol","brandName":"Panadol","strength":"500mg","unit":"viên"}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.label").value("Paracetamol (Panadol) 500mg"));
        post("/medicines", manager, """
                {"activeIngredient":"Amoxicillin","strength":"500mg","unit":"viên","drugClass":"ANTIBIOTIC"}""")
                .andExpect(status().isCreated());

        get("/medicines?q=panad", tokenFor(Role.DOCTOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].activeIngredient").value("Paracetamol"));
        get("/medicines", tokenFor(Role.RECEPTIONIST)).andExpect(status().isForbidden());
    }

    @Autowired
    DemoDataSeeder seeder;

    @Test
    void demoSeedCreatesWorkingAccountsAndCatalog() throws Exception {
        org.assertj.core.api.Assertions.assertThat(seeder.seed()).isTrue();
        org.assertj.core.api.Assertions.assertThat(seeder.seed()).isFalse(); // chạy lại không tạo trùng

        String admin = read(post("/auth/login", null, """
                {"phone":"0900000001","password":"%s"}""".formatted(DemoDataSeeder.DEMO_PASSWORD))
                .andExpect(status().isOk()), "$.token");
        get("/staff?role=DOCTOR", admin).andExpect(jsonPath("$.total").value(4));
        get("/doctors", null).andExpect(jsonPath("$", hasSize(4)));
        get("/specialties", null).andExpect(jsonPath("$", hasSize(4)));
        // Có lịch khám để đặt và bệnh nhân demo đăng nhập được
        get("/slots?from=" + vn.phongkham.common.VnTime.today() + "&to=" + vn.phongkham.common.VnTime.today().plusDays(14), null)
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
        post("/auth/login", null, """
                {"phone":"0900000099","password":"%s"}""".formatted(DemoDataSeeder.DEMO_PASSWORD)).andExpect(status().isOk());
    }
}
