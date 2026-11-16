package vn.phongkham.visit.service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.notification.service.NotificationService;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.schedule.service.ScheduleLookups;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.dto.VisitDtos.MyQueueItem;
import vn.phongkham.visit.dto.VisitDtos.QueueView;
import vn.phongkham.visit.dto.VisitDtos.VisitResponse;
import vn.phongkham.visit.dto.VisitDtos.WaitingItem;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;
import vn.phongkham.visit.repository.VisitRepository;

/** UC17, UC21: hàng đợi theo phiên khám, bác sĩ gọi người tiếp theo, bắt đầu khám, gọi lại khi có kết quả. */
@Service
public class QueueService {

    private final VisitRepository visits;
    private final WorkSessionRepository sessions;
    private final PatientAccountRepository accounts;
    private final VisitViews views;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;
    private final NotificationService notifications;
    private final int walkInEvery;
    private final VitalsService vitals;

    public QueueService(VisitRepository visits, WorkSessionRepository sessions, PatientAccountRepository accounts,
                        VisitViews views, ScheduleLookups lookups, CurrentStaff currentStaff, NotificationService notifications,
                        @Value("${app.queue.walk-in-every:3}") int walkInEvery, VitalsService vitals) {
        this.visits = visits;
        this.sessions = sessions;
        this.accounts = accounts;
        this.views = views;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
        this.notifications = notifications;
        this.walkInEvery = walkInEvery;
        this.vitals = vitals;
    }

