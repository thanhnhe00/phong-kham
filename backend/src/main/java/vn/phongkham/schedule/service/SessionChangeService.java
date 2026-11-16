package vn.phongkham.schedule.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.schedule.dto.ScheduleDtos.ChangeRequestCreate;
import vn.phongkham.schedule.dto.ScheduleDtos.ChangeRequestResponse;
import vn.phongkham.schedule.model.ScheduleEnums.ChangeType;
import vn.phongkham.schedule.model.ScheduleEnums.RequestStatus;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.model.ScheduleEnums.SlotStatus;
import vn.phongkham.schedule.model.SessionChangeRequest;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.AppointmentSlotRepository;
import vn.phongkham.schedule.repository.SessionChangeRequestRepository;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.staff.service.CurrentStaff;

/**
 * UC22, UC32: bác sĩ xin nghỉ / nhờ bác sĩ cùng chuyên khoa thay / đổi chéo ca.
 * Nhờ thay hoặc đổi chéo: người được nhờ đồng ý trước, sau đó quản lý duyệt. Xin nghỉ: quản lý duyệt.
 * Duyệt xong, lịch hẹn của phiên chuyển "cần xác nhận lại" để lễ tân gọi từng bệnh nhân (không tự đổi).
 */
@Service
public class SessionChangeService {

    private final SessionChangeRequestRepository requests;
    private final WorkSessionRepository sessions;
    private final AppointmentSlotRepository slots;
    private final SessionService sessionService;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;
    private final Duration minNotice;

