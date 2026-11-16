package vn.phongkham.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.notification.model.PushToken;

public interface PushTokenRepository extends JpaRepository<PushToken, String> {

    boolean existsByUserId(Long userId);
}
