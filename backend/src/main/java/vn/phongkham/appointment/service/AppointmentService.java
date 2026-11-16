package vn.phongkham.appointment.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.appointment.dto.AppointmentDtos.AppointmentResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.ChangeRequestResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.ContactLogged;
import vn.phongkham.appointment.dto.AppointmentDtos.ContactRequest;
import vn.phongkham.appointment.dto.AppointmentDtos.ContactResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.QueueItem;
import vn.phongkham.appointment.dto.AppointmentDtos.StaffBookingRequest;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.appointment.model.AppointmentChangeRequest;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.model.AppointmentEnums.BookingChannel;
import vn.phongkham.appointment.model.AppointmentEnums.ChangeRequestType;
import vn.phongkham.appointment.model.AppointmentEnums.ContactChannel;
import vn.phongkham.appointment.model.AppointmentEnums.ContactPurpose;
import vn.phongkham.appointment.model.AppointmentEnums.ContactResult;
import vn.phongkham.appointment.model.AppointmentEnums.SimpleRequestStatus;
import vn.phongkham.appointment.model.ContactLog;
import vn.phongkham.appointment.repository.AppointmentChangeRequestRepository;
import vn.phongkham.appointment.repository.AppointmentRepository;
import vn.phongkham.appointment.repository.ContactLogRepository;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.schedule.model.AppointmentSlot;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.repository.AppointmentSlotRepository;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.schedule.service.ScheduleLookups;
import vn.phongkham.staff.service.CurrentStaff;

