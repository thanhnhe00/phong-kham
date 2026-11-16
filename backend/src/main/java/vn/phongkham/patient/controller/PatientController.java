package vn.phongkham.patient.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.patient.dto.PatientDtos.AllergyRequest;
import vn.phongkham.patient.dto.PatientDtos.AllergyResponse;
import vn.phongkham.patient.dto.PatientDtos.LinkCodeResponse;
import vn.phongkham.patient.dto.PatientDtos.PatientDetail;
import vn.phongkham.patient.dto.PatientDtos.PatientRequest;
import vn.phongkham.patient.dto.PatientDtos.PatientResponse;
import vn.phongkham.patient.dto.PatientDtos.RepresentativeRequest;
import vn.phongkham.patient.dto.PatientDtos.RepresentativeResponse;
import vn.phongkham.patient.service.PatientService;

@Tag(name = "Hồ sơ bệnh nhân (nhân viên)")
@RestController
@RequestMapping("/patients")
public class PatientController {

    /** Ai được sửa hồ sơ hành chính */
    private static final String FRONT_DESK = "hasAnyRole('RECEPTIONIST','ADMIN')";
    /** Ai được ghi nhận dị ứng */
    private static final String CLINICAL = "hasAnyRole('RECEPTIONIST','NURSE','DOCTOR')";

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @Operation(summary = "Tìm theo tên, số điện thoại, mã bệnh nhân hoặc số giấy tờ")
    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping
    public PageResponse<PatientResponse> search(@RequestParam(required = false) String q,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, pageable);
    }

    @Operation(summary = "Hồ sơ có thể trùng (gọi trước khi tạo mới); không tự gộp")
    @PreAuthorize(FRONT_DESK)
    @GetMapping("/possible-duplicates")
    public List<PatientResponse> possibleDuplicates(@RequestParam String fullName, @RequestParam LocalDate dateOfBirth,
                                                    @RequestParam(required = false) String phone,
                                                    @RequestParam(required = false) String identityNumber) {
        return service.possibleDuplicates(fullName, dateOfBirth, phone, identityNumber);
    }

    @PreAuthorize(FRONT_DESK)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PatientRequest req) {
        return service.create(me, req);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/{id}")
    public PatientDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize(FRONT_DESK)
    @PutMapping("/{id}")
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Cấp mã liên kết (8 ký tự, hạn 24 giờ) để bệnh nhân liên kết hồ sơ trên app")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/{id}/link-code")
    public LinkCodeResponse issueLinkCode(@PathVariable Long id) {
        return service.issueLinkCode(id);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/{id}/allergies")
    public List<AllergyResponse> listAllergies(@PathVariable Long id) {
        return service.listAllergies(id);
    }

    @PreAuthorize(CLINICAL)
    @PostMapping("/{id}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    public AllergyResponse addAllergy(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @Valid @RequestBody AllergyRequest req) {
        return service.addAllergy(me, id, req);
    }

    @PreAuthorize(CLINICAL)
    @DeleteMapping("/{id}/allergies/{allergyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllergy(@PathVariable Long id, @PathVariable Long allergyId) {
        service.deleteAllergy(id, allergyId);
    }

    @PreAuthorize(Patterns.ANY_STAFF)
    @GetMapping("/{id}/representatives")
    public List<RepresentativeResponse> listRepresentatives(@PathVariable Long id) {
        return service.listRepresentatives(id);
    }

    @Operation(summary = "Ghi nhận người đại diện hợp pháp sau khi đối chiếu giấy tờ")
    @PreAuthorize(FRONT_DESK)
    @PostMapping("/{id}/representatives")
    @ResponseStatus(HttpStatus.CREATED)
    public RepresentativeResponse addRepresentative(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                    @Valid @RequestBody RepresentativeRequest req) {
        return service.addRepresentative(me, id, req);
    }

    @PreAuthorize(FRONT_DESK)
    @DeleteMapping("/{id}/representatives/{representativeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRepresentative(@PathVariable Long id, @PathVariable Long representativeId) {
        service.deleteRepresentative(id, representativeId);
    }
}
