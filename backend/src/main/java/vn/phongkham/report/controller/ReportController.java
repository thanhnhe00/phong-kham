package vn.phongkham.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.report.service.ReportService;

@Tag(name = "Báo cáo")
@RestController
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @Operation(summary = "Doanh thu, lượt khám, không đến, chưa thu; theo ngày, bác sĩ, dịch vụ")
    @PreAuthorize(Patterns.ADMIN)
    @GetMapping("/reports/summary")
    public ReportService.Summary summary(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.summary(from, to);
    }

    @Operation(summary = "Báo cáo hoạt động cá nhân của bác sĩ")
    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/reports/mine")
    public ReportService.DoctorSummary mine(@AuthenticationPrincipal AuthUser me, @RequestParam LocalDate from,
                                            @RequestParam LocalDate to) {
        return service.mine(me, from, to);
    }
}