    @Transactional(readOnly = true)
    public QueueView queue(Long sessionId) {
        WorkSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên khám"));
        List<Visit> all = visits.findBySessionIdOrderByQueueNumber(sessionId);
        Map<Long, VisitResponse> byId = views.of(all).stream().collect(Collectors.toMap(VisitResponse::id, Function.identity()));
        List<Long> order = waitingOrder(all);
        Map<Long, vn.phongkham.visit.dto.VisitDtos.VitalsResponse> lastVitals = vitals.latest(order);
        List<WaitingItem> waiting = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            waiting.add(new WaitingItem(i + 1, byId.get(order.get(i)), lastVitals.get(order.get(i))));
        }
        List<VisitResponse> current = all.stream()
                .filter(v -> v.getStatus() == VisitStatus.CALLED || v.getStatus() == VisitStatus.IN_PROGRESS)
                .map(v -> byId.get(v.getId())).toList();
        List<VisitResponse> awaiting = all.stream().filter(v -> v.getStatus() == VisitStatus.AWAITING_RESULTS)
                .map(v -> byId.get(v.getId())).toList();
        long done = all.stream().filter(v -> v.getStatus() == VisitStatus.COMPLETED).count();
        return new QueueView(s.getId(), lookups.employeeNames(List.of(s.getDoctorId())).get(s.getDoctorId()),
                lookups.roomCodes(List.of(s.getRoomId())).get(s.getRoomId()), current, waiting, awaiting, done);
    }

    /** Bác sĩ của phiên gọi người tiếp theo theo thứ tự hệ thống tính; không gọi chồng khi đang khám dở */
    @Transactional
    public VisitResponse callNext(AuthUser me, Long sessionId) {
        WorkSession s = sessions.findLockedById(sessionId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên khám"));
        requireSessionDoctor(me, s.getDoctorId());
        List<Visit> all = visits.findBySessionIdOrderByQueueNumber(sessionId);
        if (all.stream().anyMatch(v -> v.getStatus() == VisitStatus.CALLED || v.getStatus() == VisitStatus.IN_PROGRESS)) {
            throw ApiException.conflict("Đang có bệnh nhân được gọi hoặc đang khám, hãy hoàn tất trước khi gọi người tiếp theo");
        }
        List<Long> order = waitingOrder(all);
        if (order.isEmpty()) {
            throw ApiException.badRequest("Không còn bệnh nhân chờ");
        }
        Visit next = all.stream().filter(v -> v.getId().equals(order.getFirst())).findFirst().orElseThrow();
        call(next, s);
        return views.of(next);
    }

    /** Gọi lại bệnh nhân đã có kết quả cận lâm sàng */
    @Transactional
    public VisitResponse callBack(AuthUser me, Long visitId) {
        Visit v = load(visitId);
        requireSessionDoctor(me, v.getDoctorId());
        if (v.getStatus() != VisitStatus.AWAITING_RESULTS) {
            throw ApiException.badRequest("Bệnh nhân không ở trạng thái chờ kết quả");
        }
        call(v, sessions.findById(v.getSessionId()).orElseThrow());
        return views.of(v);
    }

    @Transactional
    public VisitResponse start(AuthUser me, Long visitId) {
        Visit v = load(visitId);
        requireSessionDoctor(me, v.getDoctorId());
        if (v.getStatus() != VisitStatus.CALLED) {
            throw ApiException.badRequest("Bệnh nhân chưa được gọi vào khám");
        }
        v.setStatus(VisitStatus.IN_PROGRESS);
        if (v.getStartedAt() == null) {
            v.setStartedAt(Instant.now());
        }
        return views.of(v);
    }

    /** Bệnh nhân bỏ về / gọi không có mặt */
    @Transactional
    public VisitResponse markLeft(AuthUser me, Long visitId) {
        Visit v = load(visitId);
        if (me.has(Role.DOCTOR) && !me.has(Role.RECEPTIONIST)) {
            requireSessionDoctor(me, v.getDoctorId());
        }
        if (v.getStatus() != VisitStatus.WAITING && v.getStatus() != VisitStatus.CALLED) {
            throw ApiException.badRequest("Chỉ đánh dấu bỏ về khi bệnh nhân đang chờ hoặc vừa được gọi");
        }
        v.setStatus(VisitStatus.LEFT);
        return views.of(v);
    }

    /** App bệnh nhân: lượt khám hôm nay của các hồ sơ mình quản lý, kèm số người phía trước */
    @Transactional(readOnly = true)
    public List<MyQueueItem> mine(AuthUser me) {
        List<Long> patientIds = accounts.findByIdUserIdOrderByLinkedAt(me.id()).stream()
                .map(PatientAccount::getId).map(PatientAccount.Key::getPatientId).toList();
        if (patientIds.isEmpty()) {
            return List.of();
        }
        List<Visit> today = visits.findRecentOfPatients(patientIds, VnTime.at(VnTime.today(), LocalTime.MIDNIGHT));
        List<MyQueueItem> result = new ArrayList<>();
        for (VisitResponse r : views.of(today)) {
            Integer ahead = null;
            if (r.status() == VisitStatus.WAITING) {
                ahead = waitingOrder(visits.findBySessionIdOrderByQueueNumber(r.sessionId())).indexOf(r.id());
            }
            result.add(new MyQueueItem(r, ahead));
        }
        return result;
    }

    private void call(Visit v, WorkSession s) {
        v.setStatus(VisitStatus.CALLED);
        v.setCalledAt(Instant.now());
        String room = lookups.roomCodes(List.of(s.getRoomId())).get(s.getRoomId());
        notifications.toPatient(v.getPatientId(), "CALLED", "Đến lượt khám",
                "Mời bạn (số " + v.getQueueNumber() + ") vào phòng " + room + ".", "VISIT", v.getId());
    }

    /** Thứ tự chờ của một phiên: chỉ người đang WAITING; streak tính từ các lượt đã gọi (mới nhất trước) */
    private List<Long> waitingOrder(List<Visit> all) {
        List<QueueOrder.Entry> waiting = all.stream().filter(v -> v.getStatus() == VisitStatus.WAITING)
                .map(v -> new QueueOrder.Entry(v.getId(), v.getQueueGroup(), v.getCheckedInAt())).toList();
        int streak = QueueOrder.bookedStreak(all.stream().filter(v -> v.getCalledAt() != null)
                .sorted(Comparator.comparing(Visit::getCalledAt).reversed()).map(Visit::getQueueGroup).toList());
        return QueueOrder.order(waiting, streak, walkInEvery);
    }

    private void requireSessionDoctor(AuthUser me, Long doctorId) {
        if (!currentStaff.idOf(me).equals(doctorId)) {
            throw ApiException.forbidden("Chỉ bác sĩ phụ trách phiên khám mới thực hiện được");
        }
    }

    private Visit load(Long id) {
        return visits.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy lượt khám"));
    }
}
