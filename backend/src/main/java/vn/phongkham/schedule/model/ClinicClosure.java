package vn.phongkham.schedule.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Ngày phòng khám nghỉ (lễ, Tết...): không sinh phiên khám vào ngày này */
@Entity
@Table(name = "clinic_closures")
@Getter
@Setter
public class ClinicClosure {

    @Id
    private LocalDate closedDate;

    private String reason;
}
