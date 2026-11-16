package vn.phongkham.patient.model;

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
import vn.phongkham.patient.model.PatientEnums.AllergySeverity;

@Entity
@Table(name = "patient_allergies")
@Getter
@Setter
public class PatientAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private String allergen;
    private String reaction;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "allergy_severity")
    private AllergySeverity severity = AllergySeverity.UNKNOWN;

    private Long recordedBy;
    private Instant recordedAt = Instant.now();
}
