package vn.phongkham.appointment.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dịch vụ bệnh nhân đăng ký trước khi đến khám; bác sĩ xem và quyết định chỉ định khi khám */
@Entity
@Table(name = "appointment_services")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentServiceItem {

    @EmbeddedId
    private Key id;

    private String note;

    public AppointmentServiceItem(Long appointmentId, Long serviceId) {
        this.id = new Key(appointmentId, serviceId);
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        private Long appointmentId;
        private Long serviceId;

        public Key(Long appointmentId, Long serviceId) {
            this.appointmentId = appointmentId;
            this.serviceId = serviceId;
        }
    }
}
