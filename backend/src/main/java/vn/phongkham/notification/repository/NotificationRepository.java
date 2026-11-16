package vn.phongkham.notification.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.notification.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdAndIsVoidFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsVoidFalseAndReadAtIsNull(Long userId);

    List<Notification> findByRelatedEntityTypeAndRelatedEntityIdAndNotificationType(String entityType, Long entityId, String type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = CURRENT_TIMESTAMP where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId);
}
