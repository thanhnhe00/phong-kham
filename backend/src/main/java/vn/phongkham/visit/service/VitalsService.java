package vn.phongkham.visit.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.schedule.service.ScheduleLookups;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.dto.VisitDtos.VitalsRequest;
import vn.phongkham.visit.dto.VisitDtos.VitalsResponse;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;
import vn.phongkham.visit.model.VisitVitals;
import vn.phongkham.visit.repository.VisitRepository;
import vn.phongkham.visit.repository.VisitVitalsRepository;

/** UC20: điều dưỡng đo sinh hiệu trước khi vào khám; bác sĩ xem và đo lại được (mỗi lần đo một bản ghi). */
@Service
public class VitalsService {

    private final VisitVitalsRepository vitals;
    private final VisitRepository visits;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;

    public VitalsService(VisitVitalsRepository vitals, VisitRepository visits, ScheduleLookups lookups, CurrentStaff currentStaff) {
        this.vitals = vitals;
        this.visits = visits;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
    }

    @Transactional
    public VitalsResponse record(AuthUser me, Long visitId, VitalsRequest req) {
        Visit v = visits.findById(visitId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lượt khám"));
        if (!VisitStatus.OPEN.contains(v.getStatus())) {
            throw ApiException.badRequest("Lượt khám đã kết thúc");
        }
        if (Stream.of(req.pulsePerMin(), req.temperatureC(), req.systolicBp(), req.diastolicBp(), req.respiratoryRate(),
                req.spo2Percent(), req.weightKg(), req.heightCm()).allMatch(java.util.Objects::isNull)) {
            throw ApiException.badRequest("Nhập ít nhất một chỉ số sinh hiệu");
        }
        if ((req.systolicBp() == null) != (req.diastolicBp() == null)) {
            throw ApiException.badRequest("Huyết áp cần đủ cả tâm thu và tâm trương");
        }
        VisitVitals x = new VisitVitals();
        x.setVisitId(visitId);
        x.setPulsePerMin(toShort(req.pulsePerMin()));
        x.setTemperatureC(req.temperatureC());
        x.setSystolicBp(toShort(req.systolicBp()));
        x.setDiastolicBp(toShort(req.diastolicBp()));
        x.setRespiratoryRate(toShort(req.respiratoryRate()));
        x.setSpo2Percent(toShort(req.spo2Percent()));
        x.setWeightKg(req.weightKg());
        x.setHeightCm(req.heightCm());
        x.setRecordedBy(currentStaff.idOf(me));
        vitals.save(x);
        return toResponses(List.of(x)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<VitalsResponse> list(Long visitId) {
        return toResponses(vitals.findByVisitIdOrderByRecordedAt(visitId));
    }

    /** Lần đo gần nhất của từng lượt khám */
    @Transactional(readOnly = true)
    public Map<Long, VitalsResponse> latest(List<Long> visitIds) {
        if (visitIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, VitalsResponse> out = new java.util.HashMap<>();
        List<VisitVitals> all = vitals.findByVisitIdInOrderByRecordedAt(visitIds);
        List<VitalsResponse> responses = toResponses(all);
        for (int i = 0; i < all.size(); i++) {
            out.put(all.get(i).getVisitId(), responses.get(i)); // sắp theo thời gian nên bản sau đè bản trước
        }
        return out;
    }

    /** Điều dưỡng/lễ tân/bác sĩ sửa lý do khám khi bệnh nhân kể lại */
    @Transactional
    public void updateReason(Long visitId, String reason) {
        Visit v = visits.findById(visitId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lượt khám"));
        if (!VisitStatus.OPEN.contains(v.getStatus())) {
            throw ApiException.badRequest("Lượt khám đã kết thúc");
        }
        v.setReason(reason.trim());
    }

    private List<VitalsResponse> toResponses(List<VisitVitals> list) {
        Map<Long, String> names = lookups.employeeNames(list.stream().map(VisitVitals::getRecordedBy).distinct().toList());
        return list.stream().map(x -> new VitalsResponse(x.getId(), toInt(x.getPulsePerMin()), x.getTemperatureC(),
                toInt(x.getSystolicBp()), toInt(x.getDiastolicBp()), toInt(x.getRespiratoryRate()), toInt(x.getSpo2Percent()),
                x.getWeightKg(), x.getHeightCm(), x.getRecordedBy(), names.get(x.getRecordedBy()), x.getRecordedAt())).toList();
    }

    private static Short toShort(Integer i) {
        return i == null ? null : i.shortValue();
    }

    private static Integer toInt(Short s) {
        return s == null ? null : s.intValue();
    }
}
