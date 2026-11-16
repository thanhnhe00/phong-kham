package vn.phongkham.medicine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.medicine.dto.MedicineDtos.MedicineRequest;
import vn.phongkham.medicine.dto.MedicineDtos.MedicineResponse;
import vn.phongkham.medicine.service.MedicineService;

@Tag(name = "Danh mục thuốc")
@RestController
@RequestMapping("/medicines")
public class MedicineController {

    private final MedicineService service;

    public MedicineController(MedicineService service) {
        this.service = service;
    }

    @Operation(summary = "Tìm thuốc theo hoạt chất / tên thương mại")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    @GetMapping
    public PageResponse<MedicineResponse> search(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "false") boolean includeInactive,
                                                 @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, includeInactive, pageable);
    }

    @PreAuthorize(Patterns.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MedicineResponse create(@Valid @RequestBody MedicineRequest req) {
        return service.create(req);
    }

    @PreAuthorize(Patterns.ADMIN)
    @PutMapping("/{id}")
    public MedicineResponse update(@PathVariable Long id, @Valid @RequestBody MedicineRequest req) {
        return service.update(id, req);
    }
}
