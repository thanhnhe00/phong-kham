package vn.phongkham.appointment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.model.AppointmentEnums.BookingChannel;

/** Lịch hẹn. DB đảm bảo mỗi slot chỉ có một lịch hẹn còn hiệu lực. */
@Entity
@Table(name = "appointments")
@Getter
@Setter
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Khóa lạc quan: hai người cùng sửa thì người lưu sau nhận 409 */
    @jakarta.persistence.Version
    @jakarta.persistence.Column(name = "row_version")
    private int rowVersion;

    private String appointmentCode;
    private Long patientId;
    private Long slotId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "booking_channel")
    private BookingChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "appointment_status")
    private AppointmentStatus status = AppointmentStatus.PENDING_CONFIRMATION;

    private String reason;
    private Long requestedByUser;
    private Long createdBy;
    private Long confirmedBy;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private String cancelReason;
    private Instant remindedAt;
    private Long followUpOfVisitId;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
