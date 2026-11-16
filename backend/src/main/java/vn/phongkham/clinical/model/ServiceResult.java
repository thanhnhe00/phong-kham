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

/** Kết quả của một lần thực hiện; chỉ hiện cho bệnh nhân sau khi bác sĩ duyệt */
@Entity
@Table(name = "service_results")
@Getter
@Setter
public class ServiceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long performanceId;
    private String resultText;
    private String conclusion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "result_status")
    private ResultStatus status = ResultStatus.DRAFT;

    private int version = 1;
    private Long enteredBy;
    private Instant enteredAt = Instant.now();
    private Long reviewedBy;
    private Instant reviewedAt;
    private String returnNote;
}
