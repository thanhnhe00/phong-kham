package vn.phongkham.medicalservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.common.Patterns;
import vn.phongkham.medicalservice.dto.ServiceDtos.ServiceRequest;
import vn.phongkham.medicalservice.dto.ServiceDtos.ServiceResponse;
import vn.phongkham.medicalservice.model.ServiceCategory;
import vn.phongkham.medicalservice.service.MedicalServiceService;

@Tag(name = "Dịch vụ và bảng giá")
@RestController
@RequestMapping("/services")
public class MedicalServiceController {

    private final MedicalServiceService service;

    public MedicalServiceController(MedicalServiceService service) {
        this.service = service;
    }

    @Operation(summary = "Dịch vụ đang áp dụng (công khai)")
    @GetMapping
    public List<ServiceResponse> listActive(@RequestParam(required = false) ServiceCategory category) {
        return service.listActive(category);
    }

    @Operation(summary = "Tất cả dịch vụ, kể cả đã ngừng")
    @PreAuthorize(Patterns.ADMIN)
    @GetMapping("/all")
    public List<ServiceResponse> listAll() {
        return service.listAll();
    }

    @PreAuthorize(Patterns.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@Valid @RequestBody ServiceRequest req) {
        return service.create(req);
    }

    @PreAuthorize(Patterns.ADMIN)
    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceRequest req) {
        return service.update(id, req);
    }
}
