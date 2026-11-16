package vn.phongkham.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.common.Patterns;
import vn.phongkham.staff.model.StaffEnums.EmploymentStatus;
import vn.phongkham.staff.model.StaffEnums.HistoryKind;

public final class StaffDtos {

    private StaffDtos() {
    }

    public record CreateStaffRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 160, message = "Họ tên tối đa 160 ký tự") String fullName,
            @NotBlank(message = "Vui lòng nhập số điện thoại") @Pattern(regexp = Patterns.PHONE, message = Patterns.PHONE_MESSAGE) String phone,
            @Email(message = "Email không hợp lệ") String email,
            @Size(max = 100, message = "Chức danh tối đa 100 ký tự") String jobTitle,
            @NotEmpty(message = "Chọn ít nhất một vai trò") Set<Role> roles,
            List<Long> specialtyIds,
            Long primarySpecialtyId,
            LocalDate hiredAt) {
    }

    public record UpdateStaffRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 160, message = "Họ tên tối đa 160 ký tự") String fullName,
            @Email(message = "Email không hợp lệ") String email,
            @Size(max = 100, message = "Chức danh tối đa 100 ký tự") String jobTitle,
            @NotNull(message = "Vui lòng chọn tình trạng làm việc") EmploymentStatus employmentStatus,
            @NotEmpty(message = "Chọn ít nhất một vai trò") Set<Role> roles,
            List<Long> specialtyIds,
            Long primarySpecialtyId,
            LocalDate hiredAt) {
    }

    public record SpecialtyRef(Long id, String name, boolean isPrimary) {
    }

    public record StaffResponse(Long id, String employeeCode, String fullName, String jobTitle,
                                EmploymentStatus employmentStatus, LocalDate hiredAt,
                                Long userId, String phone, String email, Set<Role> roles, UserStatus accountStatus,
                                List<SpecialtyRef> specialties, boolean hasDoctorProfile) {
    }

    /** temporaryPassword chỉ hiện một lần lúc tạo; nhân viên phải đổi ở lần đăng nhập đầu */
    public record CreatedStaffResponse(StaffResponse staff, String temporaryPassword) {
    }

    public record DoctorProfileRequest(
            @Size(max = 60, message = "Học hàm/học vị tối đa 60 ký tự") String academicTitle,
            @NotBlank(message = "Vui lòng nhập số chứng chỉ hành nghề") @Size(max = 60, message = "Số chứng chỉ tối đa 60 ký tự") String practiceLicenseNo,
            @Size(max = 255, message = "Phạm vi chuyên môn tối đa 255 ký tự") String practiceScope,
            @Size(max = 60, message = "Mã liên thông tối đa 60 ký tự") String prescriberCode,
            @Min(value = 1950, message = "Năm bắt đầu hành nghề không hợp lệ") @Max(value = 2100, message = "Năm bắt đầu hành nghề không hợp lệ") Integer practiceSince,
            String bio,
            String expertise,
            Boolean isPublic) {
    }

    public record DoctorProfileResponse(Long employeeId, String academicTitle, String practiceLicenseNo, String practiceScope,
                                        String prescriberCode, Integer practiceSince, String bio, String expertise,
                                        boolean isPublic) {
    }

    public record HistoryRequest(
            @NotNull(message = "Chọn loại: đào tạo hoặc công tác") HistoryKind kind,
            @NotNull(message = "Vui lòng nhập năm bắt đầu") @Min(value = 1950, message = "Năm không hợp lệ") @Max(value = 2100, message = "Năm không hợp lệ") Integer fromYear,
            @Min(value = 1950, message = "Năm không hợp lệ") @Max(value = 2100, message = "Năm không hợp lệ") Integer toYear,
            @NotBlank(message = "Vui lòng nhập nội dung") @Size(max = 200, message = "Tối đa 200 ký tự") String title,
            @NotBlank(message = "Vui lòng nhập nơi đào tạo/công tác") @Size(max = 200, message = "Tối đa 200 ký tự") String place) {
    }

    public record HistoryResponse(Long id, HistoryKind kind, Integer fromYear, Integer toYear, String title, String place) {
    }

    /** Thẻ bác sĩ trong danh sách công khai */
    public record DoctorCard(Long id, String fullName, String academicTitle, List<SpecialtyRef> specialties,
                             Integer practiceSince, String expertise) {
    }

    public record DoctorDetail(Long id, String fullName, String academicTitle, List<SpecialtyRef> specialties,
                               Integer practiceSince, String expertise, String bio, String practiceScope,
                               List<HistoryResponse> history) {
    }
}
