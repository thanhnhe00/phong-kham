package vn.phongkham.prescription.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.prescription.model.Prescription;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByVisitIdOrderByCreatedAt(Long visitId);

    boolean existsByVisitIdAndStatus(Long visitId, Prescription.Status status);

    boolean existsByReplacesId(Long replacesId);
}
