package vn.phongkham.medicine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/** Danh mục thuốc để kê đơn (phòng khám không bán thuốc) */
@Entity
@Table(name = "medicines")
@Getter
@Setter
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tên chung quốc tế, bắt buộc ghi trên đơn */
    private String activeIngredient;
    private String brandName;
    private String strength;
    private String dosageForm;
    private String defaultRoute;
    private String unit;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "drug_class")
    private DrugClass drugClass = DrugClass.NORMAL;

    private boolean isActive = true;
}
