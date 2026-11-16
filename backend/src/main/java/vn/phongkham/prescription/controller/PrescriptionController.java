package vn.phongkham.prescription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.prescription.dto.PrescriptionDtos.PrescriptionRequest;
import vn.phongkham.prescription.dto.PrescriptionDtos.PrescriptionResponse;
import vn.phongkham.prescription.dto.PrescriptionDtos.ReplaceRequest;
import vn.phongkham.prescription.dto.PrescriptionDtos.SignRequest;
import vn.phongkham.prescription.service.PrescriptionService;

@Tag(name = "Đơn thuốc")
@RestController
public class PrescriptionController {

    private static final String CLINICAL_READ = "hasAnyRole('DOCTOR','NURSE','ADMIN')";

    private final PrescriptionService service;

    public PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @Operation(summary = "Kê đơn (nháp); một lượt khám có thể có nhiều đơn")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/visits/{visitId}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionResponse create(@AuthenticationPrincipal AuthUser me, @PathVariable Long visitId,
                                       @Valid @RequestBody PrescriptionRequest req) {
        return service.create(me, visitId, req);
    }

    @PreAuthorize(CLINICAL_READ)
    @GetMapping("/visits/{visitId}/prescriptions")
    public List<PrescriptionResponse> forVisit(@PathVariable Long visitId) {
        return service.forVisit(visitId, false);
    }

    @PreAuthorize(CLINICAL_READ)
    @GetMapping("/prescriptions/{id}")
    public PrescriptionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PutMapping("/prescriptions/{id}")
    public PrescriptionResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                       @Valid @RequestBody PrescriptionRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Ký đơn (kiểm tra theo Thông tư 26/2025, gửi liên thông mô phỏng)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/prescriptions/{id}/sign")
    public PrescriptionResponse sign(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                     @RequestBody(required = false) SignRequest req) {
        return service.sign(me, id, req);
    }

    @Operation(summary = "Kê đơn thay thế cho đơn đã ký (đơn cũ thành REPLACED khi đơn mới được ký)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/prescriptions/{id}/replace")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionResponse replace(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                        @Valid @RequestBody ReplaceRequest req) {
        return service.replace(me, id, req);
    }

    @Operation(summary = "Xóa đơn nháp (đơn đã ký chỉ được thay thế)")
    @PreAuthorize("hasRole('DOCTOR')")
    @DeleteMapping("/prescriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.deleteDraft(me, id);
    }
}
