package vn.phongkham.emr.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.emr.dto.EmrDtos.AmendRequest;
import vn.phongkham.emr.dto.EmrDtos.AmendmentResponse;
import vn.phongkham.emr.dto.EmrDtos.DiagnosisRequest;
import vn.phongkham.emr.dto.EmrDtos.DiagnosisResponse;
import vn.phongkham.emr.dto.EmrDtos.EmrRequest;
import vn.phongkham.emr.dto.EmrDtos.EmrResponse;
import vn.phongkham.emr.model.EmrAmendment;
import vn.phongkham.emr.model.EmrDiagnosis;
import vn.phongkham.emr.model.EmrRecord;
import vn.phongkham.emr.repository.EmrAmendmentRepository;
import vn.phongkham.emr.repository.EmrDiagnosisRepository;
import vn.phongkham.emr.repository.EmrRecordRepository;
import vn.phongkham.schedule.service.ScheduleLookups;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.service.VisitAccess;

/**
 * UC23, UC27: bác sĩ lập bệnh án ngoại trú, ký xác nhận. Bệnh án đã ký không sửa đè:
 * lập bản điều chỉnh, lưu nguyên nội dung cũ (JSON), lý do, người sửa; tăng phiên bản.
 */
@Service
public class EmrService {

    private final EmrRecordRepository records;
    private final EmrDiagnosisRepository diagnoses;
    private final EmrAmendmentRepository amendments;
    private final VisitAccess access;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;
    private final JsonMapper json;

