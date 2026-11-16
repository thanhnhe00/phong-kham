package vn.phongkham.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.billing.model.InvoiceItem;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    java.util.List<InvoiceItem> findByInvoiceIdInOrderById(java.util.Collection<Long> invoiceIds);
}
