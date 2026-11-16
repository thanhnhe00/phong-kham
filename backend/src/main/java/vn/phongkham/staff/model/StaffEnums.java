package vn.phongkham.staff.model;

/** Ánh xạ kiểu employment_status và history_kind trong DB */
public final class StaffEnums {

    private StaffEnums() {
    }

    public enum EmploymentStatus {
        ACTIVE, ON_LEAVE, SUSPENDED, RESIGNED
    }

    public enum HistoryKind {
        EDUCATION, WORK
    }
}
