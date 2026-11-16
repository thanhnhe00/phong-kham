package vn.phongkham.clinical.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.clinical.model.DocumentFile;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {

    java.util.List<DocumentFile> findByServiceResultIdIn(java.util.Collection<Long> resultIds);
}
