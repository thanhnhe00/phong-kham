package vn.phongkham.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.model.AppointmentEnums.BookingChannel;
import vn.phongkham.appointment.model.AppointmentEnums.ChangeRequestType;
import vn.phongkham.appointment.model.AppointmentEnums.ContactChannel;
import vn.phongkham.appointment.model.AppointmentEnums.ContactPurpose;
import vn.phongkham.appointment.model.AppointmentEnums.ContactResult;
import vn.phongkham.appointment.model.AppointmentEnums.SimpleRequestStatus;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /** Bệnh nhân gửi yêu cầu đặt lịch trên app (chờ trực ban gọi xác nhận) */
    public record BookingRequest(
            @NotNull(message = "Vui lòng chọn hồ sơ bệnh nhân") Long patientId,
            @NotNull(message = "Vui lòng chọn khung giờ") Long slotId,
            @Size(max = 1000, message = "Lý do khám tối đa 1000 ký tự") String reason,
            List<Long> serviceIds) {
    }

    /** Lễ tân tạo lịch khi bệnh nhân gọi điện / đến quầy (đã thống nhất với bệnh nhân nên xác nhận ngay) */
    public record StaffBookingRequest(
            @NotNull(message = "Vui lòng chọn hồ sơ bệnh nhân") Long patientId,
            @NotNull(message = "Vui lòng chọn khung giờ") Long slotId,
            @NotNull(message = "Vui lòng chọn kênh đặt lịch") BookingChannel channel,
            @Size(max = 1000, message = "Lý do khám tối đa 1000 ký tự") String reason,
            List<Long> serviceIds) {
    }

    public record RescheduleRequest(@NotNull(message = "Vui lòng chọn khung giờ mới") Long slotId) {
    }

    public record CancelRequest(@Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason) {
    }

    public record ContactRequest(
            @NotNull(message = "Vui lòng chọn kết quả liên hệ") ContactResult result,
            ContactPurpose purpose,
            ContactChannel channel,
            @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự") String note) {
    }

    public record ContactResponse(Long id, ContactChannel channel, ContactPurpose purpose, ContactResult result,
                                  String phoneNumber, String note, Long employeeId, String employeeName, Instant contactedAt) {
    }

    /** maxAttemptsReached: đã gọi không được đủ số lần quy định -> giữ chờ, không tự hủy */
    public record ContactLogged(ContactResponse contact, long noAnswerCount, boolean maxAttemptsReached) {
    }

    public record ChangeRequestCreate(
            @NotNull(message = "Vui lòng chọn loại yêu cầu") ChangeRequestType type,
            Long requestedSlotId,
            @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason) {
    }

    public record ChangeRequestResponse(Long id, Long appointmentId, String appointmentCode, ChangeRequestType type,
                                        Long requestedSlotId, Instant requestedSlotStart, String reason,
                                        SimpleRequestStatus status, Instant createdAt, Instant handledAt) {
    }

    public record ServiceRef(Long id, String name) {
    }

    public record AppointmentResponse(Long id, String code, AppointmentStatus status, BookingChannel channel,
                                      Long patientId, String patientCode, String patientName, String patientPhone,
                                      Long slotId, Instant start, Instant end, Long sessionId, LocalDate date, String shiftName,
                                      Long doctorId, String doctorName, Long specialtyId, String specialtyName, String roomCode,
                                      String reason, List<ServiceRef> services, Instant confirmedAt, Instant cancelledAt,
                                      String cancelReason, Instant createdAt) {
    }

    /** Một dòng trong hàng chờ xác nhận của trực ban */
    public record QueueItem(AppointmentResponse appointment, long noAnswerCount, boolean maxAttemptsReached,
                            long recentNoShows) {
    }

    public record QrResponse(String token, Instant expiresAt) {
    }
}
