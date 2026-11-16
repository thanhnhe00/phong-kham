package vn.phongkham.schedule.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.repository.UserRepository;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.room.model.Room;
import vn.phongkham.room.repository.RoomRepository;
import vn.phongkham.specialty.model.Specialty;
import vn.phongkham.specialty.repository.SpecialtyRepository;
import vn.phongkham.staff.model.Employee;
import vn.phongkham.staff.model.EmployeeSpecialty;
import vn.phongkham.staff.model.StaffEnums.EmploymentStatus;
import vn.phongkham.staff.repository.EmployeeRepository;
import vn.phongkham.staff.repository.EmployeeSpecialtyRepository;

/** Kiểm tra bác sĩ/phòng hợp lệ khi xếp lịch và tra tên để hiển thị (gom theo lô, tránh N+1). */
@Component
public class ScheduleLookups {

    private final EmployeeRepository employees;
    private final UserRepository users;
    private final EmployeeSpecialtyRepository links;
    private final SpecialtyRepository specialties;
    private final RoomRepository rooms;

    public ScheduleLookups(EmployeeRepository employees, UserRepository users, EmployeeSpecialtyRepository links,
                           SpecialtyRepository specialties, RoomRepository rooms) {
        this.employees = employees;
        this.users = users;
        this.links = links;
        this.specialties = specialties;
        this.rooms = rooms;
    }

    /** Bác sĩ đang làm việc, có vai trò bác sĩ và thuộc chuyên khoa được xếp */
    public Employee requireDoctorFor(Long doctorId, Long specialtyId) {
        Employee e = employees.findById(doctorId).orElseThrow(() -> ApiException.badRequest("Bác sĩ không tồn tại"));
        boolean isDoctor = users.findById(e.getUserId()).map(u -> u.getRoles().contains(Role.DOCTOR)).orElse(false);
        if (!isDoctor) {
            throw ApiException.badRequest("Nhân viên được chọn không phải bác sĩ");
        }
        if (e.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw ApiException.badRequest("Bác sĩ không trong trạng thái đang làm việc");
        }
        if (!links.existsById(new EmployeeSpecialty.Key(doctorId, specialtyId))) {
            throw ApiException.badRequest("Bác sĩ không thuộc chuyên khoa này");
        }
        return e;
    }

    public boolean isDoctorOf(Long doctorId, Long specialtyId) {
        return links.existsById(new EmployeeSpecialty.Key(doctorId, specialtyId));
    }

    public Room requireActiveRoom(Long roomId) {
        Room r = rooms.findById(roomId).orElseThrow(() -> ApiException.badRequest("Phòng không tồn tại"));
        if (!r.isActive()) {
            throw ApiException.badRequest("Phòng đang ngừng sử dụng");
        }
        return r;
    }

    public Map<Long, String> employeeNames(Collection<Long> ids) {
        return employees.findAllById(ids).stream().collect(Collectors.toMap(Employee::getId, Employee::getFullName));
    }

    public Map<Long, String> specialtyNames(Collection<Long> ids) {
        return specialties.findAllById(ids).stream().collect(Collectors.toMap(Specialty::getId, Specialty::getName));
    }

    public Map<Long, String> roomCodes(Collection<Long> ids) {
        return rooms.findAllById(ids).stream().collect(Collectors.toMap(Room::getId, Room::getCode));
    }
}
