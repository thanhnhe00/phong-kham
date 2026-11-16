package vn.phongkham.clinical.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.clinical.model.ServiceOrder;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

    java.util.List<ServiceOrder> findByVisitIdOrderByOrderedAt(Long visitId);
}
