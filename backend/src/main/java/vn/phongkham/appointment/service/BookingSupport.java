package vn.phongkham.appointment.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.model.AppointmentServiceItem;
import vn.phongkham.appointment.repository.AppointmentRepository;
import vn.phongkham.appointment.repository.AppointmentServiceItemRepository;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.model.ServiceCategory;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;
import vn.phongkham.schedule.model.AppointmentSlot;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.model.ScheduleEnums.SlotStatus;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.AppointmentSlotRepository;
import vn.phongkham.schedule.repository.WorkSessionRepository;

/** Phần dùng chung khi đặt / đổi / hủy lịch: khóa slot, giải phóng slot, lưu dịch vụ đăng ký trước. */
@Component
class BookingSupport {

    private final AppointmentRepository appointments;
    private final AppointmentSlotRepository slots;
    private final WorkSessionRepository sessions;
    private final AppointmentServiceItemRepository serviceItems;
    private final MedicalServiceRepository services;

    BookingSupport(AppointmentRepository appointments, AppointmentSlotRepository slots, WorkSessionRepository sessions,
                   AppointmentServiceItemRepository serviceItems, MedicalServiceRepository services) {
        this.appointments = appointments;
        this.slots = slots;
        this.sessions = sessions;
        this.serviceItems = serviceItems;
        this.services = services;
    }

    /**
     * Khóa dòng slot (SELECT ... FOR UPDATE) và kiểm tra còn nhận được: phiên đã công bố, chưa qua giờ, còn trống,
     * bệnh nhân chưa có lịch khác trong cùng phiên. Phải gọi trong transaction.
     */
    AppointmentSlot lockFreeSlot(Long slotId, Long patientId, Long ignoreAppointmentId) {
        AppointmentSlot sl = slots.findLockedById(slotId).orElseThrow(() -> ApiException.notFound("Không tìm thấy khung giờ"));
        WorkSession s = sessions.findById(sl.getSessionId()).orElseThrow();
        if (s.getStatus() != SessionStatus.PUBLISHED) {
            throw ApiException.badRequest("Phiên khám không còn nhận đặt lịch");
        }
        if (!sl.getSlotStart().isAfter(Instant.now())) {
            throw ApiException.badRequest("Khung giờ đã qua");
        }
        if (sl.getStatus() != SlotStatus.AVAILABLE) {
            throw ApiException.conflict("Khung giờ này đã có người đặt, vui lòng chọn giờ khác");
        }
        boolean already = appointments.findInSessions(List.of(s.getId()), AppointmentStatus.ACTIVE).stream()
                .anyMatch(a -> a.getPatientId().equals(patientId) && !a.getId().equals(ignoreAppointmentId));
        if (already) {
            throw ApiException.conflict("Bệnh nhân đã có lịch hẹn trong phiên khám này");
        }
        return sl;
    }

    /** Trả slot cũ về trống nếu phiên còn hoạt động và chưa qua giờ (phiên đã hủy thì slot giữ trạng thái khóa) */
    void releaseSlot(Long slotId) {
        AppointmentSlot sl = slots.findLockedById(slotId).orElseThrow();
        WorkSession s = sessions.findById(sl.getSessionId()).orElseThrow();
        if (s.getStatus() == SessionStatus.PUBLISHED && sl.getSlotStart().isAfter(Instant.now())) {
            sl.release();
        }
    }

    Appointment newAppointment() {
        Appointment a = new Appointment();
        a.setAppointmentCode("TMP-" + UUID.randomUUID()); // đổi thành LH<id> sau khi lưu
        return a;
    }

    void assignCode(Appointment a) {
        appointments.saveAndFlush(a);
        a.setAppointmentCode("LH%07d".formatted(a.getId()));
    }

    /** Dịch vụ đăng ký trước (xét nghiệm, siêu âm...): phải đang áp dụng và không phải phí khám */
    void saveServices(Long appointmentId, List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        var ids = new LinkedHashSet<>(serviceIds);
        List<MedicalService> found = services.findAllById(ids);
        if (found.size() != ids.size() || found.stream().anyMatch(s -> !s.isActive())) {
            throw ApiException.badRequest("Có dịch vụ không tồn tại hoặc đã ngừng áp dụng");
        }
        if (found.stream().anyMatch(s -> s.getCategory() == ServiceCategory.CONSULTATION)) {
            throw ApiException.badRequest("Phí khám đã tính theo chuyên khoa, không cần đăng ký thêm");
        }
        serviceItems.saveAll(ids.stream().map(id -> new AppointmentServiceItem(appointmentId, id)).toList());
    }
}
