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

/** Bản điều chỉnh kết quả đã duyệt: giữ kết quả cũ, lý do, người sửa, người duyệt lại */
@Entity
@Table(name = "service_result_amendments")
@Getter
@Setter
public class ServiceResultAmendment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long resultId;
    private int fromVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    private String previousSnapshot;

    private String reason;
    private Long amendedBy;
    private Instant amendedAt = Instant.now();
    private Long reconfirmedBy;
    private Instant reconfirmedAt;
}
