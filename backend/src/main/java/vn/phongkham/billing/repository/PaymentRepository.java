package vn.phongkham.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.billing.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    java.util.List<Payment> findByInvoiceIdInOrderByCreatedAt(java.util.Collection<Long> invoiceIds);

    java.util.List<Payment> findByInvoiceIdAndStatus(Long invoiceId, Payment.Status status);
}
