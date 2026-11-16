package vn.phongkham.medicine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.phongkham.medicine.model.DrugClass;
import vn.phongkham.medicine.model.Medicine;

public final class MedicineDtos {

    private MedicineDtos() {
    }

    public record MedicineRequest(
            @NotBlank(message = "Vui lòng nhập hoạt chất") @Size(max = 200, message = "Hoạt chất tối đa 200 ký tự") String activeIngredient,
            @Size(max = 160, message = "Tên thương mại tối đa 160 ký tự") String brandName,
            @Size(max = 80, message = "Hàm lượng tối đa 80 ký tự") String strength,
            @Size(max = 80, message = "Dạng bào chế tối đa 80 ký tự") String dosageForm,
            @Size(max = 40, message = "Đường dùng tối đa 40 ký tự") String defaultRoute,
            @NotBlank(message = "Vui lòng nhập đơn vị") @Size(max = 30, message = "Đơn vị tối đa 30 ký tự") String unit,
            DrugClass drugClass,
            Boolean isActive) {
    }

    /** label: chuỗi in trên đơn, vd "Paracetamol (Panadol) 500mg" */
    public record MedicineResponse(Long id, String activeIngredient, String brandName, String strength, String dosageForm,
                                   String defaultRoute, String unit, DrugClass drugClass, boolean isActive, String label) {

        public static MedicineResponse of(Medicine m) {
            StringBuilder label = new StringBuilder(m.getActiveIngredient());
            if (m.getBrandName() != null && !m.getBrandName().isBlank()) {
                label.append(" (").append(m.getBrandName()).append(')');
            }
            if (m.getStrength() != null && !m.getStrength().isBlank()) {
                label.append(' ').append(m.getStrength());
            }
            return new MedicineResponse(m.getId(), m.getActiveIngredient(), m.getBrandName(), m.getStrength(),
                    m.getDosageForm(), m.getDefaultRoute(), m.getUnit(), m.getDrugClass(), m.isActive(), label.toString());
        }
    }
}
