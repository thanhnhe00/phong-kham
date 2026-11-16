package vn.phongkham.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.phongkham.ClinicFixtures;
import vn.phongkham.auth.model.Role;
import vn.phongkham.common.VnTime;

/**
 * Bước 7-9: từ check-in đến thanh toán trên một phiên khám hôm nay.
 * Phiên khám bắt đầu từ đầu giờ hiện tại tới tối đa 23:30 (test bỏ qua nếu chạy sau 22:30).
 */
class ClinicFlowTest extends ClinicFixtures {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    String manager;
    String receptionist;
    String nurse;
    String tech;
    Doctor doctor;
    Integer sessionId;
    List<Integer> slots;
    Integer labService;

    @BeforeEach
    void setUp() throws Exception {
        LocalTime now = LocalTime.now(VnTime.ZONE);
        Assumptions.assumeTrue(now.isBefore(LocalTime.of(22, 30)), "Phiên khám test cần còn ít nhất 1 giờ trong ngày");
        manager = tokenFor(Role.ADMIN);
        receptionist = tokenFor(Role.RECEPTIONIST);
        nurse = tokenFor(Role.NURSE);
        tech = tokenFor(Role.NURSE);
        Integer fee = read(post("/services", manager, """
                {"code":"KHAM","name":"Khám nội","category":"CONSULTATION","listPrice":150000}"""), "$.id");
        labService = read(post("/services", manager, """
                {"code":"XN","name":"Công thức máu","category":"LAB","listPrice":80000}"""), "$.id");
        Integer specialtyId = read(post("/specialties", manager, """
                {"code":"NOI","name":"Nội","consultationServiceId":%d}""".formatted(fee)), "$.id");
        doctor = doctor(specialtyId.longValue());
        LocalTime start = now.truncatedTo(ChronoUnit.HOURS);
        LocalTime end = start.plusHours(3).isBefore(start) || start.isAfter(LocalTime.of(20, 30)) ? LocalTime.of(23, 30) : start.plusHours(3);
        sessionId = read(post("/schedule/sessions", manager, """
                {"doctorId":%d,"specialtyId":%d,"roomId":%d,"date":"%s","shiftName":"Ca","startTime":"%s","endTime":"%s","walkInCapacity":1}
                """.formatted(doctor.id(), specialtyId, room("P1"), VnTime.today(), start, end)).andExpect(status().isCreated()), "$.session.id");
        slots = read(get("/slots?doctorId=" + doctor.id() + "&from=" + VnTime.today() + "&to=" + VnTime.today(), null), "$[0].slots[*].id");
    }

