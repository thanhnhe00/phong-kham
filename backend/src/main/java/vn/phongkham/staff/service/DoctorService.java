package vn.phongkham.staff.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.staff.dto.StaffDtos.DoctorCard;
import vn.phongkham.staff.dto.StaffDtos.DoctorDetail;
import vn.phongkham.staff.model.DoctorProfile;
import vn.phongkham.staff.model.Employee;
import vn.phongkham.staff.repository.DoctorHistoryRepository;
import vn.phongkham.staff.repository.DoctorProfileRepository;
import vn.phongkham.staff.repository.EmployeeRepository;

/** UC01, UC02: thông tin bác sĩ công khai cho khách và bệnh nhân. */
@Service
public class DoctorService {

    private final EmployeeRepository employees;
    private final DoctorProfileRepository profiles;
    private final DoctorHistoryRepository history;
    private final StaffSpecialties staffSpecialties;

    public DoctorService(EmployeeRepository employees, DoctorProfileRepository profiles, DoctorHistoryRepository history,
                         StaffSpecialties staffSpecialties) {
        this.employees = employees;
        this.profiles = profiles;
        this.history = history;
        this.staffSpecialties = staffSpecialties;
    }

    @Transactional(readOnly = true)
    public List<DoctorCard> list(Long specialtyId) {
        List<Employee> list = employees.findPublicDoctors(null, specialtyId);
        var ids = list.stream().map(Employee::getId).toList();
        Map<Long, DoctorProfile> profileById = profiles.findAllById(ids).stream()
                .collect(Collectors.toMap(DoctorProfile::getEmployeeId, Function.identity()));
        var specs = staffSpecialties.of(ids);
        return list.stream().map(e -> {
            DoctorProfile p = profileById.get(e.getId());
            return new DoctorCard(e.getId(), e.getFullName(), p.getAcademicTitle(), specs.getOrDefault(e.getId(), List.of()),
                    p.getPracticeSince() == null ? null : p.getPracticeSince().intValue(), p.getExpertise());
        }).toList();
    }

    @Transactional(readOnly = true)
    public DoctorDetail get(Long id) {
        Employee e = employees.findPublicDoctors(id, null).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bác sĩ"));
        DoctorProfile p = profiles.findById(id).orElseThrow();
        return new DoctorDetail(e.getId(), e.getFullName(), p.getAcademicTitle(),
                staffSpecialties.of(List.of(id)).getOrDefault(id, List.of()),
                p.getPracticeSince() == null ? null : p.getPracticeSince().intValue(), p.getExpertise(), p.getBio(),
                p.getPracticeScope(),
                history.findByEmployeeIdOrderByKindAscFromYearDesc(id).stream().map(StaffService::toHistory).toList());
    }
}