    public SessionChangeService(SessionChangeRequestRepository requests, WorkSessionRepository sessions,
                                AppointmentSlotRepository slots, SessionService sessionService, ScheduleLookups lookups,
                                CurrentStaff currentStaff, @Value("${app.schedule.change-min-notice:24h}") Duration minNotice) {
        this.requests = requests;
        this.sessions = sessions;
        this.slots = slots;
        this.sessionService = sessionService;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
        this.minNotice = minNotice;
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> list(AuthUser me, RequestStatus status) {
        boolean manager = me.has(Role.ADMIN);
        Long employeeId = manager ? null : currentStaff.idOf(me);
        return toResponses(requests.search(status, employeeId));
    }

    @Transactional
    public ChangeRequestResponse create(AuthUser me, ChangeRequestCreate req) {
        Long doctorId = currentStaff.idOf(me);
        WorkSession s = loadSession(req.sessionId());
        if (!s.getDoctorId().equals(doctorId)) {
            throw ApiException.forbidden("Chỉ bác sĩ phụ trách phiên khám mới gửi được yêu cầu");
        }
        requireChangeable(s);
        if (requests.existsBySessionIdAndStatusIn(s.getId(), List.of(RequestStatus.PENDING_COLLEAGUE, RequestStatus.PENDING_MANAGER))) {
            throw ApiException.conflict("Phiên khám này đã có yêu cầu đang chờ xử lý");
        }
        SessionChangeRequest r = new SessionChangeRequest();
        r.setSessionId(s.getId());
        r.setRequestType(req.type());
        r.setRequestedBy(doctorId);
        r.setReason(req.reason().trim());
        switch (req.type()) {
            case LEAVE -> r.setStatus(RequestStatus.PENDING_MANAGER);
            case REPLACEMENT -> {
                Long other = requireColleague(req.replacementDoctorId(), doctorId, s);
                requireFree(other, s, List.of());
                r.setReplacementDoctorId(other);
                r.setStatus(RequestStatus.PENDING_COLLEAGUE);
            }
            case SWAP -> {
                if (req.swapSessionId() == null) {
                    throw ApiException.badRequest("Vui lòng chọn ca muốn đổi");
                }
                WorkSession other = loadSession(req.swapSessionId());
                requireChangeable(other);
                Long otherDoctor = requireColleague(other.getDoctorId(), doctorId, s);
                if (!other.getSpecialtyId().equals(s.getSpecialtyId())) {
                    throw ApiException.badRequest("Chỉ đổi chéo với ca cùng chuyên khoa");
                }
                if (other.getStartsAt().isBefore(s.getEndsAt()) && other.getEndsAt().isAfter(s.getStartsAt())) {
                    throw ApiException.badRequest("Hai ca trùng giờ nhau, không đổi chéo được");
                }
                requireFree(otherDoctor, s, List.of(other.getId()));
                requireFree(doctorId, other, List.of(s.getId()));
                r.setReplacementDoctorId(otherDoctor);
                r.setSwapSessionId(other.getId());
                r.setStatus(RequestStatus.PENDING_COLLEAGUE);
            }
        }
        return toResponses(List.of(requests.save(r))).getFirst();
    }

    /** Bác sĩ được nhờ đồng ý / từ chối */
    @Transactional
    public ChangeRequestResponse respond(AuthUser me, Long id, boolean accept) {
        SessionChangeRequest r = load(id);
        if (!currentStaff.idOf(me).equals(r.getReplacementDoctorId())) {
            throw ApiException.forbidden("Bạn không phải bác sĩ được nhờ trong yêu cầu này");
        }
        if (r.getStatus() != RequestStatus.PENDING_COLLEAGUE) {
            throw ApiException.badRequest("Yêu cầu không còn chờ bạn trả lời");
        }
        r.setColleagueRespondedAt(Instant.now());
        r.setStatus(accept ? RequestStatus.PENDING_MANAGER : RequestStatus.REJECTED);
        return toResponses(List.of(r)).getFirst();
    }

    @Transactional
    public ChangeRequestResponse withdraw(AuthUser me, Long id) {
        SessionChangeRequest r = load(id);
        if (!currentStaff.idOf(me).equals(r.getRequestedBy())) {
            throw ApiException.forbidden("Chỉ người gửi mới rút được yêu cầu");
        }
        if (r.getStatus() != RequestStatus.PENDING_COLLEAGUE && r.getStatus() != RequestStatus.PENDING_MANAGER) {
            throw ApiException.badRequest("Yêu cầu đã được xử lý");
        }
        r.setStatus(RequestStatus.WITHDRAWN);
        return toResponses(List.of(r)).getFirst();
    }

    /** Quản lý duyệt / từ chối. Duyệt thì áp dụng thay đổi lên phiên khám trong cùng transaction. */
    @Transactional
    public ChangeRequestResponse decide(AuthUser me, Long id, boolean approve, String note) {
        SessionChangeRequest r = load(id);
        if (r.getStatus() != RequestStatus.PENDING_MANAGER) {
            throw ApiException.badRequest(r.getStatus() == RequestStatus.PENDING_COLLEAGUE
                    ? "Bác sĩ được nhờ chưa đồng ý" : "Yêu cầu đã được xử lý");
        }
        r.setManagerId(currentStaff.idOf(me));
        r.setManagerDecidedAt(Instant.now());
        r.setManagerNote(note);
        if (!approve) {
            r.setStatus(RequestStatus.REJECTED);
            return toResponses(List.of(r)).getFirst();
        }
        WorkSession s = sessions.findLockedById(r.getSessionId()).orElseThrow();
        if (s.getStatus() != SessionStatus.PUBLISHED || !s.getStartsAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("Phiên khám không còn thay đổi được");
        }
        switch (r.getRequestType()) {
            case LEAVE -> {
                s.setStatus(SessionStatus.CANCELLED);
                sessionService.markNeedsReconfirmation(List.of(s.getId()));
                slots.blockAll(s.getId(), SlotStatus.BLOCKED);
            }
            case REPLACEMENT -> {
                requireFree(r.getReplacementDoctorId(), s, List.of());
                s.setDoctorId(r.getReplacementDoctorId());
                sessionService.markNeedsReconfirmation(List.of(s.getId()));
            }
            case SWAP -> {
                WorkSession other = sessions.findLockedById(r.getSwapSessionId()).orElseThrow();
                if (other.getStatus() != SessionStatus.PUBLISHED || !other.getStartsAt().isAfter(Instant.now())) {
                    throw ApiException.badRequest("Ca đổi chéo không còn thay đổi được");
                }
                Long a = s.getDoctorId();
                s.setDoctorId(other.getDoctorId());
                other.setDoctorId(a);
                sessionService.markNeedsReconfirmation(List.of(s.getId(), other.getId()));
            }
        }
        r = requests.findById(id).orElseThrow(); // markNeedsReconfirmation/blockAll có thể đã làm mới context
        r.setStatus(RequestStatus.APPROVED);
        r.setManagerId(currentStaff.idOf(me));
        r.setManagerDecidedAt(Instant.now());
        r.setManagerNote(note);
        return toResponses(List.of(r)).getFirst();
    }

    private void requireChangeable(WorkSession s) {
        if (s.getStatus() != SessionStatus.PUBLISHED) {
            throw ApiException.badRequest("Phiên khám không còn hoạt động");
        }
        if (s.getStartsAt().isBefore(Instant.now().plus(minNotice))) {
            throw ApiException.badRequest("Yêu cầu phải gửi trước giờ khám ít nhất " + minNotice.toHours()
                    + " giờ; trường hợp gấp hãy liên hệ quản lý");
        }
    }

    private Long requireColleague(Long colleagueId, Long self, WorkSession s) {
        if (colleagueId == null) {
            throw ApiException.badRequest("Vui lòng chọn bác sĩ thay");
        }
        if (colleagueId.equals(self)) {
            throw ApiException.badRequest("Không thể nhờ chính mình");
        }
        if (!lookups.isDoctorOf(colleagueId, s.getSpecialtyId())) {
            throw ApiException.badRequest("Bác sĩ được nhờ phải cùng chuyên khoa");
        }
        lookups.requireDoctorFor(colleagueId, s.getSpecialtyId());
        return colleagueId;
    }

    /** Bác sĩ không có phiên khác trùng giờ phiên s (bỏ qua các phiên trong exclude) */
    private void requireFree(Long doctorId, WorkSession s, List<Long> exclude) {
        boolean busy = sessions.findOverlaps(doctorId, -1L, s.getStartsAt(), s.getEndsAt(), exclude).stream()
                .anyMatch(o -> o.getDoctorId().equals(doctorId));
        if (busy) {
            throw ApiException.conflict("Bác sĩ được nhờ đã có lịch khám trùng giờ");
        }
    }

    private WorkSession loadSession(Long id) {
        return sessions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên khám"));
    }

    private SessionChangeRequest load(Long id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu"));
    }

    private List<ChangeRequestResponse> toResponses(List<SessionChangeRequest> list) {
        Map<Long, WorkSession> byId = sessions.findAllById(list.stream().map(SessionChangeRequest::getSessionId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(WorkSession::getId, x -> x));
        Map<Long, String> names = lookups.employeeNames(list.stream()
                .flatMap(r -> Stream.of(r.getRequestedBy(), r.getReplacementDoctorId())).filter(java.util.Objects::nonNull).distinct().toList());
        return list.stream().map(r -> {
            WorkSession s = byId.get(r.getSessionId());
            return new ChangeRequestResponse(r.getId(), r.getSessionId(), s.getSessionDate(), s.getShiftName(), r.getRequestType(),
                    r.getRequestedBy(), names.get(r.getRequestedBy()), r.getReplacementDoctorId(),
                    r.getReplacementDoctorId() == null ? null : names.get(r.getReplacementDoctorId()), r.getSwapSessionId(),
                    r.getReason(), r.getStatus(), r.getManagerNote(), r.getCreatedAt());
        }).toList();
    }
}
