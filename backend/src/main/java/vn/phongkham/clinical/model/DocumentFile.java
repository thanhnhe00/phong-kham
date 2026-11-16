package vn.phongkham.clinical.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;
import vn.phongkham.clinical.model.ClinicalEnums.*;

/** Tệp đính kèm (ảnh, PDF kết quả). Nội dung nằm trên ổ đĩa / object storage, DB giữ storage_key */
@Entity
@Table(name = "document_files")
@Getter
@Setter
public class DocumentFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private Long visitId;
    private Long serviceResultId;
    private Long uploadedBy;
    private String fileName;
    private String storageKey;
    private String mimeType;
    private Long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "file_category")
    private FileCategory category = FileCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "file_visibility")
    private FileVisibility visibility = FileVisibility.STAFF_ONLY;

    private Instant createdAt = Instant.now();
}
