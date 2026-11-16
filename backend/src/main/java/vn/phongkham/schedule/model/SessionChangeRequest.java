package vn.phongkham.schedule.model;

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
import vn.phongkham.schedule.model.ScheduleEnums.ChangeType;
import vn.phongkham.schedule.model.ScheduleEnums.RequestStatus;

/** Yêu cầu xin nghỉ / nhờ người thay / đổi chéo ca của bác sĩ */
@Entity
@Table(name = "session_change_requests")
@Getter
@Setter
public class SessionChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "session_change_type")
    private ChangeType requestType;

    private Long requestedBy;
    private Long replacementDoctorId;
    private Long swapSessionId;
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "request_status")
    private RequestStatus status;

    private Instant colleagueRespondedAt;
    private Long managerId;
    private Instant managerDecidedAt;
    private String managerNote;
    private Instant createdAt = Instant.now();
}
