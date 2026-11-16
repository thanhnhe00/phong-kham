package vn.phongkham.appointment.service;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.phongkham.appointment.dto.AppointmentDtos.AppointmentResponse;
import vn.phongkham.appointment.model.Appointment;
import vn.phongkham.common.VnTime;
import vn.phongkham.notification.service.NotificationService;

/** Nội dung thông báo gửi bệnh nhân theo từng sự kiện của lịch hẹn */
@Component
public class AppointmentNotifier {

    public static final String ENTITY = "APPOINTMENT";
    public static final String REMINDER = "REMINDER";

    private final NotificationService notifications;
    private final AppointmentViews views;

    public AppointmentNotifier(NotificationService notifications, AppointmentViews views) {
        this.notifications = notifications;
        this.views = views;
    }

    public void confirmed(Appointment a) {
        AppointmentResponse r = views.of(a);
        send(r, "APPOINTMENT_CONFIRMED", "Lịch khám đã được xác nhận",
                "Lịch " + r.code() + " lúc " + VnTime.format(r.start()) + " với bác sĩ " + r.doctorName()
                        + ", phòng " + r.roomCode() + ". Vui lòng đến trước giờ hẹn 15 phút và mang theo giấy tờ tùy thân.");
    }

    public void cancelled(Appointment a) {
        AppointmentResponse r = views.of(a);
        notifications.voidAll(ENTITY, a.getId(), REMINDER);
        send(r, "APPOINTMENT_CANCELLED", "Lịch khám đã hủy",
                "Lịch " + r.code() + " lúc " + VnTime.format(r.start()) + " đã được hủy.");
    }

    /** Đổi bác sĩ / hủy phiên: báo trước, trực ban sẽ gọi để thống nhất */
    public void needsReconfirmation(List<Appointment> list) {
        for (AppointmentResponse r : views.of(list)) {
            notifications.voidAll(ENTITY, r.id(), REMINDER);
            send(r, "APPOINTMENT_RECONFIRM", "Lịch khám có thay đổi",
                    "Lịch " + r.code() + " lúc " + VnTime.format(r.start())
                            + " có thay đổi về bác sĩ hoặc phiên khám. Phòng khám sẽ gọi điện để thống nhất lại với bạn.");
        }
    }

    public void voidReminders(Appointment a) {
        notifications.voidAll(ENTITY, a.getId(), REMINDER);
    }

    public void reminder(Appointment a) {
        AppointmentResponse r = views.of(a);
        send(r, REMINDER, "Nhắc lịch khám ngày mai",
                "Bạn có lịch khám lúc " + VnTime.format(r.start()) + " với bác sĩ " + r.doctorName() + ", phòng " + r.roomCode() + ".");
    }

    private void send(AppointmentResponse r, String type, String title, String body) {
        notifications.toPatient(r.patientId(), type, title, body, ENTITY, r.id());
    }
}
