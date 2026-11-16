package vn.phongkham.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.auth.model.PhoneVerification;
import vn.phongkham.auth.model.VerifyPurpose;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, VerifyPurpose purpose);

    /** Các phiên chưa dùng gần đây của một số, mới nhất trước */
    List<PhoneVerification> findByPhoneAndPurposeAndUsedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
            String phone, VerifyPurpose purpose, Instant after);

    /** Các phiên đang chờ tin nhắn của một số (mọi mục đích) */
    List<PhoneVerification> findByPhoneAndVerifiedAtIsNullAndExpiresAtAfter(String phone, Instant now);

    long countByPhoneAndCreatedAtAfter(String phone, Instant after);
}
