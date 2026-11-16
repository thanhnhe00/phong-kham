package vn.phongkham.prescription.service;

import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.emr.service.EmrService;
import vn.phongkham.medicine.dto.MedicineDtos.MedicineResponse;
import vn.phongkham.medicine.model.DrugClass;
import vn.phongkham.medicine.model.Medicine;
import vn.phongkham.medicine.repository.MedicineRepository;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.model.PatientEnums.IdentityType;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.prescription.dto.PrescriptionDtos.ItemRequest;
import vn.phongkham.prescription.dto.PrescriptionDtos.ItemResponse;
import vn.phongkham.prescription.dto.PrescriptionDtos.PrescriptionRequest;
import vn.phongkham.prescription.dto.PrescriptionDtos.PrescriptionResponse;
import vn.phongkham.prescription.dto.PrescriptionDtos.ReplaceRequest;
import vn.phongkham.prescription.dto.PrescriptionDtos.SignRequest;
import vn.phongkham.patient.repository.PatientAllergyRepository;
import vn.phongkham.prescription.model.Prescription;
import vn.phongkham.prescription.model.PrescriptionItem;
import vn.phongkham.prescription.repository.PrescriptionItemRepository;
import vn.phongkham.prescription.repository.PrescriptionRepository;
import vn.phongkham.schedule.service.ScheduleLookups;
import vn.phongkham.staff.repository.DoctorProfileRepository;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.repository.VisitVitalsRepository;
import vn.phongkham.visit.service.VisitAccess;

/**
 * UC26: kê đơn theo Thông tư 26/2025/TT-BYT. Khi ký: có chẩn đoán chính; bệnh nhân có số định danh/CCCD/hộ chiếu
 * (trẻ dưới 72 tháng: cân nặng + người đưa đến khám); mỗi thuốc tối đa 30 ngày; thuốc độc ghi trước.
 * Đơn đã ký không sửa: kê đơn thay thế, đơn cũ chuyển REPLACED khi đơn mới được ký.
 */
@Service
public class PrescriptionService {

    static final int MAX_DAYS = 30;
    static final int CHILD_MONTHS = 72;

    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final MedicineRepository medicines;
    private final PatientRepository patients;
    private final VisitVitalsRepository vitals;
    private final DoctorProfileRepository doctorProfiles;
    private final EmrService emr;
    private final VisitAccess access;
    private final ScheduleLookups lookups;
    private final CurrentStaff currentStaff;
    private final String clinicCode;
    private final PatientAllergyRepository allergies;

    public PrescriptionService(PrescriptionRepository prescriptions, PrescriptionItemRepository items, MedicineRepository medicines,
                               PatientRepository patients, VisitVitalsRepository vitals, DoctorProfileRepository doctorProfiles,
                               EmrService emr, VisitAccess access, ScheduleLookups lookups, CurrentStaff currentStaff,
                               @Value("${app.clinic.code:PK001}") String clinicCode, PatientAllergyRepository allergies) {
        this.prescriptions = prescriptions;
        this.items = items;
        this.medicines = medicines;
        this.patients = patients;
        this.vitals = vitals;
        this.doctorProfiles = doctorProfiles;
        this.emr = emr;
        this.access = access;
        this.lookups = lookups;
        this.currentStaff = currentStaff;
        this.clinicCode = clinicCode;
        this.allergies = allergies;
    }

    @Transactional
    public PrescriptionResponse create(AuthUser me, Long visitId, PrescriptionRequest req) {
        access.forDoctor(me, visitId, VisitAccess.EXAMINING);
        return toResponses(List.of(newDraft(visitId, req, null, null))).getFirst();
    }

    @Transactional
    public PrescriptionResponse update(AuthUser me, Long id, PrescriptionRequest req) {
        Prescription p = load(id);
        access.forDoctor(me, p.getVisitId(), Set.of());
        requireDraft(p);
        p.setAdvice(req.advice());
        items.deleteAllOfPrescription(p.getId());
        saveItems(p.getId(), req);
        return toResponses(List.of(p)).getFirst();
    }

    @Transactional
    public PrescriptionResponse sign(AuthUser me, Long id, SignRequest req) {
        Prescription p = load(id);
        Visit v = access.forDoctor(me, p.getVisitId(), Set.of());
        requireDraft(p);
        checkPatientInfo(v);
        if (!allergies.findByPatientIdOrderByRecordedAt(v.getPatientId()).isEmpty()) {
            if (req == null || !Boolean.TRUE.equals(req.allergyChecked())) {
                throw ApiException.badRequest("Bệnh nhân có tiền sử dị ứng: hãy xác nhận đã đối chiếu dị ứng trước khi ký đơn");
            }
            p.setAllergyAck(req.allergyNote() == null || req.allergyNote().isBlank() ? "Đã đối chiếu dị ứng" : req.allergyNote().trim());
        }
        p.setDiagnosisText(emr.diagnosisText(v.getId()));
        renumberToxicFirst(p.getId());
        Instant now = Instant.now();
        p.setStatus(Prescription.Status.SIGNED);
        p.setSignedBy(currentStaff.idOf(me));
        p.setSignedAt(now);
        p.setNationalSentAt(now); // shortcut: mô phỏng gửi Hệ thống đơn thuốc quốc gia; khi tích hợp thật thì gọi API và lưu mã trả về
        if (p.getReplacesId() != null) {
            load(p.getReplacesId()).setStatus(Prescription.Status.REPLACED);
        }
        return toResponses(List.of(p)).getFirst();
    }

