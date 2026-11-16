package vn.phongkham.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Giờ Việt Nam: DB lưu mốc thời gian UTC, lịch làm việc nhập theo giờ VN. */
public final class VnTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private VnTime() {
    }

    public static Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE).toInstant();
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    private static final java.time.format.DateTimeFormatter DISPLAY =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZONE);

    /** "08:30 16/11/2026" theo giờ VN, dùng trong nội dung thông báo */
    public static String format(Instant instant) {
        return DISPLAY.format(instant);
    }
}
