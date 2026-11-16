package vn.phongkham.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.phongkham.ClinicFixtures;
import vn.phongkham.auth.model.Role;

/** Bước 4 - UC02, UC21, UC22, UC31, UC32: lịch tuần, phiên khám, slot, hủy phiên, xin nghỉ / thay / đổi ca. */
class ScheduleTest extends ClinicFixtures {

    private String templateJson(Long doctorId, Long specialtyId, Long roomId, LocalDate date, String start, String end) {
        return """
                {"doctorId":%d,"specialtyId":%d,"roomId":%d,"weekday":%d,"shiftName":"Sáng","startTime":"%s","endTime":"%s",
                 "slotMinutes":30,"walkInCapacity":3,"effectiveFrom":"%s"}
                """.formatted(doctorId, specialtyId, roomId, date.getDayOfWeek().getValue(), start, end, VnTime_today());
    }

    private static LocalDate VnTime_today() {
        return daysAhead(0);
    }

    @Test
    void weeklyTemplateNeedsDoctorConfirmAndManagerApprovalBeforeSessionsAppear() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Long noi = specialty("NOI");
        Long p1 = room("P1");
        Doctor an = doctor(noi);
        LocalDate day = daysAhead(3);

        Integer id = read(post("/schedule/templates", manager, templateJson(an.id(), noi, p1, day, "08:00", "10:00"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")), "$.id");

        // Chưa xác nhận thì quản lý chưa duyệt được; bác sĩ khác không xác nhận hộ được
        post("/schedule/templates/" + id + "/approve", manager, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bác sĩ chưa xác nhận lịch tuần này"));
        post("/schedule/templates/" + id + "/confirm", doctor(noi).token(), "{}").andExpect(status().isForbidden());

        post("/schedule/templates/" + id + "/confirm", an.token(), "{}").andExpect(jsonPath("$.status").value("DOCTOR_CONFIRMED"));
        post("/schedule/templates/" + id + "/approve", manager, "{}").andExpect(jsonPath("$.status").value("APPROVED"));

        // Ngày nghỉ tuần sau thì bỏ qua; tuần này sinh 1 phiên, 4 slot 30 phút
        post("/schedule/closures", manager, """
                {"date":"%s","reason":"Nghỉ lễ"}""".formatted(day.plusDays(7))).andExpect(status().isCreated());
        post("/schedule/sessions/generate", manager, """
                {"from":"%s","to":"%s"}""".formatted(daysAhead(1), daysAhead(13)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped[0].reason").value("Phòng khám nghỉ"));
        // Chạy lại không tạo trùng
        post("/schedule/sessions/generate", manager, """
                {"from":"%s","to":"%s"}""".formatted(daysAhead(1), daysAhead(13)))
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.alreadyExisted").value(1));

        get("/slots?doctorId=" + an.id() + "&from=" + day + "&to=" + day, null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].slots", hasSize(4)));
    }

    @Test
    void templateConflictsAreRejected() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Long noi = specialty("NOI");
        Long p1 = room("P1");
        Long p2 = room("P2");
        Doctor an = doctor(noi);
        Doctor binh = doctor(noi);
        LocalDate day = daysAhead(3);
        post("/schedule/templates", manager, templateJson(an.id(), noi, p1, day, "08:00", "11:00")).andExpect(status().isCreated());

        post("/schedule/templates", manager, templateJson(an.id(), noi, p2, day, "10:00", "12:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Bác sĩ đã có lịch tuần khác trùng giờ")));
        post("/schedule/templates", manager, templateJson(binh.id(), noi, p1, day, "09:00", "10:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Phòng đã được xếp cho bác sĩ khác")));
        // Bác sĩ không thuộc chuyên khoa
        post("/schedule/templates", manager, templateJson(binh.id(), specialty("NHI"), p2, day, "08:00", "09:00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Bác sĩ không thuộc chuyên khoa này"));
    }

    @Test
    void sessionsCannotOverlapRoomOrDoctor() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Long noi = specialty("NOI");
        Long p1 = room("P1");
        Long p2 = room("P2");
        Doctor an = doctor(noi);
        Doctor binh = doctor(noi);
        LocalDate day = daysAhead(2);
        session(manager, an.id(), noi, p1, day, "08:00", "10:00");

        post("/schedule/sessions", manager, """
                {"doctorId":%d,"specialtyId":%d,"roomId":%d,"date":"%s","shiftName":"Sáng","startTime":"09:00","endTime":"11:00"}
                """.formatted(binh.id(), noi, p1, day))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Phòng đã có phiên khám khác trùng giờ")));
        post("/schedule/sessions", manager, """
                {"doctorId":%d,"specialtyId":%d,"roomId":%d,"date":"%s","shiftName":"Sáng","startTime":"09:30","endTime":"11:00"}
                """.formatted(an.id(), noi, p2, day))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Bác sĩ đã có phiên khám khác trùng giờ")));
        get("/schedule/sessions/mine?from=" + day + "&to=" + day, an.token()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void cancellingSessionPutsAppointmentsBackForPhoneReconfirmation() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        String receptionist = tokenFor(Role.RECEPTIONIST);
        Long noi = specialty("NOI");
        Doctor an = doctor(noi);
        Integer sessionId = session(manager, an.id(), noi, room("P1"), daysAhead(2), "08:00", "10:00");
        Integer slot = slotIds(manager, sessionId).getFirst();
        PatientApp p = patientApp("Nguyễn Văn Khách");
        Integer appt = read(post("/appointments", receptionist, """
                {"patientId":%d,"slotId":%d,"channel":"PHONE"}""".formatted(p.patientId(), slot))
                .andExpect(status().isCreated()), "$.id");

        post("/schedule/sessions/" + sessionId + "/cancel", manager, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedAppointments").value(1))
                .andExpect(jsonPath("$.session.status").value("CANCELLED"));

        get("/appointments/" + appt, receptionist).andExpect(jsonPath("$.status").value("NEEDS_RECONFIRMATION"));
        get("/appointments/confirmation-queue", receptionist).andExpect(jsonPath("$[0].appointment.id").value(appt));
        // Phiên đã hủy: không xác nhận lại vào giờ cũ được, phải đổi giờ
        post("/appointments/" + appt + "/confirm", receptionist, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phiên khám đã hủy, hãy đổi lịch sang giờ khác"));
    }

    @Test
    void replacementNeedsColleagueThenManagerAndKeepsPatientsInformed() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Long noi = specialty("NOI");
        Doctor an = doctor(noi);
        Doctor binh = doctor(noi);
        Integer sessionId = session(manager, an.id(), noi, room("P1"), daysAhead(3), "08:00", "10:00");
        PatientApp p = patientApp("Lê Thị Hẹn");
        Integer appt = read(post("/appointments", tokenFor(Role.RECEPTIONIST), """
                {"patientId":%d,"slotId":%d,"channel":"COUNTER"}""".formatted(p.patientId(), slotIds(manager, sessionId).getFirst())), "$.id");

        Integer req = read(post("/schedule/change-requests", an.token(), """
                {"sessionId":%d,"type":"REPLACEMENT","replacementDoctorId":%d,"reason":"Đi hội thảo"}""".formatted(sessionId, binh.id()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_COLLEAGUE")), "$.id");
        // Một phiên chỉ một yêu cầu đang mở
        post("/schedule/change-requests", an.token(), """
                {"sessionId":%d,"type":"LEAVE","reason":"Ốm"}""".formatted(sessionId)).andExpect(status().isConflict());
        // Chưa được người thay đồng ý thì quản lý chưa duyệt được
        post("/schedule/change-requests/" + req + "/decide", manager, """
                {"approve":true}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bác sĩ được nhờ chưa đồng ý"));

        get("/schedule/change-requests", binh.token()).andExpect(jsonPath("$", hasSize(1)));
        post("/schedule/change-requests/" + req + "/respond", binh.token(), """
                {"accept":true}""").andExpect(jsonPath("$.status").value("PENDING_MANAGER"));
        post("/schedule/change-requests/" + req + "/decide", manager, """
                {"approve":true,"note":"Đồng ý"}""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        get("/schedule/sessions/" + sessionId, manager).andExpect(jsonPath("$.session.doctorId").value(binh.id()))
                .andExpect(jsonPath("$.session.originalDoctorId").value(an.id()));
        get("/me/appointments/" + appt, p.token()).andExpect(jsonPath("$.status").value("NEEDS_RECONFIRMATION"))
                .andExpect(jsonPath("$.doctorId").value(binh.id()));
    }

    @Test
    void replacementDoctorMustBeFreeAndSameSpecialty() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Long noi = specialty("NOI");
        Doctor an = doctor(noi);
        Doctor binh = doctor(noi);
        Doctor nhi = doctor(specialty("NHI"));
        LocalDate day = daysAhead(3);
        Integer sessionId = session(manager, an.id(), noi, room("P1"), day, "08:00", "10:00");
        session(manager, binh.id(), noi, room("P2"), day, "09:00", "11:00");

        post("/schedule/change-requests", an.token(), """
                {"sessionId":%d,"type":"REPLACEMENT","replacementDoctorId":%d,"reason":"x"}""".formatted(sessionId, binh.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Bác sĩ được nhờ đã có lịch khám trùng giờ"));
        post("/schedule/change-requests", an.token(), """
                {"sessionId":%d,"type":"REPLACEMENT","replacementDoctorId":%d,"reason":"x"}""".formatted(sessionId, nhi.id()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Bác sĩ được nhờ phải cùng chuyên khoa"));
    }

    @Test
    void swapExchangesDoctorsOfTwoSessions() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Long noi = specialty("NOI");
        Doctor an = doctor(noi);
        Doctor binh = doctor(noi);
        Integer mine = session(manager, an.id(), noi, room("P1"), daysAhead(3), "08:00", "10:00");
        Integer theirs = session(manager, binh.id(), noi, room("P2"), daysAhead(4), "08:00", "10:00");

        Integer req = read(post("/schedule/change-requests", an.token(), """
                {"sessionId":%d,"type":"SWAP","replacementDoctorId":%d,"swapSessionId":%d,"reason":"Việc gia đình"}"""
                .formatted(mine, binh.id(), theirs)).andExpect(status().isCreated()), "$.id");
        post("/schedule/change-requests/" + req + "/respond", binh.token(), """
                {"accept":true}""").andExpect(status().isOk());
        post("/schedule/change-requests/" + req + "/decide", manager, """
                {"approve":true}""").andExpect(status().isOk());

        get("/schedule/sessions/" + mine, manager).andExpect(jsonPath("$.session.doctorId").value(binh.id()));
        get("/schedule/sessions/" + theirs, manager).andExpect(jsonPath("$.session.doctorId").value(an.id()));
    }

    @Test
    void approvedLeaveCancelsSessionAndBlocksSlots() throws Exception {
        String manager = tokenFor(Role.ADMIN);
        Long noi = specialty("NOI");
        Doctor an = doctor(noi);
        LocalDate day = daysAhead(3);
        Integer sessionId = session(manager, an.id(), noi, room("P1"), day, "08:00", "09:00");

        Integer req = read(post("/schedule/change-requests", an.token(), """
                {"sessionId":%d,"type":"LEAVE","reason":"Ốm"}""".formatted(sessionId))
                .andExpect(jsonPath("$.status").value("PENDING_MANAGER")), "$.id");
        post("/schedule/change-requests/" + req + "/decide", manager, """
                {"approve":true}""").andExpect(status().isOk());

        get("/schedule/sessions/" + sessionId, manager).andExpect(jsonPath("$.session.status").value("CANCELLED"))
                .andExpect(jsonPath("$.slots[0].status").value("BLOCKED"));
        List<?> open = read(get("/slots?from=" + day + "&to=" + day, null), "$");
        assertThat(open).isEmpty();
    }
}
