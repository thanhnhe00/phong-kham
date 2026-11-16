package vn.phongkham.appointment.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Lịch hẹn chưa đến khám thuộc các phiên khám (dùng khi đổi bác sĩ / hủy phiên) */
    @Query("""
            select a from Appointment a, AppointmentSlot sl
            where a.slotId = sl.id and sl.sessionId in :sessionIds and a.status in :statuses
            """)
    List<Appointment> findInSessions(@Param("sessionIds") Collection<Long> sessionIds,
                                     @Param("statuses") Collection<AppointmentStatus> statuses);

    /** Lịch hẹn của bệnh nhân còn hiệu lực trong cùng phiên khám (mỗi người một lượt mỗi phiên) */
    @Query("""
            select count(a) > 0 from Appointment a, AppointmentSlot sl
            where a.slotId = sl.id and sl.sessionId = :sessionId and a.patientId = :patientId and a.status in :statuses
            """)
    boolean existsInSession(@Param("patientId") Long patientId, @Param("sessionId") Long sessionId,
                            @Param("statuses") Collection<AppointmentStatus> statuses);

    long countByPatientIdAndStatusAndCreatedAtAfter(Long patientId, AppointmentStatus status, Instant after);

    @Query("""
            select a from Appointment a, AppointmentSlot sl, WorkSession s
            where a.slotId = sl.id and sl.sessionId = s.id
              and (cast(:date as LocalDate) is null or s.sessionDate = :date)
              and (:doctorId is null or s.doctorId = :doctorId)
              and (:patientId is null or a.patientId = :patientId)
              and (:anyStatus = true or a.status = :status)
            order by sl.slotStart, a.id
            """)
    Page<Appointment> search(@Param("date") LocalDate date, @Param("doctorId") Long doctorId, @Param("patientId") Long patientId,
                             @Param("anyStatus") boolean anyStatus, @Param("status") AppointmentStatus status, Pageable pageable);

    default Page<Appointment> search(LocalDate date, Long doctorId, Long patientId, AppointmentStatus status, Pageable pageable) {
        return search(date, doctorId, patientId, status == null, status == null ? AppointmentStatus.CONFIRMED : status, pageable);
    }

    /** Hàng chờ xác nhận của trực ban: yêu cầu mới + lịch cần xác nhận lại, giờ khám gần nhất trước */
    @Query("""
            select a from Appointment a, AppointmentSlot sl
            where a.slotId = sl.id and a.status in :statuses and sl.slotStart > :now
            order by sl.slotStart, a.id
            """)
    List<Appointment> findQueue(@Param("statuses") Collection<AppointmentStatus> statuses, @Param("now") Instant now);

    @Query("""
            select a from Appointment a, AppointmentSlot sl
            where a.slotId = sl.id and a.patientId in :patientIds
            order by sl.slotStart desc
            """)
    List<Appointment> findByPatients(@Param("patientIds") Collection<Long> patientIds);

    @Query("""
            select a from Appointment a, AppointmentSlot sl
            where a.slotId = sl.id and a.status = :status and a.remindedAt is null
              and sl.slotStart > :from and sl.slotStart <= :to
            """)
    List<Appointment> findDueReminders(@Param("status") AppointmentStatus status, @Param("from") Instant from,
                                       @Param("to") Instant to);

    /** Lịch đã xác nhận mà quá giờ hẹn + grace chưa check-in -> NO_SHOW */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Appointment a set a.status = :noShow, a.updatedAt = :now
            where a.status = :confirmed
              and a.slotId in (select sl.id from AppointmentSlot sl where sl.slotStart < :cutoff)
            """)
    int markNoShows(@Param("cutoff") Instant cutoff, @Param("now") Instant now,
                    @Param("confirmed") AppointmentStatus confirmed, @Param("noShow") AppointmentStatus noShow);
}
