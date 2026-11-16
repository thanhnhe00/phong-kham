package vn.phongkham.staff.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.staff.model.DoctorHistory;

public interface DoctorHistoryRepository extends JpaRepository<DoctorHistory, Long> {

    List<DoctorHistory> findByEmployeeIdOrderByKindAscFromYearDesc(Long employeeId);

    Optional<DoctorHistory> findByIdAndEmployeeId(Long id, Long employeeId);
}
