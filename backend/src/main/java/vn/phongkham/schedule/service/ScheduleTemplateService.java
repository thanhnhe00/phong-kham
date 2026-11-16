package vn.phongkham.schedule.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.schedule.dto.ScheduleDtos.TemplateRequest;
import vn.phongkham.schedule.dto.ScheduleDtos.TemplateResponse;
import vn.phongkham.schedule.model.ScheduleEnums.TemplateStatus;
import vn.phongkham.schedule.model.ScheduleTemplate;
import vn.phongkham.schedule.repository.ScheduleTemplateRepository;
import vn.phongkham.staff.service.CurrentStaff;

/**
 * UC31, UC32: lịch tuần cố định. Quản lý soạn (DRAFT) -> bác sĩ xác nhận (DOCTOR_CONFIRMED)
 * -> quản lý duyệt (APPROVED). Chỉ lịch đã duyệt mới được sinh phiên khám. Bác sĩ không tự tạo lịch.
 */
@Service
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository repo;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;

    public ScheduleTemplateService(ScheduleTemplateRepository repo, ScheduleLookups lookups, CurrentStaff currentStaff) {
        this.repo = repo;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(Long doctorId) {
        return toResponses(repo.findByDoctor(doctorId));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> mine(AuthUser me) {
        return toResponses(repo.findByDoctor(currentStaff.idOf(me)));
    }

    @Transactional
    public TemplateResponse create(AuthUser me, TemplateRequest req) {
        ScheduleTemplate t = new ScheduleTemplate();
        t.setCreatedBy(currentStaff.idOf(me));
        apply(t, req);
        return toResponses(List.of(repo.save(t))).getFirst();
    }

    /** Sửa lịch thì phải xác nhận và duyệt lại từ đầu */
    @Transactional
    public TemplateResponse update(Long id, TemplateRequest req) {
        ScheduleTemplate t = load(id);
        if (t.getStatus() == TemplateStatus.INACTIVE) {
            throw ApiException.badRequest("Lịch tuần đã ngừng áp dụng, hãy tạo lịch mới");
        }
        apply(t, req);
        t.setStatus(TemplateStatus.DRAFT);
        t.setDoctorConfirmedAt(null);
        t.setApprovedBy(null);
        t.setApprovedAt(null);
        return toResponses(List.of(t)).getFirst();
    }

    @Transactional
    public TemplateResponse confirm(AuthUser me, Long id) {
        ScheduleTemplate t = load(id);
        if (!t.getDoctorId().equals(currentStaff.idOf(me))) {
            throw ApiException.forbidden("Chỉ bác sĩ được xếp lịch mới xác nhận được");
        }
        if (t.getStatus() != TemplateStatus.DRAFT) {
            throw ApiException.badRequest("Lịch tuần không ở trạng thái chờ bác sĩ xác nhận");
        }
        t.setStatus(TemplateStatus.DOCTOR_CONFIRMED);
        t.setDoctorConfirmedAt(Instant.now());
        return toResponses(List.of(t)).getFirst();
    }

    @Transactional
    public TemplateResponse approve(AuthUser me, Long id) {
        ScheduleTemplate t = load(id);
        if (t.getStatus() != TemplateStatus.DOCTOR_CONFIRMED) {
            throw ApiException.badRequest("Bác sĩ chưa xác nhận lịch tuần này");
        }
        t.setStatus(TemplateStatus.APPROVED);
        t.setApprovedBy(currentStaff.idOf(me));
        t.setApprovedAt(Instant.now());
        return toResponses(List.of(t)).getFirst();
    }

    /** Ngừng áp dụng: không sinh phiên mới; phiên đã sinh giữ nguyên (hủy riêng nếu cần) */
    @Transactional
    public TemplateResponse deactivate(Long id) {
        ScheduleTemplate t = load(id);
        t.setStatus(TemplateStatus.INACTIVE);
        return toResponses(List.of(t)).getFirst();
    }

    private void apply(ScheduleTemplate t, TemplateRequest req) {
        lookups.requireDoctorFor(req.doctorId(), req.specialtyId());
        lookups.requireActiveRoom(req.roomId());
        int slot = req.slotMinutes() == null ? 30 : req.slotMinutes();
        if (!req.startTime().isBefore(req.endTime())) {
            throw ApiException.badRequest("Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (Duration.between(req.startTime(), req.endTime()).toMinutes() < slot) {
            throw ApiException.badRequest("Buổi khám ngắn hơn một lượt khám");
        }
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw ApiException.badRequest("Ngày kết thúc áp dụng phải sau ngày bắt đầu");
        }
        t.setDoctorId(req.doctorId());
        t.setSpecialtyId(req.specialtyId());
        t.setRoomId(req.roomId());
        t.setWeekday(req.weekday().shortValue());
        t.setShiftName(req.shiftName().trim());
        t.setStartTime(req.startTime());
        t.setEndTime(req.endTime());
        t.setSlotMinutes((short) slot);
        t.setWalkInCapacity((short) (req.walkInCapacity() == null ? 0 : req.walkInCapacity()));
        t.setEffectiveFrom(req.effectiveFrom());
        t.setEffectiveTo(req.effectiveTo());
        List<ScheduleTemplate> conflicts = repo.findConflicts(t);
        if (!conflicts.isEmpty()) {
            ScheduleTemplate c = conflicts.getFirst();
            throw ApiException.conflict(c.getDoctorId().equals(t.getDoctorId())
                    ? "Bác sĩ đã có lịch tuần khác trùng giờ này (lịch #" + c.getId() + ")"
                    : "Phòng đã được xếp cho bác sĩ khác vào giờ này (lịch #" + c.getId() + ")");
        }
    }

    private ScheduleTemplate load(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy lịch tuần"));
    }

    private List<TemplateResponse> toResponses(List<ScheduleTemplate> list) {
        Map<Long, String> doctors = lookups.employeeNames(list.stream().map(ScheduleTemplate::getDoctorId).distinct().toList());
        Map<Long, String> specs = lookups.specialtyNames(list.stream().map(ScheduleTemplate::getSpecialtyId).distinct().toList());
        Map<Long, String> rooms = lookups.roomCodes(list.stream().map(ScheduleTemplate::getRoomId).distinct().toList());
        return list.stream().map(t -> new TemplateResponse(t.getId(), t.getDoctorId(), doctors.get(t.getDoctorId()),
                t.getSpecialtyId(), specs.get(t.getSpecialtyId()), t.getRoomId(), rooms.get(t.getRoomId()), t.getWeekday(),
                t.getShiftName(), t.getStartTime(), t.getEndTime(), t.getSlotMinutes(), t.getWalkInCapacity(),
                t.getEffectiveFrom(), t.getEffectiveTo(), t.getStatus(), t.getDoctorConfirmedAt(), t.getApprovedAt())).toList();
    }
}
