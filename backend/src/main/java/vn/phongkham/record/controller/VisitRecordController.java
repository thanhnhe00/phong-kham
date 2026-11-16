package vn.phongkham.record.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.record.service.VisitRecordService;
import vn.phongkham.record.service.VisitRecordService.VisitRecord;
import vn.phongkham.visit.dto.VisitDtos.VisitResponse;

@Tag(name = "Hồ sơ lượt khám")
@RestController
public class VisitRecordController {

    private final VisitRecordService service;

    public VisitRecordController(VisitRecordService service) {
        this.service = service;
    }

    @Operation(summary = "Chuyển bệnh nhân đi làm cận lâm sàng (chờ kết quả)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/visits/{id}/await-results")
    public VisitResponse awaitResults(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.awaitResults(me, id);
    }

    @Operation(summary = "Kết thúc lượt khám: lập hóa đơn, thông báo bệnh nhân (không cần đã thanh toán)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/visits/{id}/complete")
    public VisitRecord complete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.complete(me, id);
    }

    @Operation(summary = "Hồ sơ đầy đủ của lượt khám (sinh hiệu, bệnh án, chỉ định, kết quả, đơn thuốc, hóa đơn)")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN')")
    @GetMapping("/visits/{id}/record")
    public VisitRecord record(@PathVariable Long id) {
        return service.forStaff(id);
    }

    @Operation(summary = "Lịch sử khám của một bệnh nhân (bác sĩ, điều dưỡng, quản lý)")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN')")
    @GetMapping("/patients/{patientId}/visits")
    public List<VisitResponse> patientVisits(@PathVariable Long patientId) {
        return service.ofPatient(patientId);
    }

    @Operation(summary = "App bệnh nhân: lịch sử khám")
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/me/visits")
    public List<VisitResponse> myVisits(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long patientId) {
        return service.myVisits(me, patientId);
    }

    @Operation(summary = "App bệnh nhân: chi tiết lượt khám (chỉ bệnh án đã ký, kết quả đã duyệt, đơn đã ký)")
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/me/visits/{id}")
    public VisitRecord myVisit(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.forPatient(me, id);
    }
}
