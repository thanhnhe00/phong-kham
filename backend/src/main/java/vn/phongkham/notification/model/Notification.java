package vn.phongkham.notification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Thông báo trong app cho một tài khoản */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    /** APPOINTMENT_CONFIRMED, APPOINTMENT_RECONFIRM, REMINDER, CALLED, RESULTS_READY, VISIT_COMPLETED... */
    private String notificationType;
    private String title;
    private String body;
    private String relatedEntityType;
    private Long relatedEntityId;
    /** Thông báo không còn đúng (vd. nhắc lịch của lịch đã đổi/hủy) */
    private boolean isVoid;
    private Instant readAt;
    private Instant createdAt = Instant.now();
}
