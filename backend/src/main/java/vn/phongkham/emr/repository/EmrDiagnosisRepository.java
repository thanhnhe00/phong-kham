package vn.phongkham.emr.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.emr.model.EmrDiagnosis;

public interface EmrDiagnosisRepository extends JpaRepository<EmrDiagnosis, Long> {

    List<EmrDiagnosis> findByEmrIdOrderById(Long emrId);

    /** Xóa ngay bằng câu lệnh để chèn lại danh sách mới không vướng ràng buộc một chẩn đoán chính */
    @Modifying(flushAutomatically = true)
    @Query("delete from EmrDiagnosis d where d.emrId = :emrId")
    void deleteAllOfEmr(@Param("emrId") Long emrId);
}
