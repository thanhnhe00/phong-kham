package vn.phongkham.staff.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.staff.model.Employee;
import vn.phongkham.staff.model.StaffEnums.EmploymentStatus;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUserId(Long userId);

    /**
     * Không truyền null cho tham số chuỗi/enum: PostgreSQL không xác định được kiểu của null.
     * q = "" là không lọc; anyRole/anyStatus = true là không lọc theo vai trò/tình trạng.
     */
    @Query("""
            select e from Employee e, User u
            where u.id = e.userId
              and (:anyRole = true or :role member of u.roles)
              and (:anyStatus = true or e.employmentStatus = :status)
              and (:q = '' or lower(e.fullName) like lower(concat('%', :q, '%')) or u.phone like concat('%', :q, '%'))
            order by e.fullName
            """)
    Page<Employee> search(@Param("anyRole") boolean anyRole, @Param("role") Role role,
                          @Param("anyStatus") boolean anyStatus, @Param("status") EmploymentStatus status,
                          @Param("q") String q, Pageable pageable);

    default Page<Employee> search(Role role, EmploymentStatus status, String q, Pageable pageable) {
        return search(role == null, role == null ? Role.DOCTOR : role,
                status == null, status == null ? EmploymentStatus.ACTIVE : status, q, pageable);
    }

    /** Bác sĩ hiển thị công khai: có hồ sơ công khai, đang làm việc, tài khoản hoạt động, có vai trò bác sĩ */
    @Query("""
            select e from Employee e, User u, DoctorProfile p
            where u.id = e.userId and p.employeeId = e.id
              and p.isPublic = true
              and e.employmentStatus = :working
              and u.status = :active
              and :doctor member of u.roles
              and (:employeeId is null or e.id = :employeeId)
              and (:specialtyId is null or exists (
                    select 1 from EmployeeSpecialty es where es.id.employeeId = e.id and es.id.specialtyId = :specialtyId))
            order by e.fullName
            """)
    List<Employee> findPublicDoctors(@Param("employeeId") Long employeeId, @Param("specialtyId") Long specialtyId,
                                     @Param("working") EmploymentStatus working, @Param("active") UserStatus active,
                                     @Param("doctor") Role doctor);

    default List<Employee> findPublicDoctors(Long employeeId, Long specialtyId) {
        return findPublicDoctors(employeeId, specialtyId, EmploymentStatus.ACTIVE, UserStatus.ACTIVE, Role.DOCTOR);
    }
}
