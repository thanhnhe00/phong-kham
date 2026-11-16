package vn.phongkham.schedule.model;

/** Ánh xạ các kiểu liệt kê của phần lịch bác sĩ trong DB */
public final class ScheduleEnums {

    private ScheduleEnums() {
    }

    /** template_status: quản lý soạn -> bác sĩ xác nhận -> quản lý duyệt */
    public enum TemplateStatus {
        DRAFT, DOCTOR_CONFIRMED, APPROVED, INACTIVE
    }

    /** session_status */
    public enum SessionStatus {
        DRAFT, PUBLISHED, CLOSED, CANCELLED
    }

    /** session_change_type: xin nghỉ / nhờ người thay / đổi chéo ca */
    public enum ChangeType {
        LEAVE, REPLACEMENT, SWAP
    }

    /** request_status */
    public enum RequestStatus {
        PENDING_COLLEAGUE, PENDING_MANAGER, APPROVED, REJECTED, WITHDRAWN
    }

    /** slot_status */
    public enum SlotStatus {
        AVAILABLE, HELD, BOOKED, BLOCKED
    }
}
