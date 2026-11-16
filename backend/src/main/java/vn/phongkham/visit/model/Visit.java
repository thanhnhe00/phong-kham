package vn.phongkham.visit.model;

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
import vn.phongkham.visit.model.VisitEnums.CheckinMethod;
import vn.phongkham.visit.model.VisitEnums.QueueGroup;
import vn.phongkham.visit.model.VisitEnums.VisitSource;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;

/** Lượt khám: check-in, số thứ tự, trạng thái khám nằm chung một bảng (một nguồn trạng thái) */
@Entity
@Table(name = "visits")
@Getter
@Setter
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Khóa lạc quan: hai người cùng sửa thì người lưu sau nhận 409 */
    @jakarta.persistence.Version
    @jakarta.persistence.Column(name = "row_version")
    private int rowVersion;

    private String visitCode;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "visit_source")
    private VisitSource source;

    /** null với bệnh nhân vãng lai */
    private Long appointmentId;
    private Long patientId;
    private Long sessionId;
    /** Bác sĩ thực khám (lưu lại, không suy từ phiên) */
    private Long doctorId;
    private String reason;
    private String companionName;
    private String companionPhone;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "checkin_method")
    private CheckinMethod checkinMethod;

    private Instant checkedInAt = Instant.now();
    private Long checkedInBy;
    private Integer queueNumber;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "queue_group")
    private QueueGroup queueGroup;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "visit_status")
    private VisitStatus status = VisitStatus.WAITING;

    private Instant calledAt;
    private Instant startedAt;
    private Instant completedAt;
}
