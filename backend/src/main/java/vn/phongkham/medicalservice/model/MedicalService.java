package vn.phongkham.medicalservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/** Dịch vụ có giá: phí khám (CONSULTATION), xét nghiệm, chẩn đoán hình ảnh... */
@Entity
@Table(name = "services")
@Getter
@Setter
public class MedicalService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "service_category")
    private ServiceCategory category;

    private String description;
    /** VND */
    private BigDecimal listPrice;
    private Short estimatedMinutes;
    private boolean requiresSpecialist;
    private boolean isActive = true;
}
