package vn.phongkham.clinical.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.clinical.model.ServiceResult;

public interface ServiceResultRepository extends JpaRepository<ServiceResult, Long> {

    java.util.Optional<ServiceResult> findByPerformanceId(Long performanceId);

    java.util.List<ServiceResult> findByPerformanceIdIn(java.util.Collection<Long> performanceIds);
}
