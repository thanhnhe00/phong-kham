package vn.phongkham.specialty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class SpecialtyDtos {

    private SpecialtyDtos() {
    }

    public record SpecialtyRequest(
            @NotBlank(message = "Vui lòng nhập mã chuyên khoa") @Size(max = 30, message = "Mã tối đa 30 ký tự") String code,
            @NotBlank(message = "Vui lòng nhập tên chuyên khoa") @Size(max = 120, message = "Tên tối đa 120 ký tự") String name,
            String description,
            Long consultationServiceId,
            Boolean isActive) {
    }

    /** consultationPrice: phí khám hiển thị cho bệnh nhân khi chọn chuyên khoa */
    public record SpecialtyResponse(Long id, String code, String name, String description, boolean isActive,
                                    Long consultationServiceId, BigDecimal consultationPrice) {
    }
}
