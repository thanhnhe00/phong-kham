package vn.phongkham.clinical.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.clinical.model.ServicePerformance;

public interface ServicePerformanceRepository extends JpaRepository<ServicePerformance, Long> {

    java.util.List<ServicePerformance> findByOrderItemIdIn(java.util.Collection<Long> itemIds);

    java.util.List<ServicePerformance> findByOrderItemId(Long itemId);
}
