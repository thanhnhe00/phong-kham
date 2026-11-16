package vn.phongkham.staff.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Hồ sơ hành nghề (in trên đơn thuốc) và hồ sơ công khai của bác sĩ */
@Entity
@Table(name = "doctor_profiles")
@Getter
@Setter
public class DoctorProfile {

    /** Trùng id nhân viên */
    @Id
    private Long employeeId;

    private String academicTitle;
    private String practiceLicenseNo;
    private String practiceScope;
    /** Mã liên thông người kê đơn (mô phỏng Hệ thống đơn thuốc quốc gia) */
    private String prescriberCode;
    private Short practiceSince;
    private String bio;
    private String expertise;
    private Long avatarFileId;
    private boolean isPublic = true;
}
