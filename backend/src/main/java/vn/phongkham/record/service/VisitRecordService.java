package vn.phongkham.record.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.billing.dto.BillingDtos.InvoiceResponse;
import vn.phongkham.billing.model.Invoice;
import vn.phongkham.billing.service.BillingService;
import vn.phongkham.clinical.dto.ClinicalDtos.OrderResponse;
import vn.phongkham.clinical.service.ClinicalService;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.emr.dto.EmrDtos.EmrResponse;
import vn.phongkham.emr.model.EmrRecord;
import vn.phongkham.emr.service.EmrService;
import vn.phongkham.notification.service.NotificationService;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.prescription.dto.PrescriptionDtos.PrescriptionResponse;
import vn.phongkham.prescription.service.PrescriptionService;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.dto.VisitDtos.VisitResponse;
import vn.phongkham.visit.dto.VisitDtos.VitalsResponse;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;
import vn.phongkham.visit.repository.VisitRepository;
import vn.phongkham.visit.service.VisitAccess;
import vn.phongkham.visit.service.VisitViews;
import vn.phongkham.visit.service.VitalsService;

/**
 * UC09, UC27: kết thúc lượt khám (bệnh án đã ký, đơn đã ký, dịch vụ đã xong và kết quả đã duyệt) rồi lập hóa đơn;
 * hồ sơ một lượt khám cho nhân viên (đầy đủ) và cho bệnh nhân (chỉ nội dung đã ký / đã duyệt).
 * Kết thúc khám không phụ thuộc đã thanh toán hay chưa.
 */
@Service
public class VisitRecordService {

    /** Toàn bộ hồ sơ một lượt khám; emr/invoice có thể null */
    public record VisitRecord(VisitResponse visit, List<VitalsResponse> vitals, EmrResponse emr, List<OrderResponse> orders,
                              List<PrescriptionResponse> prescriptions, InvoiceResponse invoice) {
    }

    private final VisitRepository visits;
    private final PatientAccountRepository accounts;
    private final VisitAccess access;
    private final VisitViews views;
    private final VitalsService vitals;
    private final EmrService emr;
    private final ClinicalService clinical;
    private final PrescriptionService prescriptions;
    private final BillingService billing;
    private final NotificationService notifications;
    private final CurrentStaff currentStaff;

    public VisitRecordService(VisitRepository visits, PatientAccountRepository accounts, VisitAccess access, VisitViews views,
                              VitalsService vitals, EmrService emr, ClinicalService clinical, PrescriptionService prescriptions,
                              BillingService billing, NotificationService notifications, CurrentStaff currentStaff) {
        this.visits = visits;
        this.accounts = accounts;
        this.access = access;
        this.views = views;
        this.vitals = vitals;
        this.emr = emr;
        this.clinical = clinical;
        this.prescriptions = prescriptions;
        this.billing = billing;
        this.notifications = notifications;
        this.currentStaff = currentStaff;
    }

    /** Bệnh nhân đi làm cận lâm sàng; bác sĩ gọi lại khi có kết quả */
    @Transactional
    public VisitResponse awaitResults(AuthUser me, Long visitId) {
        Visit v = access.forDoctor(me, visitId, Set.of(VisitStatus.IN_PROGRESS));
        if (!clinical.hasPendingItems(visitId)) {
            throw ApiException.badRequest("Lượt khám không có dịch vụ nào đang chờ thực hiện");
        }
        v.setStatus(VisitStatus.AWAITING_RESULTS);
        return views.of(v);
    }

    @Transactional
    public VisitRecord complete(AuthUser me, Long visitId) {
        Visit v = access.forDoctor(me, visitId, VisitAccess.EXAMINING);
        EmrRecord record = emr.find(visitId).orElseThrow(() -> ApiException.badRequest("Chưa lập bệnh án"));
        if (record.getStatus() == EmrRecord.Status.DRAFT) {
            throw ApiException.badRequest("Hãy ký bệnh án trước khi kết thúc lượt khám");
        }
        if (prescriptions.hasDraft(visitId)) {
            throw ApiException.badRequest("Còn đơn thuốc chưa ký: hãy ký hoặc xóa đơn nháp");
        }
        String pending = clinical.pendingWork(visitId);
        if (pending != null) {
            throw ApiException.badRequest(pending);
        }
        v.setStatus(VisitStatus.COMPLETED);
        v.setCompletedAt(Instant.now());
        Invoice inv = billing.createForVisit(v, currentStaff.idOf(me));
        String followUp = record.getFollowUpDate() == null ? ""
                : " Hẹn tái khám ngày " + record.getFollowUpDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".";
        notifications.toPatient(v.getPatientId(), "VISIT_COMPLETED", "Đã khám xong",
                "Lượt khám " + v.getVisitCode() + " đã hoàn tất. Hóa đơn " + inv.getInvoiceCode() + ": "
                        + inv.getTotalAmount().toPlainString() + " đ." + followUp, "VISIT", v.getId());
        return record(v, false);
    }

    @Transactional(readOnly = true)
    public VisitRecord forStaff(Long visitId) {
        return record(access.load(visitId), false);
    }

    @Transactional(readOnly = true)
    public VisitRecord forPatient(AuthUser me, Long visitId) {
        return record(access.forPatient(me, visitId), true);
    }

    /** Nhân viên lâm sàng xem lịch sử khám của một bệnh nhân, mới nhất trước */
    @Transactional(readOnly = true)
    public List<VisitResponse> ofPatient(Long patientId) {
        return views.of(visits.findByPatientIdInOrderByCheckedInAtDesc(List.of(patientId)));
    }

    /** Lịch sử khám của các hồ sơ tài khoản quản lý, mới nhất trước */
    @Transactional(readOnly = true)
    public List<VisitResponse> myVisits(AuthUser me, Long patientId) {
        List<Long> ids = accounts.findByIdUserIdOrderByLinkedAt(me.id()).stream()
                .map(PatientAccount::getId).map(PatientAccount.Key::getPatientId)
                .filter(id -> patientId == null || id.equals(patientId)).toList();
        return ids.isEmpty() ? List.of() : views.of(visits.findByPatientIdInOrderByCheckedInAtDesc(ids));
    }

    private VisitRecord record(Visit v, boolean patientView) {
        EmrResponse e = emr.find(v.getId())
                .filter(r -> !patientView || r.getStatus() != EmrRecord.Status.DRAFT)
                .map(emr::toResponse).orElse(null);
        List<InvoiceResponse> inv = billing.forVisits(List.of(v.getId()));
        return new VisitRecord(views.of(v), vitals.list(v.getId()), e, clinical.forVisit(v.getId(), patientView),
                prescriptions.forVisit(v.getId(), patientView), inv.isEmpty() ? null : inv.getFirst());
    }
}
