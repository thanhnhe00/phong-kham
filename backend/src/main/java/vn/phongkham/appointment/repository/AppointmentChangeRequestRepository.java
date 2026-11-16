package vn.phongkham.appointment.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.appointment.model.AppointmentChangeRequest;
import vn.phongkham.appointment.model.AppointmentEnums.SimpleRequestStatus;

public interface AppointmentChangeRequestRepository extends JpaRepository<AppointmentChangeRequest, Long> {

    boolean existsByAppointmentIdAndStatus(Long appointmentId, SimpleRequestStatus status);

    List<AppointmentChangeRequest> findByStatusOrderByCreatedAt(SimpleRequestStatus status);

    List<AppointmentChangeRequest> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);
}
