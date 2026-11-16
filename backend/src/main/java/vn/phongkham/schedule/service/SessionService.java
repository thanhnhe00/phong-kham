package vn.phongkham.schedule.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.appointment.model.AppointmentEnums.AppointmentStatus;
import vn.phongkham.appointment.repository.AppointmentRepository;
import vn.phongkham.appointment.service.AppointmentNotifier;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.schedule.dto.ScheduleDtos.CancelResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.ClosureRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.ClosureResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.GenerateResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.OpenSession;
import vn.phongkham.schedule.dto.ScheduleDtos.SessionDetail;
import vn.phongkham.schedule.dto.ScheduleDtos.SessionRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.SessionResponse;
import vn.phongkham.schedule.dto.ScheduleDtos.SkippedSession;
import vn.phongkham.schedule.dto.ScheduleDtos.SlotResponse;
import vn.phongkham.schedule.model.AppointmentSlot;
import vn.phongkham.schedule.model.ClinicClosure;
import vn.phongkham.schedule.model.ScheduleEnums.SessionStatus;
import vn.phongkham.schedule.model.ScheduleEnums.SlotStatus;
import vn.phongkham.schedule.model.ScheduleTemplate;
import vn.phongkham.schedule.model.WorkSession;
import vn.phongkham.schedule.repository.AppointmentSlotRepository;
import vn.phongkham.schedule.repository.ClinicClosureRepository;
import vn.phongkham.schedule.repository.ScheduleTemplateRepository;
import vn.phongkham.schedule.repository.WorkSessionRepository;
import vn.phongkham.staff.service.CurrentStaff;

/** UC02, UC21, UC31: phiên khám theo ngày, slot đặt lịch, ngày nghỉ của phòng khám. */
@Service
public class SessionService {

    static final int MAX_RANGE_DAYS = 62;

    private final WorkSessionRepository sessions;
    private final AppointmentSlotRepository slots;
    private final ScheduleTemplateRepository templates;
    private final ClinicClosureRepository closures;
    private final AppointmentRepository appointments;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;
    private final AppointmentNotifier notifier;

    public SessionService(WorkSessionRepository sessions, AppointmentSlotRepository slots, ScheduleTemplateRepository templates,
                          ClinicClosureRepository closures, AppointmentRepository appointments, ScheduleLookups lookups,
                          CurrentStaff currentStaff, AppointmentNotifier notifier) {
        this.sessions = sessions;
        this.slots = slots;
        this.templates = templates;
        this.closures = closures;
        this.appointments = appointments;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
        this.notifier = notifier;
    }

    /** Sinh phiên khám + slot từ lịch tuần đã duyệt. Chạy lại nhiều lần không tạo trùng. */
    @Transactional
    public GenerateResponse generate(AuthUser me, LocalDate from, LocalDate to) {
        return generate(currentStaff.idOf(me), from, to);
    }

