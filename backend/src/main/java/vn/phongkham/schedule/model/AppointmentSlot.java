package vn.phongkham.schedule.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import vn.phongkham.schedule.model.ScheduleEnums.SlotStatus;

/** Một lượt đặt lịch (mặc định 30 phút) trong phiên khám; tối đa một bệnh nhân */
@Entity
@Table(name = "appointment_slots")
@Getter
@Setter
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Instant slotStart;
    private Instant slotEnd;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "slot_status")
    private SlotStatus status = SlotStatus.AVAILABLE;

    /** Có giá trị khi status = HELD (yêu cầu từ app đang chờ xác nhận) */
    private Instant heldUntil;

    public void hold(Instant until) {
        status = SlotStatus.HELD;
        heldUntil = until;
    }

    public void book() {
        status = SlotStatus.BOOKED;
        heldUntil = null;
    }

    public void release() {
        status = SlotStatus.AVAILABLE;
        heldUntil = null;
    }

    public void block() {
        status = SlotStatus.BLOCKED;
        heldUntil = null;
    }
}
