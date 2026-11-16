package vn.phongkham.auth.model;

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

/**
 * Phiên xác minh số điện thoại (đăng ký, quên mật khẩu): người dùng nhắn mã từ chính SIM của mình tới phòng khám.
 * Chỉ lưu hash của mã.
 */
@Entity
@Table(name = "phone_verifications")
@Getter
@Setter
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "verify_purpose")
    private VerifyPurpose purpose;

    private String codeHash;
    private short attempts;
    private Instant expiresAt;
    private Instant verifiedAt;
    private Instant usedAt;
    private Instant createdAt = Instant.now();
}
