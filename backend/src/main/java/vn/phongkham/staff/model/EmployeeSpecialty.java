package vn.phongkham.staff.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chuyên khoa của nhân viên; mỗi người tối đa một chuyên khoa chính */
@Entity
@Table(name = "employee_specialties")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeSpecialty {

    @EmbeddedId
    private Key id;

    private boolean isPrimary;

    public EmployeeSpecialty(Long employeeId, Long specialtyId, boolean isPrimary) {
        this.id = new Key(employeeId, specialtyId);
        this.isPrimary = isPrimary;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        private Long employeeId;
        private Long specialtyId;

        public Key(Long employeeId, Long specialtyId) {
            this.employeeId = employeeId;
            this.specialtyId = specialtyId;
        }
    }
}
