package vn.phongkham.staff.model;

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
import vn.phongkham.staff.model.StaffEnums.HistoryKind;

/** Quá trình đào tạo / công tác của bác sĩ (hiển thị trên trang hồ sơ) */
@Entity
@Table(name = "doctor_history")
@Getter
@Setter
public class DoctorHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "history_kind")
    private HistoryKind kind;

    private Short fromYear;
    private Short toYear;
    private String title;
    private String place;
}
