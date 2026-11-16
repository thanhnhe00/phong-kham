package vn.phongkham.patient.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.patient.model.PatientAllergy;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Long> {

    List<PatientAllergy> findByPatientIdOrderByRecordedAt(Long patientId);

    Optional<PatientAllergy> findByIdAndPatientId(Long id, Long patientId);
}
