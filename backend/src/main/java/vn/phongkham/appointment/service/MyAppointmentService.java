package vn.phongkham.appointment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.appointment.dto.AppointmentDtos.AppointmentResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.BookingRequest;
import vn.phongkham.appointment.dto.AppointmentDtos.ChangeRequestCreate;
import vn.phongkham.appointment.dto.AppointmentDtos.ChangeRequestResponse;
import vn.phongkham.appointment.dto.AppointmentDtos.QrResponse;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.appointment.model.AppointmentChangeRequest;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.model.AppointmentEnums.BookingChannel;
import vn.phongkham.appointment.model.AppointmentEnums.ChangeRequestType;
import vn.phongkham.appointment.model.AppointmentEnums.SimpleRequestStatus;
import vn.phongkham.appointment.model.AppointmentQrToken;
import vn.phongkham.appointment.repository.AppointmentChangeRequestRepository;
import vn.phongkham.appointment.repository.AppointmentQrTokenRepository;
import vn.phongkham.appointment.repository.AppointmentRepository;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.patient.service.MyPatientService;
import vn.phongkham.schedule.model.AppointmentSlot;
import vn.phongkham.schedule.model.ScheduleEnums.SlotStatus;
import vn.phongkham.schedule.repository.AppointmentSlotRepository;

