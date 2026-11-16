package vn.phongkham.patient.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.patient.dto.PatientDtos.ContactUpdate;
import vn.phongkham.patient.dto.PatientDtos.LinkRequest;
import vn.phongkham.patient.dto.PatientDtos.MyPatientCreate;
import vn.phongkham.patient.dto.PatientDtos.MyPatientResponse;
import vn.phongkham.patient.dto.PatientDtos.PatientDetail;
import vn.phongkham.patient.service.MyPatientService;

@Tag(name = "Hồ sơ của tôi (app bệnh nhân)")
@RestController
@RequestMapping("/me/patients")
@PreAuthorize("hasRole('PATIENT')")
public class MyPatientController {

    private final MyPatientService service;

    public MyPatientController(MyPatientService service) {
        this.service = service;
    }

    @Operation(summary = "Các hồ sơ tài khoản này quản lý (của mình và người thân)")
    @GetMapping
    public List<MyPatientResponse> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me);
    }

    @Operation(summary = "Tạo hồ sơ mới cho mình hoặc người thân")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MyPatientResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody MyPatientCreate req) {
        return service.create(me, req);
    }

    @Operation(summary = "Liên kết với hồ sơ đã có ở phòng khám bằng mã lễ tân cấp")
    @PostMapping("/link")
    public MyPatientResponse link(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody LinkRequest req) {
        return service.link(me, req);
    }

    @GetMapping("/{id}")
    public PatientDetail get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.get(me, id);
    }

    @Operation(summary = "Sửa thông tin liên hệ (họ tên, ngày sinh, giấy tờ do phòng khám sửa)")
    @PutMapping("/{id}")
    public MyPatientResponse updateContact(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                           @Valid @RequestBody ContactUpdate req) {
        return service.updateContact(me, id, req);
    }

    @Operation(summary = "Gỡ liên kết hồ sơ khỏi tài khoản (hồ sơ vẫn lưu ở phòng khám)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.unlink(me, id);
    }
}