    /** Đơn đã ký cần sửa: tạo đơn nháp thay thế (mỗi đơn chỉ được thay một lần) */
    @Transactional
    public PrescriptionResponse replace(AuthUser me, Long id, ReplaceRequest req) {
        Prescription old = load(id);
        access.forDoctor(me, old.getVisitId(), Set.of());
        if (old.getStatus() != Prescription.Status.SIGNED) {
            throw ApiException.badRequest("Chỉ kê đơn thay thế cho đơn đã ký và còn hiệu lực");
        }
        if (prescriptions.existsByReplacesId(old.getId())) {
            throw ApiException.conflict("Đơn này đã có đơn thay thế");
        }
        return toResponses(List.of(newDraft(old.getVisitId(), req.content(), old.getId(), req.reason().trim()))).getFirst();
    }

    /** Đơn nháp chưa có giá trị pháp lý nên xóa hẳn; đơn đã ký thì chỉ thay thế, không xóa */
    @Transactional
    public void deleteDraft(AuthUser me, Long id) {
        Prescription p = load(id);
        access.forDoctor(me, p.getVisitId(), Set.of());
        requireDraft(p);
        items.deleteAllOfPrescription(p.getId());
        prescriptions.delete(p);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> forVisit(Long visitId, boolean patientView) {
        List<Prescription> list = prescriptions.findByVisitIdOrderByCreatedAt(visitId);
        if (patientView) {
            list = list.stream().filter(p -> p.getStatus() == Prescription.Status.SIGNED || p.getStatus() == Prescription.Status.REPLACED).toList();
        }
        return toResponses(list);
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse get(Long id) {
        return toResponses(List.of(load(id))).getFirst();
    }

    public boolean hasDraft(Long visitId) {
        return prescriptions.existsByVisitIdAndStatus(visitId, Prescription.Status.DRAFT);
    }

    private Prescription newDraft(Long visitId, PrescriptionRequest req, Long replacesId, String reason) {
        Prescription p = new Prescription();
        p.setVisitId(visitId);
        p.setPrescriptionCode("TMP-" + UUID.randomUUID());
        p.setDiagnosisText("(chưa ký)"); // chép chẩn đoán từ bệnh án lúc ký
        p.setAdvice(req.advice());
        p.setReplacesId(replacesId);
        p.setReplacementReason(reason);
        prescriptions.saveAndFlush(p);
        // shortcut: định dạng mã mô phỏng <mã cơ sở><năm><số thứ tự>; khi liên thông thật dùng mã Hệ thống đơn thuốc quốc gia cấp
        p.setPrescriptionCode("%s%d%07d".formatted(clinicCode, VnTime.today().getYear(), p.getId()));
        saveItems(p.getId(), req);
        return p;
    }

    private void saveItems(Long prescriptionId, PrescriptionRequest req) {
        Map<Long, Medicine> byId = medicines.findAllById(req.items().stream().map(ItemRequest::medicineId).distinct().toList())
                .stream().collect(Collectors.toMap(Medicine::getId, Function.identity()));
        List<PrescriptionItem> list = new ArrayList<>();
        short line = 1;
        for (ItemRequest r : req.items()) {
            Medicine m = byId.get(r.medicineId());
            if (m == null || !m.isActive()) {
                throw ApiException.badRequest("Có thuốc không có trong danh mục hoặc đã ngừng sử dụng");
            }
            if (r.days() > MAX_DAYS) {
                // shortcut: chưa có danh mục 252 bệnh được kê dài ngày (tối đa 90 ngày); khi bổ sung thì kiểm tra theo chẩn đoán
                throw ApiException.badRequest("Mỗi thuốc kê tối đa " + MAX_DAYS + " ngày (" + m.getActiveIngredient() + ")");
            }
            String route = r.route() == null || r.route().isBlank() ? m.getDefaultRoute() : r.route().trim();
            if (route == null) {
                throw ApiException.badRequest("Vui lòng nhập đường dùng cho " + m.getActiveIngredient());
            }
            PrescriptionItem x = new PrescriptionItem();
            x.setPrescriptionId(prescriptionId);
            x.setLineNo(line++);
            x.setMedicineId(m.getId());
            x.setMedicineLabel(MedicineResponse.of(m).label());
            x.setQuantity(r.quantity());
            x.setDosePerTime(r.dosePerTime().trim());
            x.setTimesPerDay(r.timesPerDay().shortValue());
            x.setRoute(route);
            x.setTiming(r.timing());
            x.setDays(r.days().shortValue());
            x.setNote(r.note());
            list.add(x);
        }
        items.saveAll(list);
        items.flush();
    }

    /** Thuốc độc ghi trước các thuốc khác (Thông tư 26/2025): đánh số lại thứ tự dòng */
    private void renumberToxicFirst(Long prescriptionId) {
        List<PrescriptionItem> list = items.findByPrescriptionIdOrderByLineNo(prescriptionId);
        Map<Long, DrugClass> cls = medicines.findAllById(list.stream().map(PrescriptionItem::getMedicineId).distinct().toList())
                .stream().collect(Collectors.toMap(Medicine::getId, Medicine::getDrugClass));
        List<PrescriptionItem> sorted = list.stream()
                .sorted(Comparator.comparing((PrescriptionItem i) -> cls.get(i.getMedicineId()) == DrugClass.TOXIC ? 0 : 1)
                        .thenComparing(PrescriptionItem::getLineNo)).toList();
        // tránh trùng UNIQUE(prescription_id, line_no) khi đổi chỗ: đẩy sang số âm trước rồi gán lại
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setLineNo((short) (-(i + 1)));
        }
        items.flush();
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setLineNo((short) (i + 1));
        }
        items.flush();
    }

