package vn.phongkham.patient.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.patient.dto.PatientDtos.AllergyRequest;
import vn.phongkham.patient.dto.PatientDtos.AllergyResponse;
import vn.phongkham.patient.dto.PatientDtos.LinkCodeResponse;
import vn.phongkham.patient.dto.PatientDtos.PatientDetail;
import vn.phongkham.patient.dto.PatientDtos.PatientRequest;
import vn.phongkham.patient.dto.PatientDtos.PatientResponse;
import vn.phongkham.patient.dto.PatientDtos.RepresentativeRequest;
import vn.phongkham.patient.dto.PatientDtos.RepresentativeResponse;
import vn.phongkham.patient.model.Patient;
import vn.phongkham.patient.model.PatientAllergy;
import vn.phongkham.patient.model.PatientEnums.AllergySeverity;
import vn.phongkham.patient.model.PatientRepresentative;
import vn.phongkham.patient.repository.PatientAccountRepository;
import vn.phongkham.patient.repository.PatientAllergyRepository;
import vn.phongkham.patient.repository.PatientRepository;
import vn.phongkham.patient.repository.PatientRepresentativeRepository;
import vn.phongkham.staff.service.CurrentStaff;

/** UC13: lễ tân tạo/cập nhật hồ sơ hành chính, phát hiện hồ sơ trùng (không tự gộp), cấp mã liên kết app. */
@Service
public class PatientService {

    static final Duration LINK_CODE_TTL = Duration.ofHours(24);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PatientRepository patients;
    private final PatientAccountRepository accounts;
    private final PatientAllergyRepository allergies;
    private final PatientRepresentativeRepository representatives;
    private final CurrentStaff currentStaff;

    public PatientService(PatientRepository patients, PatientAccountRepository accounts, PatientAllergyRepository allergies,
                          PatientRepresentativeRepository representatives, CurrentStaff currentStaff) {
        this.patients = patients;
        this.accounts = accounts;
        this.allergies = allergies;
        this.representatives = representatives;
        this.currentStaff = currentStaff;
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> search(String q, Pageable pageable) {
        return PageResponse.of(patients.search(q == null ? "" : q.trim(), pageable), PatientMapper::toResponse);
    }

    /** Lễ tân gọi trước khi tạo hồ sơ mới để tránh tạo trùng */
    @Transactional(readOnly = true)
    public List<PatientResponse> possibleDuplicates(String fullName, LocalDate dob, String phone, String identity) {
        return patients.findPossibleDuplicates(fullName.trim(), dob, phone == null ? "" : phone.trim(),
                identity == null ? "" : identity.trim()).stream().map(PatientMapper::toResponse).toList();
    }

    @Transactional
    public PatientResponse create(AuthUser me, PatientRequest req) {
        return PatientMapper.toResponse(createPatient(req, currentStaff.idOf(me)));
    }

    /** Dùng chung cho lễ tân và bệnh nhân tự tạo (createdBy = null) */
    Patient createPatient(PatientRequest req, Long createdBy) {
        Patient p = new Patient();
        PatientMapper.apply(p, req);
        requireUniqueIdentity(p);
        p.setCreatedBy(createdBy);
        p.setPatientCode("TMP-" + java.util.UUID.randomUUID()); // đổi thành BN<id> ngay khi có id
        patients.saveAndFlush(p);
        p.setPatientCode("BN%06d".formatted(p.getId()));
        return p;
    }

    @Transactional(readOnly = true)
    public PatientDetail get(Long id) {
        Patient p = load(id);
        return new PatientDetail(PatientMapper.toResponse(p),
                allergies.findByPatientIdOrderByRecordedAt(id).stream().map(PatientMapper::toAllergy).toList(),
                representatives.findByPatientIdOrderById(id).stream().map(PatientMapper::toRepresentative).toList(),
                accounts.findByIdPatientId(id).size());
    }

    @Transactional
    public PatientResponse update(Long id, PatientRequest req) {
        Patient p = load(id);
        PatientMapper.apply(p, req);
        requireUniqueIdentity(p);
        return PatientMapper.toResponse(p);
    }

    /** Mã 8 ký tự, dùng một lần, hạn 24 giờ: bệnh nhân nhập trên app để liên kết với hồ sơ này */
    @Transactional
    public LinkCodeResponse issueLinkCode(Long id) {
        Patient p = load(id);
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        p.setLinkCodeHash(hashLinkCode(code.toString()));
        p.setLinkCodeExpiresAt(Instant.now().plus(LINK_CODE_TTL));
        return new LinkCodeResponse(code.toString(), p.getLinkCodeExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<AllergyResponse> listAllergies(Long patientId) {
        load(patientId);
        return allergies.findByPatientIdOrderByRecordedAt(patientId).stream().map(PatientMapper::toAllergy).toList();
    }

    @Transactional
    public AllergyResponse addAllergy(AuthUser me, Long patientId, AllergyRequest req) {
        load(patientId);
        PatientAllergy a = new PatientAllergy();
        a.setPatientId(patientId);
        a.setAllergen(req.allergen().trim());
        a.setReaction(PatientMapper.blankToNull(req.reaction()));
        a.setSeverity(req.severity() == null ? AllergySeverity.UNKNOWN : req.severity());
        a.setRecordedBy(currentStaff.idOf(me));
        return PatientMapper.toAllergy(allergies.save(a));
    }

    @Transactional
    public void deleteAllergy(Long patientId, Long allergyId) {
        allergies.delete(allergies.findByIdAndPatientId(allergyId, patientId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông tin dị ứng")));
    }

    @Transactional(readOnly = true)
    public List<RepresentativeResponse> listRepresentatives(Long patientId) {
        load(patientId);
        return representatives.findByPatientIdOrderById(patientId).stream().map(PatientMapper::toRepresentative).toList();
    }

    /** Lễ tân ghi nhận người đại diện sau khi đối chiếu giấy tờ chứng minh quyền đại diện */
    @Transactional
    public RepresentativeResponse addRepresentative(AuthUser me, Long patientId, RepresentativeRequest req) {
        load(patientId);
        PatientRepresentative r = new PatientRepresentative();
        r.setPatientId(patientId);
        r.setFullName(req.fullName().trim());
        r.setRelationship(req.relationship().trim());
        r.setPhone(PatientMapper.blankToNull(req.phone()));
        r.setIdentityNumber(PatientMapper.blankToNull(req.identityNumber()));
        r.setAuthorityNote(PatientMapper.blankToNull(req.authorityNote()));
        r.setVerifiedBy(currentStaff.idOf(me));
        r.setVerifiedAt(Instant.now());
        return PatientMapper.toRepresentative(representatives.save(r));
    }

    @Transactional
    public void deleteRepresentative(Long patientId, Long representativeId) {
        representatives.delete(representatives.findByIdAndPatientId(representativeId, patientId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người đại diện")));
    }

    Patient load(Long id) {
        return patients.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy hồ sơ bệnh nhân"));
    }

    private void requireUniqueIdentity(Patient p) {
        if (p.getIdentityNumber() == null) {
            return;
        }
        patients.findByIdentityTypeAndIdentityNumber(p.getIdentityType(), p.getIdentityNumber())
                .filter(other -> !other.getId().equals(p.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict("Đã có hồ sơ bệnh nhân với số giấy tờ này (" + other.getPatientCode() + ")");
                });
    }

    static String hashLinkCode(String code) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(code.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
