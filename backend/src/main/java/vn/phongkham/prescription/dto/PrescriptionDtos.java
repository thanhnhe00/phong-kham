package vn.phongkham.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.phongkham.medicine.model.DrugClass;
import vn.phongkham.prescription.model.Prescription;

public final class PrescriptionDtos {

    private PrescriptionDtos() {
    }

    public record ItemRequest(
            @NotNull(message = "Vui lòng chọn thuốc") Long medicineId,
            @NotNull(message = "Vui lòng nhập số lượng") @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0") BigDecimal quantity,
            @NotBlank(message = "Vui lòng nhập liều mỗi lần") @Size(max = 60, message = "Liều tối đa 60 ký tự") String dosePerTime,
            @NotNull(message = "Vui lòng nhập số lần dùng mỗi ngày") @Min(value = 1, message = "Số lần/ngày từ 1 đến 12") @Max(value = 12, message = "Số lần/ngày từ 1 đến 12") Integer timesPerDay,
            @Size(max = 40, message = "Đường dùng tối đa 40 ký tự") String route,
            @Size(max = 120, message = "Thời điểm dùng tối đa 120 ký tự") String timing,
            @NotNull(message = "Vui lòng nhập số ngày dùng") @Min(value = 1, message = "Số ngày dùng tối thiểu 1") Integer days,
            String note) {
    }

    public record PrescriptionRequest(@NotEmpty(message = "Đơn thuốc cần ít nhất một thuốc") @Valid List<ItemRequest> items,
                                      String advice) {
    }

    public record ReplaceRequest(@NotBlank(message = "Vui lòng nhập lý do thay đơn") String reason,
                                 @NotNull(message = "Thiếu nội dung đơn mới") @Valid PrescriptionRequest content) {
    }

    public record ItemResponse(Long id, int lineNo, Long medicineId, String medicineLabel, DrugClass drugClass,
                               BigDecimal quantity, String unit, String dosePerTime, int timesPerDay, String route,
                               String timing, int days, String note) {
    }

    public record PrescriptionResponse(Long id, String code, Long visitId, Prescription.Status status, String diagnosisText,
                                       String advice, Long replacesId, String replacementReason, Long signedBy,
                                       String signedByName, String prescriberLicenseNo, Instant signedAt,
                                       Instant nationalSentAt, Instant createdAt, String allergyAck, List<ItemResponse> items) {
    }

    /** Bệnh nhân có dị ứng: bác sĩ phải xác nhận đã đối chiếu (allergyChecked) và có thể ghi lý do vẫn kê */
    public record SignRequest(Boolean allergyChecked, String allergyNote) {
    }
}
