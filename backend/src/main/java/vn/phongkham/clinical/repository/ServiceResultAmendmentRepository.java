package vn.phongkham.clinical.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.clinical.model.ServiceResultAmendment;

public interface ServiceResultAmendmentRepository extends JpaRepository<ServiceResultAmendment, Long> {

    java.util.List<ServiceResultAmendment> findByResultIdOrderByFromVersion(Long resultId);
}
