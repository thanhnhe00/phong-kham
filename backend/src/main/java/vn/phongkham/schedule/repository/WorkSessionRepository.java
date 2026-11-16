package vn.phongkham.schedule.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.model.WorkSession;

public interface WorkSessionRepository extends JpaRepository<WorkSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WorkSession s where s.id = :id")
    Optional<WorkSession> findLockedById(@Param("id") Long id);

    /** Phiên chưa hủy của bác sĩ (doctorId) hoặc tại phòng (roomId) giao với [start, end), bỏ qua các phiên trong exclude */
    @Query("""
            select s from WorkSession s
            where s.status <> :cancelled and s.id not in :exclude
              and (s.doctorId = :doctorId or s.roomId = :roomId)
              and s.startsAt < :end and s.endsAt > :start
            """)
    List<WorkSession> findOverlaps(@Param("doctorId") Long doctorId, @Param("roomId") Long roomId,
                                   @Param("start") Instant start, @Param("end") Instant end,
                                   @Param("exclude") List<Long> exclude, @Param("cancelled") SessionStatus cancelled);

    /** roomId = -1 để chỉ xét trùng bác sĩ */
    default List<WorkSession> findOverlaps(Long doctorId, Long roomId, Instant start, Instant end, List<Long> exclude) {
        return findOverlaps(doctorId, roomId, start, end, exclude.isEmpty() ? List.of(-1L) : exclude, SessionStatus.CANCELLED);
    }

    boolean existsByTemplateIdAndSessionDate(Long templateId, LocalDate sessionDate);

    @Query("""
            select s from WorkSession s
            where s.sessionDate between :from and :to
              and (:doctorId is null or s.doctorId = :doctorId)
              and (:specialtyId is null or s.specialtyId = :specialtyId)
              and (:roomId is null or s.roomId = :roomId)
            order by s.startsAt, s.roomId
            """)
    List<WorkSession> search(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("doctorId") Long doctorId,
                             @Param("specialtyId") Long specialtyId, @Param("roomId") Long roomId);
}
