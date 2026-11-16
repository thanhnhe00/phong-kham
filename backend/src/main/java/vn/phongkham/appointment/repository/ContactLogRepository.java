package vn.phongkham.appointment.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.appointment.model.AppointmentEnums.ContactResult;
import vn.phongkham.appointment.model.ContactLog;

public interface ContactLogRepository extends JpaRepository<ContactLog, Long> {

    List<ContactLog> findByAppointmentIdOrderByContactedAtDesc(Long appointmentId);

    /** [0] = appointmentId, [1] = số lần liên hệ có kết quả result */
    @Query("select c.appointmentId, count(c) from ContactLog c where c.appointmentId in :ids and c.result = :result group by c.appointmentId")
    List<Object[]> countByResult(@Param("ids") Collection<Long> appointmentIds, @Param("result") ContactResult result);
}
