package vn.phongkham.medicalservice.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.medicalservice.dto.ServiceDtos.ServiceRequest;
import vn.phongkham.medicalservice.dto.ServiceDtos.ServiceResponse;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.model.ServiceCategory;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;

/** UC31: danh mục dịch vụ và bảng giá. */
@Service
public class MedicalServiceService {

    private final MedicalServiceRepository repo;

    public MedicalServiceService(MedicalServiceRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listActive(ServiceCategory category) {
        var list = category == null ? repo.findByIsActiveTrueOrderByCategoryAscNameAsc()
                : repo.findByIsActiveTrueAndCategoryOrderByNameAsc(category);
        return list.stream().map(ServiceResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listAll() {
        return repo.findAllByOrderByCategoryAscNameAsc().stream().map(ServiceResponse::of).toList();
    }

    @Transactional
    public ServiceResponse create(ServiceRequest req) {
        MedicalService s = new MedicalService();
        apply(s, req);
        return ServiceResponse.of(repo.saveAndFlush(s));
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest req) {
        MedicalService s = load(id);
        apply(s, req);
        return ServiceResponse.of(repo.saveAndFlush(s));
    }

    public MedicalService load(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy dịch vụ"));
    }

    private static void apply(MedicalService s, ServiceRequest req) {
        s.setCode(req.code().trim());
        s.setName(req.name().trim());
        s.setCategory(req.category());
        s.setDescription(req.description());
        s.setListPrice(req.listPrice());
        s.setEstimatedMinutes(req.estimatedMinutes() == null ? null : req.estimatedMinutes().shortValue());
        s.setRequiresSpecialist(Boolean.TRUE.equals(req.requiresSpecialist()));
        if (req.isActive() != null) {
            s.setActive(req.isActive());
        }
    }
}
