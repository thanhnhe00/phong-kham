package vn.phongkham.specialty.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.model.ServiceCategory;
import vn.phongkham.medicalservice.repository.MedicalServiceRepository;
import vn.phongkham.specialty.dto.SpecialtyDtos.SpecialtyRequest;
import vn.phongkham.specialty.dto.SpecialtyDtos.SpecialtyResponse;
import vn.phongkham.specialty.model.Specialty;
import vn.phongkham.specialty.repository.SpecialtyRepository;

/** UC01, UC31: chuyên khoa và phí khám của chuyên khoa. */
@Service
public class SpecialtyService {

    private final SpecialtyRepository repo;
    private final MedicalServiceRepository services;

    public SpecialtyService(SpecialtyRepository repo, MedicalServiceRepository services) {
        this.repo = repo;
        this.services = services;
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> listActive() {
        return toResponses(repo.findByIsActiveTrueOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> listAll() {
        return toResponses(repo.findAllByOrderByNameAsc());
    }

    @Transactional
    public SpecialtyResponse create(SpecialtyRequest req) {
        Specialty s = new Specialty();
        apply(s, req);
        return toResponses(List.of(repo.saveAndFlush(s))).getFirst();
    }

    @Transactional
    public SpecialtyResponse update(Long id, SpecialtyRequest req) {
        Specialty s = repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy chuyên khoa"));
        apply(s, req);
        return toResponses(List.of(repo.saveAndFlush(s))).getFirst();
    }

    private void apply(Specialty s, SpecialtyRequest req) {
        if (req.consultationServiceId() != null) {
            MedicalService fee = services.findById(req.consultationServiceId())
                    .orElseThrow(() -> ApiException.badRequest("Dịch vụ phí khám không tồn tại"));
            if (fee.getCategory() != ServiceCategory.CONSULTATION) {
                throw ApiException.badRequest("Phí khám phải là dịch vụ loại Khám (CONSULTATION)");
            }
        }
        s.setCode(req.code().trim());
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setConsultationServiceId(req.consultationServiceId());
        if (req.isActive() != null) {
            s.setActive(req.isActive());
        }
    }

    private List<SpecialtyResponse> toResponses(List<Specialty> list) {
        var ids = list.stream().map(Specialty::getConsultationServiceId).filter(java.util.Objects::nonNull).toList();
        Map<Long, MedicalService> fees = services.findAllById(ids).stream()
                .collect(Collectors.toMap(MedicalService::getId, Function.identity()));
        return list.stream().map(s -> {
            MedicalService fee = fees.get(s.getConsultationServiceId());
            BigDecimal price = fee == null ? null : fee.getListPrice();
            return new SpecialtyResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.isActive(),
                    s.getConsultationServiceId(), price);
        }).toList();
    }
}
