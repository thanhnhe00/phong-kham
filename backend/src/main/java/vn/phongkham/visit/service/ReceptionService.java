package vn.phongkham.visit.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.model.AppointmentQrToken;
import vn.phongkham.appointment.repository.AppointmentQrTokenRepository;
import vn.phongkham.appointment.repository.AppointmentRepository;
import vn.phongkham.appointment.service.MyAppointmentService;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.schedule.model.AppointmentSlot;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.AppointmentSlotRepository;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.dto.VisitDtos.VisitResponse;
import vn.phongkham.visit.dto.VisitDtos.WalkInRequest;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.model.VisitEnums.CheckinMethod;
import vn.phongkham.visit.model.VisitEnums.QueueGroup;
import vn.phongkham.visit.model.VisitEnums.VisitSource;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;
import vn.phongkham.visit.repository.VisitRepository;

/**
 * UC08, UC17, UC18: check-in bằng QR hoặc tại quầy, cấp số thứ tự; tiếp nhận vãng lai khi phiên còn hạn mức.
 * Check-in muộn hơn giờ hẹn thì xếp nhóm "đến muộn"; quá giờ hẹn + 15 phút thì lịch đã thành "không đến".
 */
@Service
public class ReceptionService {

    private final VisitRepository visits;
    private final AppointmentRepository appointments;
    private final AppointmentQrTokenRepository qrTokens;
    private final AppointmentSlotRepository slots;
    private final WorkSessionRepository sessions;
    private final PatientRepository patients;
    private final VisitViews views;
    private final CurrentStaff currentStaff;
    private final Duration lateLimit;

    public ReceptionService(VisitRepository visits, AppointmentRepository appointments, AppointmentQrTokenRepository qrTokens,
                            AppointmentSlotRepository slots, WorkSessionRepository sessions, PatientRepository patients,
                            VisitViews views, CurrentStaff currentStaff,
                            @Value("${app.booking.no-show-after:15m}") Duration lateLimit) {
        this.visits = visits;
        this.appointments = appointments;
        this.qrTokens = qrTokens;
        this.slots = slots;
        this.sessions = sessions;
        this.patients = patients;
        this.views = views;
        this.currentStaff = currentStaff;
        this.lateLimit = lateLimit;
    }

    /** Quét QR trên app bệnh nhân: mã dùng một lần, đúng ngày khám */
    @Transactional
    public VisitResponse checkInByQr(AuthUser me, String token, String companionName, String companionPhone) {
        AppointmentQrToken q = qrTokens.findByTokenHash(MyAppointmentService.sha256(token.trim()))
                .orElseThrow(() -> ApiException.badRequest("Mã QR không hợp lệ"));
        if (q.getUsedAt() != null) {
            throw ApiException.badRequest("Mã QR đã được sử dụng");
        }
        if (!q.getExpiresAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("Mã QR đã hết hạn");
        }
        if (qrTokens.markUsed(q.getId(), Instant.now()) == 0) {
            throw ApiException.badRequest("Mã QR đã được sử dụng"); // hai lần quét cùng lúc: chỉ một lần thành công
        }
        return checkIn(me, q.getAppointmentId(), CheckinMethod.QR_RECEPTION, companionName, companionPhone);
    }

    /** Lễ tân tìm lịch hẹn và check-in thủ công (bệnh nhân không có app / quên điện thoại) */
    @Transactional
    public VisitResponse checkInAppointment(AuthUser me, Long appointmentId, String companionName, String companionPhone) {
        return checkIn(me, appointmentId, CheckinMethod.MANUAL, companionName, companionPhone);
    }

