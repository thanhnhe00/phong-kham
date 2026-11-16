package vn.phongkham.staff.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.staff.model.EmployeeSpecialty;

public interface EmployeeSpecialtyRepository extends JpaRepository<EmployeeSpecialty, EmployeeSpecialty.Key> {

    List<EmployeeSpecialty> findByIdEmployeeIdIn(Collection<Long> employeeIds);

    /** Xóa ngay bằng câu lệnh (không chờ flush) để chèn lại danh sách mới không bị trùng khóa */
    @Modifying(flushAutomatically = true)
    @Query("delete from EmployeeSpecialty es where es.id.employeeId = :employeeId")
    void deleteAllOfEmployee(@Param("employeeId") Long employeeId);
}
