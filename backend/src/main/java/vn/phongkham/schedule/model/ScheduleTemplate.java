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
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import vn.phongkham.schedule.model.ScheduleEnums.TemplateStatus;

/** Lịch tuần cố định: bác sĩ X khám chuyên khoa Y tại phòng Z vào buổi B của thứ W */
@Entity
@Table(name = "schedule_templates")
@Getter
@Setter
public class ScheduleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long doctorId;
    private Long specialtyId;
    private Long roomId;
    /** 1 = thứ Hai ... 7 = Chủ nhật */
    private Short weekday;
    private String shiftName;
    private LocalTime startTime;
    private LocalTime endTime;
    private Short slotMinutes;
    private Short walkInCapacity;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "template_status")
    private TemplateStatus status = TemplateStatus.DRAFT;

    private Long createdBy;
    private Instant doctorConfirmedAt;
    private Long approvedBy;
    private Instant approvedAt;
}
