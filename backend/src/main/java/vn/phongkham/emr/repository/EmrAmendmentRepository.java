package vn.phongkham.emr.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.emr.model.EmrAmendment;

public interface EmrAmendmentRepository extends JpaRepository<EmrAmendment, Long> {

    List<EmrAmendment> findByEmrIdOrderByFromVersion(Long emrId);
}
