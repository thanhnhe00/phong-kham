package vn.phongkham.appointment.model;

import java.util.EnumSet;
import java.util.Set;

/** Ánh xạ các kiểu liệt kê của phần lịch hẹn trong DB */
public final class AppointmentEnums {

    private AppointmentEnums() {
    }

    /** Lịch hẹn chỉ sống tới lúc check-in; sau đó trạng thái nằm ở lượt khám (visits) */
    public enum AppointmentStatus {
        PENDING_CONFIRMATION, CONFIRMED, NEEDS_RECONFIRMATION, CHECKED_IN, CANCELLED, NO_SHOW;

        /** Đang giữ slot */
        public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(PENDING_CONFIRMATION, CONFIRMED, NEEDS_RECONFIRMATION, CHECKED_IN);
        /** Chưa đến khám, còn đổi/hủy được */
        public static final Set<AppointmentStatus> UPCOMING = EnumSet.of(PENDING_CONFIRMATION, CONFIRMED, NEEDS_RECONFIRMATION);
    }

    public enum BookingChannel {
        APP_REQUEST, PHONE, COUNTER
    }

    public enum ChangeRequestType {
        RESCHEDULE, CANCEL
    }

    public enum SimpleRequestStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum ContactChannel {
        PHONE, SMS, IN_APP
    }

    public enum ContactDirection {
        OUTBOUND, INBOUND
    }

    public enum ContactPurpose {
        CONFIRM_BOOKING, DOCTOR_CHANGE, RESCHEDULE, CANCEL, FOLLOW_UP, PAYMENT, OTHER
    }

    public enum ContactResult {
        REACHED, NO_ANSWER, PATIENT_AGREED, PATIENT_DECLINED, CALLBACK_REQUESTED, FAILED, OTHER
    }
}
