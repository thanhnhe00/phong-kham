package vn.phongkham.appointment.model;

import jakarta.persistence.Column;
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

/** Mã QR check-in: DB chỉ giữ SHA-256 của token, token gốc chỉ nằm trong app bệnh nhân */
@Entity
@Table(name = "appointment_qr_tokens")
@Getter
@Setter
public class AppointmentQrToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appointmentId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String tokenHash;

    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt = Instant.now();
}
