package vn.phongkham.emr.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.emr.model.EmrRecord;

public interface EmrRecordRepository extends JpaRepository<EmrRecord, Long> {

    Optional<EmrRecord> findByVisitId(Long visitId);
}
