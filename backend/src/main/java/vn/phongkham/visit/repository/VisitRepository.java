package vn.phongkham.visit.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.model.VisitEnums.VisitSource;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    @Query("select coalesce(max(v.queueNumber), 0) from Visit v where v.sessionId = :sessionId")
    int maxQueueNumber(@Param("sessionId") Long sessionId);

    long countBySessionIdAndSourceAndStatusNot(Long sessionId, VisitSource source, VisitStatus status);

    List<Visit> findBySessionIdOrderByQueueNumber(Long sessionId);

    boolean existsBySessionIdAndPatientIdAndStatusIn(Long sessionId, Long patientId, Collection<VisitStatus> statuses);

    Optional<Visit> findByAppointmentId(Long appointmentId);

    @Query("select v from Visit v where v.patientId in :patientIds and v.checkedInAt >= :since order by v.checkedInAt desc")
    List<Visit> findRecentOfPatients(@Param("patientIds") Collection<Long> patientIds, @Param("since") Instant since);

    List<Visit> findByPatientIdInOrderByCheckedInAtDesc(Collection<Long> patientIds);
}