    /** Thông tin bắt buộc về người bệnh trên đơn thuốc */
    private void checkPatientInfo(Visit v) {
        Patient p = patients.findById(v.getPatientId()).orElseThrow();
        long months = Period.between(p.getDateOfBirth(), VnTime.today()).toTotalMonths();
        if (months < CHILD_MONTHS) {
            if (v.getCompanionName() == null) {
                throw ApiException.badRequest("Trẻ dưới 72 tháng tuổi: cần ghi họ tên người đưa trẻ đến khám");
            }
            boolean hasWeight = vitals.findByVisitIdOrderByRecordedAt(v.getId()).stream().anyMatch(x -> x.getWeightKg() != null);
            if (!hasWeight) {
                throw ApiException.badRequest("Trẻ dưới 72 tháng tuổi: cần đo cân nặng trước khi ký đơn");
            }
        } else if (p.getIdentityType() == IdentityType.NOT_PROVIDED) {
            throw ApiException.badRequest("Đơn thuốc cần số định danh/CCCD hoặc hộ chiếu của bệnh nhân, hãy cập nhật hồ sơ");
        }
    }

    private static void requireDraft(Prescription p) {
        if (p.getStatus() != Prescription.Status.DRAFT) {
            throw ApiException.conflict("Đơn thuốc đã ký hoặc đã hủy, không sửa được; hãy kê đơn thay thế");
        }
    }

    private Prescription load(Long id) {
        return prescriptions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn thuốc"));
    }

    private List<PrescriptionResponse> toResponses(List<Prescription> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<PrescriptionItem> all = items.findByPrescriptionIdInOrderByLineNo(list.stream().map(Prescription::getId).toList());
        Map<Long, Medicine> meds = medicines.findAllById(all.stream().map(PrescriptionItem::getMedicineId).distinct().toList())
                .stream().collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Map<Long, List<ItemResponse>> byPrescription = all.stream().collect(Collectors.groupingBy(PrescriptionItem::getPrescriptionId,
                Collectors.mapping(i -> new ItemResponse(i.getId(), i.getLineNo(), i.getMedicineId(), i.getMedicineLabel(),
                        meds.get(i.getMedicineId()).getDrugClass(), i.getQuantity(), meds.get(i.getMedicineId()).getUnit(),
                        i.getDosePerTime(), i.getTimesPerDay(), i.getRoute(), i.getTiming(), i.getDays(), i.getNote()),
                        Collectors.toList())));
        var signers = list.stream().map(Prescription::getSignedBy).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = lookups.employeeNames(signers);
        Map<Long, String> licenses = doctorProfiles.findAllById(signers).stream()
                .collect(Collectors.toMap(dp -> dp.getEmployeeId(), dp -> dp.getPracticeLicenseNo()));
        return list.stream().map(p -> new PrescriptionResponse(p.getId(), p.getPrescriptionCode(), p.getVisitId(), p.getStatus(),
                p.getDiagnosisText(), p.getAdvice(), p.getReplacesId(), p.getReplacementReason(), p.getSignedBy(),
                p.getSignedBy() == null ? null : names.get(p.getSignedBy()),
                p.getSignedBy() == null ? null : licenses.get(p.getSignedBy()), p.getSignedAt(), p.getNationalSentAt(),
                p.getCreatedAt(), p.getAllergyAck(), byPrescription.getOrDefault(p.getId(), List.of()))).toList();
    }
}