    @Transactional
    public VisitResponse walkIn(AuthUser me, WalkInRequest req) {
        patients.findById(req.patientId()).orElseThrow(() -> ApiException.badRequest("Không tìm thấy hồ sơ bệnh nhân"));
        WorkSession s = sessions.findLockedById(req.sessionId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên khám"));
        requireOpenToday(s);
        long walkIns = visits.countBySessionIdAndSourceAndStatusNot(s.getId(), VisitSource.WALK_IN, VisitStatus.CANCELLED);
        if (walkIns >= s.getWalkInCapacity()) {
            throw ApiException.conflict("Phiên khám đã hết lượt nhận bệnh nhân vãng lai, vui lòng chọn phiên khác hoặc đặt lịch");
        }
        requireNotQueued(s.getId(), req.patientId());
        Visit v = newVisit(me, s, req.patientId(), VisitSource.WALK_IN, QueueGroup.WALK_IN, CheckinMethod.MANUAL);
        v.setReason(blank(req.reason()));
        v.setCompanionName(blank(req.companionName()));
        v.setCompanionPhone(blank(req.companionPhone()));
        save(v);
        return views.of(v);
    }

    private VisitResponse checkIn(AuthUser me, Long appointmentId, CheckinMethod method, String companionName, String companionPhone) {
        Appointment a = appointments.findById(appointmentId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lịch hẹn"));
        if (a.getStatus() == AppointmentStatus.CHECKED_IN) {
            throw ApiException.conflict("Lịch hẹn đã check-in");
        }
        if (a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.badRequest(a.getStatus() == AppointmentStatus.NO_SHOW
                    ? "Lịch hẹn đã quá giờ, vui lòng đăng ký khám vãng lai"
                    : "Lịch hẹn chưa được xác nhận hoặc đã hủy");
        }
        AppointmentSlot sl = slots.findById(a.getSlotId()).orElseThrow();
        WorkSession s = sessions.findLockedById(sl.getSessionId()).orElseThrow();
        requireOpenToday(s);
        Instant now = Instant.now();
        if (now.isAfter(sl.getSlotStart().plus(lateLimit))) {
            throw ApiException.badRequest("Đã quá giờ hẹn hơn " + lateLimit.toMinutes() + " phút, vui lòng đăng ký khám vãng lai");
        }
        requireNotQueued(s.getId(), a.getPatientId());
        QueueGroup group = now.isAfter(sl.getSlotStart()) ? QueueGroup.LATE : QueueGroup.ON_TIME;
        Visit v = newVisit(me, s, a.getPatientId(), VisitSource.APPOINTMENT, group, method);
        v.setAppointmentId(a.getId());
        v.setReason(a.getReason());
        v.setCompanionName(blank(companionName));
        v.setCompanionPhone(blank(companionPhone));
        save(v);
        a.setStatus(AppointmentStatus.CHECKED_IN);
        return views.of(v);
    }

    /** Số thứ tự tăng dần trong phiên; phiên đã được khóa dòng nên hai người check-in cùng lúc không trùng số */
    private Visit newVisit(AuthUser me, WorkSession s, Long patientId, VisitSource source, QueueGroup group, CheckinMethod method) {
        Visit v = new Visit();
        v.setVisitCode("TMP-" + UUID.randomUUID());
        v.setSource(source);
        v.setPatientId(patientId);
        v.setSessionId(s.getId());
        v.setDoctorId(s.getDoctorId());
        v.setCheckinMethod(method);
        v.setCheckedInBy(currentStaff.idOf(me));
        v.setQueueGroup(group);
        v.setQueueNumber(visits.maxQueueNumber(s.getId()) + 1);
        return v;
    }

    private void save(Visit v) {
        visits.saveAndFlush(v);
        v.setVisitCode("LK%07d".formatted(v.getId()));
    }

    private void requireOpenToday(WorkSession s) {
        if (s.getStatus() != SessionStatus.PUBLISHED) {
            throw ApiException.badRequest("Phiên khám không hoạt động");
        }
        if (!s.getSessionDate().equals(VnTime.today())) {
            throw ApiException.badRequest("Chỉ tiếp nhận vào đúng ngày khám");
        }
        if (!s.getEndsAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("Phiên khám đã kết thúc");
        }
    }

    private void requireNotQueued(Long sessionId, Long patientId) {
        if (visits.existsBySessionIdAndPatientIdAndStatusIn(sessionId, patientId, VisitStatus.OPEN)) {
            throw ApiException.conflict("Bệnh nhân đã có trong hàng đợi của phiên khám này");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
