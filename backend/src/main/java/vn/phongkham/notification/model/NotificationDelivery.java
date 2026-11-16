package vn.phongkham.notification.model;

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

/** Trạng thái gửi từng kênh (trong app / push / SMS) của một thông báo */
@Entity
@Table(name = "notification_deliveries")
@Getter
@Setter
public class NotificationDelivery {

    public enum Channel { IN_APP, PUSH, SMS }

    public enum Status { QUEUED, SENT, FAILED, SUPPRESSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "notification_channel")
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "delivery_status")
    private Status status = Status.QUEUED;

    private String providerReference;
    private String errorMessage;
    private Instant attemptedAt;
}
