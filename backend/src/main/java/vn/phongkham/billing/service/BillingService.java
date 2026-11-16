package vn.phongkham.billing.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.billing.dto.BillingDtos.InvoiceResponse;
import vn.phongkham.billing.dto.BillingDtos.ItemResponse;
import vn.phongkham.billing.dto.BillingDtos.PaymentRequest;
import vn.phongkham.billing.dto.BillingDtos.PaymentResponse;
import vn.phongkham.billing.model.Invoice;
import vn.phongkham.billing.model.InvoiceItem;
import vn.phongkham.billing.model.Payment;
import vn.phongkham.billing.repository.InvoiceItemRepository;
import vn.phongkham.billing.repository.InvoiceRepository;
import vn.phongkham.billing.repository.PaymentRepository;
import vn.phongkham.clinical.model.ServiceOrderItem;
import vn.phongkham.clinical.service.ClinicalService;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.specialty.repository.SpecialtyRepository;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.repository.VisitRepository;
import vn.phongkham.visit.service.VisitAccess;

/**
 * UC19: một hóa đơn tổng hợp mỗi lượt khám, lập khi kết thúc khám: phí khám theo chuyên khoa + dịch vụ đã thực hiện
 * (dịch vụ bị từ chối / bớt / chưa làm không tính). Không thu trước. Đã trả = tổng thu thành công − tổng hoàn.
 */
@Service
public class BillingService {

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository invoiceItems;
    private final PaymentRepository payments;
    private final ClinicalService clinical;
    private final MedicalServiceRepository services;
    private final SpecialtyRepository specialties;
    private final WorkSessionRepository sessions;
    private final VisitRepository visits;
    private final PatientRepository patients;
    private final VisitAccess access;
    private final CurrentStaff currentStaff;

    public BillingService(InvoiceRepository invoices, InvoiceItemRepository invoiceItems, PaymentRepository payments,
                          ClinicalService clinical, MedicalServiceRepository services, SpecialtyRepository specialties,
                          WorkSessionRepository sessions, VisitRepository visits, PatientRepository patients,
                          VisitAccess access, CurrentStaff currentStaff) {
        this.invoices = invoices;
        this.invoiceItems = invoiceItems;
        this.payments = payments;
        this.clinical = clinical;
        this.services = services;
        this.specialties = specialties;
        this.sessions = sessions;
        this.visits = visits;
        this.patients = patients;
        this.access = access;
        this.currentStaff = currentStaff;
    }

