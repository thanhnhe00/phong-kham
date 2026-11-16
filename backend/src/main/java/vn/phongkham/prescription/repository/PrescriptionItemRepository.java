package vn.phongkham.prescription.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.prescription.model.PrescriptionItem;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    List<PrescriptionItem> findByPrescriptionIdInOrderByLineNo(Collection<Long> prescriptionIds);

    List<PrescriptionItem> findByPrescriptionIdOrderByLineNo(Long prescriptionId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PrescriptionItem i where i.prescriptionId = :id")
    void deleteAllOfPrescription(@Param("id") Long prescriptionId);
}
