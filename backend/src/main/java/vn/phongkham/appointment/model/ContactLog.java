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
import vn.phongkham.appointment.model.AppointmentEnums.ContactChannel;
import vn.phongkham.appointment.model.AppointmentEnums.ContactDirection;
import vn.phongkham.appointment.model.AppointmentEnums.ContactPurpose;
import vn.phongkham.appointment.model.AppointmentEnums.ContactResult;

/** Nhật ký mỗi lần trực ban/lễ tân liên hệ bệnh nhân */
@Entity
@Table(name = "contact_logs")
@Getter
@Setter
public class ContactLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private Long appointmentId;
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "contact_channel")
    private ContactChannel channel = ContactChannel.PHONE;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "contact_direction")
    private ContactDirection direction = ContactDirection.OUTBOUND;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "contact_purpose")
    private ContactPurpose purpose;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "contact_result")
    private ContactResult result;

    private String note;
    private Instant contactedAt = Instant.now();
}
