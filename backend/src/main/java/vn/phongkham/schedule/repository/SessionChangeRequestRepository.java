package vn.phongkham.schedule.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.phongkham.schedule.model.ScheduleEnums.RequestStatus;
import vn.phongkham.schedule.model.SessionChangeRequest;

public interface SessionChangeRequestRepository extends JpaRepository<SessionChangeRequest, Long> {

    boolean existsBySessionIdAndStatusIn(Long sessionId, java.util.Collection<RequestStatus> statuses);

    /** anyStatus = true: không lọc trạng thái. employeeId = null: mọi yêu cầu (quản lý) */
    @Query("""
            select r from SessionChangeRequest r
            where (:anyStatus = true or r.status = :status)
              and (:employeeId is null or r.requestedBy = :employeeId or r.replacementDoctorId = :employeeId)
            order by r.createdAt desc
            """)
    List<SessionChangeRequest> search(@Param("anyStatus") boolean anyStatus, @Param("status") RequestStatus status,
                                      @Param("employeeId") Long employeeId);

    default List<SessionChangeRequest> search(RequestStatus status, Long employeeId) {
        return search(status == null, status == null ? RequestStatus.PENDING_MANAGER : status, employeeId);
    }
}
