package vn.phongkham.visit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.phongkham.visit.model.VisitEnums.QueueGroup;
import vn.phongkham.visit.model.VisitEnums.VisitSource;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;

public final class VisitDtos {

    private VisitDtos() {
    }

    /** Người đưa đến khám: bắt buộc với trẻ dưới 72 tháng khi kê đơn */
    public record QrCheckInRequest(@NotBlank(message = "Thiếu mã QR") String token,
                                   @Size(max = 160, message = "Tối đa 160 ký tự") String companionName,
                                   @Size(max = 20, message = "Tối đa 20 ký tự") String companionPhone) {
    }

    public record CheckInRequest(@Size(max = 160, message = "Tối đa 160 ký tự") String companionName,
                                 @Size(max = 20, message = "Tối đa 20 ký tự") String companionPhone) {
    }

    public record WalkInRequest(@NotNull(message = "Vui lòng chọn hồ sơ bệnh nhân") Long patientId,
                                @NotNull(message = "Vui lòng chọn phiên khám") Long sessionId,
                                @Size(max = 1000, message = "Lý do khám tối đa 1000 ký tự") String reason,
                                @Size(max = 160, message = "Tối đa 160 ký tự") String companionName,
                                @Size(max = 20, message = "Tối đa 20 ký tự") String companionPhone) {
    }

    public record VisitResponse(Long id, String code, VisitSource source, VisitStatus status, int queueNumber,
                                QueueGroup queueGroup, Long patientId, String patientCode, String patientName,
                                LocalDate patientDateOfBirth, Long sessionId, LocalDate date, String shiftName,
                                Long doctorId, String doctorName, Long specialtyId, String specialtyName, String roomCode,
                                Long appointmentId, String reason, String companionName, Instant checkedInAt,
                                Instant calledAt, Instant startedAt, Instant completedAt) {
    }

    /** vitals: lần đo sinh hiệu gần nhất (null nếu chưa đo) */
    public record WaitingItem(int position, VisitResponse visit, VitalsResponse vitals) {
    }

    public record ReasonRequest(@NotBlank(message = "Vui lòng nhập lý do khám") @Size(max = 500, message = "Tối đa 500 ký tự") String reason) {
    }

    /** Màn hình hàng đợi của một phòng/phiên khám */
    public record QueueView(Long sessionId, String doctorName, String roomCode, List<VisitResponse> current,
                            List<WaitingItem> waiting, List<VisitResponse> awaitingResults, long done) {
    }

    /** peopleAhead: số người được gọi trước (chỉ khi đang chờ) */
    public record MyQueueItem(VisitResponse visit, Integer peopleAhead) {
    }

    public record VitalsRequest(
            @Min(value = 20, message = "Mạch từ 20 đến 300 lần/phút") @Max(value = 300, message = "Mạch từ 20 đến 300 lần/phút") Integer pulsePerMin,
            @DecimalMin(value = "30", message = "Nhiệt độ từ 30 đến 45 °C") @DecimalMax(value = "45", message = "Nhiệt độ từ 30 đến 45 °C") BigDecimal temperatureC,
            @Min(value = 40, message = "Huyết áp tâm thu từ 40 đến 300") @Max(value = 300, message = "Huyết áp tâm thu từ 40 đến 300") Integer systolicBp,
            @Min(value = 20, message = "Huyết áp tâm trương từ 20 đến 200") @Max(value = 200, message = "Huyết áp tâm trương từ 20 đến 200") Integer diastolicBp,
            @Min(value = 5, message = "Nhịp thở từ 5 đến 80 lần/phút") @Max(value = 80, message = "Nhịp thở từ 5 đến 80 lần/phút") Integer respiratoryRate,
            @Min(value = 50, message = "SpO2 từ 50 đến 100%") @Max(value = 100, message = "SpO2 từ 50 đến 100%") Integer spo2Percent,
            @DecimalMin(value = "0.1", message = "Cân nặng không hợp lệ") @DecimalMax(value = "500", message = "Cân nặng không hợp lệ") BigDecimal weightKg,
            @DecimalMin(value = "20", message = "Chiều cao không hợp lệ") @DecimalMax(value = "260", message = "Chiều cao không hợp lệ") BigDecimal heightCm) {
    }

    public record VitalsResponse(Long id, Integer pulsePerMin, BigDecimal temperatureC, Integer systolicBp, Integer diastolicBp,
                                 Integer respiratoryRate, Integer spo2Percent, BigDecimal weightKg, BigDecimal heightCm,
                                 Long recordedBy, String recordedByName, Instant recordedAt) {
    }
}
