package vn.phongkham.staff.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.phongkham.specialty.model.Specialty;
import vn.phongkham.specialty.repository.SpecialtyRepository;
import vn.phongkham.staff.dto.StaffDtos.SpecialtyRef;
import vn.phongkham.staff.model.EmployeeSpecialty;
import vn.phongkham.staff.repository.EmployeeSpecialtyRepository;

/** Đọc chuyên khoa của nhiều nhân viên trong 2 câu truy vấn (tránh N+1), dùng chung cho danh sách nhân viên và bác sĩ. */
@Component
public class StaffSpecialties {

    private final EmployeeSpecialtyRepository links;
    private final SpecialtyRepository specialties;

    public StaffSpecialties(EmployeeSpecialtyRepository links, SpecialtyRepository specialties) {
        this.links = links;
        this.specialties = specialties;
    }

    /** employeeId -> danh sách chuyên khoa (chuyên khoa chính đứng đầu) */
    public Map<Long, List<SpecialtyRef>> of(Collection<Long> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        List<EmployeeSpecialty> rows = links.findByIdEmployeeIdIn(employeeIds);
        Map<Long, Specialty> byId = specialties.findAllById(rows.stream().map(r -> r.getId().getSpecialtyId()).distinct().toList())
                .stream().collect(Collectors.toMap(Specialty::getId, Function.identity()));
        return rows.stream().collect(Collectors.groupingBy(r -> r.getId().getEmployeeId(), Collectors.collectingAndThen(
                Collectors.toList(),
                list -> list.stream()
                        .map(r -> new SpecialtyRef(r.getId().getSpecialtyId(), byId.get(r.getId().getSpecialtyId()).getName(), r.isPrimary()))
                        .sorted(Comparator.comparing(SpecialtyRef::isPrimary).reversed().thenComparing(SpecialtyRef::name))
                        .toList())));
    }
}
