package vn.phongkham.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import vn.phongkham.schedule.model.ScheduleEnums.ChangeType;
import vn.phongkham.schedule.model.ScheduleEnums.RequestStatus;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.model.ScheduleEnums.SlotStatus;
import vn.phongkham.schedule.model.ScheduleEnums.TemplateStatus;

/** Dữ liệu vào/ra của lịch bác sĩ. Giờ (startTime/endTime) là giờ Việt Nam dạng "HH:mm". */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record TemplateRequest(
            @NotNull(message = "Vui lòng chọn bác sĩ") Long doctorId,
            @NotNull(message = "Vui lòng chọn chuyên khoa") Long specialtyId,
            @NotNull(message = "Vui lòng chọn phòng") Long roomId,
            @NotNull(message = "Vui lòng chọn thứ") @Min(value = 1, message = "Thứ từ 1 (thứ Hai) đến 7 (Chủ nhật)") @Max(value = 7, message = "Thứ từ 1 (thứ Hai) đến 7 (Chủ nhật)") Integer weekday,
            @NotBlank(message = "Vui lòng nhập tên buổi") @Size(max = 30, message = "Tên buổi tối đa 30 ký tự") String shiftName,
            @NotNull(message = "Vui lòng nhập giờ bắt đầu") LocalTime startTime,
            @NotNull(message = "Vui lòng nhập giờ kết thúc") LocalTime endTime,
            @Min(value = 5, message = "Mỗi lượt tối thiểu 5 phút") @Max(value = 120, message = "Mỗi lượt tối đa 120 phút") Integer slotMinutes,
            @Min(value = 0, message = "Số lượt vãng lai không được âm") Integer walkInCapacity,
            @NotNull(message = "Vui lòng nhập ngày bắt đầu áp dụng") LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public record TemplateResponse(Long id, Long doctorId, String doctorName, Long specialtyId, String specialtyName,
                                   Long roomId, String roomCode, int weekday, String shiftName, LocalTime startTime,
                                   LocalTime endTime, int slotMinutes, int walkInCapacity, LocalDate effectiveFrom,
                                   LocalDate effectiveTo, TemplateStatus status, Instant doctorConfirmedAt, Instant approvedAt) {
    }

    public record GenerateRequest(@NotNull(message = "Vui lòng chọn ngày bắt đầu") LocalDate from,
                                  @NotNull(message = "Vui lòng chọn ngày kết thúc") LocalDate to) {
    }

    public record SkippedSession(Long templateId, LocalDate date, String reason) {
    }

    public record GenerateResponse(int created, int alreadyExisted, List<SkippedSession> skipped) {
    }

    /** Phiên khám tạo tay ngoài lịch tuần (khám bù, tăng ca) */
    public record SessionRequest(
            @NotNull(message = "Vui lòng chọn bác sĩ") Long doctorId,
            @NotNull(message = "Vui lòng chọn chuyên khoa") Long specialtyId,
            @NotNull(message = "Vui lòng chọn phòng") Long roomId,
            @NotNull(message = "Vui lòng chọn ngày") LocalDate date,
            @NotBlank(message = "Vui lòng nhập tên buổi") @Size(max = 30, message = "Tên buổi tối đa 30 ký tự") String shiftName,
            @NotNull(message = "Vui lòng nhập giờ bắt đầu") LocalTime startTime,
            @NotNull(message = "Vui lòng nhập giờ kết thúc") LocalTime endTime,
            @Min(value = 5, message = "Mỗi lượt tối thiểu 5 phút") @Max(value = 120, message = "Mỗi lượt tối đa 120 phút") Integer slotMinutes,
            @Min(value = 0, message = "Số lượt vãng lai không được âm") Integer walkInCapacity) {
    }

    public record SessionResponse(Long id, Long templateId, LocalDate date, String shiftName, Instant startsAt, Instant endsAt,
                                  SessionStatus status, Long doctorId, String doctorName, Long originalDoctorId,
                                  Long specialtyId, String specialtyName, Long roomId, String roomCode,
                                  int walkInCapacity, int totalSlots, int availableSlots) {
    }

    public record SlotResponse(Long id, Instant start, Instant end, SlotStatus status) {
    }

    public record SessionDetail(SessionResponse session, List<SlotResponse> slots) {
    }

    /** affectedAppointments: số lịch hẹn chuyển sang "cần xác nhận lại" để lễ tân gọi */
    public record CancelResponse(SessionResponse session, int affectedAppointments) {
    }

    public record ClosureRequest(@NotNull(message = "Vui lòng chọn ngày") LocalDate date,
                                 @NotBlank(message = "Vui lòng nhập lý do") @Size(max = 200, message = "Lý do tối đa 200 ký tự") String reason) {
    }

    public record ClosureResponse(LocalDate date, String reason) {
    }

    /** Slot trống công khai, gom theo phiên khám */
    public record OpenSession(Long sessionId, LocalDate date, String shiftName, Long doctorId, String doctorName,
                              Long specialtyId, String specialtyName, String roomCode, List<SlotResponse> slots) {
    }

    public record ChangeRequestCreate(
            @NotNull(message = "Vui lòng chọn phiên khám") Long sessionId,
            @NotNull(message = "Vui lòng chọn loại yêu cầu") ChangeType type,
            Long replacementDoctorId,
            Long swapSessionId,
            @NotBlank(message = "Vui lòng nhập lý do") String reason) {
    }

    public record ChangeRequestResponse(Long id, Long sessionId, LocalDate sessionDate, String shiftName, ChangeType type,
                                        Long requestedBy, String requesterName, Long replacementDoctorId, String replacementName,
                                        Long swapSessionId, String reason, RequestStatus status, String managerNote,
                                        Instant createdAt) {
    }

    public record RespondRequest(@NotNull(message = "Vui lòng chọn đồng ý hoặc từ chối") Boolean accept) {
    }

    public record DecisionRequest(@NotNull(message = "Vui lòng chọn duyệt hoặc từ chối") Boolean approve, String note) {
    }
}
