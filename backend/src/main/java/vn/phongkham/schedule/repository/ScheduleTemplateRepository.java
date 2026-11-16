package vn.phongkham.schedule.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.schedule.model.ScheduleEnums.TemplateStatus;
import vn.phongkham.schedule.model.ScheduleTemplate;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {

    LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    /** Lịch tuần khác (chưa ngừng) trùng bác sĩ hoặc trùng phòng, cùng thứ, giao giờ và giao khoảng hiệu lực */
    @Query("""
            select t from ScheduleTemplate t
            where t.status <> :inactive and t.id <> :selfId and t.weekday = :weekday
              and (t.doctorId = :doctorId or t.roomId = :roomId)
              and t.startTime < :endTime and t.endTime > :startTime
              and t.effectiveFrom <= :to and coalesce(t.effectiveTo, :openEnd) >= :from
            """)
    List<ScheduleTemplate> findConflicts(@Param("selfId") long selfId, @Param("weekday") short weekday,
                                         @Param("doctorId") Long doctorId, @Param("roomId") Long roomId,
                                         @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                                         @Param("from") LocalDate from, @Param("to") LocalDate to,
                                         @Param("openEnd") LocalDate openEnd, @Param("inactive") TemplateStatus inactive);

    default List<ScheduleTemplate> findConflicts(ScheduleTemplate t) {
        return findConflicts(t.getId() == null ? -1 : t.getId(), t.getWeekday(), t.getDoctorId(), t.getRoomId(),
                t.getStartTime(), t.getEndTime(), t.getEffectiveFrom(),
                t.getEffectiveTo() == null ? OPEN_END : t.getEffectiveTo(), OPEN_END, TemplateStatus.INACTIVE);
    }

    @Query("""
            select t from ScheduleTemplate t
            where (:doctorId is null or t.doctorId = :doctorId)
            order by t.weekday, t.startTime
            """)
    List<ScheduleTemplate> findByDoctor(@Param("doctorId") Long doctorId);

    /** Lịch tuần đã duyệt có hiệu lực giao với [from, to] */
    @Query("""
            select t from ScheduleTemplate t
            where t.status = :approved and t.effectiveFrom <= :to and coalesce(t.effectiveTo, :openEnd) >= :from
            """)
    List<ScheduleTemplate> findApprovedIn(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                          @Param("openEnd") LocalDate openEnd, @Param("approved") TemplateStatus approved);

    default List<ScheduleTemplate> findApprovedIn(LocalDate from, LocalDate to) {
        return findApprovedIn(from, to, OPEN_END, TemplateStatus.APPROVED);
    }
}
