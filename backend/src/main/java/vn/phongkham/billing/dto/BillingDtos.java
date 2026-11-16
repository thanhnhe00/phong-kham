package vn.phongkham.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.phongkham.billing.model.Invoice;
import vn.phongkham.billing.model.InvoiceItem;
import vn.phongkham.billing.model.Payment;

public final class BillingDtos {

    private BillingDtos() {
    }

    public record PaymentRequest(
            @NotNull(message = "Vui lòng chọn hình thức thanh toán") Payment.Method method,
            @NotNull(message = "Vui lòng nhập số tiền") @DecimalMin(value = "1", message = "Số tiền phải lớn hơn 0")
            @Digits(integer = 12, fraction = 0, message = "Số tiền là số nguyên VND") BigDecimal amount,
            String note) {
    }

    public record RefundRequest(
            @NotNull(message = "Vui lòng nhập số tiền hoàn") @DecimalMin(value = "1", message = "Số tiền phải lớn hơn 0")
            @Digits(integer = 12, fraction = 0, message = "Số tiền là số nguyên VND") BigDecimal amount,
            @NotNull(message = "Vui lòng nhập lý do hoàn tiền") String note) {
    }

    public record ItemResponse(Long id, InvoiceItem.Type type, String description, BigDecimal quantity, BigDecimal unitPrice,
                               BigDecimal lineTotal) {
    }

    public record PaymentResponse(Long id, String code, Payment.Kind kind, Payment.Method method, BigDecimal amount,
                                  Payment.Status status, String providerReference, Long receivedBy, Instant paidAt, String note) {
    }

    public record InvoiceResponse(Long id, String code, Long visitId, String visitCode, Long patientId, String patientCode,
                                  String patientName, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount,
                                  BigDecimal paidAmount, BigDecimal remaining, Invoice.Status status, Instant issuedAt,
                                  List<ItemResponse> items, List<PaymentResponse> payments) {
    }
}
