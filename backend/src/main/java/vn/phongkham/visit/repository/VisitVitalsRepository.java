package vn.phongkham.visit.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.visit.model.VisitVitals;

public interface VisitVitalsRepository extends JpaRepository<VisitVitals, Long> {

    List<VisitVitals> findByVisitIdOrderByRecordedAt(Long visitId);

    List<VisitVitals> findByVisitIdInOrderByRecordedAt(List<Long> visitIds);

    Optional<VisitVitals> findFirstByVisitIdOrderByRecordedAtDesc(Long visitId);
}
