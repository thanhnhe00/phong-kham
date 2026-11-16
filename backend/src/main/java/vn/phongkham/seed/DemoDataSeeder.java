package vn.phongkham.seed;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.auth.repository.UserRepository;
import vn.phongkham.common.VnTime;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.model.ServiceCategory;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;
import vn.phongkham.medicine.model.DrugClass;
import vn.phongkham.medicine.model.Medicine;
import vn.phongkham.medicine.repository.MedicineRepository;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.model.PatientEnums.AccountRelationship;
import vn.phongkham.patient.model.PatientEnums.Sex;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.room.model.Room;
import vn.phongkham.room.repository.RoomRepository;
import vn.phongkham.schedule.model.ScheduleEnums.TemplateStatus;
import vn.phongkham.schedule.model.ScheduleTemplate;
import vn.phongkham.schedule.repository.ScheduleTemplateRepository;
import vn.phongkham.schedule.service.SessionService;
import vn.phongkham.specialty.model.Specialty;
import vn.phongkham.specialty.repository.SpecialtyRepository;
import vn.phongkham.staff.model.DoctorHistory;
import vn.phongkham.staff.model.DoctorProfile;
import vn.phongkham.staff.model.Employee;
import vn.phongkham.staff.model.EmployeeSpecialty;
import vn.phongkham.staff.model.StaffEnums.HistoryKind;
import vn.phongkham.staff.repository.DoctorHistoryRepository;
import vn.phongkham.staff.repository.DoctorProfileRepository;
import vn.phongkham.staff.repository.EmployeeRepository;
import vn.phongkham.staff.repository.EmployeeSpecialtyRepository;

