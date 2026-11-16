package vn.phongkham.schedule.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.schedule.model.AppointmentSlot;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.model.ScheduleEnums.SlotStatus;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {

    /** Khóa dòng slot để hai người đặt cùng lúc không cùng lấy được một slot */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSlot s where s.id = :id")
    Optional<AppointmentSlot> findLockedById(@Param("id") Long id);

    List<AppointmentSlot> findBySessionIdOrderBySlotStart(Long sessionId);

    List<AppointmentSlot> findBySessionIdIn(Collection<Long> sessionIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AppointmentSlot s set s.status = :blocked, s.heldUntil = null where s.sessionId = :sessionId")
    int blockAll(@Param("sessionId") Long sessionId, @Param("blocked") SlotStatus blocked);

    /** Slot còn trống của các phiên đã công bố, kèm phiên khám: phần tử [0] = slot, [1] = phiên */
    @Query("""
            select sl, s from AppointmentSlot sl, WorkSession s
            where sl.sessionId = s.id and s.status = :published and sl.status = :available and sl.slotStart > :now
              and s.sessionDate between :from and :to
              and (:doctorId is null or s.doctorId = :doctorId)
              and (:specialtyId is null or s.specialtyId = :specialtyId)
            order by s.sessionDate, s.startsAt, s.doctorId, sl.slotStart
            """)
    List<Object[]> findOpenSlots(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("doctorId") Long doctorId,
                                 @Param("specialtyId") Long specialtyId, @Param("now") Instant now,
                                 @Param("published") SessionStatus published, @Param("available") SlotStatus available);

    default List<Object[]> findOpenSlots(LocalDate from, LocalDate to, Long doctorId, Long specialtyId, Instant now) {
        return findOpenSlots(from, to, doctorId, specialtyId, now, SessionStatus.PUBLISHED, SlotStatus.AVAILABLE);
    }
}