    @Test
    void fullVisitFromQrCheckInToPayment() throws Exception {
        // --- Đặt lịch, lấy QR, check-in ---
        PatientApp p = patientApp("Nguyễn Văn Khám");
        Integer appt = read(post("/appointments", receptionist, """
                {"patientId":%d,"slotId":%d,"channel":"PHONE"}""".formatted(p.patientId(), slots.getFirst())), "$.id");
        String qr = read(get("/me/appointments/" + appt + "/qr", p.token()), "$.token");
        Integer visit = read(post("/reception/check-in/qr", receptionist, """
                {"token":"%s"}""".formatted(qr)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.queueNumber").value(1)).andExpect(jsonPath("$.queueGroup").value("ON_TIME")), "$.id");
        post("/reception/check-in/qr", receptionist, """
                {"token":"%s"}""".formatted(qr)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã QR đã được sử dụng"));
        get("/me/appointments/" + appt, p.token()).andExpect(jsonPath("$.status").value("CHECKED_IN"));

        // Vãng lai: hạn mức 1 lượt
        PatientApp w = patientApp("Trần Vãng Lai");
        post("/reception/walk-ins", receptionist, """
                {"patientId":%d,"sessionId":%d}""".formatted(w.patientId(), sessionId))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.queueGroup").value("WALK_IN"));
        PatientApp w2 = patientApp("Lê Hết Lượt");
        post("/reception/walk-ins", receptionist, """
                {"patientId":%d,"sessionId":%d}""".formatted(w2.patientId(), sessionId)).andExpect(status().isConflict());

        get("/me/queue", p.token()).andExpect(jsonPath("$[0].peopleAhead").value(0));
        get("/me/queue", w.token()).andExpect(jsonPath("$[0].peopleAhead").value(1));

        // --- Điều dưỡng đo sinh hiệu, bác sĩ gọi ---
        post("/visits/" + visit + "/vitals", nurse, """
                {"pulsePerMin":80,"temperatureC":37.2,"systolicBp":120,"diastolicBp":80,"weightKg":65}""")
                .andExpect(status().isCreated());
        post("/queue/sessions/" + sessionId + "/call-next", doctor.token(), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(visit)).andExpect(jsonPath("$.status").value("CALLED"));
        post("/queue/sessions/" + sessionId + "/call-next", doctor.token(), "{}").andExpect(status().isConflict());
        get("/me/notifications", p.token()).andExpect(jsonPath("$.items[*].type", hasItem("CALLED")));
        post("/visits/" + visit + "/start", doctor.token(), "{}").andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // --- Bệnh án, chỉ định ---
        put("/visits/" + visit + "/emr", doctor.token(), """
                {"chiefComplaint":"Sốt, ho 3 ngày","organExam":"Họng đỏ","conclusion":"Viêm họng cấp",
                 "followUpDate":"%s",
                 "diagnoses":[{"icd10Code":"J02.9","name":"Viêm họng cấp","type":"PRIMARY"},
                              {"icd10Code":"I10","name":"Tăng huyết áp","type":"SECONDARY"}]}""".formatted(VnTime.today().plusDays(7)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        get("/visits/" + visit + "/emr", receptionist).andExpect(status().isForbidden()); // lễ tân không xem nội dung chuyên môn

        Integer order = read(post("/visits/" + visit + "/orders", doctor.token(), """
                {"items":[{"serviceId":%d}]}""".formatted(labService)).andExpect(status().isCreated()), "$.items[0].id");
        post("/visits/" + visit + "/await-results", doctor.token(), "{}").andExpect(jsonPath("$.status").value("AWAITING_RESULTS"));
        post("/visits/" + visit + "/complete", doctor.token(), "{}").andExpect(status().isBadRequest());

        // --- Kỹ thuật viên làm xét nghiệm, bác sĩ duyệt ---
        get("/order-items/worklist", tech).andExpect(jsonPath("$", hasSize(1)));
        Integer perf = read(post("/order-items/" + order + "/perform", tech, "{}").andExpect(status().isCreated()), "$.id");
        Integer result = read(post("/performances/" + perf + "/result", tech, """
                {"resultText":"WBC 12 G/L","conclusion":"Bạch cầu tăng nhẹ","submit":true}""")
                .andExpect(jsonPath("$.status").value("SUBMITTED")), "$.id");
        Integer file = read(mvc.perform(multipart("/results/" + result + "/files")
                .file(new MockMultipartFile("file", "ket-qua.png", "image/png", PNG))
                .header("Authorization", "Bearer " + tech)).andExpect(status().isCreated()), "$.id");
        // Chưa duyệt: bệnh nhân không thấy kết quả, không tải được tệp
        get("/me/visits/" + visit, p.token()).andExpect(jsonPath("$.orders[0].items[0].performances[0].result").doesNotExist());
        get("/me/files/" + file, p.token()).andExpect(status().isNotFound());

        post("/visits/" + visit + "/call-back", doctor.token(), "{}").andExpect(jsonPath("$.status").value("CALLED"));
        post("/visits/" + visit + "/start", doctor.token(), "{}").andExpect(status().isOk());
        post("/results/" + result + "/review", doctor.token(), "{}").andExpect(jsonPath("$.status").value("REVIEWED"));
        get("/me/visits/" + visit, p.token())
                .andExpect(jsonPath("$.orders[0].items[0].performances[0].result.conclusion").value("Bạch cầu tăng nhẹ"));
        get("/me/files/" + file, p.token()).andExpect(status().isOk()).andExpect(content().contentType("image/png"));

        // --- Đơn thuốc theo Thông tư 26/2025 ---
        Integer med = read(post("/medicines", manager, """
                {"activeIngredient":"Paracetamol","strength":"500mg","unit":"viên","defaultRoute":"Uống"}"""), "$.id");
        Integer toxic = read(post("/medicines", manager, """
                {"activeIngredient":"Thuốc độc A","unit":"viên","defaultRoute":"Uống","drugClass":"TOXIC"}"""), "$.id");
        post("/visits/" + visit + "/prescriptions", doctor.token(), """
                {"items":[{"medicineId":%d,"quantity":10,"dosePerTime":"1 viên","timesPerDay":2,"days":31}]}""".formatted(med))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(startsWith("Mỗi thuốc kê tối đa 30 ngày")));
        Integer rx = read(post("/visits/" + visit + "/prescriptions", doctor.token(), """
                {"items":[{"medicineId":%d,"quantity":10,"dosePerTime":"1 viên","timesPerDay":2,"timing":"Sau ăn","days":5},
                          {"medicineId":%d,"quantity":2,"dosePerTime":"1 viên","timesPerDay":1,"days":2}],
                 "advice":"Uống nhiều nước"}""".formatted(med, toxic)).andExpect(status().isCreated()), "$.id");
        post("/visits/" + visit + "/complete", doctor.token(), "{}").andExpect(status().isBadRequest()); // bệnh án + đơn chưa ký
        post("/prescriptions/" + rx + "/sign", doctor.token(), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đơn thuốc cần số định danh/CCCD hoặc hộ chiếu của bệnh nhân, hãy cập nhật hồ sơ"));
        put("/patients/" + p.patientId(), receptionist, """
                {"fullName":"Nguyễn Văn Khám","dateOfBirth":"1990-05-20","sex":"MALE","identityType":"CCCD","identityNumber":"001090001234"}""")
                .andExpect(status().isOk());
        post("/prescriptions/" + rx + "/sign", doctor.token(), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.code").value(startsWith("PK001")))
                .andExpect(jsonPath("$.diagnosisText").value("J02.9 - Viêm họng cấp; I10 - Tăng huyết áp"))
                .andExpect(jsonPath("$.items[0].medicineLabel").value("Thuốc độc A")) // thuốc độc ghi trước
                .andExpect(jsonPath("$.nationalSentAt").exists());

        // --- Ký bệnh án, kết thúc khám, hóa đơn ---
        post("/visits/" + visit + "/emr/sign", doctor.token(), "{}").andExpect(jsonPath("$.status").value("SIGNED"));
        Integer invoice = read(post("/visits/" + visit + "/complete", doctor.token(), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.visit.status").value("COMPLETED"))
                .andExpect(jsonPath("$.invoice.totalAmount").value(230000))
                .andExpect(jsonPath("$.invoice.status").value("UNPAID"))
                .andExpect(jsonPath("$.invoice.items", hasSize(2))), "$.invoice.id");
        get("/me/notifications", p.token()).andExpect(jsonPath("$.items[*].type", hasItem("VISIT_COMPLETED")));

        post("/invoices/" + invoice + "/payments", receptionist, """
                {"method":"CASH","amount":100000}""").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID")).andExpect(jsonPath("$.remaining").value(130000));
        post("/invoices/" + invoice + "/payments", receptionist, """
                {"method":"CASH","amount":200000}""").andExpect(status().isBadRequest());
        get("/invoices?status=PARTIALLY_PAID", receptionist).andExpect(jsonPath("$.total").value(1));
        post("/me/invoices/" + invoice + "/pay", p.token(), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        post("/invoices/" + invoice + "/refunds", receptionist, """
                {"amount":50000,"note":"Hoàn tiền"}""").andExpect(status().isForbidden());
        post("/invoices/" + invoice + "/refunds", manager, """
                {"amount":50000,"note":"Thu thừa"}""").andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidAmount").value(180000)).andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));

        // --- Điều chỉnh bệnh án sau khi ký: giữ bản cũ ---
        put("/visits/" + visit + "/emr", doctor.token(), "{}").andExpect(status().isBadRequest()); // lượt khám đã kết thúc
        post("/visits/" + visit + "/emr/amend", doctor.token(), """
                {"reason":"Bổ sung kết luận","content":{"chiefComplaint":"Sốt, ho 3 ngày","conclusion":"Viêm họng cấp do vi khuẩn",
                 "followUpDate":"%s","diagnoses":[{"icd10Code":"J02.9","name":"Viêm họng cấp","type":"PRIMARY"}]}}"""
                .formatted(VnTime.today().plusDays(7)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.status").value("AMENDED"));
        get("/visits/" + visit + "/emr/amendments", doctor.token())
                .andExpect(jsonPath("$[0].previousSnapshot.conclusion").value("Viêm họng cấp"));

        // --- Bệnh nhân xem hồ sơ, gửi phản hồi; quản lý xem báo cáo ---
        get("/me/visits", p.token()).andExpect(jsonPath("$[0].status").value("COMPLETED"));
        get("/patients/" + p.patientId() + "/visits", doctor.token()).andExpect(jsonPath("$[0].id").value(visit));
        get("/patients/" + p.patientId() + "/visits", receptionist).andExpect(status().isForbidden());
        get("/me/visits/" + visit, p.token()).andExpect(jsonPath("$.emr.version").value(2))
                .andExpect(jsonPath("$.prescriptions", hasSize(1))).andExpect(jsonPath("$.invoice.code").value(startsWith("HD")));
        post("/me/feedback", p.token(), """
                {"patientId":%d,"visitId":%d,"category":"Thái độ phục vụ","content":"Lễ tân nhiệt tình"}""".formatted(p.patientId(), visit))
                .andExpect(status().isCreated());
        get("/feedback", manager).andExpect(jsonPath("$", hasSize(1)));

        get("/reports/summary?from=" + VnTime.today() + "&to=" + VnTime.today(), manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.visits").value(2))
                .andExpect(jsonPath("$.completedVisits").value(1))
                .andExpect(jsonPath("$.walkIns").value(1))
                .andExpect(jsonPath("$.billed").value(230000))
                .andExpect(jsonPath("$.collected").value(230000))
                .andExpect(jsonPath("$.refunded").value(50000))
                .andExpect(jsonPath("$.byService[*].serviceName", hasItem("Công thức máu")));
        get("/reports/mine?from=" + VnTime.today() + "&to=" + VnTime.today(), doctor.token())
                .andExpect(jsonPath("$.completed").value(1)).andExpect(jsonPath("$.followUps").value(1));
    }

    @Test
    void refusedServiceIsNotBilledAndChildPrescriptionNeedsWeightAndCompanion() throws Exception {
        PatientApp mom = patientApp("Mẹ Bé");
        Integer child = read(post("/me/patients", mom.token(), """
                {"relationship":"PARENT","profile":{"fullName":"Bé Tí","dateOfBirth":"%s"}}""".formatted(VnTime.today().minusYears(2))), "$.patient.id");
        Integer visit = read(post("/reception/walk-ins", receptionist, """
                {"patientId":%d,"sessionId":%d}""".formatted(child, sessionId)).andExpect(status().isCreated()), "$.id");
        post("/queue/sessions/" + sessionId + "/call-next", doctor.token(), "{}").andExpect(status().isOk());
        post("/visits/" + visit + "/start", doctor.token(), "{}").andExpect(status().isOk());

        Integer item = read(post("/visits/" + visit + "/orders", doctor.token(), """
                {"items":[{"serviceId":%d}]}""".formatted(labService)), "$.items[0].id");
        post("/order-items/" + item + "/refuse", doctor.token(), """
                {"note":"Gia đình không đồng ý lấy máu"}""").andExpect(jsonPath("$.status").value("REFUSED"));

        put("/visits/" + visit + "/emr", doctor.token(), """
                {"chiefComplaint":"Sổ mũi","diagnoses":[{"icd10Code":"J00","name":"Viêm mũi họng cấp"}]}""").andExpect(status().isOk());
        Integer med = read(post("/medicines", manager, """
                {"activeIngredient":"Natri clorid","strength":"0,9%","unit":"chai","defaultRoute":"Nhỏ mũi"}"""), "$.id");
        Integer rx = read(post("/visits/" + visit + "/prescriptions", doctor.token(), """
                {"items":[{"medicineId":%d,"quantity":1,"dosePerTime":"2 giọt","timesPerDay":3,"days":5}]}""".formatted(med)), "$.id");
        post("/prescriptions/" + rx + "/sign", doctor.token(), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trẻ dưới 72 tháng tuổi: cần ghi họ tên người đưa trẻ đến khám"));
        jdbc.update("update visits set companion_name = 'Mẹ Bé' where id = ?", visit);
        post("/prescriptions/" + rx + "/sign", doctor.token(), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trẻ dưới 72 tháng tuổi: cần đo cân nặng trước khi ký đơn"));
        post("/visits/" + visit + "/vitals", nurse, """
                {"weightKg":12.5}""").andExpect(status().isCreated());
        post("/prescriptions/" + rx + "/sign", doctor.token(), "{}").andExpect(status().isOk());

        // Đơn đã ký: không sửa, chỉ thay thế
        put("/prescriptions/" + rx, doctor.token(), """
                {"items":[{"medicineId":%d,"quantity":2,"dosePerTime":"2 giọt","timesPerDay":3,"days":5}]}""".formatted(med))
                .andExpect(status().isConflict());
        Integer rx2 = read(post("/prescriptions/" + rx + "/replace", doctor.token(), """
                {"reason":"Tăng số lượng","content":{"items":[{"medicineId":%d,"quantity":2,"dosePerTime":"2 giọt","timesPerDay":3,"days":5}]}}"""
                .formatted(med)).andExpect(status().isCreated()), "$.id");
        post("/prescriptions/" + rx2 + "/sign", doctor.token(), "{}").andExpect(status().isOk());
        get("/prescriptions/" + rx, doctor.token()).andExpect(jsonPath("$.status").value("REPLACED"));

        post("/visits/" + visit + "/emr/sign", doctor.token(), "{}").andExpect(status().isOk());
        post("/visits/" + visit + "/complete", doctor.token(), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.totalAmount").value(150000)) // chỉ phí khám, xét nghiệm bị từ chối không tính
                .andExpect(jsonPath("$.invoice.items", hasSize(1)));
        get("/me/notifications", mom.token()).andExpect(jsonPath("$.items[*].type", hasItem("VISIT_COMPLETED")));
    }

    @org.springframework.beans.factory.annotation.Autowired
    vn.phongkham.appointment.service.AppointmentService appointmentService;

    @Test
    void reminderIsSentOnceAndVoidedWhenAppointmentChanges() throws Exception {
        PatientApp p = patientApp("Người Được Nhắc");
        Integer appt = read(post("/appointments", receptionist, """
                {"patientId":%d,"slotId":%d,"channel":"PHONE"}""".formatted(p.patientId(), slots.getFirst())), "$.id");
        assertThat(appointmentService.sendReminders(java.time.Instant.now())).isEqualTo(1);
        assertThat(appointmentService.sendReminders(java.time.Instant.now())).isZero(); // không nhắc lặp
        get("/me/notifications", p.token()).andExpect(jsonPath("$.items[*].type", hasItem("REMINDER")));

        post("/appointments/" + appt + "/cancel", receptionist, """
                {"reason":"Bệnh nhân bận"}""").andExpect(status().isOk());
        get("/me/notifications", p.token())
                .andExpect(jsonPath("$.items[*].type", org.hamcrest.Matchers.not(hasItem("REMINDER"))))
                .andExpect(jsonPath("$.items[*].type", hasItem("APPOINTMENT_CANCELLED")));
    }

    @Test
    void onlyTheSessionDoctorCallsAndExamines() throws Exception {
        PatientApp w = patientApp("Khách Vãng Lai");
        Integer visit = read(post("/reception/walk-ins", receptionist, """
                {"patientId":%d,"sessionId":%d}""".formatted(w.patientId(), sessionId)), "$.id");
        Doctor other = doctor(specialtyRepo.findAll().getFirst().getId());
        post("/queue/sessions/" + sessionId + "/call-next", other.token(), "{}").andExpect(status().isForbidden());
        post("/queue/sessions/" + sessionId + "/call-next", doctor.token(), "{}").andExpect(status().isOk());
        put("/visits/" + visit + "/emr", doctor.token(), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Hãy bắt đầu khám trước khi thực hiện thao tác này"));
        post("/visits/" + visit + "/left", receptionist, "{}").andExpect(jsonPath("$.status").value("LEFT"));
        get("/queue/sessions/" + sessionId, nurse).andExpect(jsonPath("$.waiting", hasSize(0)));
        assertThat(slots).isNotEmpty();
    }

    @Test
    void nurseVitalsReasonResultReturnAndAllergyCheckBeforeSigning() throws Exception {
        PatientApp p = patientApp("Đặng Thị Thu");
        Integer visit = read(post("/reception/walk-ins", receptionist, """
                {"patientId":%d,"sessionId":%d,"reason":"Sốt"}""".formatted(p.patientId(), sessionId)).andExpect(status().isCreated()), "$.id");

        // Hàng đợi cho biết ai chưa đo sinh hiệu; điều dưỡng sửa lý do khám và đo
        get("/queue/sessions/" + sessionId, nurse).andExpect(jsonPath("$.waiting[0].vitals").doesNotExist());
        patch("/visits/" + visit + "/reason", nurse, """
                {"reason":"Sốt, đau đầu từ tối qua"}""").andExpect(status().isNoContent());
        post("/visits/" + visit + "/vitals", nurse, """
                {"temperatureC":39.2,"pulsePerMin":104}""").andExpect(status().isCreated());
        get("/queue/sessions/" + sessionId, doctor.token())
                .andExpect(jsonPath("$.waiting[0].vitals.temperatureC").value(39.2))
                .andExpect(jsonPath("$.waiting[0].visit.reason").value("Sốt, đau đầu từ tối qua"));

        post("/queue/sessions/" + sessionId + "/call-next", doctor.token(), "{}").andExpect(status().isOk());
        post("/visits/" + visit + "/start", doctor.token(), "{}").andExpect(status().isOk());
        Integer item = read(post("/visits/" + visit + "/orders", doctor.token(), """
                {"items":[{"serviceId":%d}]}""".formatted(labService)).andExpect(status().isCreated()), "$.items[0].id");

        // Điều dưỡng chỉ lấy việc xét nghiệm; mục vẫn còn trong danh sách tới khi bác sĩ duyệt
        get("/order-items/worklist?category=LAB", nurse).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorId").value(doctor.id()));
        get("/order-items/worklist?category=IMAGING", nurse).andExpect(jsonPath("$", hasSize(0)));
        Integer perf = read(post("/order-items/" + item + "/perform", nurse, "{}"), "$.id");
        Integer result = read(post("/performances/" + perf + "/result", nurse, """
                {"resultText":"Glucose 5,2","submit":true}"""), "$.id");
        get("/order-items/worklist?category=LAB", nurse)
                .andExpect(jsonPath("$[0].item.performances[0].result.status").value("SUBMITTED"));

        // Bác sĩ trả lại kèm lý do; điều dưỡng sửa và gửi lại thì lý do được xóa
        post("/results/" + result + "/return", doctor.token(), "{}").andExpect(status().isBadRequest());
        post("/results/" + result + "/return", nurse, """
                {"reason":"x"}""").andExpect(status().isForbidden());
        post("/results/" + result + "/return", doctor.token(), """
                {"reason":"Ghi rõ lấy mẫu lúc đói hay sau ăn"}""")
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.returnNote").value("Ghi rõ lấy mẫu lúc đói hay sau ăn"));
        get("/order-items/worklist?category=LAB", nurse)
                .andExpect(jsonPath("$[0].item.performances[0].result.returnNote").value("Ghi rõ lấy mẫu lúc đói hay sau ăn"));
        post("/performances/" + perf + "/result", nurse, """
                {"resultText":"Glucose 5,2 (lúc đói)","submit":true}""").andExpect(jsonPath("$.returnNote").doesNotExist());
        post("/results/" + result + "/review", doctor.token(), "{}").andExpect(jsonPath("$.status").value("REVIEWED"));
        get("/order-items/worklist", nurse).andExpect(jsonPath("$", hasSize(0)));

        // Bệnh nhân có dị ứng: phải xác nhận đã đối chiếu mới ký được đơn
        put("/patients/" + p.patientId(), receptionist, """
                {"fullName":"Đặng Thị Thu","dateOfBirth":"1990-04-12","sex":"FEMALE","identityType":"CCCD","identityNumber":"079190004512"}""")
                .andExpect(status().isOk());
        post("/patients/" + p.patientId() + "/allergies", nurse, """
                {"allergen":"Penicillin","reaction":"Nổi mẩn"}""").andExpect(status().isCreated());
        put("/visits/" + visit + "/emr", doctor.token(), """
                {"chiefComplaint":"Sốt","diagnoses":[{"icd10Code":"J03.9","name":"Viêm amidan cấp","type":"PRIMARY"}]}""")
                .andExpect(status().isOk());
        Integer med = read(post("/medicines", manager, """
                {"activeIngredient":"Cefuroxim","strength":"500mg","unit":"viên","defaultRoute":"Uống","drugClass":"ANTIBIOTIC"}"""), "$.id");
        Integer rx = read(post("/visits/" + visit + "/prescriptions", doctor.token(), """
                {"items":[{"medicineId":%d,"quantity":14,"dosePerTime":"1 viên","timesPerDay":2,"days":7}]}""".formatted(med)), "$.id");
        post("/prescriptions/" + rx + "/sign", doctor.token(), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Bệnh nhân có tiền sử dị ứng")));
        post("/prescriptions/" + rx + "/sign", doctor.token(), """
                {"allergyChecked":true,"allergyNote":"Đã hỏi kỹ, chỉ dị ứng nhẹ, theo dõi"}""").andExpect(status().isOk())
                .andExpect(jsonPath("$.allergyAck").value("Đã hỏi kỹ, chỉ dị ứng nhẹ, theo dõi"));
    }
}
