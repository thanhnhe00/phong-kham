package vn.phongkham.report.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.VnTime;
import vn.phongkham.common.exception.ApiException;
import vn.phongkham.staff.service.CurrentStaff;

/**
 * UC28, UC33: báo cáo trong khoảng ngày (giờ VN).
 * Doanh thu = tiền thực thu trong kỳ (theo thời điểm thu) − tiền hoàn; Phát sinh = tổng hóa đơn lập trong kỳ;
 * Chưa thu = phần còn lại của các hóa đơn lập trong kỳ. Số lượt khám không dùng làm chỉ số đánh giá chuyên môn.
 */
@Service
public class ReportService {

    public record DoctorRow(Long doctorId, String doctorName, long visits, long completed, BigDecimal billed) {
    }

    public record ServiceRow(Long serviceId, String serviceName, BigDecimal quantity, BigDecimal amount) {
    }

    public record DayRow(LocalDate date, long visits, BigDecimal collected) {
    }

    public record Summary(LocalDate from, LocalDate to, long visits, long completedVisits, long walkIns, long noShows,
                          long cancelledAppointments, BigDecimal billed, BigDecimal collected, BigDecimal refunded,
                          BigDecimal unpaid, List<DayRow> byDay, List<DoctorRow> byDoctor, List<ServiceRow> byService) {
    }

    public record DoctorSummary(LocalDate from, LocalDate to, long visits, long completed, long followUps, long noShows,
                                long sessions) {
    }

    private final JdbcTemplate jdbc;
    private final CurrentStaff currentStaff;

    public ReportService(JdbcTemplate jdbc, CurrentStaff currentStaff) {
        this.jdbc = jdbc;
        this.currentStaff = currentStaff;
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        Timestamp[] r = range(from, to);
        long visits = count("select count(*) from visits where checked_in_at >= ? and checked_in_at < ?", r);
        long completed = count("select count(*) from visits where status = 'COMPLETED' and checked_in_at >= ? and checked_in_at < ?", r);
        long walkIns = count("select count(*) from visits where source = 'WALK_IN' and checked_in_at >= ? and checked_in_at < ?", r);
        long noShows = count("""
                select count(*) from appointments a join appointment_slots s on s.id = a.slot_id
                where a.status = 'NO_SHOW' and s.slot_start >= ? and s.slot_start < ?""", r);
        long cancelled = count("""
                select count(*) from appointments a join appointment_slots s on s.id = a.slot_id
                where a.status = 'CANCELLED' and s.slot_start >= ? and s.slot_start < ?""", r);
        BigDecimal billed = money("select coalesce(sum(total_amount), 0) from invoices where created_at >= ? and created_at < ?", r);
        BigDecimal unpaid = money("""
                select coalesce(sum(total_amount - paid_amount), 0) from invoices
                where status in ('UNPAID', 'PARTIALLY_PAID') and created_at >= ? and created_at < ?""", r);
        BigDecimal collected = money("""
                select coalesce(sum(amount), 0) from payments where status = 'SUCCESS' and kind = 'PAYMENT'
                  and paid_at >= ? and paid_at < ?""", r);
        BigDecimal refunded = money("""
                select coalesce(sum(amount), 0) from payments where status = 'SUCCESS' and kind = 'REFUND'
                  and paid_at >= ? and paid_at < ?""", r);
        List<DayRow> byDay = jdbc.query("""
                select d::date as day,
                       (select count(*) from visits v where (v.checked_in_at at time zone 'Asia/Ho_Chi_Minh')::date = d::date) as visits,
                       (select coalesce(sum(case when p.kind = 'PAYMENT' then p.amount else -p.amount end), 0) from payments p
                         where p.status = 'SUCCESS' and (p.paid_at at time zone 'Asia/Ho_Chi_Minh')::date = d::date) as collected
                from generate_series(?::date, ?::date, interval '1 day') d order by d""",
                (rs, i) -> new DayRow(rs.getDate("day").toLocalDate(), rs.getLong("visits"), rs.getBigDecimal("collected")),
                from, to);
        List<DoctorRow> byDoctor = jdbc.query("""
                select v.doctor_id, e.full_name, count(*) as visits,
                       count(*) filter (where v.status = 'COMPLETED') as completed,
                       coalesce(sum(i.total_amount), 0) as billed
                from visits v join employees e on e.id = v.doctor_id left join invoices i on i.visit_id = v.id
                where v.checked_in_at >= ? and v.checked_in_at < ?
                group by v.doctor_id, e.full_name order by visits desc""",
                (rs, i) -> new DoctorRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getBigDecimal(5)),
                (Object[]) r);
        List<ServiceRow> byService = jdbc.query("""
                select it.service_id, it.description, sum(it.quantity), sum(it.line_total)
                from invoice_items it join invoices i on i.id = it.invoice_id
                where i.created_at >= ? and i.created_at < ?
                group by it.service_id, it.description order by sum(it.line_total) desc""",
                (rs, i) -> new ServiceRow(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4)),
                (Object[]) r);
        return new Summary(from, to, visits, completed, walkIns, noShows, cancelled, billed, collected, refunded, unpaid,
                byDay, byDoctor, byService);
    }

    /** Báo cáo hoạt động cá nhân của bác sĩ đang đăng nhập */
    @Transactional(readOnly = true)
    public DoctorSummary mine(AuthUser me, LocalDate from, LocalDate to) {
        Long doctorId = currentStaff.idOf(me);
        Timestamp[] r = range(from, to);
        Object[] args = {doctorId, r[0], r[1]};
        long visits = jdbc.queryForObject("select count(*) from visits where doctor_id = ? and checked_in_at >= ? and checked_in_at < ?", Long.class, args);
        long completed = jdbc.queryForObject("select count(*) from visits where doctor_id = ? and status = 'COMPLETED' and checked_in_at >= ? and checked_in_at < ?", Long.class, args);
        long followUps = jdbc.queryForObject("""
                select count(*) from emr_records m join visits v on v.id = m.visit_id
                where v.doctor_id = ? and m.follow_up_date is not null and v.checked_in_at >= ? and v.checked_in_at < ?""", Long.class, args);
        long noShows = jdbc.queryForObject("""
                select count(*) from appointments a join appointment_slots s on s.id = a.slot_id join work_sessions w on w.id = s.session_id
                where w.doctor_id = ? and a.status = 'NO_SHOW' and s.slot_start >= ? and s.slot_start < ?""", Long.class, args);
        long sessions = jdbc.queryForObject("""
                select count(*) from work_sessions where doctor_id = ? and status <> 'CANCELLED' and starts_at >= ? and starts_at < ?""",
                Long.class, args);
        return new DoctorSummary(from, to, visits, completed, followUps, noShows, sessions);
    }

    private static Timestamp[] range(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw ApiException.badRequest("Khoảng ngày không hợp lệ (tối đa 1 năm)");
        }
        return new Timestamp[]{Timestamp.from(VnTime.at(from, LocalTime.MIDNIGHT)), Timestamp.from(VnTime.at(to.plusDays(1), LocalTime.MIDNIGHT))};
    }

    private long count(String sql, Timestamp[] r) {
        return jdbc.queryForObject(sql, Long.class, (Object[]) r);
    }

    private BigDecimal money(String sql, Timestamp[] r) {
        return jdbc.queryForObject(sql, BigDecimal.class, (Object[]) r);
    }
}
