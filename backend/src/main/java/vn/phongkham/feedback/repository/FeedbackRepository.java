package vn.phongkham.feedback.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.phongkham.feedback.model.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByStatusInOrderByCreatedAtDesc(Collection<Feedback.Status> statuses);

    List<Feedback> findByPatientIdInOrderByCreatedAtDesc(Collection<Long> patientIds);
}
