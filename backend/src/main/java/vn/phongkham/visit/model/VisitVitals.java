package vn.phongkham.visit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Sinh hiệu điều dưỡng đo trước khi vào khám (đo lại thì thêm bản ghi mới) */
@Entity
@Table(name = "visit_vitals")
@Getter
@Setter
public class VisitVitals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long visitId;
    private Short pulsePerMin;
    @Column(name = "temperature_c") // tên kết thúc bằng chữ hoa đơn lẻ, cách đặt tên mặc định thành "temperaturec"
    private BigDecimal temperatureC;
    private Short systolicBp;
    private Short diastolicBp;
    private Short respiratoryRate;
    private Short spo2Percent;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private Long recordedBy;
    private Instant recordedAt = Instant.now();
}
