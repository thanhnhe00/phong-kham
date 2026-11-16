package vn.phongkham.appointment.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tác vụ nền mỗi phút: lịch không đến khám -> NO_SHOW, và nhắc lịch trước 24 giờ. Tắt trong test (app.jobs.enabled=false). */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class NoShowJob {

    private static final Logger log = LoggerFactory.getLogger(NoShowJob.class);

    private final AppointmentService appointments;

    public NoShowJob(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @Scheduled(fixedDelayString = "${app.jobs.no-show-interval:60s}")
    public void run() {
        int n = appointments.markNoShows(Instant.now());
        if (n > 0) {
            log.info("Đã chuyển {} lịch hẹn sang không đến khám", n);
        }
        int r = appointments.sendReminders(Instant.now());
        if (r > 0) {
            log.info("Đã gửi {} nhắc lịch", r);
        }
    }
}
