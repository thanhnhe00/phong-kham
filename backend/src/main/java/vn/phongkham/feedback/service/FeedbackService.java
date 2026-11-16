package vn.phongkham.feedback.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.feedback.model.Feedback;
import vn.phongkham.feedback.repository.FeedbackRepository;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.patient.service.MyPatientService;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.service.VisitAccess;

/** UC11, UC32: bệnh nhân gửi phản hồi riêng; quản lý tiếp nhận và xử lý. */
@Service
public class FeedbackService {

    public record FeedbackRequest(@NotNull(message = "Vui lòng chọn hồ sơ bệnh nhân") Long patientId, Long visitId,
                                  @Size(max = 80, message = "Chủ đề tối đa 80 ký tự") String category,
                                  @NotBlank(message = "Vui lòng nhập nội dung") @Size(max = 4000, message = "Nội dung tối đa 4000 ký tự") String content) {
    }

    public record StatusRequest(@NotNull(message = "Vui lòng chọn trạng thái") Feedback.Status status) {
    }

    public record FeedbackResponse(Long id, Long patientId, Long visitId, String category, String content, Feedback.Status status,
                                   Long handledBy, Instant handledAt, Instant createdAt) {
    }

    private final FeedbackRepository repo;
    private final PatientAccountRepository accounts;
    private final MyPatientService myPatients;
    private final VisitAccess visits;
    private final CurrentStaff currentStaff;

    public FeedbackService(FeedbackRepository repo, PatientAccountRepository accounts, MyPatientService myPatients,
                           VisitAccess visits, CurrentStaff currentStaff) {
        this.repo = repo;
        this.accounts = accounts;
        this.myPatients = myPatients;
        this.visits = visits;
        this.currentStaff = currentStaff;
    }

    @Transactional
    public FeedbackResponse create(AuthUser me, FeedbackRequest req) {
        myPatients.requireLinked(me, req.patientId());
        if (req.visitId() != null && !visits.forPatient(me, req.visitId()).getPatientId().equals(req.patientId())) {
            throw ApiException.badRequest("Lượt khám không thuộc hồ sơ đã chọn");
        }
        Feedback f = new Feedback();
        f.setPatientId(req.patientId());
        f.setVisitId(req.visitId());
        f.setCategory(req.category());
        f.setContent(req.content().trim());
        return toResponse(repo.save(f));
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> mine(AuthUser me) {
        var ids = accounts.findByIdUserIdOrderByLinkedAt(me.id()).stream().map(PatientAccount::getId)
                .map(PatientAccount.Key::getPatientId).toList();
        return ids.isEmpty() ? List.of() : repo.findByPatientIdInOrderByCreatedAtDesc(ids).stream().map(FeedbackService::toResponse).toList();
    }

    /** Mặc định: phản hồi chưa xử lý xong */
    @Transactional(readOnly = true)
    public List<FeedbackResponse> list(Feedback.Status status) {
        var statuses = status == null ? EnumSet.of(Feedback.Status.NEW, Feedback.Status.IN_REVIEW) : EnumSet.of(status);
        return repo.findByStatusInOrderByCreatedAtDesc(statuses).stream().map(FeedbackService::toResponse).toList();
    }

    @Transactional
    public FeedbackResponse setStatus(AuthUser me, Long id, Feedback.Status status) {
        Feedback f = repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phản hồi"));
        f.setStatus(status);
        f.setHandledBy(currentStaff.idOf(me));
        f.setHandledAt(Instant.now());
        return toResponse(f);
    }

    private static FeedbackResponse toResponse(Feedback f) {
        return new FeedbackResponse(f.getId(), f.getPatientId(), f.getVisitId(), f.getCategory(), f.getContent(), f.getStatus(),
                f.getHandledBy(), f.getHandledAt(), f.getCreatedAt());
    }
}
