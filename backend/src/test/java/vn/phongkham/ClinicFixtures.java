package vn.phongkham;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;
import vn.phongkham.common.VnTime;
import vn.phongkham.room.model.Room;
import vn.phongkham.room.repository.RoomRepository;
import vn.phongkham.specialty.model.Specialty;
import vn.phongkham.specialty.repository.SpecialtyRepository;
import vn.phongkham.staff.model.EmployeeSpecialty;
import vn.phongkham.staff.repository.EmployeeSpecialtyRepository;

/** Dữ liệu nền cho test lịch / bệnh nhân / lịch hẹn: chuyên khoa, phòng, bác sĩ, phiên khám, bệnh nhân có app. */
public abstract class ClinicFixtures extends IntegrationTest {

    @Autowired
    protected SpecialtyRepository specialtyRepo;
    @Autowired
    protected RoomRepository roomRepo;
    @Autowired
    protected EmployeeSpecialtyRepository employeeSpecialtyRepo;

    /** Bác sĩ: id hồ sơ nhân viên + token đăng nhập */
    public record Doctor(Long id, String token) {
    }

    /** Tài khoản app + một hồ sơ bệnh nhân của chính chủ */
    public record PatientApp(Long patientId, String token) {
    }

    protected Long specialty(String code) {
        Specialty s = new Specialty();
        s.setCode(code);
        s.setName("Khoa " + code);
        return specialtyRepo.save(s).getId();
    }

    protected Long room(String code) {
        Room r = new Room();
        r.setCode(code);
        r.setName("Phòng " + code);
        return roomRepo.save(r).getId();
    }

    protected Doctor doctor(Long specialtyId) {
        User u = createUser(newPhone(), "matkhau123", Role.DOCTOR);
        Long employeeId = employees.findByUserId(u.getId()).orElseThrow().getId();
        employeeSpecialtyRepo.save(new EmployeeSpecialty(employeeId, specialtyId, true));
        return new Doctor(employeeId, tokens.issue(u).token());
    }

    protected static LocalDate daysAhead(int days) {
        return VnTime.today().plusDays(days);
    }

    /** Phiên khám tạo tay, slot 30 phút; trả id phiên */
    protected Integer session(String managerToken, Long doctorId, Long specialtyId, Long roomId, LocalDate date,
                              String start, String end) throws Exception {
        return read(post("/schedule/sessions", managerToken, """
                {"doctorId":%d,"specialtyId":%d,"roomId":%d,"date":"%s","shiftName":"Sáng","startTime":"%s","endTime":"%s","walkInCapacity":5}
                """.formatted(doctorId, specialtyId, roomId, date, start, end)).andExpect(status().isCreated()), "$.session.id");
    }

    protected List<Integer> slotIds(String staffToken, Integer sessionId) throws Exception {
        return read(get("/schedule/sessions/" + sessionId, staffToken).andExpect(status().isOk()), "$.slots[*].id");
    }

    protected PatientApp patientApp(String fullName) throws Exception {
        User u = createUser(newPhone(), "matkhau123", Role.PATIENT);
        String token = tokens.issue(u).token();
        Integer id = read(post("/me/patients", token, """
                {"relationship":"SELF","profile":{"fullName":"%s","dateOfBirth":"1990-05-20","sex":"MALE"}}""".formatted(fullName))
                .andExpect(status().isCreated()), "$.patient.id");
        return new PatientApp(id.longValue(), token);
    }
}
