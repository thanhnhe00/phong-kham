package vn.phongkham.notification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Token nhận thông báo đẩy của thiết bị (Expo push token) */
@Entity
@Table(name = "push_tokens")
@Getter
@Setter
public class PushToken {

    @Id
    private String token;

    private Long userId;
    private String platform;
    private Instant updatedAt = Instant.now();
}
