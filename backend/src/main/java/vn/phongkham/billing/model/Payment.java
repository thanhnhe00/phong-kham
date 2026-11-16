package vn.phongkham.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/** Giao dịch thu tiền / hoàn tiền (số tiền luôn dương, phân biệt bằng kind) */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    public enum Kind { PAYMENT, REFUND }

    public enum Method { CASH, BANK_TRANSFER, ONLINE_SIMULATED, OTHER }

    public enum Status { PENDING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paymentCode;
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "payment_kind")
    private Kind kind = Kind.PAYMENT;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "payment_method")
    private Method method;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "payment_status")
    private Status status = Status.PENDING;

    private String providerReference;
    private Long receivedBy;
    private Instant paidAt;
    private String note;
    private Instant createdAt = Instant.now();
}
