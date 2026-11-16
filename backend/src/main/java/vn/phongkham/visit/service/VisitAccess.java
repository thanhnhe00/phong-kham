package vn.phongkham.visit.service;

import java.util.Set;
import org.springframework.stereotype.Component;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.staff.service.CurrentStaff;
import vn.phongkham.visit.model.Visit;
import vn.phongkham.visit.model.VisitEnums.VisitStatus;
import vn.phongkham.visit.repository.VisitRepository;

/** Kiểm tra quyền trên một lượt khám, dùng chung cho bệnh án, chỉ định, đơn thuốc */
@Component
public class VisitAccess {

    /** Đang khám (kể cả đang chờ kết quả) thì bác sĩ được ghi bệnh án, chỉ định, kê đơn */
    public static final Set<VisitStatus> EXAMINING = Set.of(VisitStatus.IN_PROGRESS, VisitStatus.AWAITING_RESULTS);

    private final VisitRepository visits;
    private final PatientAccountRepository accounts;
    private final CurrentStaff currentStaff;

    public VisitAccess(VisitRepository visits, PatientAccountRepository accounts, CurrentStaff currentStaff) {
        this.visits = visits;
        this.accounts = accounts;
        this.currentStaff = currentStaff;
    }

    public Visit load(Long visitId) {
        return visits.findById(visitId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lượt khám"));
    }

    /** Chỉ bác sĩ của lượt khám; statuses rỗng = không kiểm tra trạng thái */
    public Visit forDoctor(AuthUser me, Long visitId, Set<VisitStatus> statuses) {
        Visit v = load(visitId);
        if (!currentStaff.idOf(me).equals(v.getDoctorId())) {
            throw ApiException.forbidden("Chỉ bác sĩ khám lượt này mới thực hiện được");
        }
        if (!statuses.isEmpty() && !statuses.contains(v.getStatus())) {
            throw ApiException.badRequest(v.getStatus() == VisitStatus.COMPLETED ? "Lượt khám đã kết thúc"
                    : "Hãy bắt đầu khám trước khi thực hiện thao tác này");
        }
        return v;
    }

    /** Bệnh nhân chỉ xem lượt khám của hồ sơ mình quản lý (404 để không lộ dữ liệu người khác) */
    public Visit forPatient(AuthUser me, Long visitId) {
        Visit v = load(visitId);
        if (!accounts.isLinked(me.id(), v.getPatientId())) {
            throw ApiException.notFound("Không tìm thấy lượt khám");
        }
        return v;
    }
}
