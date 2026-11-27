package fr.epita.platform.apps.leave.repository;

import fr.epita.platform.apps.leave.entity.LeaveBalance;
import fr.epita.platform.apps.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    List<LeaveRequest> findByUserIdOrderByStartDateDesc(UUID userId);

    List<LeaveRequest> findByUserIdInOrderByStartDateAsc(Collection<UUID> userIds);

    @Query("""
            select r from LeaveRequest r where r.userId in :userIds
            and r.status = fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveStatus.APPROVED
            and r.startDate <= :to and r.endDate >= :from order by r.startDate
            """)
    List<LeaveRequest> approvedBetween(@Param("userIds") Collection<UUID> userIds, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
