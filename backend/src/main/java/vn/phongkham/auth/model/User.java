package vn.phongkham.auth.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/** Tài khoản đăng nhập (bệnh nhân hoặc nhân viên). Số điện thoại là tên đăng nhập. */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String phone;
    private String email;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "user_status")
    private UserStatus status = UserStatus.PENDING;

    private Instant phoneVerifiedAt;
    /** Tăng mỗi khi khóa tài khoản / đổi mật khẩu: token cũ mang version cũ sẽ bị từ chối */
    private int tokenVersion;
    private boolean mustChangePassword;
    private int failedLogins;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private Instant passwordChangedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "role", columnDefinition = "role_code")
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    public void bumpTokenVersion() {
        tokenVersion++;
    }
}