/**
 * Dữ liệu demo (bật bằng app.seed-demo=true). Chỉ chạy khi chưa có tài khoản nào.
 * Mọi tài khoản demo dùng mật khẩu 12345678. Gồm lịch tuần đã duyệt (thứ 2 - thứ 6) và phiên khám 14 ngày tới.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "12345678";

    private final UserRepository users;
    private final EmployeeRepository employees;
    private final EmployeeSpecialtyRepository links;
    private final DoctorProfileRepository profiles;
    private final DoctorHistoryRepository history;
    private final SpecialtyRepository specialties;
    private final RoomRepository rooms;
    private final MedicalServiceRepository services;
    private final MedicineRepository medicines;
    private final ScheduleTemplateRepository templates;
    private final SessionService sessionService;
    private final PatientRepository patients;
    private final PatientAccountRepository patientAccounts;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DemoDataSeeder(UserRepository users, EmployeeRepository employees, EmployeeSpecialtyRepository links,
                          DoctorProfileRepository profiles, DoctorHistoryRepository history, SpecialtyRepository specialties,
                          RoomRepository rooms, MedicalServiceRepository services, MedicineRepository medicines,
                          ScheduleTemplateRepository templates, SessionService sessionService, PatientRepository patients,
                          PatientAccountRepository patientAccounts, PasswordEncoder encoder,
                          @Value("${app.seed-demo:false}") boolean enabled) {
        this.users = users;
        this.employees = employees;
        this.links = links;
        this.profiles = profiles;
        this.history = history;
        this.specialties = specialties;
        this.rooms = rooms;
        this.services = services;
        this.medicines = medicines;
        this.templates = templates;
        this.sessionService = sessionService;
        this.patients = patients;
        this.patientAccounts = patientAccounts;
        this.encoder = encoder;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
        }
    }

    /** Tạo dữ liệu demo nếu DB chưa có tài khoản; trả false nếu bỏ qua */
    @Transactional
    public boolean seed() {
        if (users.count() > 0) {
            return false;
        }
        String hash = encoder.encode(DEMO_PASSWORD);

        Specialty noi = specialty("NOI", "Nội tổng quát", service("KHAM_NOI", "Khám Nội tổng quát", ServiceCategory.CONSULTATION, 150_000, 30));
        Specialty nhi = specialty("NHI", "Nhi", service("KHAM_NHI", "Khám Nhi", ServiceCategory.CONSULTATION, 150_000, 30));
        Specialty tmh = specialty("TMH", "Tai Mũi Họng", service("KHAM_TMH", "Khám Tai Mũi Họng", ServiceCategory.CONSULTATION, 180_000, 30));
        Specialty da = specialty("DALIEU", "Da liễu", service("KHAM_DA", "Khám Da liễu", ServiceCategory.CONSULTATION, 180_000, 30));
        service("XN_CTM", "Xét nghiệm công thức máu", ServiceCategory.LAB, 80_000, 30);
        service("XN_DUONG", "Xét nghiệm đường huyết", ServiceCategory.LAB, 40_000, 15);
        service("SA_BUNG", "Siêu âm ổ bụng", ServiceCategory.ULTRASOUND, 200_000, 20);
        service("XQ_NGUC", "X-quang ngực thẳng", ServiceCategory.IMAGING, 150_000, 15);
        service("NOI_SOI_TMH", "Nội soi Tai Mũi Họng", ServiceCategory.PROCEDURE, 250_000, 20);

        Room p101 = room("P101", "Phòng khám 101", noi.getId());
        Room p102 = room("P102", "Phòng khám 102", nhi.getId());
        Room p103 = room("P103", "Phòng khám 103", tmh.getId());
        Room p104 = room("P104", "Phòng khám 104", null);
        room("XN01", "Phòng xét nghiệm", null);

        Employee manager = staff("0900000001", "Trần Thị Quản Lý", "Quản lý phòng khám", hash, EnumSet.of(Role.ADMIN));
        staff("0900000003", "Lê Thị Lễ Tân", "Lễ tân", hash, EnumSet.of(Role.RECEPTIONIST));
        staff("0900000004", "Phạm Thị Điều Dưỡng", "Điều dưỡng (đo sinh hiệu, xét nghiệm)", hash, EnumSet.of(Role.NURSE));
        Employee an = doctor("0900000011", "Nguyễn Văn An", "ThS.BS", "CCHN-0001", 2008, noi, hash, "Tăng huyết áp, đái tháo đường, bệnh dạ dày");
        Employee binh = doctor("0900000012", "Trần Thị Bình", "BSCKI", "CCHN-0002", 2012, nhi, hash, "Hô hấp nhi, dinh dưỡng trẻ em");
        Employee chau = doctor("0900000013", "Lê Minh Châu", "BSCKII", "CCHN-0003", 2005, tmh, hash, "Viêm xoang, viêm họng mạn, nội soi TMH");
        Employee dung = doctor("0900000014", "Phạm Thu Dung", "BS", "CCHN-0004", 2016, da, hash, "Mụn trứng cá, viêm da cơ địa");

        // Lịch tuần thứ 2 - thứ 6: sáng 7:30-11:30, chiều 13:30-17:00; bác sĩ Da liễu chỉ khám chiều
        for (int weekday = 1; weekday <= 5; weekday++) {
            template(an, noi, p101, weekday, "Sáng", "07:30", "11:30", manager);
            template(an, noi, p101, weekday, "Chiều", "13:30", "17:00", manager);
            template(binh, nhi, p102, weekday, "Sáng", "07:30", "11:30", manager);
            template(binh, nhi, p102, weekday, "Chiều", "13:30", "17:00", manager);
            template(chau, tmh, p103, weekday, "Sáng", "07:30", "11:30", manager);
            template(chau, tmh, p103, weekday, "Chiều", "13:30", "17:00", manager);
            template(dung, da, p104, weekday, "Chiều", "13:30", "17:00", manager);
        }
        sessionService.generate(manager.getId(), VnTime.today(), VnTime.today().plusDays(14));

        demoPatient(hash);

        medicine("Paracetamol", "Panadol", "500mg", "Viên nén", "Uống", "viên", DrugClass.NORMAL);
        medicine("Amoxicillin", null, "500mg", "Viên nang", "Uống", "viên", DrugClass.ANTIBIOTIC);
        medicine("Cetirizin", null, "10mg", "Viên nén", "Uống", "viên", DrugClass.NORMAL);
        medicine("Omeprazol", null, "20mg", "Viên nang", "Uống", "viên", DrugClass.NORMAL);
        medicine("Amlodipin", null, "5mg", "Viên nén", "Uống", "viên", DrugClass.NORMAL);
        medicine("Metformin", null, "500mg", "Viên nén", "Uống", "viên", DrugClass.NORMAL);
        medicine("Natri clorid", null, "0,9%", "Dung dịch nhỏ mũi", "Nhỏ mũi", "chai", DrugClass.NORMAL);
        log.info("Đã tạo dữ liệu demo. Đăng nhập quản lý: 0900000001 / {}", DEMO_PASSWORD);
        return true;
    }

    /** Tài khoản app 0900000099 có sẵn hồ sơ của chính mình */
    private void demoPatient(String hash) {
        User u = new User();
        u.setPhone("0900000099");
        u.setPasswordHash(hash);
        u.setStatus(UserStatus.ACTIVE);
        u.setPhoneVerifiedAt(Instant.now());
        u.setRoles(EnumSet.of(Role.PATIENT));
        users.save(u);
        Patient p = new Patient();
        p.setPatientCode("BN000001");
        p.setFullName("Nguyễn Văn Bệnh Nhân");
        p.setDateOfBirth(LocalDate.of(1990, 5, 20));
        p.setSex(Sex.MALE);
        p.setPhone("0900000099");
        p.setProvince("Hà Nội");
        patients.save(p);
        patientAccounts.save(new PatientAccount(p.getId(), u.getId(), AccountRelationship.SELF));
    }

    private void template(Employee doctor, Specialty specialty, Room room, int weekday, String shift, String start,
                          String end, Employee manager) {
        ScheduleTemplate t = new ScheduleTemplate();
        t.setDoctorId(doctor.getId());
        t.setSpecialtyId(specialty.getId());
        t.setRoomId(room.getId());
        t.setWeekday((short) weekday);
        t.setShiftName(shift);
        t.setStartTime(LocalTime.parse(start));
        t.setEndTime(LocalTime.parse(end));
        t.setSlotMinutes((short) 30);
        t.setWalkInCapacity((short) 4);
        t.setEffectiveFrom(VnTime.today());
        t.setStatus(TemplateStatus.APPROVED);
        t.setCreatedBy(manager.getId());
        t.setDoctorConfirmedAt(Instant.now());
        t.setApprovedBy(manager.getId());
        t.setApprovedAt(Instant.now());
        templates.save(t);
    }

    private MedicalService service(String code, String name, ServiceCategory category, long price, int minutes) {
        MedicalService s = new MedicalService();
        s.setCode(code);
        s.setName(name);
        s.setCategory(category);
        s.setListPrice(BigDecimal.valueOf(price));
        s.setEstimatedMinutes((short) minutes);
        s.setRequiresSpecialist(category != ServiceCategory.CONSULTATION);
        return services.save(s);
    }

    private Specialty specialty(String code, String name, MedicalService fee) {
        Specialty s = new Specialty();
        s.setCode(code);
        s.setName(name);
        s.setConsultationServiceId(fee.getId());
        return specialties.save(s);
    }

    private Room room(String code, String name, Long specialtyId) {
        Room r = new Room();
        r.setCode(code);
        r.setName(name);
        r.setSpecialtyId(specialtyId);
        return rooms.save(r);
    }

    private Employee staff(String phone, String fullName, String jobTitle, String hash, EnumSet<Role> roles) {
        User u = new User();
        u.setPhone(phone);
        u.setPasswordHash(hash);
        u.setStatus(UserStatus.ACTIVE);
        u.setPhoneVerifiedAt(Instant.now());
        u.setRoles(roles);
        users.save(u);
        Employee e = new Employee();
        e.setUserId(u.getId());
        e.setEmployeeCode("NV" + phone.substring(6));
        e.setFullName(fullName);
        e.setJobTitle(jobTitle);
        return employees.save(e);
    }

    private Employee doctor(String phone, String fullName, String title, String license, int since, Specialty specialty,
                            String hash, String expertise) {
        Employee e = staff(phone, fullName, "Bác sĩ " + specialty.getName(), hash, EnumSet.of(Role.DOCTOR));
        links.save(new EmployeeSpecialty(e.getId(), specialty.getId(), true));
        DoctorProfile p = new DoctorProfile();
        p.setEmployeeId(e.getId());
        p.setAcademicTitle(title);
        p.setPracticeLicenseNo(license);
        p.setPracticeScope("Khám bệnh, chữa bệnh chuyên khoa " + specialty.getName());
        p.setPracticeSince((short) since);
        p.setExpertise(expertise);
        p.setBio("Bác sĩ " + fullName + " có nhiều năm kinh nghiệm khám và điều trị chuyên khoa " + specialty.getName() + ".");
        profiles.save(p);
        for (var h : List.of(
                new Object[]{HistoryKind.EDUCATION, since - 6, since, "Bác sĩ đa khoa", "Đại học Y Hà Nội"},
                new Object[]{HistoryKind.WORK, since, null, "Bác sĩ điều trị", "Bệnh viện Bạch Mai"})) {
            DoctorHistory d = new DoctorHistory();
            d.setEmployeeId(e.getId());
            d.setKind((HistoryKind) h[0]);
            d.setFromYear(((Integer) h[1]).shortValue());
            d.setToYear(h[2] == null ? null : ((Integer) h[2]).shortValue());
            d.setTitle((String) h[3]);
            d.setPlace((String) h[4]);
            history.save(d);
        }
        return e;
    }

    private void medicine(String ingredient, String brand, String strength, String form, String route, String unit, DrugClass cls) {
        Medicine m = new Medicine();
        m.setActiveIngredient(ingredient);
        m.setBrandName(brand);
        m.setStrength(strength);
        m.setDosageForm(form);
        m.setDefaultRoute(route);
        m.setUnit(unit);
        m.setDrugClass(cls);
        medicines.save(m);
    }
}
