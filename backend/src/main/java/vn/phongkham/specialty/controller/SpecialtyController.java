package vn.phongkham.specialty.controller;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.common.Patterns;
import vn.phongkham.specialty.dto.SpecialtyDtos.SpecialtyRequest;
import vn.phongkham.specialty.dto.SpecialtyDtos.SpecialtyResponse;
import vn.phongkham.specialty.service.SpecialtyService;

@Tag(name = "Chuyên khoa")
@RestController
@RequestMapping("/specialties")
public class SpecialtyController {

    private final SpecialtyService service;

    public SpecialtyController(SpecialtyService service) {
        this.service = service;
    }

    @Operation(summary = "Chuyên khoa đang hoạt động, kèm phí khám (công khai)")
    @GetMapping
    public List<SpecialtyResponse> listActive() {
        return service.listActive();
    }

    @Operation(summary = "Tất cả chuyên khoa, kể cả đã ngừng")
    @PreAuthorize(Patterns.ADMIN)
    @GetMapping("/all")
    public List<SpecialtyResponse> listAll() {
        return service.listAll();
    }

    @PreAuthorize(Patterns.ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialtyResponse create(@Valid @RequestBody SpecialtyRequest req) {
        return service.create(req);
    }

    @PreAuthorize(Patterns.ADMIN)
    @PutMapping("/{id}")
    public SpecialtyResponse update(@PathVariable Long id, @Valid @RequestBody SpecialtyRequest req) {
        return service.update(id, req);
    }
}
