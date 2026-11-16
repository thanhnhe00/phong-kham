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
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;

/** Phiên khám cụ thể của một ngày. DB tự chặn trùng phòng và trùng giờ bác sĩ (EXCLUDE). */
@Entity
@Table(name = "work_sessions")
@Getter
@Setter
public class WorkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long templateId;
    /** Bác sĩ đang phụ trách (đổi khi duyệt người thay) */
    private Long doctorId;
    private Long originalDoctorId;
    private Long specialtyId;
    private Long roomId;
    private LocalDate sessionDate;
    private String shiftName;
    private Instant startsAt;
    private Instant endsAt;
    private Short walkInCapacity;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "session_status")
    private SessionStatus status = SessionStatus.DRAFT;

    private Long publishedBy;
    private Instant publishedAt;
    private Short doctorLateMinutes;
}
