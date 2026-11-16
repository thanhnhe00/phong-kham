package vn.phongkham.emr.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/** Chẩn đoán (mã ICD-10): một chẩn đoán chính, nhiều bệnh kèm theo / chẩn đoán phân biệt */
@Entity
@Table(name = "emr_diagnoses")
@Getter
@Setter
public class EmrDiagnosis {

    public enum Type { PRIMARY, SECONDARY, DIFFERENTIAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long emrId;
    private String icd10Code;
    private String diagnosisName;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "diagnosis_type")
    private Type diagnosisType = Type.PRIMARY;

    private String note;
}