/** UC05-08: bệnh nhân gửi yêu cầu đặt lịch, xem lịch, xin đổi/hủy, lấy mã QR check-in trên app. */
@Service
public class MyAppointmentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppointmentRepository appointments;
    private final AppointmentSlotRepository slots;
    private final AppointmentChangeRequestRepository changeRequests;
    private final AppointmentQrTokenRepository qrTokens;
    private final PatientAccountRepository accounts;
    private final MyPatientService myPatients;
    private final AppointmentService appointmentService;
    private final BookingSupport booking;
    private final AppointmentViews views;
    private final int noShowLimit;
    private final Duration noShowWindow;

    public MyAppointmentService(AppointmentRepository appointments, AppointmentSlotRepository slots,
                                AppointmentChangeRequestRepository changeRequests, AppointmentQrTokenRepository qrTokens,
                                PatientAccountRepository accounts, MyPatientService myPatients,
                                AppointmentService appointmentService, BookingSupport booking, AppointmentViews views,
                                @Value("${app.booking.no-show-limit:3}") int noShowLimit,
                                @Value("${app.booking.no-show-window:30d}") Duration noShowWindow) {
        this.appointments = appointments;
        this.slots = slots;
        this.changeRequests = changeRequests;
        this.qrTokens = qrTokens;
        this.accounts = accounts;
        this.myPatients = myPatients;
        this.appointmentService = appointmentService;
        this.booking = booking;
        this.views = views;
        this.noShowLimit = noShowLimit;
        this.noShowWindow = noShowWindow;
    }

    /** Yêu cầu đặt lịch: slot được giữ tạm (HELD) tới giờ khám, chờ trực ban gọi xác nhận */
    @Transactional
    public AppointmentResponse request(AuthUser me, BookingRequest req) {
        myPatients.requireLinked(me, req.patientId());
        long noShows = appointments.countByPatientIdAndStatusAndCreatedAtAfter(req.patientId(), AppointmentStatus.NO_SHOW,
                Instant.now().minus(noShowWindow));
        if (noShows >= noShowLimit) {
            throw ApiException.forbidden("Bạn đã vắng mặt " + noShows + " lần gần đây, vui lòng gọi phòng khám để đặt lịch");
        }
        AppointmentSlot sl = booking.lockFreeSlot(req.slotId(), req.patientId(), null);
        Appointment a = booking.newAppointment();
        a.setPatientId(req.patientId());
        a.setSlotId(sl.getId());
        a.setChannel(BookingChannel.APP_REQUEST);
        a.setRequestedByUser(me.id());
        a.setReason(req.reason() == null || req.reason().isBlank() ? null : req.reason().trim());
        sl.hold(sl.getSlotStart());
        booking.assignCode(a);
        booking.saveServices(a.getId(), req.serviceIds());
        return views.of(a);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(AuthUser me, Long patientId) {
        List<Long> patientIds = patientId != null
                ? List.of(myPatients.requireLinked(me, patientId).getId().getPatientId())
                : accounts.findByIdUserIdOrderByLinkedAt(me.id()).stream().map(PatientAccount::getId).map(PatientAccount.Key::getPatientId).toList();
        return patientIds.isEmpty() ? List.of() : views.of(appointments.findByPatients(patientIds));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(AuthUser me, Long id) {
        return views.of(loadOwn(me, id));
    }

    /** Bệnh nhân không tự hủy/đổi: gửi yêu cầu để lễ tân xử lý */
    @Transactional
    public ChangeRequestResponse requestChange(AuthUser me, Long id, ChangeRequestCreate req) {
        Appointment a = loadOwn(me, id);
        AppointmentService.requireUpcoming(a);
        if (changeRequests.existsByAppointmentIdAndStatus(a.getId(), SimpleRequestStatus.PENDING)) {
            throw ApiException.conflict("Lịch hẹn đã có yêu cầu đổi/hủy đang chờ xử lý");
        }
        if (req.type() == ChangeRequestType.RESCHEDULE) {
            if (req.requestedSlotId() == null) {
                throw ApiException.badRequest("Vui lòng chọn khung giờ mới");
            }
            AppointmentSlot target = slots.findById(req.requestedSlotId())
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy khung giờ"));
            if (target.getStatus() != SlotStatus.AVAILABLE || !target.getSlotStart().isAfter(Instant.now())) {
                throw ApiException.conflict("Khung giờ mới không còn trống");
            }
        }
        AppointmentChangeRequest r = new AppointmentChangeRequest();
        r.setAppointmentId(a.getId());
        r.setRequestType(req.type());
        r.setRequestedSlotId(req.type() == ChangeRequestType.RESCHEDULE ? req.requestedSlotId() : null);
        r.setRequestedByUser(me.id());
        r.setReason(req.reason() == null || req.reason().isBlank() ? null : req.reason().trim());
        return appointmentService.toChangeResponses(List.of(changeRequests.save(r))).getFirst();
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> changeRequests(AuthUser me, Long id) {
        Appointment a = loadOwn(me, id);
        return appointmentService.toChangeResponses(changeRequests.findByAppointmentIdOrderByCreatedAtDesc(a.getId()));
    }

    /**
     * Mã QR check-in: chỉ cấp cho lịch đã xác nhận, hết hạn cuối ngày khám. Mỗi lần gọi cấp token mới (token cũ mất hiệu lực).
     * DB chỉ lưu SHA-256 của token.
     */
    @Transactional
    public QrResponse qr(AuthUser me, Long id) {
        Appointment a = loadOwn(me, id);
        if (a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.badRequest("Chỉ lịch hẹn đã xác nhận mới có mã QR");
        }
        AppointmentSlot sl = slots.findById(a.getSlotId()).orElseThrow();
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        AppointmentQrToken q = qrTokens.findByAppointmentId(a.getId()).orElseGet(AppointmentQrToken::new);
        q.setAppointmentId(a.getId());
        q.setTokenHash(sha256(token));
        q.setExpiresAt(VnTime.at(VnTime.dateOf(sl.getSlotStart()).plusDays(1), java.time.LocalTime.MIDNIGHT));
        q.setUsedAt(null);
        q.setCreatedAt(Instant.now());
        qrTokens.save(q);
        return new QrResponse(token, q.getExpiresAt());
    }

    private Appointment loadOwn(AuthUser me, Long id) {
        Appointment a = appointments.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy lịch hẹn"));
        if (!accounts.isLinked(me.id(), a.getPatientId())) {
            throw ApiException.notFound("Không tìm thấy lịch hẹn");
        }
        return a;
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