    public EmrService(EmrRecordRepository records, EmrDiagnosisRepository diagnoses, EmrAmendmentRepository amendments,
                      VisitAccess access, ScheduleLookups lookups, CurrentStaff currentStaff, JsonMapper json) {
        this.records = records;
        this.diagnoses = diagnoses;
        this.amendments = amendments;
        this.access = access;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public EmrResponse get(Long visitId) {
        return toResponse(records.findByVisitId(visitId).orElseThrow(() -> ApiException.notFound("Lượt khám chưa có bệnh án")));
    }

    public Optional<EmrRecord> find(Long visitId) {
        return records.findByVisitId(visitId);
    }

    /** Lưu nháp (tạo nếu chưa có). Chỉ khi chưa ký. */
    @Transactional
    public EmrResponse saveDraft(AuthUser me, Long visitId, EmrRequest req) {
        access.forDoctor(me, visitId, VisitAccess.EXAMINING);
        EmrRecord r = records.findByVisitId(visitId).orElseGet(() -> {
            EmrRecord n = new EmrRecord();
            n.setVisitId(visitId);
            return n;
        });
        if (r.getStatus() != EmrRecord.Status.DRAFT) {
            throw ApiException.conflict("Bệnh án đã ký, chỉ được lập bản điều chỉnh");
        }
        apply(r, req);
        records.saveAndFlush(r);
        replaceDiagnoses(r.getId(), req.diagnoses());
        return toResponse(r);
    }

    /** Ký xác nhận: cần lý do khám và một chẩn đoán chính */
    @Transactional
    public EmrResponse sign(AuthUser me, Long visitId) {
        access.forDoctor(me, visitId, Set.of());
        EmrRecord r = records.findByVisitId(visitId).orElseThrow(() -> ApiException.badRequest("Chưa có bệnh án để ký"));
        if (r.getStatus() != EmrRecord.Status.DRAFT) {
            throw ApiException.badRequest("Bệnh án đã được ký");
        }
        requireSignable(r);
        r.setStatus(EmrRecord.Status.SIGNED);
        r.setSignedBy(currentStaff.idOf(me));
        r.setSignedAt(Instant.now());
        return toResponse(r);
    }

    /** Điều chỉnh bệnh án đã ký: chụp lại bản cũ, áp nội dung mới, tăng phiên bản, ký lại */
    @Transactional
    public EmrResponse amend(AuthUser me, Long visitId, AmendRequest req) {
        access.forDoctor(me, visitId, Set.of());
        EmrRecord r = records.findByVisitId(visitId).orElseThrow(() -> ApiException.notFound("Lượt khám chưa có bệnh án"));
        if (r.getStatus() == EmrRecord.Status.DRAFT) {
            throw ApiException.badRequest("Bệnh án chưa ký, hãy sửa trực tiếp bản nháp");
        }
        EmrAmendment a = new EmrAmendment();
        a.setEmrId(r.getId());
        a.setFromVersion(r.getVersion());
        a.setPreviousSnapshot(json.writeValueAsString(toResponse(r)));
        a.setReason(req.reason().trim());
        a.setAmendedBy(currentStaff.idOf(me));
        amendments.save(a);

        apply(r, req.content());
        replaceDiagnoses(r.getId(), req.content().diagnoses());
        requireSignable(r);
        r.setVersion(r.getVersion() + 1);
        r.setStatus(EmrRecord.Status.AMENDED);
        r.setSignedBy(currentStaff.idOf(me));
        r.setSignedAt(Instant.now());
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<AmendmentResponse> listAmendments(Long visitId) {
        EmrRecord r = records.findByVisitId(visitId).orElseThrow(() -> ApiException.notFound("Lượt khám chưa có bệnh án"));
        List<EmrAmendment> list = amendments.findByEmrIdOrderByFromVersion(r.getId());
        Map<Long, String> names = lookups.employeeNames(list.stream().map(EmrAmendment::getAmendedBy).distinct().toList());
        return list.stream().map(a -> new AmendmentResponse(a.getId(), a.getFromVersion(), a.getReason(), a.getAmendedBy(),
                names.get(a.getAmendedBy()), a.getAmendedAt(), json.readValue(a.getPreviousSnapshot(), Map.class))).toList();
    }

    private void requireSignable(EmrRecord r) {
        if (r.getChiefComplaint() == null || r.getChiefComplaint().isBlank()) {
            throw ApiException.badRequest("Bệnh án cần có lý do khám");
        }
        boolean hasPrimary = diagnoses.findByEmrIdOrderById(r.getId()).stream()
                .anyMatch(d -> d.getDiagnosisType() == EmrDiagnosis.Type.PRIMARY);
        if (!hasPrimary) {
            throw ApiException.badRequest("Bệnh án cần có chẩn đoán chính");
        }
    }

    private void replaceDiagnoses(Long emrId, List<DiagnosisRequest> list) {
        List<DiagnosisRequest> items = list == null ? List.of() : list;
        if (items.stream().filter(d -> d.type() == null || d.type() == EmrDiagnosis.Type.PRIMARY).count() > 1) {
            throw ApiException.badRequest("Chỉ có một chẩn đoán chính; các chẩn đoán khác chọn loại bệnh kèm theo");
        }
        diagnoses.deleteAllOfEmr(emrId);
        diagnoses.saveAll(items.stream().map(d -> {
            EmrDiagnosis x = new EmrDiagnosis();
            x.setEmrId(emrId);
            x.setIcd10Code(d.icd10Code() == null || d.icd10Code().isBlank() ? null : d.icd10Code().trim());
            x.setDiagnosisName(d.name().trim());
            x.setDiagnosisType(d.type() == null ? EmrDiagnosis.Type.PRIMARY : d.type());
            x.setNote(d.note());
            return x;
        }).toList());
        diagnoses.flush();
    }

    private static void apply(EmrRecord r, EmrRequest q) {
        r.setChiefComplaint(q.chiefComplaint());
        r.setPresentIllness(q.presentIllness());
        r.setPersonalHistory(q.personalHistory());
        r.setFamilyHistory(q.familyHistory());
        r.setGeneralExam(q.generalExam());
        r.setOrganExam(q.organExam());
        r.setParaclinicalSummary(q.paraclinicalSummary());
        r.setConclusion(q.conclusion());
        r.setTreatmentPlan(q.treatmentPlan());
        r.setAdvice(q.advice());
        r.setFollowUpDate(q.followUpDate());
    }

    public EmrResponse toResponse(EmrRecord r) {
        List<DiagnosisResponse> d = diagnoses.findByEmrIdOrderById(r.getId()).stream()
                .sorted(java.util.Comparator.comparing(EmrDiagnosis::getDiagnosisType))
                .map(x -> new DiagnosisResponse(x.getId(), x.getIcd10Code(), x.getDiagnosisName(), x.getDiagnosisType(), x.getNote()))
                .toList();
        String signer = r.getSignedBy() == null ? null : lookups.employeeNames(List.of(r.getSignedBy())).get(r.getSignedBy());
        return new EmrResponse(r.getId(), r.getVisitId(), r.getStatus(), r.getVersion(), r.getChiefComplaint(),
                r.getPresentIllness(), r.getPersonalHistory(), r.getFamilyHistory(), r.getGeneralExam(), r.getOrganExam(),
                r.getParaclinicalSummary(), r.getConclusion(), r.getTreatmentPlan(), r.getAdvice(), r.getFollowUpDate(), d,
                r.getSignedBy(), signer, r.getSignedAt(), r.getUpdatedAt());
    }

    /** "J06.9 - Viêm họng cấp; I10 - Tăng huyết áp" để in trên đơn thuốc */
    public String diagnosisText(Long visitId) {
        EmrRecord r = records.findByVisitId(visitId).orElseThrow(() -> ApiException.badRequest("Cần lập bệnh án và chẩn đoán trước khi ký đơn"));
        List<EmrDiagnosis> list = diagnoses.findByEmrIdOrderById(r.getId()).stream()
                .filter(d -> d.getDiagnosisType() != EmrDiagnosis.Type.DIFFERENTIAL)
                .sorted(java.util.Comparator.comparing(EmrDiagnosis::getDiagnosisType)).toList();
        if (list.stream().noneMatch(d -> d.getDiagnosisType() == EmrDiagnosis.Type.PRIMARY)) {
            throw ApiException.badRequest("Cần có chẩn đoán chính trước khi ký đơn thuốc");
        }
        return String.join("; ", list.stream()
                .map(d -> d.getIcd10Code() == null ? d.getDiagnosisName() : d.getIcd10Code() + " - " + d.getDiagnosisName()).toList());
    }
}