    /** Gọi trong transaction kết thúc lượt khám; lượt khám đã có hóa đơn thì trả hóa đơn cũ */
    public Invoice createForVisit(Visit v, Long issuedBy) {
        var existing = invoices.findByVisitId(v.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Invoice inv = new Invoice();
        inv.setInvoiceCode("TMP-" + UUID.randomUUID());
        inv.setVisitId(v.getId());
        inv.setIssuedBy(issuedBy);
        inv.setIssuedAt(Instant.now());
        invoices.saveAndFlush(inv);
        inv.setInvoiceCode("HD%07d".formatted(inv.getId()));

        List<InvoiceItem> lines = new ArrayList<>();
        WorkSession s = sessions.findById(v.getSessionId()).orElseThrow();
        Long feeId = specialties.findById(s.getSpecialtyId()).map(x -> x.getConsultationServiceId()).orElse(null);
        if (feeId != null) {
            MedicalService fee = services.findById(feeId).orElseThrow();
            lines.add(line(inv.getId(), InvoiceItem.Type.CONSULTATION, fee.getId(), null, fee.getName(), 1, fee.getListPrice()));
        }
        Map<ServiceOrderItem, Integer> performed = clinical.performedItems(v.getId());
        Map<Long, String> names = services.findAllById(performed.keySet().stream().map(ServiceOrderItem::getServiceId).distinct().toList())
                .stream().collect(Collectors.toMap(MedicalService::getId, MedicalService::getName));
        performed.forEach((item, qty) -> lines.add(line(inv.getId(), InvoiceItem.Type.SERVICE, item.getServiceId(), item.getId(),
                names.get(item.getServiceId()), qty, item.getUnitPrice())));
        invoiceItems.saveAll(lines);
        BigDecimal subtotal = lines.stream().map(InvoiceItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        inv.setSubtotal(subtotal);
        inv.setTotalAmount(subtotal.subtract(inv.getDiscountAmount()));
        recompute(inv);
        return inv;
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return toResponses(List.of(load(id))).getFirst();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse forVisit(Long visitId) {
        return toResponses(List.of(invoices.findByVisitId(visitId)
                .orElseThrow(() -> ApiException.notFound("Lượt khám chưa có hóa đơn")))).getFirst();
    }

    /** Lễ tân theo dõi hóa đơn (mặc định hôm nay); status = UNPAID để xem công nợ */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(Invoice.Status status, LocalDate from, LocalDate to, Pageable pageable) {
        LocalDate start = from == null ? VnTime.today() : from;
        LocalDate end = to == null ? start : to;
        Page<Invoice> page = invoices.search(status == null, status == null ? Invoice.Status.UNPAID : status,
                VnTime.at(start, LocalTime.MIDNIGHT), VnTime.at(end.plusDays(1), LocalTime.MIDNIGHT), pageable);
        return new PageResponse<>(toResponses(page.getContent()), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Thu tiền tại quầy (tiền mặt / chuyển khoản), có thể thu nhiều lần */
    @Transactional
    public InvoiceResponse collect(AuthUser me, Long invoiceId, PaymentRequest req) {
        if (req.method() == Payment.Method.ONLINE_SIMULATED) {
            throw ApiException.badRequest("Thanh toán trực tuyến do bệnh nhân thực hiện trên app");
        }
        Invoice inv = loadForUpdate(invoiceId);
        requireWithinRemaining(inv, req.amount());
        payments.save(payment(inv.getId(), Payment.Kind.PAYMENT, req.method(), req.amount(), currentStaff.idOf(me), null, req.note()));
        payments.flush();
        recompute(inv);
        return toResponses(List.of(inv)).getFirst();
    }

    /**
     * Bệnh nhân thanh toán trực tuyến phần còn lại.
     * shortcut: mô phỏng cổng thanh toán thành công ngay; khi tích hợp thật phải tạo giao dịch PENDING và chỉ đánh dấu
     * SUCCESS khi máy chủ cổng thanh toán xác nhận (không dựa vào trình duyệt quay về trang thành công).
     */
    @Transactional
    public InvoiceResponse payOnline(AuthUser me, Long invoiceId) {
        Invoice inv = loadForUpdate(invoiceId);
        access.forPatient(me, inv.getVisitId());
        BigDecimal remaining = inv.getTotalAmount().subtract(inv.getPaidAmount());
        if (remaining.signum() <= 0) {
            throw ApiException.badRequest("Hóa đơn đã thanh toán đủ");
        }
        payments.save(payment(inv.getId(), Payment.Kind.PAYMENT, Payment.Method.ONLINE_SIMULATED, remaining, null,
                "SIM-" + UUID.randomUUID(), "Thanh toán trực tuyến (mô phỏng)"));
        payments.flush();
        recompute(inv);
        return toResponses(List.of(inv)).getFirst();
    }

    @Transactional
    public InvoiceResponse refund(AuthUser me, Long invoiceId, BigDecimal amount, String note) {
        Invoice inv = loadForUpdate(invoiceId);
        if (amount.compareTo(inv.getPaidAmount()) > 0) {
            throw ApiException.badRequest("Số tiền hoàn vượt quá số đã thu");
        }
        payments.save(payment(inv.getId(), Payment.Kind.REFUND, Payment.Method.CASH, amount, currentStaff.idOf(me), null, note));
        payments.flush();
        recompute(inv);
        return toResponses(List.of(inv)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> forVisits(List<Long> visitIds) {
        return visitIds.isEmpty() ? List.of() : toResponses(invoices.findByVisitIdIn(visitIds));
    }

    private void requireWithinRemaining(Invoice inv, BigDecimal amount) {
        BigDecimal remaining = inv.getTotalAmount().subtract(inv.getPaidAmount());
        if (remaining.signum() <= 0) {
            throw ApiException.badRequest("Hóa đơn đã thanh toán đủ");
        }
        if (amount.compareTo(remaining) > 0) {
            throw ApiException.badRequest("Số tiền vượt quá số còn phải thu (" + remaining.toPlainString() + " đ)");
        }
    }

    /** Đã trả = tổng thu thành công − tổng hoàn thành công; cập nhật trạng thái hóa đơn */
    private void recompute(Invoice inv) {
        BigDecimal paid = BigDecimal.ZERO;
        boolean anyPayment = false;
        for (Payment p : payments.findByInvoiceIdAndStatus(inv.getId(), Payment.Status.SUCCESS)) {
            paid = p.getKind() == Payment.Kind.PAYMENT ? paid.add(p.getAmount()) : paid.subtract(p.getAmount());
            anyPayment |= p.getKind() == Payment.Kind.PAYMENT;
        }
        inv.setPaidAmount(paid.max(BigDecimal.ZERO));
        int cmp = inv.getPaidAmount().compareTo(inv.getTotalAmount());
        if (inv.getPaidAmount().signum() == 0) {
            inv.setStatus(anyPayment ? Invoice.Status.REFUNDED : inv.getTotalAmount().signum() == 0 ? Invoice.Status.PAID : Invoice.Status.UNPAID);
        } else {
            inv.setStatus(cmp >= 0 ? Invoice.Status.PAID : Invoice.Status.PARTIALLY_PAID);
        }
    }

    private Payment payment(Long invoiceId, Payment.Kind kind, Payment.Method method, BigDecimal amount, Long receivedBy,
                            String providerRef, String note) {
        Payment p = new Payment();
        p.setPaymentCode("TT-" + UUID.randomUUID());
        p.setInvoiceId(invoiceId);
        p.setKind(kind);
        p.setMethod(method);
        p.setAmount(amount);
        p.setStatus(Payment.Status.SUCCESS);
        p.setPaidAt(Instant.now());
        p.setReceivedBy(receivedBy);
        p.setProviderReference(providerRef);
        p.setNote(note);
        return p;
    }

    private static InvoiceItem line(Long invoiceId, InvoiceItem.Type type, Long serviceId, Long orderItemId, String description,
                                    int qty, BigDecimal unitPrice) {
        InvoiceItem i = new InvoiceItem();
        i.setInvoiceId(invoiceId);
        i.setItemType(type);
        i.setServiceId(serviceId);
        i.setServiceOrderItemId(orderItemId);
        i.setDescription(description);
        i.setQuantity(BigDecimal.valueOf(qty));
        i.setUnitPrice(unitPrice);
        i.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));
        return i;
    }

    private Invoice load(Long id) {
        return invoices.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn"));
    }

    private Invoice loadForUpdate(Long id) {
        return invoices.findLockedById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn"));
    }

    private List<InvoiceResponse> toResponses(List<Invoice> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        var ids = list.stream().map(Invoice::getId).toList();
        Map<Long, List<ItemResponse>> itemMap = invoiceItems.findByInvoiceIdInOrderById(ids).stream()
                .collect(Collectors.groupingBy(InvoiceItem::getInvoiceId, Collectors.mapping(i -> new ItemResponse(i.getId(),
                        i.getItemType(), i.getDescription(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal()), Collectors.toList())));
        Map<Long, List<PaymentResponse>> payMap = payments.findByInvoiceIdInOrderByCreatedAt(ids).stream()
                .collect(Collectors.groupingBy(Payment::getInvoiceId, Collectors.mapping(p -> new PaymentResponse(p.getId(),
                        p.getPaymentCode(), p.getKind(), p.getMethod(), p.getAmount(), p.getStatus(), p.getProviderReference(),
                        p.getReceivedBy(), p.getPaidAt(), p.getNote()), Collectors.toList())));
        Map<Long, Visit> visitById = visits.findAllById(list.stream().map(Invoice::getVisitId).toList()).stream()
                .collect(Collectors.toMap(Visit::getId, Function.identity()));
        Map<Long, Patient> patientById = patients.findAllById(visitById.values().stream().map(Visit::getPatientId).distinct().toList())
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        return list.stream().map(i -> {
            Visit v = visitById.get(i.getVisitId());
            Patient p = patientById.get(v.getPatientId());
            return new InvoiceResponse(i.getId(), i.getInvoiceCode(), v.getId(), v.getVisitCode(), p.getId(), p.getPatientCode(),
                    p.getFullName(), i.getSubtotal(), i.getDiscountAmount(), i.getTotalAmount(), i.getPaidAmount(),
                    i.getTotalAmount().subtract(i.getPaidAmount()).max(BigDecimal.ZERO), i.getStatus(), i.getIssuedAt(),
                    itemMap.getOrDefault(i.getId(), List.of()), payMap.getOrDefault(i.getId(), List.of()));
        }).toList();
    }
}
