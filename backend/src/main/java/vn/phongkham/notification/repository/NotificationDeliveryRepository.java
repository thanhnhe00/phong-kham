package vn.phongkham.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.notification.model.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
}
