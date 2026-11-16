package vn.phongkham.patient.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import vn.phongkham.patient.model.PatientEnums.AccountRelationship;

/** Tài khoản app được quản lý hồ sơ bệnh nhân (một hồ sơ nhiều tài khoản: bố và mẹ cùng quản lý hồ sơ con) */
@Entity
@Table(name = "patient_accounts")
@Getter
@Setter
@NoArgsConstructor
public class PatientAccount {

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "account_relationship")
    private AccountRelationship relationship = AccountRelationship.SELF;

    /** Nhân viên xác minh; null = tài khoản tự liên kết bằng mã */
    private Long verifiedBy;
    private Instant linkedAt = Instant.now();

    public PatientAccount(Long patientId, Long userId, AccountRelationship relationship) {
        this.id = new Key(patientId, userId);
        this.relationship = relationship;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        private Long patientId;
        private Long userId;

        public Key(Long patientId, Long userId) {
            this.patientId = patientId;
            this.userId = userId;
        }
    }
}
