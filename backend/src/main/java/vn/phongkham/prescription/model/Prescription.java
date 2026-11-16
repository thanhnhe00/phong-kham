package vn.phongkham.prescription.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/** Đơn thuốc ngoại trú (Thông tư 26/2025/TT-BYT). Đã ký thì không sửa: kê đơn thay thế. */
@Entity
@Table(name = "prescriptions")
@Getter
@Setter
public class Prescription {

    public enum Status { DRAFT, SIGNED, REPLACED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String prescriptionCode;
    private Long visitId;
    /** Chẩn đoán in trên đơn, chép từ bệnh án lúc ký */
    private String diagnosisText;
    private String advice;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "prescription_status")
    private Status status = Status.DRAFT;

    private Long replacesId;
    private String replacementReason;
    private String allergyAck;
    private Long signedBy;
    private Instant signedAt;
    /** Đã gửi Hệ thống đơn thuốc quốc gia (mô phỏng) */
    private Instant nationalSentAt;
    private Instant createdAt = Instant.now();
}
