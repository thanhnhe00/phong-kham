package vn.phongkham.feedback.model;

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

/** Phản hồi riêng của bệnh nhân gửi phòng khám (không có đánh giá sao công khai) */
@Entity
@Table(name = "feedback")
@Getter
@Setter
public class Feedback {

    public enum Status { NEW, IN_REVIEW, RESOLVED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private Long visitId;
    private String category;
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "feedback_status")
    private Status status = Status.NEW;

    private Long handledBy;
    private Instant handledAt;
    private Instant createdAt = Instant.now();
}
