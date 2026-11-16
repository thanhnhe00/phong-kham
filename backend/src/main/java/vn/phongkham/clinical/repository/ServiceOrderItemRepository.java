package vn.phongkham.clinical.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.clinical.model.ServiceOrderItem;

public interface ServiceOrderItemRepository extends JpaRepository<ServiceOrderItem, Long> {

    java.util.List<ServiceOrderItem> findByServiceOrderIdIn(java.util.Collection<Long> orderIds);

    java.util.List<ServiceOrderItem> findByServiceOrderId(Long orderId);

    /** Mục chỉ định của các lượt khám còn mở (chưa kết thúc) */
    @org.springframework.data.jpa.repository.Query("""
            select i from ServiceOrderItem i, ServiceOrder o, vn.phongkham.visit.model.Visit v
            where i.serviceOrderId = o.id and o.visitId = v.id and v.status in :visitStatuses and i.status in :statuses
            order by i.createdAt""")
    java.util.List<ServiceOrderItem> findOfOpenVisits(java.util.Collection<vn.phongkham.visit.model.VisitEnums.VisitStatus> visitStatuses,
                                                      java.util.Collection<vn.phongkham.clinical.model.ClinicalEnums.ItemStatus> statuses);

    java.util.List<ServiceOrderItem> findByStatusInOrderByCreatedAt(java.util.Collection<vn.phongkham.clinical.model.ClinicalEnums.ItemStatus> statuses);
}
