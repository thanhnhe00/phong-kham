package vn.phongkham.staff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.staff.dto.StaffDtos.DoctorCard;
import vn.phongkham.staff.dto.StaffDtos.DoctorDetail;
import vn.phongkham.staff.service.DoctorService;

@Tag(name = "Bác sĩ (công khai)")
@RestController
@RequestMapping("/doctors")
public class DoctorController {

    private final DoctorService service;

    public DoctorController(DoctorService service) {
        this.service = service;
    }

    @Operation(summary = "Danh sách bác sĩ, lọc theo chuyên khoa")
    @GetMapping
    public List<DoctorCard> list(@RequestParam(required = false) Long specialtyId) {
        return service.list(specialtyId);
    }

    @Operation(summary = "Hồ sơ bác sĩ: chuyên môn, quá trình đào tạo/công tác")
    @GetMapping("/{id}")
    public DoctorDetail get(@PathVariable Long id) {
        return service.get(id);
    }
}
