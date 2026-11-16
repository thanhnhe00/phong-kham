package vn.phongkham.patient.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.model.PatientEnums.AccountRelationship;

public interface PatientAccountRepository extends JpaRepository<PatientAccount, PatientAccount.Key> {

    List<PatientAccount> findByIdUserIdOrderByLinkedAt(Long userId);

    List<PatientAccount> findByIdPatientId(Long patientId);

    boolean existsByIdUserIdAndRelationship(Long userId, AccountRelationship relationship);

    default boolean isLinked(Long userId, Long patientId) {
        return existsById(new PatientAccount.Key(patientId, userId));
    }
}
