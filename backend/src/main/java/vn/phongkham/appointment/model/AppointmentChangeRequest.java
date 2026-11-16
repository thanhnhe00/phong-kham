package vn.phongkham.appointment.model;

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
import vn.phongkham.appointment.model.AppointmentEnums.ChangeRequestType;
import vn.phongkham.appointment.model.AppointmentEnums.SimpleRequestStatus;

/** Bệnh nhân xin đổi/hủy lịch; lễ tân xử lý (không tự hủy) */
@Entity
@Table(name = "appointment_change_requests")
@Getter
@Setter
public class AppointmentChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "change_request_type")
    private ChangeRequestType requestType;

    private Long requestedSlotId;
    private Long requestedByUser;
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "simple_request_status")
    private SimpleRequestStatus status = SimpleRequestStatus.PENDING;

    private Long handledBy;
    private Instant handledAt;
    private Instant createdAt = Instant.now();
}
