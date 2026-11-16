package vn.phongkham.patient.model;

/** Ánh xạ các kiểu liệt kê của phần bệnh nhân trong DB */
public final class PatientEnums {

    private PatientEnums() {
    }

    public enum Sex {
        MALE, FEMALE, OTHER, UNKNOWN
    }

    /** Đơn thuốc bắt buộc có số định danh/CCCD/hộ chiếu (Thông tư 26/2025), trừ trẻ em */
    public enum IdentityType {
        CCCD, PASSPORT, PERSONAL_ID, OTHER, NOT_PROVIDED
    }

    /** Quan hệ giữa chủ tài khoản app và người có hồ sơ */
    public enum AccountRelationship {
        SELF, PARENT, LEGAL_GUARDIAN, FAMILY_MEMBER, OTHER
    }

    public enum AllergySeverity {
        MILD, MODERATE, SEVERE, UNKNOWN
    }
}
