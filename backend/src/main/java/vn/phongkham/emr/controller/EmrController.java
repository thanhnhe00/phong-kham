package vn.phongkham.emr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.emr.dto.EmrDtos.AmendRequest;
import vn.phongkham.emr.dto.EmrDtos.AmendmentResponse;
import vn.phongkham.emr.dto.EmrDtos.EmrRequest;
import vn.phongkham.emr.dto.EmrDtos.EmrResponse;
import vn.phongkham.emr.service.EmrService;

@Tag(name = "Bệnh án")
@RestController
@RequestMapping("/visits/{visitId}/emr")
public class EmrController {

    /** Lễ tân không xem/sửa nội dung chuyên môn */
    private static final String CLINICAL_READ = "hasAnyRole('DOCTOR','NURSE','ADMIN')";

    private final EmrService service;

    public EmrController(EmrService service) {
        this.service = service;
    }

    @PreAuthorize(CLINICAL_READ)
    @GetMapping
    public EmrResponse get(@PathVariable Long visitId) {
        return service.get(visitId);
    }

    @Operation(summary = "Lưu nháp bệnh án (bác sĩ khám, trước khi ký)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PutMapping
    public EmrResponse save(@AuthenticationPrincipal AuthUser me, @PathVariable Long visitId, @Valid @RequestBody EmrRequest req) {
        return service.saveDraft(me, visitId, req);
    }

    @Operation(summary = "Ký xác nhận bệnh án (cần lý do khám và chẩn đoán chính)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/sign")
    public EmrResponse sign(@AuthenticationPrincipal AuthUser me, @PathVariable Long visitId) {
        return service.sign(me, visitId);
    }

    @Operation(summary = "Điều chỉnh bệnh án đã ký (lưu bản cũ, lý do, người sửa)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/amend")
    public EmrResponse amend(@AuthenticationPrincipal AuthUser me, @PathVariable Long visitId, @Valid @RequestBody AmendRequest req) {
        return service.amend(me, visitId, req);
    }

    @PreAuthorize(CLINICAL_READ)
    @GetMapping("/amendments")
    public List<AmendmentResponse> amendments(@PathVariable Long visitId) {
        return service.listAmendments(visitId);
    }
}
