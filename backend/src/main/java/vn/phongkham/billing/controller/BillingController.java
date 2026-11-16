package vn.phongkham.billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.billing.dto.BillingDtos.InvoiceResponse;
import vn.phongkham.billing.dto.BillingDtos.PaymentRequest;
import vn.phongkham.billing.dto.BillingDtos.RefundRequest;
import vn.phongkham.billing.model.Invoice;
import vn.phongkham.billing.service.BillingService;
import vn.phongkham.common.Patterns;
import vn.phongkham.common.dto.PageResponse;

@Tag(name = "Hóa đơn và thanh toán")
@RestController
public class BillingController {

    private static final String CASHIER = "hasAnyRole('RECEPTIONIST','ADMIN')";

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách hóa đơn (mặc định hôm nay); status=UNPAID để theo dõi chưa thu")
    @PreAuthorize(CASHIER)
    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> search(@RequestParam(required = false) Invoice.Status status,
                                                @RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @PageableDefault(size = 50) Pageable pageable) {
        return service.search(status, from, to, pageable);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/invoices/{id}")
    public InvoiceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/visits/{visitId}/invoice")
    public InvoiceResponse forVisit(@PathVariable Long visitId) {
        return service.forVisit(visitId);
    }

    @Operation(summary = "Thu tiền tại quầy (CASH, BANK_TRANSFER, OTHER)")
    @PreAuthorize(CASHIER)
    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse collect(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                   @Valid @RequestBody PaymentRequest req) {
        return service.collect(me, id, req);
    }

    @Operation(summary = "Hoàn tiền (quản lý)")
    @PreAuthorize(Patterns.ADMIN)
    @PostMapping("/invoices/{id}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse refund(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                  @Valid @RequestBody RefundRequest req) {
        return service.refund(me, id, req.amount(), req.note());
    }

    @Operation(summary = "Bệnh nhân thanh toán trực tuyến phần còn lại (mô phỏng)")
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/me/invoices/{id}/pay")
    public InvoiceResponse payOnline(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.payOnline(me, id);
    }
}
