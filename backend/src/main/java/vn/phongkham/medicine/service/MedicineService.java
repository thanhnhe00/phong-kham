package vn.phongkham.medicine.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.dto.PageResponse;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.medicine.dto.MedicineDtos.MedicineRequest;
import vn.phongkham.medicine.dto.MedicineDtos.MedicineResponse;
import vn.phongkham.medicine.model.DrugClass;
import vn.phongkham.medicine.model.Medicine;
import vn.phongkham.medicine.repository.MedicineRepository;

/** Danh mục thuốc dùng khi kê đơn. */
@Service
public class MedicineService {

    private final MedicineRepository repo;

    public MedicineService(MedicineRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicineResponse> search(String q, boolean includeInactive, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        return PageResponse.of(repo.search(query, includeInactive, pageable), MedicineResponse::of);
    }

    @Transactional
    public MedicineResponse create(MedicineRequest req) {
        Medicine m = new Medicine();
        apply(m, req);
        return MedicineResponse.of(repo.save(m));
    }

    @Transactional
    public MedicineResponse update(Long id, MedicineRequest req) {
        Medicine m = repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thuốc"));
        apply(m, req);
        return MedicineResponse.of(m);
    }

    private static void apply(Medicine m, MedicineRequest req) {
        m.setActiveIngredient(req.activeIngredient().trim());
        m.setBrandName(req.brandName());
        m.setStrength(req.strength());
        m.setDosageForm(req.dosageForm());
        m.setDefaultRoute(req.defaultRoute());
        m.setUnit(req.unit().trim());
        m.setDrugClass(req.drugClass() == null ? DrugClass.NORMAL : req.drugClass());
        if (req.isActive() != null) {
            m.setActive(req.isActive());
        }
    }
}
