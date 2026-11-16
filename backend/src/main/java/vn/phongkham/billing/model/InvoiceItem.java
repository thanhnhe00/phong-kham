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

/** Dòng hóa đơn: phí khám hoặc một dịch vụ đã thực hiện (mỗi mục chỉ định chỉ tính một lần) */
@Entity
@Table(name = "invoice_items")
@Getter
@Setter
public class InvoiceItem {

    public enum Type { CONSULTATION, SERVICE, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "invoice_item_type")
    private Type itemType;

    private Long serviceId;
    private Long serviceOrderItemId;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
