package vn.phongkham.patient.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.phongkham.patient.model.PatientEnums.AccountRelationship;
import vn.phongkham.patient.model.PatientEnums.AllergySeverity;
import vn.phongkham.patient.model.PatientEnums.IdentityType;
import vn.phongkham.patient.model.PatientEnums.Sex;

public final class PatientDtos {

    private PatientDtos() {
    }

    /** Số điện thoại cho phép để trống; nếu có thì 10 số bắt đầu bằng 0 */
    static final String OPTIONAL_PHONE = "^$|^0\\d{9}$";

    /** Hồ sơ đầy đủ: lễ tân tạo/sửa, hoặc bệnh nhân tự tạo lần đầu */
    public record PatientRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 160, message = "Họ tên tối đa 160 ký tự") String fullName,
            @NotNull(message = "Vui lòng nhập ngày sinh") @PastOrPresent(message = "Ngày sinh không hợp lệ") LocalDate dateOfBirth,
            Sex sex,
            IdentityType identityType,
            @Size(max = 40, message = "Số giấy tờ tối đa 40 ký tự") String identityNumber,
            @Pattern(regexp = OPTIONAL_PHONE, message = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0") String phone,
            @Email(message = "Email không hợp lệ") String email,
            @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự") String addressLine,
            @Size(max = 120, message = "Phường/xã tối đa 120 ký tự") String ward,
            @Size(max = 120, message = "Tỉnh/thành phố tối đa 120 ký tự") String province,
            @Size(max = 120, message = "Nghề nghiệp tối đa 120 ký tự") String occupation,
            @Size(max = 60, message = "Dân tộc tối đa 60 ký tự") String ethnicity,
            @Size(max = 60, message = "Quốc tịch tối đa 60 ký tự") String nationality) {
    }

    /** Bệnh nhân tự tạo hồ sơ cho mình hoặc người thân (relationship) */
    public record MyPatientCreate(@NotNull(message = "Thiếu thông tin hồ sơ") @jakarta.validation.Valid PatientRequest profile,
                                  @NotNull(message = "Vui lòng chọn quan hệ với người có hồ sơ") AccountRelationship relationship) {
    }

    /** Bệnh nhân chỉ tự sửa thông tin liên hệ; họ tên, ngày sinh, giấy tờ do phòng khám xác minh và sửa */
    public record ContactUpdate(
            @Pattern(regexp = OPTIONAL_PHONE, message = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0") String phone,
            @Email(message = "Email không hợp lệ") String email,
            @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự") String addressLine,
            @Size(max = 120, message = "Phường/xã tối đa 120 ký tự") String ward,
            @Size(max = 120, message = "Tỉnh/thành phố tối đa 120 ký tự") String province,
            @Size(max = 120, message = "Nghề nghiệp tối đa 120 ký tự") String occupation) {
    }

    public record LinkRequest(@NotBlank(message = "Vui lòng nhập mã liên kết") String code,
                              @NotNull(message = "Vui lòng chọn quan hệ với người có hồ sơ") AccountRelationship relationship) {
    }

    public record LinkCodeResponse(String code, Instant expiresAt) {
    }

    public record PatientResponse(Long id, String patientCode, String fullName, LocalDate dateOfBirth, Integer ageInMonths,
                                  Sex sex, IdentityType identityType, String identityNumber, String phone, String email,
                                  String addressLine, String ward, String province, String occupation, String ethnicity,
                                  String nationality) {
    }

    public record MyPatientResponse(PatientResponse patient, AccountRelationship relationship) {
    }

    public record RepresentativeRequest(
            @NotBlank(message = "Vui lòng nhập họ tên người đại diện") @Size(max = 160, message = "Họ tên tối đa 160 ký tự") String fullName,
            @NotBlank(message = "Vui lòng nhập quan hệ") @Size(max = 80, message = "Quan hệ tối đa 80 ký tự") String relationship,
            @Pattern(regexp = OPTIONAL_PHONE, message = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0") String phone,
            @Size(max = 40, message = "Số giấy tờ tối đa 40 ký tự") String identityNumber,
            @Size(max = 255, message = "Ghi chú tối đa 255 ký tự") String authorityNote) {
    }

    public record RepresentativeResponse(Long id, String fullName, String relationship, String phone, String identityNumber,
                                         String authorityNote, Instant verifiedAt) {
    }

    public record AllergyRequest(
            @NotBlank(message = "Vui lòng nhập tác nhân dị ứng") @Size(max = 160, message = "Tối đa 160 ký tự") String allergen,
            String reaction,
            AllergySeverity severity) {
    }

    public record AllergyResponse(Long id, String allergen, String reaction, AllergySeverity severity, Instant recordedAt) {
    }

    public record PatientDetail(PatientResponse patient, List<AllergyResponse> allergies,
                                List<RepresentativeResponse> representatives, int linkedAccounts) {
    }
}
