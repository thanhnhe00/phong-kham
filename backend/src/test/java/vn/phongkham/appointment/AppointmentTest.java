package vn.phongkham.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.phongkham.ClinicFixtures;
import vn.phongkham.appointment.service.AppointmentService;
import vn.phongkham.auth.model.Role;

/** Bước 6 - UC05-08, UC14-16: yêu cầu đặt lịch, trực ban gọi xác nhận, đổi/hủy, QR, không đến khám. */
class AppointmentTest extends ClinicFixtures {

    @Autowired
    AppointmentService appointmentService;

    String manager;
    String receptionist;
    Long specialtyId;
    Doctor doctor;
    Integer sessionId;
    List<Integer> slots;

    @BeforeEach
    void setUpSession() throws Exception {
        manager = tokenFor(Role.ADMIN);
        receptionist = tokenFor(Role.RECEPTIONIST);
        specialtyId = specialty("NOI");
        doctor = doctor(specialtyId);
        sessionId = session(manager, doctor.id(), specialtyId, room("P1"), daysAhead(2), "08:00", "10:00");
        slots = slotIds(manager, sessionId);
    }

    private Integer request(PatientApp p, Integer slot) throws Exception {
        return read(post("/me/appointments", p.token(), """
                {"patientId":%d,"slotId":%d,"reason":"Đau bụng"}""".formatted(p.patientId(), slot))
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void appRequestHoldsSlotUntilFrontDeskConfirmsByPhone() throws Exception {
        PatientApp p = patientApp("Nguyễn Văn Đặt");
        Integer appt = request(p, slots.getFirst());
        get("/me/appointments/" + appt, p.token()).andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("LH")));

        // Slot đã giữ: không còn trong danh sách trống, người khác không đặt được
        List<Integer> open = read(get("/slots?doctorId=" + doctor.id() + "&from=" + daysAhead(2) + "&to=" + daysAhead(2), null), "$[0].slots[*].id");
        assertThat(open).doesNotContain(slots.getFirst()).hasSize(3);
        PatientApp other = patientApp("Người Khác");
        post("/me/appointments", other.token(), """
                {"patientId":%d,"slotId":%d}""".formatted(other.patientId(), slots.getFirst()))
                .andExpect(status().isConflict());

        // Chưa xác nhận thì chưa có QR
        get("/me/appointments/" + appt + "/qr", p.token()).andExpect(status().isBadRequest());

        // Gọi 3 lần không nghe máy: vẫn giữ chờ, không tự hủy
        get("/appointments/confirmation-queue", receptionist).andExpect(jsonPath("$", hasSize(1)));
        for (int i = 1; i <= 3; i++) {
            post("/appointments/" + appt + "/contacts", receptionist, """
                    {"result":"NO_ANSWER"}""").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.noAnswerCount").value(i))
                    .andExpect(jsonPath("$.maxAttemptsReached").value(i >= 3));
        }
        get("/appointments/confirmation-queue", receptionist)
                .andExpect(jsonPath("$[0].maxAttemptsReached").value(true))
                .andExpect(jsonPath("$[0].appointment.status").value("PENDING_CONFIRMATION"));

        post("/appointments/" + appt + "/contacts", receptionist, """
                {"result":"PATIENT_AGREED","note":"Bệnh nhân đồng ý giờ khám"}""").andExpect(status().isCreated());
        post("/appointments/" + appt + "/confirm", receptionist, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        get("/appointments/" + appt + "/contacts", receptionist).andExpect(jsonPath("$", hasSize(4)));

        String token = read(get("/me/appointments/" + appt + "/qr", p.token()).andExpect(status().isOk()), "$.token");
        assertThat(token).hasSizeGreaterThan(40);
        get("/appointments/confirmation-queue", receptionist).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void frontDeskBookingIsConfirmedAtOnceWithPreRegisteredServices() throws Exception {
        PatientApp p = patientApp("Lê Văn Gọi");
        Integer lab = read(post("/services", manager, """
                {"code":"XN","name":"Công thức máu","category":"LAB","listPrice":80000}"""), "$.id");
        Integer fee = read(post("/services", manager, """
                {"code":"KHAM","name":"Khám nội","category":"CONSULTATION","listPrice":150000}"""), "$.id");

        post("/appointments", receptionist, """
                {"patientId":%d,"slotId":%d,"channel":"PHONE","serviceIds":[%d]}""".formatted(p.patientId(), slots.get(1), fee))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phí khám đã tính theo chuyên khoa, không cần đăng ký thêm"));
        post("/appointments", receptionist, """
                {"patientId":%d,"slotId":%d,"channel":"PHONE","serviceIds":[%d]}""".formatted(p.patientId(), slots.get(1), lab))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.services[0].name").value("Công thức máu"));

        // Mỗi bệnh nhân một lượt trong một phiên
        post("/appointments", receptionist, """
                {"patientId":%d,"slotId":%d,"channel":"PHONE"}""".formatted(p.patientId(), slots.get(2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Bệnh nhân đã có lịch hẹn trong phiên khám này"));
        post("/appointments", tokenFor(Role.DOCTOR), """
                {"patientId":%d,"slotId":%d,"channel":"PHONE"}""".formatted(p.patientId(), slots.get(3))).andExpect(status().isForbidden());
    }

    @Test
    void patientRequestsRescheduleAndFrontDeskApproves() throws Exception {
        PatientApp p = patientApp("Trần Thị Đổi");
        Integer appt = request(p, slots.getFirst());
        Integer req = read(post("/me/appointments/" + appt + "/change-requests", p.token(), """
                {"type":"RESCHEDULE","requestedSlotId":%d,"reason":"Bận việc"}""".formatted(slots.get(2)))
                .andExpect(status().isCreated()), "$.id");
        post("/me/appointments/" + appt + "/change-requests", p.token(), """
                {"type":"CANCEL"}""").andExpect(status().isConflict());

        // App thấy yêu cầu đang chờ để không cho gửi lại
        get("/me/appointments/" + appt + "/change-requests", p.token()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("RESCHEDULE"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].requestedSlotStart").isNotEmpty());

        get("/appointment-change-requests", receptionist).andExpect(jsonPath("$", hasSize(1)));
        post("/appointment-change-requests/" + req + "/approve", receptionist, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        get("/me/appointments/" + appt + "/change-requests", p.token()).andExpect(jsonPath("$[0].status").value("APPROVED"));

        get("/me/appointments/" + appt, p.token()).andExpect(jsonPath("$.slotId").value(slots.get(2)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        // Slot cũ được trả lại cho người khác đặt
        PatientApp other = patientApp("Người Sau");
        request(other, slots.getFirst());
    }

    @Test
    void cancelRequestIsHandledByFrontDeskNotAutomatic() throws Exception {
        PatientApp p = patientApp("Phạm Văn Hủy");
        Integer appt = request(p, slots.getFirst());
        Integer req = read(post("/me/appointments/" + appt + "/change-requests", p.token(), """
                {"type":"CANCEL","reason":"Đã khỏi"}"""), "$.id");
        get("/me/appointments/" + appt, p.token()).andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));

        post("/appointment-change-requests/" + req + "/approve", receptionist, "{}").andExpect(status().isOk());
        get("/me/appointments/" + appt, p.token()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Bệnh nhân yêu cầu hủy: Đã khỏi"));
    }

    @Test
    void missedAppointmentsBecomeNoShowAndRepeatedNoShowsBlockAppBooking() throws Exception {
        PatientApp p = patientApp("Hoàng Văn Vắng");
        for (int i = 0; i < 3; i++) {
            Integer s = session(manager, doctor.id(), specialtyId, room("PX" + i), daysAhead(5 + i), "08:00", "09:00");
            Integer appt = read(post("/appointments", receptionist, """
                    {"patientId":%d,"slotId":%d,"channel":"PHONE"}""".formatted(p.patientId(), slotIds(manager, s).getFirst())), "$.id");
            // Giả lập đã qua giờ hẹn 20 phút
            jdbc.update("update appointment_slots set slot_start = now() - interval '20 minutes', slot_end = now() "
                    + "where id = (select slot_id from appointments where id = ?)", appt);
        }
        assertThat(appointmentService.markNoShows(Instant.now())).isEqualTo(3);
        get("/appointments?status=NO_SHOW&patientId=" + p.patientId(), receptionist).andExpect(jsonPath("$.total").value(3));
        // Lọc theo ngày (lễ tân xem lịch hẹn trong ngày): ngày khác không có lịch của người này
        get("/appointments?date=" + daysAhead(5) + "&patientId=" + p.patientId(), receptionist).andExpect(status().isOk());
        get("/appointments?date=" + daysAhead(30) + "&patientId=" + p.patientId(), receptionist).andExpect(jsonPath("$.total").value(0));

        post("/me/appointments", p.token(), """
                {"patientId":%d,"slotId":%d}""".formatted(p.patientId(), slots.getFirst()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn đã vắng mặt 3 lần gần đây, vui lòng gọi phòng khám để đặt lịch"));
    }

    @Test
    void patientsOnlySeeTheirOwnAppointments() throws Exception {
        PatientApp p = patientApp("Chủ Lịch");
        Integer appt = request(p, slots.getFirst());
        PatientApp stranger = patientApp("Người Lạ");
        get("/me/appointments/" + appt, stranger.token()).andExpect(status().isNotFound());
        get("/me/appointments/" + appt + "/change-requests", stranger.token()).andExpect(status().isNotFound());
        post("/me/appointments", stranger.token(), """
                {"patientId":%d,"slotId":%d}""".formatted(p.patientId(), slots.get(1))).andExpect(status().isNotFound());
        get("/me/appointments", p.token()).andExpect(jsonPath("$", hasSize(1)));
        get("/appointments", p.token()).andExpect(status().isForbidden());
    }

    /** Hai lễ tân cùng bấm xác nhận một lịch: không bao giờ cả hai cùng thành công, bệnh nhân chỉ nhận một thông báo */
    @Test
    void twoReceptionistsConfirmingAtOnceOnlyOneSucceeds() throws Exception {
        String second = tokenFor(Role.RECEPTIONIST);
        for (Integer slot : slots) {
            PatientApp p = patientApp("Bệnh Nhân " + slot);
            Integer appt = request(p, slot);
            java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> codes = pool.invokeAll(List.of(receptionist, second).stream()
                    .map(t -> (java.util.concurrent.Callable<Integer>) () -> {
                        start.await();
                        return post("/appointments/" + appt + "/confirm", t, "{}").andReturn().getResponse().getStatus();
                    }).toList());
            pool.shutdown();
            List<Integer> got = new java.util.ArrayList<>();
            for (var c : codes) {
                got.add(c.get());
            }
            assertThat(got).as("lịch %d", appt).containsOnlyOnce(200);
            assertThat(got).allMatch(c -> c == 200 || c == 400 || c == 409);
            get("/me/appointments/" + appt, p.token()).andExpect(jsonPath("$.status").value("CONFIRMED"));
            get("/me/notifications", p.token()).andExpect(jsonPath("$.items[?(@.type == 'APPOINTMENT_CONFIRMED')]", hasSize(1)));
        }
    }
}
