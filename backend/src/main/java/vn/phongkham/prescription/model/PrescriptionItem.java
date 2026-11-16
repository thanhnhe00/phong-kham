package vn.phongkham.prescription.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Một thuốc trong đơn: liều mỗi lần, số lần/ngày, đường dùng, thời điểm dùng, số ngày */
@Entity
@Table(name = "prescription_items")
@Getter
@Setter
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long prescriptionId;
    private Short lineNo;
    private Long medicineId;
    /** "hoạt chất (tên thương mại) hàm lượng" chép lúc kê, không đổi khi danh mục thuốc đổi */
    private String medicineLabel;
    private BigDecimal quantity;
    private String dosePerTime;
    private Short timesPerDay;
    private String route;
    private String timing;
    private Short days;
    private String note;
}
