package vn.phongkham.notification.service;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.notification.model.Notification;
import vn.phongkham.notification.model.NotificationDelivery;
import vn.phongkham.notification.model.PushToken;
import vn.phongkham.notification.repository.NotificationDeliveryRepository;
import vn.phongkham.notification.repository.NotificationRepository;
import vn.phongkham.notification.repository.PushTokenRepository;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.repository.PatientAccountRepository;

/**
 * UC12: thông báo trong app (luôn có) + ghi nhận kênh push.
 * Thông báo cho một hồ sơ bệnh nhân được gửi tới mọi tài khoản đang quản lý hồ sơ đó.
 */
@Service
public class NotificationService {

    public record NotificationResponse(Long id, String type, String title, String body, String relatedEntityType,
                                       Long relatedEntityId, Instant readAt, Instant createdAt) {
    }

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final PushTokenRepository pushTokens;
    private final PatientAccountRepository accounts;

    public NotificationService(NotificationRepository notifications, NotificationDeliveryRepository deliveries,
                               PushTokenRepository pushTokens, PatientAccountRepository accounts) {
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.pushTokens = pushTokens;
        this.accounts = accounts;
    }

    /** Gửi cho mọi tài khoản app đang quản lý hồ sơ bệnh nhân (cha và mẹ cùng nhận thông báo của con) */
    public void toPatient(Long patientId, String type, String title, String body, String entityType, Long entityId) {
        for (PatientAccount a : accounts.findByIdPatientId(patientId)) {
            toUser(a.getId().getUserId(), type, title, body, entityType, entityId);
        }
    }

    public void toUser(Long userId, String type, String title, String body, String entityType, Long entityId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setNotificationType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setRelatedEntityType(entityType);
        n.setRelatedEntityId(entityId);
        notifications.save(n);
        deliveries.save(delivery(n.getId(), NotificationDelivery.Channel.IN_APP, NotificationDelivery.Status.SENT));
        if (pushTokens.existsByUserId(userId)) {
            // shortcut: chưa gọi Expo Push API, chỉ ghi hàng đợi QUEUED; khi tích hợp thì gửi và cập nhật SENT/FAILED
            deliveries.save(delivery(n.getId(), NotificationDelivery.Channel.PUSH, NotificationDelivery.Status.QUEUED));
        }
    }

    /** Đánh dấu không còn hiệu lực (vd. nhắc lịch của lịch đã đổi/hủy) */
    public void voidAll(String entityType, Long entityId, String type) {
        notifications.findByRelatedEntityTypeAndRelatedEntityIdAndNotificationType(entityType, entityId, type)
                .forEach(n -> n.setVoid(true));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(AuthUser me, Pageable pageable) {
        return PageResponse.of(notifications.findByUserIdAndIsVoidFalseOrderByCreatedAtDesc(me.id(), pageable),
                NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthUser me) {
        return notifications.countByUserIdAndIsVoidFalseAndReadAtIsNull(me.id());
    }

    @Transactional
    public NotificationResponse markRead(AuthUser me, Long id) {
        Notification n = notifications.findById(id).filter(x -> x.getUserId().equals(me.id()))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        return toResponse(n);
    }

    @Transactional
    public int markAllRead(AuthUser me) {
        return notifications.markAllRead(me.id());
    }

    /** Một token chỉ thuộc một tài khoản: đăng nhập tài khoản khác trên cùng máy thì token chuyển chủ */
    @Transactional
    public void registerPushToken(AuthUser me, String token, String platform) {
        PushToken t = pushTokens.findById(token).orElseGet(PushToken::new);
        t.setToken(token);
        t.setUserId(me.id());
        t.setPlatform(platform);
        t.setUpdatedAt(Instant.now());
        pushTokens.save(t);
    }

    @Transactional
    public void removePushToken(AuthUser me, String token) {
        pushTokens.findById(token).filter(t -> t.getUserId().equals(me.id())).ifPresent(pushTokens::delete);
    }

    private static NotificationDelivery delivery(Long notificationId, NotificationDelivery.Channel channel,
                                                 NotificationDelivery.Status status) {
        NotificationDelivery d = new NotificationDelivery();
        d.setNotificationId(notificationId);
        d.setChannel(channel);
        d.setStatus(status);
        d.setAttemptedAt(status == NotificationDelivery.Status.SENT ? Instant.now() : null);
        return d;
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getNotificationType(), n.getTitle(), n.getBody(),
                n.getRelatedEntityType(), n.getRelatedEntityId(), n.getReadAt(), n.getCreatedAt());
    }

    /** Dùng trong test / danh sách nhanh */
    public List<Notification> ofUser(Long userId) {
        return notifications.findByUserIdAndIsVoidFalseOrderByCreatedAtDesc(userId, Pageable.unpaged()).getContent();
    }
}
