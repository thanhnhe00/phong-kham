package vn.phongkham.medicalservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.model.ServiceCategory;

public interface MedicalServiceRepository extends JpaRepository<MedicalService, Long> {

    List<MedicalService> findByIsActiveTrueOrderByCategoryAscNameAsc();

    List<MedicalService> findByIsActiveTrueAndCategoryOrderByNameAsc(ServiceCategory category);

    List<MedicalService> findAllByOrderByCategoryAscNameAsc();
}
