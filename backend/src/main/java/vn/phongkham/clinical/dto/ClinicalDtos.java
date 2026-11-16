package vn.phongkham.clinical.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.phongkham.clinical.model.ClinicalEnums.FileCategory;
import vn.phongkham.clinical.model.ClinicalEnums.ItemStatus;
import vn.phongkham.clinical.model.ClinicalEnums.OrderStatus;
import vn.phongkham.clinical.model.ClinicalEnums.PerformanceStatus;
import vn.phongkham.clinical.model.ClinicalEnums.ResultStatus;
import vn.phongkham.medicalservice.model.ServiceCategory;

public final class ClinicalDtos {

    private ClinicalDtos() {
    }

    public record OrderItemRequest(@NotNull(message = "Vui lòng chọn dịch vụ") Long serviceId,
                                   @Min(value = 1, message = "Số lượng tối thiểu 1") @Max(value = 20, message = "Số lượng tối đa 20") Integer quantity) {
    }

    public record OrderRequest(@NotEmpty(message = "Chọn ít nhất một dịch vụ") @Valid List<OrderItemRequest> items, String note) {
    }

    public record RefuseRequest(@NotBlank(message = "Vui lòng ghi nhận lý do bệnh nhân từ chối") String note) {
    }

    public record PerformRequest(@Min(value = 1, message = "Số lượng tối thiểu 1") Integer quantity, String note) {
    }

    /** submit = true: gửi bác sĩ duyệt; false: lưu nháp */
    public record ResultRequest(String resultText, String conclusion, Boolean submit) {
    }

    public record AmendResultRequest(String resultText, String conclusion,
                                     @NotBlank(message = "Vui lòng nhập lý do điều chỉnh") String reason) {
    }

    public record FileResponse(Long id, String fileName, String mimeType, long sizeBytes, FileCategory category, Instant createdAt) {
    }

    public record ResultResponse(Long id, Long performanceId, ResultStatus status, int version, String resultText,
                                 String conclusion, Long enteredBy, String enteredByName, Instant enteredAt, Long reviewedBy,
                                 String reviewedByName, Instant reviewedAt, String returnNote, List<FileResponse> files) {
    }

    public record PerformanceResponse(Long id, Long orderItemId, Long performedBy, String performedByName, Instant performedAt, int quantity,
                                      PerformanceStatus status, String note, ResultResponse result) {
    }

    public record ItemResponse(Long id, Long orderId, Long serviceId, String serviceName, ServiceCategory category,
                               int quantity, BigDecimal unitPrice, ItemStatus status, String refusalNote,
                               List<PerformanceResponse> performances) {
    }

    public record OrderResponse(Long id, Long visitId, Long orderedBy, String orderedByName, Instant orderedAt, String note,
                                OrderStatus status, List<ItemResponse> items) {
    }

    /** Danh sách việc của kỹ thuật viên */
    /** Việc cận lâm sàng của lượt khám còn mở: chờ làm, đang nhập, bị trả lại, chờ bác sĩ duyệt */
    public record WorklistItem(ItemResponse item, Long visitId, String visitCode, Long patientId, String patientCode,
                               String patientName, Long doctorId, String doctorName, Instant orderedAt) {
    }

    public record ReturnRequest(@NotBlank(message = "Vui lòng ghi lý do trả lại") String reason) {
    }

    public record StoredFile(String fileName, String mimeType, byte[] content) {
    }
}
