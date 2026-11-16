package vn.phongkham.visit.model;

import java.util.EnumSet;
import java.util.Set;

/** Ánh xạ các kiểu liệt kê của lượt khám trong DB */
public final class VisitEnums {

    private VisitEnums() {
    }

    public enum VisitSource {
        APPOINTMENT, WALK_IN
    }

    public enum CheckinMethod {
        QR_RECEPTION, QR_SELF, MANUAL
    }

    /** Đúng giờ / đến muộn (người đặt lịch) / vãng lai */
    public enum QueueGroup {
        ON_TIME, LATE, WALK_IN
    }

    public enum VisitStatus {
        WAITING, CALLED, IN_PROGRESS, AWAITING_RESULTS, COMPLETED, LEFT, CANCELLED;

        /** Còn ở phòng khám (chưa xong, chưa bỏ về) */
        public static final Set<VisitStatus> OPEN = EnumSet.of(WAITING, CALLED, IN_PROGRESS, AWAITING_RESULTS);
    }
}