    /** publisher: id nhân viên công bố (dữ liệu demo gọi trực tiếp) */
    @Transactional
    public GenerateResponse generate(Long publisher, LocalDate from, LocalDate to) {
        checkRange(from, to);
        if (from.isBefore(VnTime.today())) {
            throw ApiException.badRequest("Không sinh lịch cho ngày đã qua");
        }
        Set<LocalDate> closed = closures.findByClosedDateBetweenOrderByClosedDate(from, to).stream()
                .map(ClinicClosure::getClosedDate).collect(Collectors.toSet());
        int created = 0;
        int existed = 0;
        List<SkippedSession> skipped = new ArrayList<>();
        List<ScheduleTemplate> approved = templates.findApprovedIn(from, to);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (ScheduleTemplate t : approved) {
                if (t.getWeekday() != d.getDayOfWeek().getValue() || d.isBefore(t.getEffectiveFrom())
                        || (t.getEffectiveTo() != null && d.isAfter(t.getEffectiveTo()))) {
                    continue;
                }
                if (sessions.existsByTemplateIdAndSessionDate(t.getId(), d)) {
                    existed++;
                    continue;
                }
                if (closed.contains(d)) {
                    skipped.add(new SkippedSession(t.getId(), d, "Phòng khám nghỉ"));
                    continue;
                }
                Instant start = VnTime.at(d, t.getStartTime());
                Instant end = VnTime.at(d, t.getEndTime());
                var overlaps = sessions.findOverlaps(t.getDoctorId(), t.getRoomId(), start, end, List.of());
                if (!overlaps.isEmpty()) {
                    skipped.add(new SkippedSession(t.getId(), d, "Trùng phòng hoặc trùng giờ bác sĩ với phiên #" + overlaps.getFirst().getId()));
                    continue;
                }
                createSession(t.getId(), t.getDoctorId(), t.getSpecialtyId(), t.getRoomId(), d, t.getShiftName(),
                        t.getStartTime(), t.getEndTime(), t.getSlotMinutes(), t.getWalkInCapacity(), publisher);
                created++;
            }
        }
        return new GenerateResponse(created, existed, skipped);
    }

    /** Phiên khám tạo tay (khám bù, tăng ca), công bố ngay */
    @Transactional
    public SessionDetail create(AuthUser me, SessionRequest req) {
        lookups.requireDoctorFor(req.doctorId(), req.specialtyId());
        lookups.requireActiveRoom(req.roomId());
        int slotMinutes = req.slotMinutes() == null ? 30 : req.slotMinutes();
        if (req.date().isBefore(VnTime.today())) {
            throw ApiException.badRequest("Không tạo phiên khám cho ngày đã qua");
        }
        if (!req.startTime().isBefore(req.endTime())) {
            throw ApiException.badRequest("Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (Duration.between(req.startTime(), req.endTime()).toMinutes() < slotMinutes) {
            throw ApiException.badRequest("Buổi khám ngắn hơn một lượt khám");
        }
        if (closures.existsById(req.date())) {
            throw ApiException.badRequest("Ngày này phòng khám nghỉ");
        }
        Instant start = VnTime.at(req.date(), req.startTime());
        Instant end = VnTime.at(req.date(), req.endTime());
        var overlaps = sessions.findOverlaps(req.doctorId(), req.roomId(), start, end, List.of());
        if (!overlaps.isEmpty()) {
            WorkSession o = overlaps.getFirst();
            throw ApiException.conflict(o.getDoctorId().equals(req.doctorId())
                    ? "Bác sĩ đã có phiên khám khác trùng giờ (phiên #" + o.getId() + ")"
                    : "Phòng đã có phiên khám khác trùng giờ (phiên #" + o.getId() + ")");
        }
        WorkSession s = createSession(null, req.doctorId(), req.specialtyId(), req.roomId(), req.date(), req.shiftName().trim(),
                req.startTime(), req.endTime(), (short) slotMinutes,
                (short) (req.walkInCapacity() == null ? 0 : req.walkInCapacity()), currentStaff.idOf(me));
        return detail(s.getId());
    }

    /** Hủy phiên: khóa mọi slot, lịch hẹn chưa khám chuyển "cần xác nhận lại" để lễ tân gọi đổi lịch */
    @Transactional
    public CancelResponse cancel(Long id) {
        WorkSession s = sessions.findLockedById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên khám"));
        if (s.getStatus() == SessionStatus.CANCELLED) {
            throw ApiException.badRequest("Phiên khám đã bị hủy trước đó");
        }
        if (!s.getEndsAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("Phiên khám đã kết thúc");
        }
        s.setStatus(SessionStatus.CANCELLED);
        int affected = markNeedsReconfirmation(List.of(s.getId()));
        slots.blockAll(s.getId(), SlotStatus.BLOCKED);
        return new CancelResponse(toResponses(List.of(sessions.findById(id).orElseThrow())).getFirst(), affected);
    }

    /** Lịch hẹn chưa khám của các phiên -> NEEDS_RECONFIRMATION; trả về số lịch bị ảnh hưởng */
    int markNeedsReconfirmation(List<Long> sessionIds) {
        List<Appointment> list = appointments.findInSessions(sessionIds, AppointmentStatus.UPCOMING);
        list.forEach(a -> a.setStatus(AppointmentStatus.NEEDS_RECONFIRMATION));
        appointments.flush();
        notifier.needsReconfirmation(list);
        return list.size();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(LocalDate from, LocalDate to, Long doctorId, Long specialtyId, Long roomId) {
        checkRange(from, to);
        return toResponses(sessions.search(from, to, doctorId, specialtyId, roomId));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> mine(AuthUser me, LocalDate from, LocalDate to) {
        return list(from, to, currentStaff.idOf(me), null, null);
    }

    @Transactional(readOnly = true)
    public SessionDetail detail(Long id) {
        WorkSession s = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên khám"));
        return new SessionDetail(toResponses(List.of(s)).getFirst(),
                slots.findBySessionIdOrderBySlotStart(id).stream().map(SessionService::toSlot).toList());
    }

    /** Công khai: slot còn trống từ hôm nay, tối đa 31 ngày, lọc bác sĩ / chuyên khoa */
    @Transactional(readOnly = true)
    public List<OpenSession> openSlots(LocalDate from, LocalDate to, Long doctorId, Long specialtyId) {
        LocalDate start = from == null || from.isBefore(VnTime.today()) ? VnTime.today() : from;
        LocalDate end = to == null ? start.plusDays(6) : to;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 31) {
            throw ApiException.badRequest("Khoảng ngày xem lịch tối đa 31 ngày");
        }
        List<Object[]> rows = slots.findOpenSlots(start, end, doctorId, specialtyId, Instant.now());
        Map<Long, WorkSession> bySession = new LinkedHashMap<>();
        Map<Long, List<SlotResponse>> slotsBySession = new LinkedHashMap<>();
        for (Object[] r : rows) {
            AppointmentSlot sl = (AppointmentSlot) r[0];
            WorkSession s = (WorkSession) r[1];
            bySession.putIfAbsent(s.getId(), s);
            slotsBySession.computeIfAbsent(s.getId(), k -> new ArrayList<>()).add(toSlot(sl));
        }
        var all = bySession.values();
        Map<Long, String> doctors = lookups.employeeNames(all.stream().map(WorkSession::getDoctorId).distinct().toList());
        Map<Long, String> specs = lookups.specialtyNames(all.stream().map(WorkSession::getSpecialtyId).distinct().toList());
        Map<Long, String> rooms = lookups.roomCodes(all.stream().map(WorkSession::getRoomId).distinct().toList());
        return all.stream().map(s -> new OpenSession(s.getId(), s.getSessionDate(), s.getShiftName(), s.getDoctorId(),
                doctors.get(s.getDoctorId()), s.getSpecialtyId(), specs.get(s.getSpecialtyId()), rooms.get(s.getRoomId()),
                slotsBySession.get(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<ClosureResponse> listClosures(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? VnTime.today() : from;
        LocalDate end = to == null ? start.plusYears(1) : to;
        return closures.findByClosedDateBetweenOrderByClosedDate(start, end).stream()
                .map(c -> new ClosureResponse(c.getClosedDate(), c.getReason())).toList();
    }

    @Transactional
    public ClosureResponse addClosure(ClosureRequest req) {
        if (closures.existsById(req.date())) {
            throw ApiException.conflict("Ngày này đã được đánh dấu nghỉ");
        }
        ClinicClosure c = new ClinicClosure();
        c.setClosedDate(req.date());
        c.setReason(req.reason().trim());
        closures.save(c);
        return new ClosureResponse(c.getClosedDate(), c.getReason());
    }

    @Transactional
    public void deleteClosure(LocalDate date) {
        if (!closures.existsById(date)) {
            throw ApiException.notFound("Ngày này không phải ngày nghỉ");
        }
        closures.deleteById(date);
    }

    private WorkSession createSession(Long templateId, Long doctorId, Long specialtyId, Long roomId, LocalDate date,
                                      String shiftName, LocalTime startTime, LocalTime endTime, short slotMinutes,
                                      short walkInCapacity, Long publisher) {
        WorkSession s = new WorkSession();
        s.setTemplateId(templateId);
        s.setDoctorId(doctorId);
        s.setOriginalDoctorId(doctorId);
        s.setSpecialtyId(specialtyId);
        s.setRoomId(roomId);
        s.setSessionDate(date);
        s.setShiftName(shiftName);
        s.setStartsAt(VnTime.at(date, startTime));
        s.setEndsAt(VnTime.at(date, endTime));
        s.setWalkInCapacity(walkInCapacity);
        s.setStatus(SessionStatus.PUBLISHED);
        s.setPublishedBy(publisher);
        s.setPublishedAt(Instant.now());
        sessions.save(s);
        List<AppointmentSlot> list = new ArrayList<>();
        Duration step = Duration.ofMinutes(slotMinutes);
        for (Instant t = s.getStartsAt(); !t.plus(step).isAfter(s.getEndsAt()); t = t.plus(step)) {
            AppointmentSlot sl = new AppointmentSlot();
            sl.setSessionId(s.getId());
            sl.setSlotStart(t);
            sl.setSlotEnd(t.plus(step));
            list.add(sl);
        }
        slots.saveAll(list);
        return s;
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("Khoảng ngày không hợp lệ");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("Khoảng ngày tối đa " + MAX_RANGE_DAYS + " ngày");
        }
    }

    static SlotResponse toSlot(AppointmentSlot sl) {
        return new SlotResponse(sl.getId(), sl.getSlotStart(), sl.getSlotEnd(), sl.getStatus());
    }

    List<SessionResponse> toResponses(List<WorkSession> list) {
        var ids = list.stream().map(WorkSession::getId).toList();
        Map<Long, List<AppointmentSlot>> slotMap = ids.isEmpty() ? Map.of()
                : slots.findBySessionIdIn(ids).stream().collect(Collectors.groupingBy(AppointmentSlot::getSessionId));
        Map<Long, String> doctors = lookups.employeeNames(list.stream().map(WorkSession::getDoctorId).distinct().toList());
        Map<Long, String> specs = lookups.specialtyNames(list.stream().map(WorkSession::getSpecialtyId).distinct().toList());
        Map<Long, String> rooms = lookups.roomCodes(list.stream().map(WorkSession::getRoomId).distinct().toList());
        Instant now = Instant.now();
        return list.stream().map(s -> {
            List<AppointmentSlot> sl = slotMap.getOrDefault(s.getId(), List.of());
            int available = (int) sl.stream().filter(x -> x.getStatus() == SlotStatus.AVAILABLE && x.getSlotStart().isAfter(now)).count();
            return new SessionResponse(s.getId(), s.getTemplateId(), s.getSessionDate(), s.getShiftName(), s.getStartsAt(),
                    s.getEndsAt(), s.getStatus(), s.getDoctorId(), doctors.get(s.getDoctorId()), s.getOriginalDoctorId(),
                    s.getSpecialtyId(), specs.get(s.getSpecialtyId()), s.getRoomId(), rooms.get(s.getRoomId()),
                    s.getWalkInCapacity(), sl.size(), available);
        }).toList();
    }
}
