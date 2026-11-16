package vn.phongkham.patient.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.patient.dto.PatientDtos.ContactUpdate;
import vn.phongkham.patient.dto.PatientDtos.LinkRequest;
import vn.phongkham.patient.dto.PatientDtos.MyPatientCreate;
import vn.phongkham.patient.dto.PatientDtos.MyPatientResponse;
import vn.phongkham.patient.dto.PatientDtos.PatientDetail;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.model.PatientAccount;
import vn.phongkham.patient.model.PatientEnums.AccountRelationship;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.patient.repository.PatientRepository;

/** UC04: bệnh nhân quản lý hồ sơ của mình và người thân trên app. */
@Service
public class MyPatientService {

    private final PatientRepository patients;
    private final PatientAccountRepository accounts;
    private final PatientService patientService;

    public MyPatientService(PatientRepository patients, PatientAccountRepository accounts, PatientService patientService) {
        this.patients = patients;
        this.accounts = accounts;
        this.patientService = patientService;
    }

    @Transactional(readOnly = true)
    public List<MyPatientResponse> list(AuthUser me) {
        List<PatientAccount> links = accounts.findByIdUserIdOrderByLinkedAt(me.id());
        Map<Long, Patient> byId = patients.findAllById(links.stream().map(l -> l.getId().getPatientId()).toList()).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        return links.stream().map(l -> new MyPatientResponse(PatientMapper.toResponse(byId.get(l.getId().getPatientId())),
                l.getRelationship())).toList();
    }

    @Transactional
    public MyPatientResponse create(AuthUser me, MyPatientCreate req) {
        requireSingleSelf(me, req.relationship());
        Patient p = patientService.createPatient(req.profile(), null);
        accounts.save(new PatientAccount(p.getId(), me.id(), req.relationship()));
        return new MyPatientResponse(PatientMapper.toResponse(p), req.relationship());
    }

    /** Liên kết với hồ sơ phòng khám đã tạo, bằng mã lễ tân cung cấp (mã dùng một lần) */
    @Transactional
    public MyPatientResponse link(AuthUser me, LinkRequest req) {
        Patient p = patients.findByLinkCodeHashAndLinkCodeExpiresAtAfter(PatientService.hashLinkCode(req.code()), Instant.now())
                .orElseThrow(() -> ApiException.badRequest("Mã liên kết không đúng hoặc đã hết hạn"));
        if (accounts.isLinked(me.id(), p.getId())) {
            throw ApiException.conflict("Tài khoản đã liên kết với hồ sơ này");
        }
        requireSingleSelf(me, req.relationship());
        accounts.save(new PatientAccount(p.getId(), me.id(), req.relationship()));
        p.setLinkCodeHash(null);
        p.setLinkCodeExpiresAt(null);
        return new MyPatientResponse(PatientMapper.toResponse(p), req.relationship());
    }

    @Transactional(readOnly = true)
    public PatientDetail get(AuthUser me, Long patientId) {
        requireLinked(me, patientId);
        return patientService.get(patientId);
    }

    @Transactional
    public MyPatientResponse updateContact(AuthUser me, Long patientId, ContactUpdate req) {
        PatientAccount link = requireLinked(me, patientId);
        Patient p = patientService.load(patientId);
        p.setPhone(PatientMapper.blankToNull(req.phone()));
        p.setEmail(PatientMapper.blankToNull(req.email()));
        p.setAddressLine(PatientMapper.blankToNull(req.addressLine()));
        p.setWard(PatientMapper.blankToNull(req.ward()));
        p.setProvince(PatientMapper.blankToNull(req.province()));
        p.setOccupation(PatientMapper.blankToNull(req.occupation()));
        return new MyPatientResponse(PatientMapper.toResponse(p), link.getRelationship());
    }

    /** Gỡ liên kết: hồ sơ vẫn giữ ở phòng khám, chỉ tài khoản này không xem được nữa */
    @Transactional
    public void unlink(AuthUser me, Long patientId) {
        accounts.delete(requireLinked(me, patientId));
    }

    /** Hồ sơ phải được liên kết với tài khoản đang đăng nhập; trả 404 để không lộ hồ sơ của người khác */
    public PatientAccount requireLinked(AuthUser me, Long patientId) {
        return accounts.findById(new PatientAccount.Key(patientId, me.id()))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hồ sơ bệnh nhân"));
    }

    private void requireSingleSelf(AuthUser me, AccountRelationship relationship) {
        if (relationship == AccountRelationship.SELF && accounts.existsByIdUserIdAndRelationship(me.id(), AccountRelationship.SELF)) {
            throw ApiException.conflict("Tài khoản đã có hồ sơ của chính mình");
        }
    }
}
