package vn.phongkham.emr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import vn.phongkham.emr.model.EmrDiagnosis;
import vn.phongkham.emr.model.EmrRecord;

public final class EmrDtos {

    private EmrDtos() {
    }

    public record DiagnosisRequest(
            @Pattern(regexp = "^$|^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,4})?$", message = "Mã ICD-10 không đúng định dạng (vd. J06.9)") String icd10Code,
            @NotBlank(message = "Vui lòng nhập tên chẩn đoán") @Size(max = 255, message = "Tên chẩn đoán tối đa 255 ký tự") String name,
            EmrDiagnosis.Type type,
            String note) {
    }

    /** Nội dung phiếu khám theo bố cục mẫu bệnh án ngoại trú */
    public record EmrRequest(
            String chiefComplaint,
            String presentIllness,
            String personalHistory,
            String familyHistory,
            String generalExam,
            String organExam,
            String paraclinicalSummary,
            String conclusion,
            String treatmentPlan,
            String advice,
            LocalDate followUpDate,
            @Valid List<DiagnosisRequest> diagnoses) {
    }

    public record AmendRequest(@NotBlank(message = "Vui lòng nhập lý do điều chỉnh") String reason,
                               @NotNull(message = "Thiếu nội dung bệnh án") @Valid EmrRequest content) {
    }

    public record DiagnosisResponse(Long id, String icd10Code, String name, EmrDiagnosis.Type type, String note) {
    }

    public record EmrResponse(Long id, Long visitId, EmrRecord.Status status, int version, String chiefComplaint,
                              String presentIllness, String personalHistory, String familyHistory, String generalExam,
                              String organExam, String paraclinicalSummary, String conclusion, String treatmentPlan,
                              String advice, LocalDate followUpDate, List<DiagnosisResponse> diagnoses, Long signedBy,
                              String signedByName, Instant signedAt, Instant updatedAt) {
    }

    /** previousSnapshot: toàn bộ bệnh án trước khi sửa */
    public record AmendmentResponse(Long id, int fromVersion, String reason, Long amendedBy, String amendedByName,
                                    Instant amendedAt, Map<String, Object> previousSnapshot) {
    }
}
