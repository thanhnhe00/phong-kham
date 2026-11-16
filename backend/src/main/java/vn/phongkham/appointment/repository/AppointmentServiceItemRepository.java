package vn.phongkham.appointment.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.appointment.model.AppointmentServiceItem;

public interface AppointmentServiceItemRepository extends JpaRepository<AppointmentServiceItem, AppointmentServiceItem.Key> {

    List<AppointmentServiceItem> findByIdAppointmentIdIn(Collection<Long> appointmentIds);
}
