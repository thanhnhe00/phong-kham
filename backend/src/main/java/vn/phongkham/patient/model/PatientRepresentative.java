package vn.phongkham.patient.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Người đại diện hợp pháp (cha mẹ, người giám hộ) của bệnh nhân */
@Entity
@Table(name = "patient_representatives")
@Getter
@Setter
public class PatientRepresentative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private String fullName;
    private String relationship;
    private String phone;
    private String identityNumber;
    private String authorityNote;
    private Long verifiedBy;
    private Instant verifiedAt;
}