/**
 * UC14-16: lễ tân / trực ban tạo lịch qua điện thoại hoặc tại quầy, xử lý hàng chờ xác nhận, ghi nhật ký gọi,
 * xác nhận / đổi giờ / hủy, xử lý yêu cầu đổi/hủy của bệnh nhân. Không tự hủy lịch khi chưa liên hệ được.
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final AppointmentSlotRepository slots;
    private final WorkSessionRepository sessions;
    private final PatientRepository patients;
    private final ContactLogRepository contacts;
    private final AppointmentChangeRequestRepository changeRequests;
    private final BookingSupport booking;
    private final AppointmentViews views;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;
    private final AppointmentNotifier notifier;
    private final int maxContactAttempts;
    private final Duration noShowAfter;
    private final Duration noShowWindow;

    public AppointmentService(AppointmentRepository appointments, AppointmentSlotRepository slots, WorkSessionRepository sessions,
                              PatientRepository patients, ContactLogRepository contacts,
                              AppointmentChangeRequestRepository changeRequests, BookingSupport booking, AppointmentViews views,
                              ScheduleLookups lookups, CurrentStaff currentStaff, AppointmentNotifier notifier,
                              @Value("${app.booking.max-contact-attempts:3}") int maxContactAttempts,
                              @Value("${app.booking.no-show-after:15m}") Duration noShowAfter,
                              @Value("${app.booking.no-show-window:30d}") Duration noShowWindow) {
        this.appointments = appointments;
        this.slots = slots;
        this.sessions = sessions;
        this.patients = patients;
        this.contacts = contacts;
        this.changeRequests = changeRequests;
        this.booking = booking;
        this.views = views;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
        this.notifier = notifier;
        this.maxContactAttempts = maxContactAttempts;
        this.noShowAfter = noShowAfter;
        this.noShowWindow = noShowWindow;
    }

    /** Lễ tân đã thống nhất giờ với bệnh nhân qua điện thoại / tại quầy nên lịch được xác nhận ngay */
    @Transactional
    public AppointmentResponse create(AuthUser me, StaffBookingRequest req) {
        if (req.channel() == BookingChannel.APP_REQUEST) {
            throw ApiException.badRequest("Kênh đặt lịch của nhân viên là PHONE hoặc COUNTER");
        }
        patients.findById(req.patientId()).orElseThrow(() -> ApiException.badRequest("Không tìm thấy hồ sơ bệnh nhân"));
        Long staffId = currentStaff.idOf(me);
        AppointmentSlot sl = booking.lockFreeSlot(req.slotId(), req.patientId(), null);
        Appointment a = booking.newAppointment();
        a.setPatientId(req.patientId());
        a.setSlotId(sl.getId());
        a.setChannel(req.channel());
        a.setReason(blankToNull(req.reason()));
        a.setCreatedBy(staffId);
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setConfirmedBy(staffId);
        a.setConfirmedAt(Instant.now());
        sl.book();
        booking.assignCode(a);
        booking.saveServices(a.getId(), req.serviceIds());
        notifier.confirmed(a);
        return views.of(a);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(LocalDate date, Long doctorId, Long patientId, AppointmentStatus status,
                                                    Pageable pageable) {
        Page<Appointment> page = appointments.search(date, doctorId, patientId, status, pageable);
        return new PageResponse<>(views.of(page.getContent()), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id) {
        return views.of(load(id));
    }

    /** Hàng chờ của trực ban: yêu cầu mới từ app + lịch cần xác nhận lại (đổi bác sĩ, hủy phiên), giờ khám gần nhất trước */
    @Transactional(readOnly = true)
    public List<QueueItem> confirmationQueue() {
        List<Appointment> list = appointments.findQueue(
                List.of(AppointmentStatus.PENDING_CONFIRMATION, AppointmentStatus.NEEDS_RECONFIRMATION), Instant.now());
        Map<Long, Long> noAnswer = noAnswerCounts(list.stream().map(Appointment::getId).toList());
        Instant since = Instant.now().minus(noShowWindow);
        var responses = views.of(list);
        return responses.stream().map(r -> {
            long n = noAnswer.getOrDefault(r.id(), 0L);
            long noShows = appointments.countByPatientIdAndStatusAndCreatedAtAfter(r.patientId(), AppointmentStatus.NO_SHOW, since);
            return new QueueItem(r, n, n >= maxContactAttempts, noShows);
        }).toList();
    }

    @Transactional
    public ContactLogged logContact(AuthUser me, Long id, ContactRequest req) {
        Appointment a = load(id);
        ContactLog c = new ContactLog();
        c.setAppointmentId(a.getId());
        c.setPatientId(a.getPatientId());
        c.setEmployeeId(currentStaff.idOf(me));
        c.setChannel(req.channel() == null ? ContactChannel.PHONE : req.channel());
        c.setPurpose(req.purpose() != null ? req.purpose()
                : a.getStatus() == AppointmentStatus.NEEDS_RECONFIRMATION ? ContactPurpose.DOCTOR_CHANGE : ContactPurpose.CONFIRM_BOOKING);
        c.setResult(req.result());
        c.setNote(blankToNull(req.note()));
        c.setPhoneNumber(patients.findById(a.getPatientId()).map(p -> p.getPhone()).orElse(null));
        contacts.save(c);
        long n = noAnswerCounts(List.of(a.getId())).getOrDefault(a.getId(), 0L);
        return new ContactLogged(toContact(c, lookups.employeeNames(List.of(c.getEmployeeId()))), n, n >= maxContactAttempts);
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts(Long id) {
        load(id);
        List<ContactLog> list = contacts.findByAppointmentIdOrderByContactedAtDesc(id);
        Map<Long, String> names = lookups.employeeNames(list.stream().map(ContactLog::getEmployeeId).distinct().toList());
        return list.stream().map(c -> toContact(c, names)).toList();
    }

    /** Trực ban đã gọi và bệnh nhân đồng ý giờ khám */
    @Transactional
    public AppointmentResponse confirm(AuthUser me, Long id) {
        Appointment a = load(id);
        if (a.getStatus() != AppointmentStatus.PENDING_CONFIRMATION && a.getStatus() != AppointmentStatus.NEEDS_RECONFIRMATION) {
            throw ApiException.badRequest("Lịch hẹn không ở trạng thái chờ xác nhận");
        }
        AppointmentSlot sl = slots.findLockedById(a.getSlotId()).orElseThrow();
        if (sessions.findById(sl.getSessionId()).orElseThrow().getStatus() != SessionStatus.PUBLISHED) {
            throw ApiException.badRequest("Phiên khám đã hủy, hãy đổi lịch sang giờ khác");
        }
        if (!sl.getSlotStart().isAfter(Instant.now())) {
            throw ApiException.badRequest("Đã qua giờ hẹn, hãy đổi lịch sang giờ khác");
        }
        sl.book();
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setConfirmedBy(currentStaff.idOf(me));
        a.setConfirmedAt(Instant.now());
        notifier.confirmed(a);
        return views.of(a);
    }

    /** Đổi sang khung giờ khác (đã thống nhất với bệnh nhân) -> xác nhận luôn */
    @Transactional
    public AppointmentResponse reschedule(AuthUser me, Long id, Long newSlotId) {
        Appointment a = load(id);
        requireUpcoming(a);
        if (a.getSlotId().equals(newSlotId)) {
            throw ApiException.badRequest("Khung giờ mới trùng khung giờ hiện tại");
        }
        AppointmentSlot target = booking.lockFreeSlot(newSlotId, a.getPatientId(), a.getId());
        booking.releaseSlot(a.getSlotId());
        a.setSlotId(target.getId());
        target.book();
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setConfirmedBy(currentStaff.idOf(me));
        a.setConfirmedAt(Instant.now());
        a.setRemindedAt(null); // giờ mới: nhắc lại
        notifier.voidReminders(a);
        notifier.confirmed(a);
        return views.of(a);
    }

    @Transactional
    public AppointmentResponse cancel(Long id, String reason) {
        Appointment a = load(id);
        requireUpcoming(a);
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelledAt(Instant.now());
        a.setCancelReason(blankToNull(reason));
        booking.releaseSlot(a.getSlotId());
        notifier.cancelled(a);
        return views.of(a);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> listChangeRequests(SimpleRequestStatus status) {
        return toChangeResponses(changeRequests.findByStatusOrderByCreatedAt(status == null ? SimpleRequestStatus.PENDING : status));
    }

    /** Duyệt yêu cầu của bệnh nhân: hủy lịch, hoặc chuyển sang khung giờ bệnh nhân chọn (nếu còn trống) */
    @Transactional
    public ChangeRequestResponse approveChangeRequest(AuthUser me, Long requestId) {
        AppointmentChangeRequest r = loadPendingRequest(requestId);
        if (r.getRequestType() == ChangeRequestType.CANCEL) {
            cancel(r.getAppointmentId(), r.getReason() == null ? "Bệnh nhân yêu cầu hủy" : "Bệnh nhân yêu cầu hủy: " + r.getReason());
        } else {
            if (r.getRequestedSlotId() == null) {
                throw ApiException.badRequest("Yêu cầu không có khung giờ mới");
            }
            reschedule(me, r.getAppointmentId(), r.getRequestedSlotId());
        }
        r.setStatus(SimpleRequestStatus.APPROVED);
        r.setHandledBy(currentStaff.idOf(me));
        r.setHandledAt(Instant.now());
        return toChangeResponses(List.of(r)).getFirst();
    }

    @Transactional
    public ChangeRequestResponse rejectChangeRequest(AuthUser me, Long requestId) {
        AppointmentChangeRequest r = loadPendingRequest(requestId);
        r.setStatus(SimpleRequestStatus.REJECTED);
        r.setHandledBy(currentStaff.idOf(me));
        r.setHandledAt(Instant.now());
        return toChangeResponses(List.of(r)).getFirst();
    }

    /** Lịch đã xác nhận quá giờ hẹn + 15 phút mà chưa check-in -> NO_SHOW (không phạt, không khóa ngay) */
    @Transactional
    public int markNoShows(Instant now) {
        return appointments.markNoShows(now.minus(noShowAfter), now, AppointmentStatus.CONFIRMED, AppointmentStatus.NO_SHOW);
    }

    /** Nhắc lịch: lịch đã xác nhận, giờ khám trong 24 giờ tới, chưa nhắc */
    @Transactional
    public int sendReminders(Instant now) {
        List<Appointment> due = appointments.findDueReminders(AppointmentStatus.CONFIRMED, now, now.plus(Duration.ofHours(24)));
        due.forEach(a -> {
            notifier.reminder(a);
            a.setRemindedAt(now);
        });
        return due.size();
    }

    List<ChangeRequestResponse> toChangeResponses(List<AppointmentChangeRequest> list) {
        Map<Long, String> codes = appointments.findAllById(list.stream().map(AppointmentChangeRequest::getAppointmentId).distinct().toList())
                .stream().collect(Collectors.toMap(Appointment::getId, Appointment::getAppointmentCode));
        Map<Long, Instant> slotStarts = slots.findAllById(list.stream().map(AppointmentChangeRequest::getRequestedSlotId)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(AppointmentSlot::getId, AppointmentSlot::getSlotStart));
        return list.stream().map(r -> new ChangeRequestResponse(r.getId(), r.getAppointmentId(), codes.get(r.getAppointmentId()),
                r.getRequestType(), r.getRequestedSlotId(), slotStarts.get(r.getRequestedSlotId()), r.getReason(), r.getStatus(),
                r.getCreatedAt(), r.getHandledAt())).toList();
    }

    private AppointmentChangeRequest loadPendingRequest(Long id) {
        AppointmentChangeRequest r = changeRequests.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu"));
        if (r.getStatus() != SimpleRequestStatus.PENDING) {
            throw ApiException.badRequest("Yêu cầu đã được xử lý");
        }
        return r;
    }

    private Map<Long, Long> noAnswerCounts(List<Long> appointmentIds) {
        if (appointmentIds.isEmpty()) {
            return Map.of();
        }
        return contacts.countByResult(appointmentIds, ContactResult.NO_ANSWER).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    static void requireUpcoming(Appointment a) {
        if (!AppointmentStatus.UPCOMING.contains(a.getStatus())) {
            throw ApiException.badRequest("Lịch hẹn đã kết thúc hoặc đã hủy, không thay đổi được");
        }
    }

    private Appointment load(Long id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy lịch hẹn"));
    }

    private static ContactResponse toContact(ContactLog c, Map<Long, String> names) {
        return new ContactResponse(c.getId(), c.getChannel(), c.getPurpose(), c.getResult(), c.getPhoneNumber(), c.getNote(),
                c.getEmployeeId(), names.get(c.getEmployeeId()), c.getContactedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
