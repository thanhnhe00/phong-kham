package vn.phongkham.emr.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bản điều chỉnh bệnh án đã ký: giữ nguyên nội dung cũ (JSON), lý do, người sửa */
@Entity
@Table(name = "emr_amendments")
@Getter
@Setter
public class EmrAmendment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long emrId;
    private int fromVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    private String previousSnapshot;

    private String reason;
    private Long amendedBy;
    private Instant amendedAt = Instant.now();
}
