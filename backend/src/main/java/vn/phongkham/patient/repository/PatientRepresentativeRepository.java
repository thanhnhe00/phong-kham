package vn.phongkham.patient.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.patient.model.PatientRepresentative;

public interface PatientRepresentativeRepository extends JpaRepository<PatientRepresentative, Long> {

    List<PatientRepresentative> findByPatientIdOrderById(Long patientId);

    Optional<PatientRepresentative> findByIdAndPatientId(Long id, Long patientId);
}
