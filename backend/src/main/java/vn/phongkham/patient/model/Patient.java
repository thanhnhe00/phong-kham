package vn.phongkham.patient.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import vn.phongkham.patient.model.PatientEnums.IdentityType;
import vn.phongkham.patient.model.PatientEnums.Sex;

/** Hồ sơ hành chính của bệnh nhân. Số điện thoại không phải khóa định danh (cả nhà có thể dùng chung). */
@Entity
@Table(name = "patients")
@Getter
@Setter
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String patientCode;
    private String fullName;
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "sex")
    private Sex sex = Sex.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "identity_type")
    private IdentityType identityType = IdentityType.NOT_PROVIDED;

    private String identityNumber;
    private String phone;
    private String email;
    private String addressLine;
    /** Phường/xã (chính quyền 2 cấp, không còn cấp huyện) */
    private String ward;
    private String province;
    private String occupation;
    private String ethnicity;
    private String nationality = "Việt Nam";
    private String linkCodeHash;
    private Instant linkCodeExpiresAt;
    private Long createdBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
