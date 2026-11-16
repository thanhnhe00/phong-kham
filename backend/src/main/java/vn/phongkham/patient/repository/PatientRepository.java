package vn.phongkham.patient.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.model.PatientEnums.IdentityType;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    /** q = "" là không lọc; tìm theo tên, số điện thoại, mã bệnh nhân hoặc số giấy tờ */
    @Query("""
            select p from Patient p
            where :q = '' or lower(p.fullName) like lower(concat('%', :q, '%'))
               or p.phone like concat('%', :q, '%') or p.patientCode = upper(:q) or p.identityNumber = :q
            order by p.fullName, p.dateOfBirth
            """)
    Page<Patient> search(@Param("q") String q, Pageable pageable);

    Optional<Patient> findByIdentityTypeAndIdentityNumber(IdentityType type, String number);

    /** Hồ sơ có thể trùng: cùng số giấy tờ, hoặc cùng ngày sinh và (cùng họ tên hoặc cùng số điện thoại). Chuỗi rỗng = bỏ qua. */
    @Query("""
            select p from Patient p
            where (:identity <> '' and p.identityNumber = :identity)
               or (p.dateOfBirth = :dob and (lower(p.fullName) = lower(:fullName) or (:phone <> '' and p.phone = :phone)))
            order by p.id
            """)
    List<Patient> findPossibleDuplicates(@Param("fullName") String fullName, @Param("dob") LocalDate dob,
                                         @Param("phone") String phone, @Param("identity") String identity);

    Optional<Patient> findByLinkCodeHashAndLinkCodeExpiresAtAfter(String hash, Instant now);
}
