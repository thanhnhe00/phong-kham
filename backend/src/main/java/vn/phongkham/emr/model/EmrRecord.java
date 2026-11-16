package vn.phongkham.emr.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/** Phiếu khám / bệnh án ngoại trú của một lượt khám (bố cục theo mẫu bệnh án ngoại trú, Thông tư 32/2023) */
@Entity
@Table(name = "emr_records")
@Getter
@Setter
public class EmrRecord {

    public enum Status { DRAFT, SIGNED, AMENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long visitId;
    private String templateCode = "OUTPATIENT_GENERAL";
    private String chiefComplaint;
    private String presentIllness;
    private String personalHistory;
    private String familyHistory;
    private String generalExam;
    private String organExam;
    private String paraclinicalSummary;
    private String conclusion;
    private String treatmentPlan;
    private String advice;
    private LocalDate followUpDate;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "record_status")
    private Status status = Status.DRAFT;

    private int version = 1;
    private Long signedBy;
    private Instant signedAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
