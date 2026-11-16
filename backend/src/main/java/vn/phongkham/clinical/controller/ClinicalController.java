package vn.phongkham.clinical.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.clinical.dto.ClinicalDtos.AmendResultRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.FileResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.ItemResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.OrderRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.OrderResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.PerformRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.PerformanceResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.RefuseRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.ResultRequest;
import vn.phongkham.clinical.dto.ClinicalDtos.ResultResponse;
import vn.phongkham.clinical.dto.ClinicalDtos.ReturnRequest;
import vn.phongkham.medicalservice.model.ServiceCategory;
import vn.phongkham.clinical.dto.ClinicalDtos.StoredFile;
import vn.phongkham.clinical.dto.ClinicalDtos.WorklistItem;
import vn.phongkham.clinical.service.ClinicalService;
import vn.phongkham.common.exception.ApiException;

@Tag(name = "Chỉ định và kết quả cận lâm sàng")
@RestController
public class ClinicalController {

    private static final String CLINICAL = "hasAnyRole('DOCTOR','NURSE','ADMIN')";
    private static final String TECH = "hasAnyRole('NURSE','DOCTOR')";

    private final ClinicalService service;

    public ClinicalController(ClinicalService service) {
        this.service = service;
    }

    @Operation(summary = "Bác sĩ chỉ định dịch vụ (giá chốt lúc chỉ định)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/visits/{visitId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse order(@AuthenticationPrincipal AuthUser me, @PathVariable Long visitId, @Valid @RequestBody OrderRequest req) {
        return service.order(me, visitId, req);
    }

    @PreAuthorize(CLINICAL)
    @GetMapping("/visits/{visitId}/orders")
    public List<OrderResponse> orders(@PathVariable Long visitId) {
        return service.forVisit(visitId, false);
    }

    @Operation(summary = "Ghi nhận bệnh nhân từ chối dịch vụ (không thực hiện, không tính tiền)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/order-items/{id}/refuse")
    public ItemResponse refuse(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody RefuseRequest req) {
        return service.refuse(me, id, req.note());
    }

    @Operation(summary = "Bớt dịch vụ chưa thực hiện")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/order-items/{id}/cancel")
    public ItemResponse cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.cancel(me, id);
    }

    @Operation(summary = "Việc cận lâm sàng: chờ làm, đang nhập, bị trả lại, chờ duyệt (lọc theo loại dịch vụ)")
    @PreAuthorize(TECH)
    @GetMapping("/order-items/worklist")
    public List<WorklistItem> worklist(@RequestParam(required = false) ServiceCategory category) {
        return service.worklist(category);
    }

    @Operation(summary = "Bác sĩ trả kết quả chờ duyệt về cho điều dưỡng sửa")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/results/{id}/return")
    public ResultResponse returnResult(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ReturnRequest req) {
        return service.returnToNurse(me, id, req.reason());
    }

    @PreAuthorize(TECH)
    @PostMapping("/order-items/{id}/perform")
    @ResponseStatus(HttpStatus.CREATED)
    public PerformanceResponse perform(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody PerformRequest req) {
        return service.perform(me, id, req);
    }

    @Operation(summary = "Nhập kết quả (submit=true để gửi bác sĩ duyệt)")
    @PreAuthorize(TECH)
    @PostMapping("/performances/{id}/result")
    public ResultResponse saveResult(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ResultRequest req) {
        return service.saveResult(me, id, req);
    }

    @Operation(summary = "Bác sĩ duyệt kết quả (từ lúc này bệnh nhân xem được)")
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/results/{id}/review")
    public ResultResponse review(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.review(me, id);
    }

    @Operation(summary = "Điều chỉnh kết quả đã duyệt (lưu bản cũ, cần bác sĩ duyệt lại)")
    @PreAuthorize(TECH)
    @PostMapping("/results/{id}/amend")
    public ResultResponse amend(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody AmendResultRequest req) {
        return service.amend(me, id, req);
    }

    @Operation(summary = "Đính kèm ảnh/PDF kết quả (JPG, PNG, PDF; tối đa 10 MB)")
    @PreAuthorize(TECH)
    @PostMapping(value = "/results/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse attach(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                               @RequestParam("file") MultipartFile file) {
        try {
            return service.attach(me, id, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest("Không đọc được tệp tải lên");
        }
    }

    @PreAuthorize(CLINICAL)
    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return asResponse(service.download(id));
    }

    @Operation(summary = "Bệnh nhân tải tệp kết quả đã được duyệt")
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/me/files/{id}")
    public ResponseEntity<byte[]> downloadMine(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return asResponse(service.downloadForPatient(me, id));
    }

    static ResponseEntity<byte[]> asResponse(StoredFile f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.content());
    }
}
