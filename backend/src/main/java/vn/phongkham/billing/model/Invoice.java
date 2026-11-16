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

/** Hóa đơn tổng hợp của một lượt khám (nội bộ, không phải hóa đơn điện tử hợp pháp) */
@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice {

    public enum Status { UNPAID, PARTIALLY_PAID, PAID, REFUNDED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String invoiceCode;
    private Long visitId;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "invoice_status")
    private Status status = Status.UNPAID;

    private Long issuedBy;
    private Instant issuedAt;
    private Instant createdAt = Instant.now();
}
