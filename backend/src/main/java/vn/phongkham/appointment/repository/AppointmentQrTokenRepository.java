package vn.phongkham.appointment.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.appointment.model.AppointmentQrToken;

public interface AppointmentQrTokenRepository extends JpaRepository<AppointmentQrToken, Long> {

    Optional<AppointmentQrToken> findByAppointmentId(Long appointmentId);

    Optional<AppointmentQrToken> findByTokenHash(String tokenHash);

    /** Đánh dấu đã dùng một cách nguyên tử: trả 0 nếu đã có người dùng trước (chống quét lại QR) */
    @Modifying(flushAutomatically = true)
    @Query("update AppointmentQrToken q set q.usedAt = :now where q.id = :id and q.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);
}
