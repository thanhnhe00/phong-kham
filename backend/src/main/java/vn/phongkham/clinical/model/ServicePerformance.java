package vn.phongkham.clinical.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;
import vn.phongkham.clinical.model.ClinicalEnums.*;

/** Một lần thực hiện dịch vụ (một mục chỉ định có thể thực hiện nhiều lần) */
@Entity
@Table(name = "service_performances")
@Getter
@Setter
public class ServicePerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderItemId;
    private Long performedBy;
    private Instant performedAt = Instant.now();
    private Short quantity = 1;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "performance_status")
    private PerformanceStatus status = PerformanceStatus.DONE;

    private String note;
}
