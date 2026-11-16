package vn.phongkham.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.billing.model.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    java.util.Optional<Invoice> findByVisitId(Long visitId);

    /** Khóa dòng khi thu/hoàn tiền để hai giao dịch cùng lúc không vượt số phải thu */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from Invoice i where i.id = :id")
    java.util.Optional<Invoice> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    java.util.List<Invoice> findByVisitIdIn(java.util.Collection<Long> visitIds);

    /** anyStatus = true: không lọc trạng thái */
    @org.springframework.data.jpa.repository.Query("""
            select i from Invoice i
            where (:anyStatus = true or i.status = :status) and i.createdAt >= :from and i.createdAt < :to
            order by i.createdAt desc
            """)
    org.springframework.data.domain.Page<Invoice> search(
            @org.springframework.data.repository.query.Param("anyStatus") boolean anyStatus,
            @org.springframework.data.repository.query.Param("status") Invoice.Status status,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to,
            org.springframework.data.domain.Pageable pageable);
}
