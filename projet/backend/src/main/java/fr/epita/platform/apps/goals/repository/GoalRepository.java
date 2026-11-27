package fr.epita.platform.apps.goals.repository;

import fr.epita.platform.apps.goals.entity.Goal;
import fr.epita.platform.apps.goals.entity.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByEmployeeIdOrderByDueDateAsc(UUID employeeId);

    List<Goal> findByEmployeeIdInOrderByDueDateAsc(Collection<UUID> employeeIds);

    long countByEmployeeIdAndStatus(UUID employeeId, GoalStatus status);

    /** Échéance passée : les objectifs encore en cours passent en SUBMITTED. */
    @Modifying
    @Query("""
            update Goal g set g.status = fr.epita.platform.apps.goals.entity.GoalStatus.SUBMITTED, g.submittedAt = :now
            where g.status = fr.epita.platform.apps.goals.entity.GoalStatus.IN_PROGRESS and g.dueDate < :today
            """)
    int submitOverdue(LocalDate today, Instant now);
}
