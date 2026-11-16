package vn.phongkham.medicalservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import vn.phongkham.medicalservice.model.MedicalService;
import vn.phongkham.medicalservice.model.ServiceCategory;

public final class ServiceDtos {

    private ServiceDtos() {
    }

    public record ServiceRequest(
            @NotBlank(message = "Vui lòng nhập mã dịch vụ") @Size(max = 40, message = "Mã tối đa 40 ký tự") String code,
            @NotBlank(message = "Vui lòng nhập tên dịch vụ") @Size(max = 160, message = "Tên tối đa 160 ký tự") String name,
            @NotNull(message = "Vui lòng chọn loại dịch vụ") ServiceCategory category,
            String description,
            @NotNull(message = "Vui lòng nhập giá") @DecimalMin(value = "0", message = "Giá không được âm")
            @Digits(integer = 12, fraction = 0, message = "Giá phải là số nguyên VND") BigDecimal listPrice,
            @Min(value = 1, message = "Thời lượng phải lớn hơn 0") @Max(value = 600, message = "Thời lượng tối đa 600 phút") Integer estimatedMinutes,
            // Boolean (không dùng boolean): Jackson 3 báo lỗi khi thiếu trường kiểu nguyên thủy
            Boolean requiresSpecialist,
            Boolean isActive) {
    }

    public record ServiceResponse(Long id, String code, String name, ServiceCategory category, String description,
                                  BigDecimal listPrice, Integer estimatedMinutes, boolean requiresSpecialist,
                                  boolean isActive) {

        public static ServiceResponse of(MedicalService s) {
            return new ServiceResponse(s.getId(), s.getCode(), s.getName(), s.getCategory(), s.getDescription(),
                    s.getListPrice(), s.getEstimatedMinutes() == null ? null : s.getEstimatedMinutes().intValue(),
                    s.isRequiresSpecialist(), s.isActive());
        }
    }
}
