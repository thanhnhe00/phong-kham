package vn.phongkham.room.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Phòng khám (buồng khám) để xếp lịch bác sĩ */
@Entity
@Table(name = "rooms")
@Getter
@Setter
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;
    /** Phòng chuyên dùng của chuyên khoa (null = dùng chung) */
    private Long specialtyId;
    private boolean isActive = true;
}
